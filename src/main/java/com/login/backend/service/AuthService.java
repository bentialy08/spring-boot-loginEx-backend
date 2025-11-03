package com.login.backend.service;

import com.login.backend.dto.AuthResponse;
import com.login.backend.dto.LoginRequest;
import com.login.backend.dto.RegisterRequest;
import com.login.backend.dto.RefreshTokenRequest;
import com.login.backend.exception.InvalidCredentialsException;
import com.login.backend.exception.UserAlreadyExistsException;
import com.login.backend.model.RefreshToken;
import com.login.backend.model.Role;
import com.login.backend.model.User;
import com.login.backend.repository.UserRepository;
import com.login.backend.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public User register(RegisterRequest request) {
        log.info("Attempting to register user: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : Role.USER)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getUsername());

        return savedUser;
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        log.info("Login attempt for user: {}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // Generate new tokens (supports multiple devices)
        String accessToken = jwtUtil.generateAccessToken(user.getUsername(), user.getRole().name());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, ipAddress, userAgent);

        long activeTokenCount = refreshTokenService.countUserActiveTokens(user);
        log.info("User logged in successfully: {} from IP: {} (active devices: {})",
                user.getUsername(), ipAddress, activeTokenCount);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(jwtUtil.getAccessTokenExpiration())
                .build();
    }

    @Transactional
    public void logout(String accessToken, String refreshTokenString) {
        tokenBlacklistService.blacklistToken(accessToken);

        if (refreshTokenString != null && !refreshTokenString.isEmpty()) {
            refreshTokenService.revokeRefreshToken(refreshTokenString);
            log.info("User logged out from current device");
        } else {
            log.info("User logged out (access token blacklisted)");
        }
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refresh token request received");

        RefreshToken refreshToken = refreshTokenService.verifyRefreshToken(request.getRefreshToken());
        User user = refreshToken.getUser();

        String newAccessToken = jwtUtil.generateAccessToken(user.getUsername(), user.getRole().name());

        log.info("Access token refreshed for user: {}", user.getUsername());

        // Return the SAME refresh token (don't create a new one)
        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(jwtUtil.getAccessTokenExpiration())
                .build();
    }

    @Transactional
    public void logoutAllDevices(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        refreshTokenService.revokeAllUserTokens(user.getId());
        log.info("All sessions logged out for user: {}", username);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }
}
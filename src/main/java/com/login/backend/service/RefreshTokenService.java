package com.login.backend.service;

import com.login.backend.exception.TokenException;
import com.login.backend.model.RefreshToken;
import com.login.backend.model.User;
import com.login.backend.repository.RefreshTokenRepository;
import com.login.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.max-refresh-tokens-per-user:5}")
    private int maxTokensPerUser;

    @Transactional
    public RefreshToken createRefreshToken(User user, String ipAddress, String userAgent) {
        log.debug("Creating refresh token for user: {} from IP: {}", user.getUsername(), ipAddress);

        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserAndRevokedFalse(user);

        if (activeTokens.size() >= maxTokensPerUser) {
            log.info("User {} has {} active tokens, revoking oldest",
                    user.getUsername(), activeTokens.size());

            activeTokens.stream()
                    .sorted((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()))
                    .limit(activeTokens.size() - maxTokensPerUser + 1)
                    .forEach(token -> {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                        log.debug("Revoked old token: {} from device: {}",
                                token.getId(), token.getDeviceName());
                    });
        }

        String tokenString = jwtUtil.generateRefreshToken();
        long expirationMs = jwtUtil.getRefreshTokenExpiration();
        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(expirationMs / 1000);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenString)
                .user(user)
                .expiryDate(expiryDate)
                .revoked(false)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceName(extractDeviceName(userAgent))
                .build();

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.info("Created refresh token for user: {} from {} (total active: {})",
                user.getUsername(), saved.getDeviceName(), activeTokens.size() + 1);

        return saved;
    }

    @Transactional(readOnly = true)
    public RefreshToken verifyRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new TokenException("Refresh token not found"));

        if (refreshToken.isRevoked()) {
            log.warn("Attempted to use revoked refresh token for user: {} from device: {}",
                    refreshToken.getUser().getUsername(), refreshToken.getDeviceName());
            throw new TokenException("Refresh token has been revoked");
        }

        if (refreshToken.isExpired()) {
            log.warn("Attempted to use expired refresh token for user: {} from device: {}",
                    refreshToken.getUser().getUsername(), refreshToken.getDeviceName());
            throw new TokenException("Refresh token has expired");
        }

        return refreshToken;
    }

    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent(refreshToken -> {
                    refreshToken.setRevoked(true);
                    refreshTokenRepository.save(refreshToken);
                    log.info("Refresh token revoked for user: {} from device: {}",
                            refreshToken.getUser().getUsername(), refreshToken.getDeviceName());
                });
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        int revokedCount = refreshTokenRepository.revokeAllUserTokens(userId);
        log.info("Revoked {} refresh tokens for user ID: {}", revokedCount, userId);
    }

    @Transactional(readOnly = true)
    public List<RefreshToken> getUserActiveTokens(User user) {
        return refreshTokenRepository.findByUserAndRevokedFalse(user);
    }

    @Transactional(readOnly = true)
    public long countUserActiveTokens(User user) {
        return refreshTokenRepository.countByUserAndRevokedFalse(user);
    }

    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        int deleted = refreshTokenRepository.deleteExpiredTokens(now);
        log.info("Cleaned up {} expired refresh tokens", deleted);
    }

    private String extractDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Device";
        }

        String ua = userAgent.toLowerCase();

        if (ua.contains("iphone")) return "iPhone";
        if (ua.contains("ipad")) return "iPad";
        if (ua.contains("android") && ua.contains("mobile")) return "Android Phone";
        if (ua.contains("android")) return "Android Tablet";

        if (ua.contains("windows")) {
            if (ua.contains("edge")) return "Windows PC (Edge)";
            if (ua.contains("chrome")) return "Windows PC (Chrome)";
            if (ua.contains("firefox")) return "Windows PC (Firefox)";
            return "Windows PC";
        }

        if (ua.contains("macintosh") || ua.contains("mac os")) {
            if (ua.contains("safari") && !ua.contains("chrome")) return "Mac (Safari)";
            if (ua.contains("chrome")) return "Mac (Chrome)";
            if (ua.contains("firefox")) return "Mac (Firefox)";
            return "Mac";
        }

        if (ua.contains("linux")) {
            if (ua.contains("chrome")) return "Linux PC (Chrome)";
            if (ua.contains("firefox")) return "Linux PC (Firefox)";
            return "Linux PC";
        }

        if (ua.contains("chrome")) return "Chrome Browser";
        if (ua.contains("firefox")) return "Firefox Browser";
        if (ua.contains("safari")) return "Safari Browser";
        if (ua.contains("edge")) return "Edge Browser";

        return "Unknown Device";
    }
}
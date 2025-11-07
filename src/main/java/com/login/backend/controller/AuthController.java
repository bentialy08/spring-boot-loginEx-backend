package com.login.backend.controller;

import com.login.backend.dto.AuthResponse;
import com.login.backend.dto.LoginRequest;
import com.login.backend.dto.RefreshTokenRequest;
import com.login.backend.dto.RegisterRequest;
import com.login.backend.model.RefreshToken;
import com.login.backend.model.User;
import com.login.backend.service.AuthService;
import com.login.backend.service.RefreshTokenService;
import com.login.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {  
        AuthResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) Map<String, String> body) {

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            String refreshToken = body != null ? body.get("refreshToken") : null;
            authService.logout(accessToken, refreshToken);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAllDevices(Authentication authentication) {
        String username = authentication.getName();
        authService.logoutAllDevices(username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getActiveSessions(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required to view sessions."));
        }

        String username = authentication.getName();
        User user = userService.findByUsername(username);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        List<RefreshToken> activeTokens = refreshTokenService.getUserActiveTokens(user);

        Map<String, Object> response = new HashMap<>();
        response.put("username", username);
        response.put("activeDeviceCount", activeTokens.size());
        response.put("sessions", activeTokens.stream()
                .map(token -> {
                    Map<String, Object> session = new HashMap<>();
                    session.put("id", token.getId());
                    session.put("deviceName", token.getDeviceName());
                    session.put("ipAddress", token.getIpAddress());
                    session.put("createdAt", token.getCreatedAt());
                    session.put("expiresAt", token.getExpiryDate());
                    session.put("current", false); // TODO: mark current session
                    return session;
                })
                .collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/sessions/{tokenId}")
    public ResponseEntity<Void> revokeSession(
            @PathVariable Long tokenId,
            Authentication authentication) {
        String username = authentication.getName();
        User user = userService.findByUsername(username);

        // Find the token and verify it belongs to this user
        RefreshToken token = refreshTokenService.getUserActiveTokens(user).stream()
                .filter(t -> t.getId().equals(tokenId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Token not found"));

        refreshTokenService.revokeRefreshToken(token.getToken());
        return ResponseEntity.noContent().build();
    }
}

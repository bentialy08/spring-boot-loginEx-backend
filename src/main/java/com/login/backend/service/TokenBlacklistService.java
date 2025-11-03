package com.login.backend.service;

import com.login.backend.model.BlacklistedToken;
import com.login.backend.repository.BlacklistedTokenRepository;
import com.login.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public void blacklistToken(String token) {
        if (blacklistedTokenRepository.existsByToken(token)) {
            log.debug("Token already blacklisted");
            return;
        }

        try {
            Date expiration = jwtUtil.extractExpiration(token);
            LocalDateTime expiryDate = expiration.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            BlacklistedToken blacklistedToken = BlacklistedToken.builder()
                    .token(token)
                    .expiryDate(expiryDate)
                    .build();

            blacklistedTokenRepository.save(blacklistedToken);
            log.info("Token blacklisted successfully");
        } catch (Exception e) {
            log.error("Error blacklisting token: {}", e.getMessage());
            throw new RuntimeException("Failed to blacklist token");
        }
    }

    @Transactional(readOnly = true)
    public boolean isTokenBlacklisted(String token) {
        return blacklistedTokenRepository.existsByToken(token);
    }
}
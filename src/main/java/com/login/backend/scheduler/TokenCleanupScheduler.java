package com.login.backend.scheduler;

import com.login.backend.repository.BlacklistedTokenRepository;
import com.login.backend.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    // Run every day at 2 AM
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting token cleanup job");

        refreshTokenService.cleanupExpiredTokens();

        LocalDateTime now = LocalDateTime.now();
        int deletedBlacklistedTokens = blacklistedTokenRepository.deleteExpiredTokens(now);
        log.info("Deleted {} expired blacklisted tokens", deletedBlacklistedTokens);

        log.info("Token cleanup job completed");
    }
}
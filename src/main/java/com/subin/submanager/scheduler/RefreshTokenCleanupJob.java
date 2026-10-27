package com.subin.submanager.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.subin.submanager.auth.domain.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 폐기되거나 만료된 지 30일이 지난 리프레시 토큰 행을 매일 04:00 KST에 지운다.
 * 폐기 직후에는 재사용 탐지에 필요하므로 남겨 둔다.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);
    private static final Duration RETENTION = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanUp() {
        int deleted = refreshTokenRepository.deleteExpiredOrRevokedBefore(Instant.now(clock).minus(RETENTION));
        if (deleted > 0) {
            log.info("오래된 리프레시 토큰 {}건 삭제", deleted);
        }
    }
}

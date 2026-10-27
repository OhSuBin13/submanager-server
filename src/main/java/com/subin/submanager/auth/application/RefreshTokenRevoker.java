package com.subin.submanager.auth.application;

import java.time.Clock;
import java.time.Instant;

import com.subin.submanager.auth.domain.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재사용 탐지 때 폐기가 예외와 함께 롤백되지 않도록, 폐기만 별도 트랜잭션으로 커밋한다.
 */
@Component
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public RefreshTokenRevoker(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAll(Long userId) {
        return refreshTokenRepository.revokeAllByUser(userId, Instant.now(clock));
    }
}

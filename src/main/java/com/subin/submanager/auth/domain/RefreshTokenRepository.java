package com.subin.submanager.auth.domain;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 갱신 중 같은 토큰으로 동시에 들어온 요청이 한 건만 성공하도록 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** 재사용 탐지·탈퇴 시 그 회원의 살아 있는 토큰을 전부 폐기한다. */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllByUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** 같은 기기로 다시 로그인하면 그 기기의 이전 토큰을 폐기한다(기기당 살아 있는 토큰 1개). */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.user.id = :userId and t.deviceId = :deviceId and t.revokedAt is null")
    int revokeByUserAndDevice(@Param("userId") Long userId, @Param("deviceId") String deviceId, @Param("now") Instant now);

    /** 정리 스케줄러용. 폐기되거나 만료된 지 오래된 행을 지운다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff or t.revokedAt < :cutoff")
    int deleteExpiredOrRevokedBefore(@Param("cutoff") Instant cutoff);
}

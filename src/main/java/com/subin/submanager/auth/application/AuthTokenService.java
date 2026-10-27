package com.subin.submanager.auth.application;

import java.time.Clock;
import java.time.Instant;

import com.subin.submanager.auth.api.dto.TokenResponse;
import com.subin.submanager.auth.domain.RefreshToken;
import com.subin.submanager.auth.domain.RefreshTokenRepository;
import com.subin.submanager.auth.jwt.JwtProvider;
import com.subin.submanager.global.config.JwtProperties;
import com.subin.submanager.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토큰 발급·갱신·폐기. 로그인 4종은 회원을 찾거나 만든 뒤 {@link #issueTokens}만 호출한다.
 */
@Service
@Transactional
public class AuthTokenService {

    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final Clock clock;

    public AuthTokenService(JwtProvider jwtProvider,
                            JwtProperties jwtProperties,
                            RefreshTokenGenerator refreshTokenGenerator,
                            RefreshTokenRepository refreshTokenRepository,
                            RefreshTokenRevoker refreshTokenRevoker,
                            Clock clock) {
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.clock = clock;
    }

    /**
     * 계정 상태를 검사하고 액세스·리프레시 토큰을 새로 발급한다. 같은 기기의 이전 리프레시 토큰은 폐기한다.
     *
     * @throws AuthException USER_SUSPENDED(정지), USER_WITHDRAWN(탈퇴)
     */
    public TokenResponse issueTokens(User user, String deviceId) {
        Instant now = Instant.now(clock);
        checkStatus(user);

        refreshTokenRepository.revokeByUserAndDevice(user.getId(), deviceId, now);

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole(), now);
        String rawRefreshToken = refreshTokenGenerator.generate();
        Instant refreshExpiresAt = now.plus(jwtProperties.refreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(
                user, refreshTokenGenerator.hash(rawRefreshToken), deviceId, refreshExpiresAt));

        return TokenResponse.of(user, accessToken, jwtProperties.accessTokenTtl(), rawRefreshToken, refreshExpiresAt);
    }

    /**
     * 리프레시 토큰을 검증하고 폐기한 뒤 새 토큰 쌍을 발급한다(회전).
     * 이미 폐기된 토큰이 들어오면 탈취로 보고 그 회원의 토큰을 전부 폐기한다.
     *
     * @throws AuthException AUTH_REFRESH_INVALID, AUTH_REFRESH_REUSED, AUTH_REFRESH_EXPIRED, USER_SUSPENDED, USER_WITHDRAWN
     */
    public TokenResponse refresh(String rawRefreshToken, String deviceId) {
        Instant now = Instant.now(clock);
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(refreshTokenGenerator.hash(rawRefreshToken))
                .orElseThrow(() -> new AuthException(AuthErrorCode.AUTH_REFRESH_INVALID));

        if (token.isRevoked()) {
            refreshTokenRevoker.revokeAll(token.getUser().getId());
            throw new AuthException(AuthErrorCode.AUTH_REFRESH_REUSED);
        }
        if (token.isExpired(now)) {
            throw new AuthException(AuthErrorCode.AUTH_REFRESH_EXPIRED);
        }

        token.revoke(now);
        return issueTokens(token.getUser(), deviceId);
    }

    /** 해당 리프레시 토큰을 폐기한다. 없거나 이미 폐기됐어도 예외 없이 끝난다(로그아웃은 항상 성공). */
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(Instant.now(clock)));
    }

    /** 그 회원의 살아 있는 리프레시 토큰을 전부 폐기한다. 탈퇴 처리에서 사용한다. */
    public void revokeAll(Long userId) {
        refreshTokenRepository.revokeAllByUser(userId, Instant.now(clock));
    }

    private void checkStatus(User user) {
        if (user.isSuspended()) {
            String reason = user.getSuspensionReason();
            String message = reason == null
                    ? AuthErrorCode.USER_SUSPENDED.defaultMessage()
                    : AuthErrorCode.USER_SUSPENDED.defaultMessage() + " 사유: " + reason;
            throw new AuthException(AuthErrorCode.USER_SUSPENDED, message);
        }
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }
    }
}

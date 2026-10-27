package com.subin.submanager.auth.api.dto;

import java.time.Duration;
import java.time.Instant;

import com.subin.submanager.user.domain.User;

/** 로그인 4종과 POST /auth/refresh가 공통으로 돌려주는 응답. */
public record TokenResponse(
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserSummary user) {

    public static TokenResponse of(User user, String accessToken, Duration accessTokenTtl,
                                   String refreshToken, Instant refreshTokenExpiresAt) {
        return new TokenResponse(accessToken, accessTokenTtl.toSeconds(), refreshToken, refreshTokenExpiresAt,
                UserSummary.from(user));
    }
}

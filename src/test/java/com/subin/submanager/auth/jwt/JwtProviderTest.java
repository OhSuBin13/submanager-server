package com.subin.submanager.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import com.subin.submanager.auth.application.AuthErrorCode;
import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.global.config.JwtProperties;
import com.subin.submanager.user.domain.UserRole;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "test-only-jwt-secret-0123456789abcdefghijklmnop";

    private final JwtProvider provider = new JwtProvider(
            new JwtProperties(SECRET, Duration.ofHours(1), Duration.ofDays(30)));

    @Test
    void 발급한_토큰을_검증하면_같은_사용자_정보가_나온다() {
        String token = provider.createAccessToken(42L, UserRole.MEMBER, Instant.now());

        AuthPrincipal principal = provider.parse(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.role()).isEqualTo(UserRole.MEMBER);
    }

    @Test
    void 만료된_토큰은_AUTH_TOKEN_EXPIRED() {
        String token = provider.createAccessToken(42L, UserRole.MEMBER, Instant.now().minus(Duration.ofMinutes(62)));

        assertThatThrownBy(() -> provider.parse(token))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    void 만료_후_60초_이내면_시계_오차로_허용한다() {
        String token = provider.createAccessToken(42L, UserRole.MEMBER, Instant.now().minus(Duration.ofSeconds(3630)));

        assertThat(provider.parse(token).userId()).isEqualTo(42L);
    }

    @Test
    void 다른_키로_서명한_토큰은_AUTH_TOKEN_INVALID() {
        JwtProvider other = new JwtProvider(
                new JwtProperties("another-secret-0123456789abcdefghijklmnopqrstuv", Duration.ofHours(1), Duration.ofDays(30)));
        String token = other.createAccessToken(42L, UserRole.MEMBER, Instant.now());

        assertThatThrownBy(() -> provider.parse(token))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void 형식이_아닌_문자열은_AUTH_TOKEN_INVALID() {
        assertThatThrownBy(() -> provider.parse("not-a-jwt"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID);
    }
}

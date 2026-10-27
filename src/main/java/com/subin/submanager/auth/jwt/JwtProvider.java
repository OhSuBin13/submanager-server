package com.subin.submanager.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.subin.submanager.auth.application.AuthErrorCode;
import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.global.config.JwtProperties;
import com.subin.submanager.user.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰(HS256 JWT)을 만들고 검증한다. DB는 보지 않는다.
 */
@Component
public class JwtProvider {

    public static final String ISSUER = "submanager";
    static final String ROLE_CLAIM = "role";
    /** 서버와 앱 사이 시계 오차 허용 */
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final SecretKey key;
    private final JwtProperties properties;
    private final JwtParser parser;

    public JwtProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build();
    }

    public String createAccessToken(Long userId, UserRole role, Instant now) {
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 서명·발급자·만료를 검증하고 사용자 정보를 돌려준다.
     *
     * @throws AuthException AUTH_TOKEN_EXPIRED(만료) 또는 AUTH_TOKEN_INVALID(그 외 모든 실패)
     */
    public AuthPrincipal parse(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            UserRole role = UserRole.valueOf(claims.get(ROLE_CLAIM, String.class));
            return new AuthPrincipal(userId, role);
        } catch (ExpiredJwtException e) {
            throw new AuthException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new AuthException(AuthErrorCode.AUTH_TOKEN_INVALID);
        }
    }
}

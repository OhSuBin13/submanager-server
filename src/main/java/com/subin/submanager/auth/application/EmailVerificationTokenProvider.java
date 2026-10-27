package com.subin.submanager.auth.application;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.subin.submanager.auth.domain.UserIdentity;
import com.subin.submanager.auth.jwt.JwtProvider;
import com.subin.submanager.global.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

/**
 * 이메일 인증 링크에 넣는 토큰. DB 없이 서명된 JWT로 만들고, purpose 클레임으로 액세스 토큰과 구분한다.
 */
@Component
public class EmailVerificationTokenProvider {

    static final String PURPOSE = "email_verify";
    static final Duration TTL = Duration.ofHours(24);

    private final SecretKey key;
    private final JwtParser parser;

    public EmailVerificationTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(JwtProvider.ISSUER)
                .require(JwtProvider.PURPOSE_CLAIM, PURPOSE)
                .build();
    }

    public String create(UserIdentity identity, Instant now) {
        return Jwts.builder()
                .issuer(JwtProvider.ISSUER)
                .subject(String.valueOf(identity.getId()))
                .claim(JwtProvider.PURPOSE_CLAIM, PURPOSE)
                .claim("email", identity.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TTL)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * @return 인증할 user_identities.id
     * @throws AuthException EMAIL_VERIFICATION_INVALID(서명·용도·만료 어느 것이든 틀리면)
     */
    public Long parse(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new AuthException(AuthErrorCode.EMAIL_VERIFICATION_INVALID);
        }
    }
}

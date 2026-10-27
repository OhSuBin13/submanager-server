package com.subin.submanager.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

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
 * 발급 당시 비밀번호 해시의 지문도 넣어, 그 사이 비밀번호가 바뀌었으면 링크가 무효가 되게 한다
 * (다른 사람이 같은 주소로 다시 가입해 비밀번호를 바꿔치기한 뒤 원래 링크로 인증되는 일을 막는다).
 */
@Component
public class EmailVerificationTokenProvider {

    static final String PURPOSE = "email_verify";
    static final String PASSWORD_CLAIM = "pwd";
    static final Duration TTL = Duration.ofHours(24);
    private static final int FINGERPRINT_LENGTH = 16;

    /** 인증 링크가 가리키는 계정과, 그 링크가 확인해 주는 비밀번호의 지문. */
    public record Verification(Long identityId, String passwordFingerprint) {
    }

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
                .claim(PASSWORD_CLAIM, fingerprint(identity.getPasswordHash()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TTL)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * @throws AuthException EMAIL_VERIFICATION_INVALID(서명·용도·만료 어느 것이든 틀리면)
     */
    public Verification parse(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            String fingerprint = claims.get(PASSWORD_CLAIM, String.class);
            if (fingerprint == null) {
                throw new AuthException(AuthErrorCode.EMAIL_VERIFICATION_INVALID);
            }
            return new Verification(Long.valueOf(claims.getSubject()), fingerprint);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new AuthException(AuthErrorCode.EMAIL_VERIFICATION_INVALID);
        }
    }

    /** 비밀번호 해시의 SHA-256 앞 16자. 토큰에 해시 자체를 싣지 않으면서 바뀌었는지는 알 수 있다. */
    static String fingerprint(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

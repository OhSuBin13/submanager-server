package com.subin.submanager.auth.social;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.subin.submanager.auth.application.AuthErrorCode;
import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.auth.domain.AuthProvider;
import com.subin.submanager.global.config.SocialProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 구글 ID 토큰 검증. 구글 공개키(JWKS)로 서명을 확인하고 iss·aud·exp를 검사한다.
 * 공개키는 NimbusJwtDecoder가 받아 캐시하므로 로그인마다 구글을 부르지 않는다.
 */
@Component
public class GoogleTokenVerifier implements SocialTokenVerifier {

    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final JwtDecoder decoder;

    @Autowired
    public GoogleTokenVerifier(SocialProperties properties) {
        this(NimbusJwtDecoder.withJwkSetUri(properties.google().jwkSetUri()).build(), properties.google().clientId());
    }

    /** 테스트에서 공개키를 직접 넣기 위한 생성자. */
    GoogleTokenVerifier(NimbusJwtDecoder decoder, String clientId) {
        decoder.setJwtValidator(validator(clientId));
        this.decoder = decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(String clientId) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                new JwtClaimValidator<String>(JwtClaimNames.ISS, iss -> iss != null && ISSUERS.contains(iss)),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(clientId)));
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public SocialUserInfo verify(String token) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (BadJwtException e) {
            throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_INVALID);
        } catch (JwtException e) {
            throw new AuthException(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_INVALID);
        }
        String email = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")) ? jwt.getClaimAsString("email") : null;
        return new SocialUserInfo(AuthProvider.GOOGLE, sub, jwt.getClaimAsString("name"), email);
    }
}

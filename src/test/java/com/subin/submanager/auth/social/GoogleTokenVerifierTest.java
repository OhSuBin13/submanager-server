package com.subin.submanager.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import com.subin.submanager.auth.application.AuthErrorCode;
import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.auth.domain.AuthProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "our-client-id.apps.googleusercontent.com";

    private static KeyPair keyPair;
    private static GoogleTokenVerifier verifier;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        verifier = new GoogleTokenVerifier(
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build(), CLIENT_ID);
    }

    @Test
    void 올바른_ID_토큰이면_sub와_이름_인증된_이메일을_돌려준다() {
        String token = idToken("https://accounts.google.com", CLIENT_ID, Instant.now(),
                Map.of("name", "수빈", "email", "subin@gmail.com", "email_verified", true));

        SocialUserInfo info = verifier.verify(token);

        assertThat(info).isEqualTo(new SocialUserInfo(AuthProvider.GOOGLE, "sub-1", "수빈", "subin@gmail.com"));
    }

    @Test
    void 이메일이_미인증이면_이메일을_저장하지_않는다() {
        String token = idToken("accounts.google.com", CLIENT_ID, Instant.now(),
                Map.of("email", "subin@gmail.com", "email_verified", false));

        assertThat(verifier.verify(token).email()).isNull();
    }

    @Test
    void aud가_다르면_SOCIAL_TOKEN_INVALID() {
        String token = idToken("https://accounts.google.com", "other-client", Instant.now(), Map.of());

        assertError(token, AuthErrorCode.SOCIAL_TOKEN_INVALID);
    }

    @Test
    void 만료되면_SOCIAL_TOKEN_INVALID() {
        String token = idToken("https://accounts.google.com", CLIENT_ID, Instant.now().minus(Duration.ofHours(2)),
                Map.of());

        assertError(token, AuthErrorCode.SOCIAL_TOKEN_INVALID);
    }

    @Test
    void 발급자가_구글이_아니면_SOCIAL_TOKEN_INVALID() {
        String token = idToken("https://evil.example.com", CLIENT_ID, Instant.now(), Map.of());

        assertError(token, AuthErrorCode.SOCIAL_TOKEN_INVALID);
    }

    @Test
    void 다른_키로_서명하면_SOCIAL_TOKEN_INVALID() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair other = generator.generateKeyPair();
        String token = Jwts.builder().issuer("https://accounts.google.com").audience().add(CLIENT_ID).and()
                .subject("sub-1").issuedAt(new Date()).expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(other.getPrivate(), Jwts.SIG.RS256).compact();

        assertError(token, AuthErrorCode.SOCIAL_TOKEN_INVALID);
    }

    private static String idToken(String issuer, String audience, Instant issuedAt, Map<String, Object> claims) {
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject("sub-1")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(Duration.ofHours(1))))
                .claims().add(claims).and()
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static void assertError(String token, AuthErrorCode expected) {
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(expected);
    }
}

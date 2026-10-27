package com.subin.submanager.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.subin.submanager.auth.application.AuthErrorCode;
import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.auth.domain.AuthProvider;
import com.subin.submanager.global.config.SocialProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoTokenVerifierTest {

    private static final long APP_ID = 123456L;

    private MockRestServiceServer server;
    private KakaoTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new KakaoTokenVerifier(builder.build(), new SocialProperties(
                new SocialProperties.Kakao(APP_ID),
                new SocialProperties.Google("client", "https://example.com/certs")));
    }

    @Test
    void 우리_앱_토큰이면_회원번호와_닉네임을_돌려준다() {
        server.expect(requestTo(KakaoTokenVerifier.TOKEN_INFO_URL))
                .andExpect(header("Authorization", "Bearer kakao-token"))
                .andRespond(withSuccess("{\"id\":42,\"app_id\":123456,\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(KakaoTokenVerifier.USER_ME_URL))
                .andRespond(withSuccess("{\"id\":42,\"kakao_account\":{\"profile\":{\"nickname\":\"수빈\"}}}",
                        MediaType.APPLICATION_JSON));

        SocialUserInfo info = verifier.verify("kakao-token");

        assertThat(info).isEqualTo(new SocialUserInfo(AuthProvider.KAKAO, "42", "수빈", null));
        server.verify();
    }

    @Test
    void 다른_앱의_토큰이면_SOCIAL_TOKEN_INVALID() {
        server.expect(requestTo(KakaoTokenVerifier.TOKEN_INFO_URL))
                .andRespond(withSuccess("{\"id\":42,\"app_id\":999}", MediaType.APPLICATION_JSON));

        assertError(AuthErrorCode.SOCIAL_TOKEN_INVALID);
    }

    @Test
    void 카카오가_401이면_SOCIAL_TOKEN_INVALID() {
        server.expect(requestTo(KakaoTokenVerifier.TOKEN_INFO_URL)).andRespond(withUnauthorizedRequest());

        assertError(AuthErrorCode.SOCIAL_TOKEN_INVALID);
    }

    @Test
    void 카카오가_500이면_SOCIAL_PROVIDER_ERROR() {
        server.expect(requestTo(KakaoTokenVerifier.TOKEN_INFO_URL)).andRespond(withServerError());

        assertError(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
    }

    private void assertError(AuthErrorCode expected) {
        assertThatThrownBy(() -> verifier.verify("kakao-token"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(expected);
    }
}

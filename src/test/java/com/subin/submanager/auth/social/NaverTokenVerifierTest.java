package com.subin.submanager.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.subin.submanager.auth.application.AuthErrorCode;
import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.auth.domain.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverTokenVerifierTest {

    private MockRestServiceServer server;
    private NaverTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new NaverTokenVerifier(builder.build());
    }

    @Test
    void resultcode가_00이면_id와_닉네임_이메일을_돌려준다() {
        server.expect(requestTo(NaverTokenVerifier.PROFILE_URL))
                .andExpect(header("Authorization", "Bearer naver-token"))
                .andRespond(withSuccess("{\"resultcode\":\"00\",\"message\":\"success\","
                        + "\"response\":{\"id\":\"abc-123\",\"nickname\":\"수빈\",\"email\":\"subin@naver.com\"}}",
                        MediaType.APPLICATION_JSON));

        SocialUserInfo info = verifier.verify("naver-token");

        assertThat(info).isEqualTo(new SocialUserInfo(AuthProvider.NAVER, "abc-123", "수빈", "subin@naver.com"));
    }

    @Test
    void 닉네임이_없으면_이름을_쓴다() {
        server.expect(requestTo(NaverTokenVerifier.PROFILE_URL))
                .andRespond(withSuccess("{\"resultcode\":\"00\",\"response\":{\"id\":\"abc\",\"name\":\"오수빈\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(verifier.verify("t").nickname()).isEqualTo("오수빈");
    }

    @Test
    void resultcode가_00이_아니면_SOCIAL_TOKEN_INVALID() {
        server.expect(requestTo(NaverTokenVerifier.PROFILE_URL))
                .andRespond(withSuccess("{\"resultcode\":\"024\",\"message\":\"Authentication failed\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify("t"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_TOKEN_INVALID);
    }

    @Test
    void 네이버가_401이면_SOCIAL_TOKEN_INVALID() {
        server.expect(requestTo(NaverTokenVerifier.PROFILE_URL)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> verifier.verify("t"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_TOKEN_INVALID);
    }
}

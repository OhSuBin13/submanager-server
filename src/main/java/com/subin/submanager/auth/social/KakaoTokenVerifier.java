package com.subin.submanager.auth.social;

import com.subin.submanager.auth.application.AuthErrorCode;
import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.auth.domain.AuthProvider;
import com.subin.submanager.global.config.SocialProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 액세스 토큰 검증. access_token_info로 우리 앱의 토큰인지 확인한 뒤 v2/user/me로 회원번호와 닉네임을 얻는다.
 */
@Component
public class KakaoTokenVerifier implements SocialTokenVerifier {

    static final String TOKEN_INFO_URL = "https://kapi.kakao.com/v1/user/access_token_info";
    static final String USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final long appId;

    public KakaoTokenVerifier(RestClient socialRestClient, SocialProperties properties) {
        this.restClient = socialRestClient;
        this.appId = properties.kakao().appId();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public SocialUserInfo verify(String token) {
        TokenInfo info = get(TOKEN_INFO_URL, token, TokenInfo.class);
        if (info == null || info.appId() == null || info.appId() != appId) {
            throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_INVALID);
        }
        UserMe me = get(USER_ME_URL, token, UserMe.class);
        if (me == null || me.id() == null) {
            throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_INVALID);
        }
        String nickname = me.kakaoAccount() != null && me.kakaoAccount().profile() != null
                ? me.kakaoAccount().profile().nickname()
                : null;
        return new SocialUserInfo(AuthProvider.KAKAO, String.valueOf(me.id()), nickname, null);
    }

    private <T> T get(String url, String token, Class<T> type) {
        try {
            return restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(type);
        } catch (HttpClientErrorException e) {
            throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_INVALID);
        } catch (RestClientException e) {
            throw new AuthException(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
        }
    }

    record TokenInfo(Long id, @JsonProperty("app_id") Long appId) {
    }

    record UserMe(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    }

    record KakaoAccount(Profile profile) {
    }

    record Profile(String nickname) {
    }
}

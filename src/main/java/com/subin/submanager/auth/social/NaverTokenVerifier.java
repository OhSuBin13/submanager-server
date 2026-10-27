package com.subin.submanager.auth.social;

import com.subin.submanager.auth.application.AuthErrorCode;
import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.auth.domain.AuthProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 네이버 액세스 토큰 검증. 회원 프로필 API 응답의 resultcode가 "00"이면 유효한 토큰이다.
 * 네이버는 응답에 앱 ID가 없어 "우리 앱의 토큰인지"는 확인할 수 없지만, id가 앱마다 달라 다른 회원으로 들어갈 수는 없다.
 */
@Component
public class NaverTokenVerifier implements SocialTokenVerifier {

    static final String PROFILE_URL = "https://openapi.naver.com/v1/nid/me";
    private static final String SUCCESS = "00";

    private final RestClient restClient;

    public NaverTokenVerifier(RestClient socialRestClient) {
        this.restClient = socialRestClient;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.NAVER;
    }

    @Override
    public SocialUserInfo verify(String token) {
        ProfileResponse body;
        try {
            body = restClient.get()
                    .uri(PROFILE_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(ProfileResponse.class);
        } catch (HttpClientErrorException e) {
            throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_INVALID);
        } catch (RestClientException e) {
            throw new AuthException(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
        }
        if (body == null || !SUCCESS.equals(body.resultcode()) || body.response() == null
                || body.response().id() == null || body.response().id().isBlank()) {
            throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_INVALID);
        }
        Profile profile = body.response();
        String nickname = profile.nickname() != null && !profile.nickname().isBlank()
                ? profile.nickname()
                : profile.name();
        return new SocialUserInfo(AuthProvider.NAVER, profile.id(), nickname, profile.email());
    }

    record ProfileResponse(String resultcode, String message, Profile response) {
    }

    record Profile(String id, String nickname, String name, String email) {
    }
}

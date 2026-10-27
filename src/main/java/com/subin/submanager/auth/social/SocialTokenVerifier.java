package com.subin.submanager.auth.social;

import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.auth.domain.AuthProvider;

/** 제공자마다 하나씩. 앱이 보낸 토큰을 그 회사 방식으로 검증하고 사용자 정보를 돌려준다. */
public interface SocialTokenVerifier {

    AuthProvider provider();

    /**
     * @throws AuthException SOCIAL_TOKEN_INVALID(토큰 만료·위조·다른 앱), SOCIAL_PROVIDER_ERROR(그 회사 장애·타임아웃)
     */
    SocialUserInfo verify(String token);
}

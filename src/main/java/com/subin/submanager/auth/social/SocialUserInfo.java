package com.subin.submanager.auth.social;

import com.subin.submanager.auth.domain.AuthProvider;

/**
 * 소셜 토큰을 검증해 얻은 사용자 정보. providerUserId는 그 회사의 고유 ID, nickname·email은 없으면 null.
 */
public record SocialUserInfo(AuthProvider provider, String providerUserId, String nickname, String email) {
}

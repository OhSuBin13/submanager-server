package com.subin.submanager.auth.jwt;

import com.subin.submanager.user.domain.UserRole;

/** 액세스 토큰에서 꺼낸 사용자 정보. 컨트롤러에서 @AuthenticationPrincipal로 받는다. */
public record AuthPrincipal(Long userId, UserRole role) {
}

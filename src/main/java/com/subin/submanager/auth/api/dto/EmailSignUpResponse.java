package com.subin.submanager.auth.api.dto;

/** 가입 직후에는 토큰을 주지 않는다. 앱은 이 주소로 인증 메일을 보냈다고 안내한다. */
public record EmailSignUpResponse(String email) {
}

package com.subin.submanager.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 카카오·네이버는 액세스 토큰, 구글은 ID 토큰을 token에 넣는다. */
public record SocialLoginRequest(
        @NotBlank String token,
        @NotBlank @Size(max = 100) String deviceId) {
}

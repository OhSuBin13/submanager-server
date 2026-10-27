package com.subin.submanager.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailLoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 64) String password,
        @NotBlank @Size(max = 100) String deviceId) {
}

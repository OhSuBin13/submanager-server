package com.subin.submanager.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailSignUpRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호에는 영문과 숫자가 각각 1자 이상 있어야 합니다.")
        String password,
        @Size(max = 30) String nickname) {
}

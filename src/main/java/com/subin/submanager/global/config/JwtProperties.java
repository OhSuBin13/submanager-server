package com.subin.submanager.global.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml의 app.jwt.* 설정. secret이 비어 있거나 32자 미만이면 서버가 시작하지 않는다.
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT_SECRET은 32자 이상이어야 합니다.") String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {
}

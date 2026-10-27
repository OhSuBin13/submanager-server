package com.subin.submanager.global.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** application.yml의 app.social.*. 소셜 토큰이 우리 앱 것인지 확인할 때 쓰는 값들. 네이버는 필요 없다. */
@Validated
@ConfigurationProperties("app.social")
public record SocialProperties(@NotNull @Valid Kakao kakao, @NotNull @Valid Google google) {

    public record Kakao(@NotNull Long appId) {
    }

    public record Google(@NotBlank String clientId, @NotBlank String jwkSetUri) {
    }
}

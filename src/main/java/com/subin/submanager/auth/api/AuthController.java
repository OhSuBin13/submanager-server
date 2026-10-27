package com.subin.submanager.auth.api;

import com.subin.submanager.auth.api.dto.LogoutRequest;
import com.subin.submanager.auth.api.dto.RefreshRequest;
import com.subin.submanager.auth.api.dto.TokenResponse;
import com.subin.submanager.auth.application.AuthTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthTokenService authTokenService;

    public AuthController(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authTokenService.refresh(request.refreshToken(), request.deviceId());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authTokenService.logout(request.refreshToken());
    }
}

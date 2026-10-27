package com.subin.submanager.auth.api;

import com.subin.submanager.auth.api.dto.EmailLoginRequest;
import com.subin.submanager.auth.api.dto.EmailSignUpRequest;
import com.subin.submanager.auth.api.dto.EmailSignUpResponse;
import com.subin.submanager.auth.api.dto.LogoutRequest;
import com.subin.submanager.auth.api.dto.RefreshRequest;
import com.subin.submanager.auth.api.dto.ResendVerificationRequest;
import com.subin.submanager.auth.api.dto.SocialLoginRequest;
import com.subin.submanager.auth.api.dto.TokenResponse;
import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.auth.application.AuthTokenService;
import com.subin.submanager.auth.application.EmailAuthPages;
import com.subin.submanager.auth.application.EmailAuthService;
import com.subin.submanager.auth.application.SocialLoginService;
import com.subin.submanager.auth.domain.AuthProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthTokenService authTokenService;
    private final SocialLoginService socialLoginService;
    private final EmailAuthService emailAuthService;
    private final EmailAuthPages pages;

    public AuthController(AuthTokenService authTokenService,
                          SocialLoginService socialLoginService,
                          EmailAuthService emailAuthService,
                          EmailAuthPages pages) {
        this.authTokenService = authTokenService;
        this.socialLoginService = socialLoginService;
        this.emailAuthService = emailAuthService;
        this.pages = pages;
    }

    // 소셜 로그인 ----------------------------------------------------------------

    @PostMapping("/kakao")
    public TokenResponse kakao(@Valid @RequestBody SocialLoginRequest request) {
        return socialLoginService.login(AuthProvider.KAKAO, request.token(), request.deviceId());
    }

    @PostMapping("/naver")
    public TokenResponse naver(@Valid @RequestBody SocialLoginRequest request) {
        return socialLoginService.login(AuthProvider.NAVER, request.token(), request.deviceId());
    }

    @PostMapping("/google")
    public TokenResponse google(@Valid @RequestBody SocialLoginRequest request) {
        return socialLoginService.login(AuthProvider.GOOGLE, request.token(), request.deviceId());
    }

    // 이메일 ---------------------------------------------------------------------

    @PostMapping("/email/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public EmailSignUpResponse emailSignUp(@Valid @RequestBody EmailSignUpRequest request) {
        return emailAuthService.signUp(request.email(), request.password(), request.nickname());
    }

    /** 인증 메일의 링크. 브라우저가 여는 주소라 JSON이 아니라 안내 HTML을 돌려준다. */
    @GetMapping(value = "/email/verify", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> emailVerify(@RequestParam String token) {
        try {
            emailAuthService.verify(token);
            return ResponseEntity.ok(pages.verified());
        } catch (AuthException e) {
            return ResponseEntity.status(e.getErrorCode().status()).body(pages.verifyFailed());
        }
    }

    @PostMapping("/email/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailAuthService.resendVerification(request.email());
    }

    @PostMapping("/email/login")
    public TokenResponse emailLogin(@Valid @RequestBody EmailLoginRequest request) {
        return emailAuthService.login(request.email(), request.password(), request.deviceId());
    }

    // 토큰 ----------------------------------------------------------------------

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

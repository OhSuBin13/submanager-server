package com.subin.submanager.auth.application;

import com.subin.submanager.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {

    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다."),
    AUTH_REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    AUTH_REFRESH_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    AUTH_REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "보안을 위해 로그아웃되었습니다. 다시 로그인해 주세요."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정입니다."),
    USER_WITHDRAWN(HttpStatus.GONE, "탈퇴한 계정입니다."),
    SOCIAL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "소셜 로그인 정보가 올바르지 않습니다. 다시 로그인해 주세요."),
    SOCIAL_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "소셜 로그인 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    EMAIL_VERIFICATION_PENDING(HttpStatus.CONFLICT,
            "인증 대기 중인 이메일입니다. 받은 편지함의 링크를 누르거나 인증 메일을 다시 요청해 주세요."),
    EMAIL_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다. 받은 편지함을 확인해 주세요."),
    EMAIL_VERIFICATION_INVALID(HttpStatus.BAD_REQUEST, "인증 링크가 만료되었거나 올바르지 않습니다."),
    MAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    AuthErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}

package com.subin.submanager.auth.application;

import com.subin.submanager.global.error.BusinessException;

public class AuthException extends BusinessException {

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(AuthErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public AuthErrorCode getErrorCode() {
        return (AuthErrorCode) super.getErrorCode();
    }
}

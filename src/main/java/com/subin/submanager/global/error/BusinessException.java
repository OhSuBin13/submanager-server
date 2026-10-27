package com.subin.submanager.global.error;

import lombok.Getter;

/** 에러 코드를 담는 예외의 공통 부모. GlobalExceptionHandler가 ErrorResponse로 바꾼다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

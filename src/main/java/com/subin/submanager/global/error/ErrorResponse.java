package com.subin.submanager.global.error;

/** 모든 에러 응답의 형식. code는 앱이 분기하는 값, message는 그대로 보여줘도 되는 문장. */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.code(), errorCode.defaultMessage());
    }

    public static ErrorResponse of(BusinessException e) {
        return new ErrorResponse(e.getErrorCode().code(), e.getMessage());
    }
}

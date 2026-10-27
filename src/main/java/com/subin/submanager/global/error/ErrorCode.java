package com.subin.submanager.global.error;

import org.springframework.http.HttpStatus;

/** 각 기능 패키지의 에러 코드 enum이 구현한다. */
public interface ErrorCode {

    HttpStatus status();

    /** 앱이 분기하는 코드. 응답의 code 필드. */
    String code();

    /** 앱이 그대로 보여줘도 되는 한국어 문장. */
    String defaultMessage();
}

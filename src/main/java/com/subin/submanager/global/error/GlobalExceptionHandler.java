package com.subin.submanager.global.error;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 에러를 {@link ErrorResponse} 한 가지 형식으로 돌려준다.
 * Spring MVC가 던지는 표준 예외(404, 405, 본문 파싱 실패 등)는 부모 클래스가 받아 handleExceptionInternal로 모은다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().status()).body(ErrorResponse.of(e));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.status())
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(CommonErrorCode.INVALID_REQUEST.defaultMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(CommonErrorCode.INVALID_REQUEST.code(), message));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             @Nullable Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        ErrorCode code = status != null && status.is4xxClientError()
                ? CommonErrorCode.INVALID_REQUEST
                : CommonErrorCode.INTERNAL_ERROR;
        String message = status == HttpStatus.NOT_FOUND ? "요청한 주소가 없습니다." : code.defaultMessage();
        return ResponseEntity.status(statusCode).headers(headers).body(new ErrorResponse(code.code(), message));
    }
}

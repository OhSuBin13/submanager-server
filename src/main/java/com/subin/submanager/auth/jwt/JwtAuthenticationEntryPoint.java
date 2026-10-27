package com.subin.submanager.auth.jwt;

import java.io.IOException;

import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.global.error.BusinessException;
import com.subin.submanager.global.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 인증 없음(401)·권한 없음(403)을 GlobalExceptionHandler와 같은 ErrorResponse 형식으로 돌려준다.
 * JSON 변환을 직접 하지 않고 HandlerExceptionResolver에 예외를 넘겨 @RestControllerAdvice가 처리하게 한다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver handlerExceptionResolver;

    public JwtAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        handlerExceptionResolver.resolveException(request, response, null,
                new AuthException(JwtAuthenticationFilter.errorCodeOf(request)));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        handlerExceptionResolver.resolveException(request, response, null,
                new BusinessException(CommonErrorCode.FORBIDDEN));
    }
}

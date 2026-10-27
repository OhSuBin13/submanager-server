package com.subin.submanager.auth.jwt;

import java.io.IOException;
import java.util.List;

import com.subin.submanager.auth.application.AuthErrorCode;
import com.subin.submanager.auth.application.AuthException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 헤더의 액세스 토큰을 읽어 SecurityContext에 넣는다.
 * 토큰이 틀리면 응답을 직접 쓰지 않고 에러 코드만 요청 속성에 남긴다.
 * 보호 경로라면 {@link JwtAuthenticationEntryPoint}가 그 코드로 401을 만들고, 공개 경로는 그대로 통과한다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 필터가 남긴 인증 실패 코드를 EntryPoint가 읽는 요청 속성 이름 */
    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".AUTH_ERROR";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                AuthPrincipal principal = jwtProvider.parse(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AuthException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getErrorCode());
            }
        }
        chain.doFilter(request, response);
    }

    static AuthErrorCode errorCodeOf(HttpServletRequest request) {
        Object code = request.getAttribute(AUTH_ERROR_ATTRIBUTE);
        return code instanceof AuthErrorCode authErrorCode ? authErrorCode : AuthErrorCode.AUTH_REQUIRED;
    }
}

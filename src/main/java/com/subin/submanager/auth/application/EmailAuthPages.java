package com.subin.submanager.auth.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 이메일 인증에 쓰는 HTML 세 장(인증 메일 본문, 링크 클릭 성공·실패 페이지). 템플릿 엔진 없이 리소스 파일을 읽어 둔다.
 */
@Component
public class EmailAuthPages {

    private static final String LINK_PLACEHOLDER = "{{link}}";

    private final String verificationMail;
    private final String verified;
    private final String verifyFailed;

    public EmailAuthPages() {
        this.verificationMail = read("auth/verification-mail.html");
        this.verified = read("auth/email-verified.html");
        this.verifyFailed = read("auth/email-verify-failed.html");
    }

    public String verificationMail(String link) {
        return verificationMail.replace(LINK_PLACEHOLDER, link);
    }

    public String verified() {
        return verified;
    }

    public String verifyFailed() {
        return verifyFailed;
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("리소스를 읽지 못했습니다: " + path, e);
        }
    }
}

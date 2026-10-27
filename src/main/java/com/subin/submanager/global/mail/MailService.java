package com.subin.submanager.global.mail;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Resend SMTP로 HTML 메일을 보낸다. 실패하면 Spring의 {@link MailException}이 그대로 올라간다. */
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String from;

    public MailService(JavaMailSender mailSender, @Value("${app.mail-from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * @throws MailException 발송 실패(SMTP 연결·인증 오류, 타임아웃 등)
     */
    public void sendHtml(String to, String subject, String html) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
        } catch (MessagingException e) {
            throw new MailPreparationException("메일을 만들지 못했습니다.", e);
        }
        mailSender.send(message);
    }
}

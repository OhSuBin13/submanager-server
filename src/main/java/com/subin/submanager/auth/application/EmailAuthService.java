package com.subin.submanager.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.subin.submanager.auth.api.dto.EmailSignUpResponse;
import com.subin.submanager.auth.api.dto.TokenResponse;
import com.subin.submanager.auth.domain.AuthProvider;
import com.subin.submanager.auth.domain.UserIdentity;
import com.subin.submanager.auth.domain.UserIdentityRepository;
import com.subin.submanager.global.mail.MailService;
import com.subin.submanager.user.domain.User;
import com.subin.submanager.user.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 가입 → 인증 메일 링크 클릭 → 로그인. 인증 전에는 로그인할 수 없다.
 * 인증 메일 발송은 가입 트랜잭션 안에서 하고, 실패하면 가입을 롤백하고 502를 돌려준다.
 */
@Service
@Transactional
public class EmailAuthService {

    private static final Logger log = LoggerFactory.getLogger(EmailAuthService.class);
    private static final int NICKNAME_MAX_LENGTH = 30;
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    /** 미인증 가입의 비밀번호를 다른 가입 요청이 바꿔치기할 수 없는 기간. 인증 링크 유효기간과 같다. */
    static final Duration PENDING_LOCK = EmailVerificationTokenProvider.TTL;
    static final String MAIL_SUBJECT = "[구독관리] 이메일 인증";

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final EmailVerificationTokenProvider tokenProvider;
    private final MailService mailService;
    private final EmailAuthPages pages;
    private final Clock clock;
    private final String baseUrl;
    /** 없는 계정에도 같은 시간이 걸리도록 비교할 더미 해시. 응답 시간으로 가입 여부를 못 알아내게 한다. */
    private final String dummyPasswordHash;
    /** 이메일별 마지막 인증 메일 발송 시각. 1분 안의 재발송을 막는다. 서버 재시작 시 비워져도 무방하다. */
    private final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    public EmailAuthService(UserRepository userRepository,
                            UserIdentityRepository userIdentityRepository,
                            PasswordEncoder passwordEncoder,
                            AuthTokenService authTokenService,
                            EmailVerificationTokenProvider tokenProvider,
                            MailService mailService,
                            EmailAuthPages pages,
                            Clock clock,
                            @Value("${app.base-url}") String baseUrl) {
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.tokenProvider = tokenProvider;
        this.mailService = mailService;
        this.pages = pages;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * @throws AuthException EMAIL_ALREADY_REGISTERED(인증 끝난 주소), EMAIL_VERIFICATION_PENDING(24시간 안의 미인증 주소),
     *                       MAIL_SEND_FAILED(메일 실패, 가입 롤백)
     */
    public EmailSignUpResponse signUp(String rawEmail, String password, String nickname) {
        String email = normalize(rawEmail);
        String passwordHash = passwordEncoder.encode(password);
        String resolvedNickname = nicknameOf(nickname, email);
        Instant now = Instant.now(clock);

        UserIdentity identity = userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, email)
                .map(existing -> {
                    if (existing.isEmailVerified()) {
                        throw new AuthException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
                    }
                    if (now.isBefore(existing.getUpdatedAt().plus(PENDING_LOCK))) {
                        // 인증 링크가 살아 있는 동안은 비밀번호를 바꿔치기할 수 없다. 메일을 다시 받으려면 재발송 API.
                        throw new AuthException(AuthErrorCode.EMAIL_VERIFICATION_PENDING);
                    }
                    // 링크가 만료된 미인증 가입: 비밀번호·닉네임을 새 값으로 바꾸고 메일을 다시 보낸다.
                    existing.changePassword(passwordHash);
                    existing.getUser().changeNickname(resolvedNickname);
                    return existing;
                })
                .orElseGet(() -> {
                    User user = userRepository.save(User.signUp(resolvedNickname));
                    return userIdentityRepository.saveAndFlush(UserIdentity.email(user, email, passwordHash));
                });

        sendVerification(identity, now);
        return new EmailSignUpResponse(email);
    }

    /**
     * @throws AuthException EMAIL_VERIFICATION_INVALID(토큰이 틀리거나, 발급 뒤 비밀번호가 바뀌었으면)
     */
    public void verify(String token) {
        EmailVerificationTokenProvider.Verification verification = tokenProvider.parse(token);
        UserIdentity identity = userIdentityRepository.findById(verification.identityId())
                .filter(found -> found.getProvider() == AuthProvider.EMAIL)
                .filter(found -> EmailVerificationTokenProvider.fingerprint(found.getPasswordHash())
                        .equals(verification.passwordFingerprint()))
                .orElseThrow(() -> new AuthException(AuthErrorCode.EMAIL_VERIFICATION_INVALID));
        if (!identity.isEmailVerified()) {
            identity.verifyEmail(Instant.now(clock));
        }
    }

    /** 계정이 없거나 이미 인증됐거나 1분 안의 재요청이면 아무것도 하지 않는다. 가입 여부가 새지 않게 응답은 항상 같다. */
    public void resendVerification(String rawEmail) {
        String email = normalize(rawEmail);
        userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, email)
                .filter(identity -> !identity.isEmailVerified())
                .ifPresent(identity -> {
                    Instant now = Instant.now(clock);
                    if (!reserveSendSlot(email, now)) {
                        return;
                    }
                    try {
                        sendVerification(identity, now);
                    } catch (AuthException e) {
                        lastSentAt.remove(email, now); // 못 보냈으면 1분을 기다리게 하지 않는다.
                        throw e;
                    }
                });
    }

    /**
     * 1분이 지났으면 지금 시각을 기록하고 true. 같은 주소의 요청이 동시에 들어와도 compute가 원자적이라 한 요청만 true를 받는다.
     */
    private boolean reserveSendSlot(String email, Instant now) {
        Instant reserved = lastSentAt.compute(email, (key, last) ->
                last != null && now.isBefore(last.plus(RESEND_INTERVAL)) ? last : now);
        return reserved == now; // 우리가 넣은 바로 그 객체가 돌아왔으면 이 요청이 자리를 잡은 것
    }

    /**
     * @throws AuthException EMAIL_LOGIN_FAILED, EMAIL_NOT_VERIFIED, USER_SUSPENDED, USER_WITHDRAWN
     */
    public TokenResponse login(String rawEmail, String password, String deviceId) {
        String email = normalize(rawEmail);
        UserIdentity identity = userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, email)
                .orElse(null);
        String hash = identity == null ? dummyPasswordHash : identity.getPasswordHash();
        if (!passwordEncoder.matches(password, hash) || identity == null) {
            throw new AuthException(AuthErrorCode.EMAIL_LOGIN_FAILED);
        }
        if (!identity.isEmailVerified()) {
            throw new AuthException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
        return authTokenService.issueTokens(identity.getUser(), deviceId).withNewUser(false);
    }

    private void sendVerification(UserIdentity identity, Instant now) {
        String link = baseUrl + "/auth/email/verify?token=" + tokenProvider.create(identity, now);
        try {
            mailService.sendHtml(identity.getEmail(), MAIL_SUBJECT, pages.verificationMail(link));
        } catch (MailException e) {
            log.error("인증 메일 발송 실패: {}", identity.getEmail(), e);
            throw new AuthException(AuthErrorCode.MAIL_SEND_FAILED);
        }
        lastSentAt.put(identity.getEmail(), now);
    }

    static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    static String nicknameOf(String nickname, String email) {
        String resolved = nickname == null ? "" : nickname.trim();
        if (resolved.isEmpty()) {
            resolved = email.substring(0, email.indexOf('@'));
        }
        return resolved.length() > NICKNAME_MAX_LENGTH ? resolved.substring(0, NICKNAME_MAX_LENGTH) : resolved;
    }
}

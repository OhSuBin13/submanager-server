package com.subin.submanager.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.subin.submanager.auth.application.AuthErrorCode;
import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.auth.application.EmailVerificationTokenProvider;
import com.subin.submanager.auth.domain.AuthProvider;
import com.subin.submanager.auth.domain.UserIdentity;
import com.subin.submanager.auth.domain.UserIdentityRepository;
import com.subin.submanager.auth.social.GoogleTokenVerifier;
import com.subin.submanager.auth.social.KakaoTokenVerifier;
import com.subin.submanager.auth.social.NaverTokenVerifier;
import com.subin.submanager.auth.social.SocialUserInfo;
import com.subin.submanager.global.mail.MailService;
import com.subin.submanager.user.domain.User;
import com.subin.submanager.user.domain.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그인 4종 API를 실제 PostgreSQL과 함께 확인한다. 소셜 검증기와 메일 발송은 모킹한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginApiTest {

    private static final String DEVICE = "test-device";
    private static final String EMAIL = "login-test@example.com";
    private static final String PASSWORD = "password1";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired UserIdentityRepository userIdentityRepository;
    @Autowired EmailVerificationTokenProvider verificationTokenProvider;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean KakaoTokenVerifier kakaoTokenVerifier;
    @MockitoBean NaverTokenVerifier naverTokenVerifier;
    @MockitoBean GoogleTokenVerifier googleTokenVerifier;
    @MockitoBean MailService mailService;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL)
                .ifPresent(identity -> createdUserIds.add(identity.getUser().getId()));
        createdUserIds.forEach(userRepository::deleteById);
    }

    // 소셜 ----------------------------------------------------------------------

    @Test
    void 카카오_첫_로그인은_가입하고_두_번째는_같은_회원으로_로그인한다() throws Exception {
        when(kakaoTokenVerifier.verify("kakao-token"))
                .thenReturn(new SocialUserInfo(AuthProvider.KAKAO, "kakao-42", "수빈", null));

        MvcResult first = mockMvc.perform(social("/auth/kakao", "kakao-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.user.nickname").value("수빈"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();
        long userId = userIdOf(first);
        createdUserIds.add(userId);

        mockMvc.perform(social("/auth/kakao", "kakao-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(false))
                .andExpect(jsonPath("$.user.id").value(userId));

        UserIdentity identity = userIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-42").orElseThrow();
        assertThat(identity.getUser().getId()).isEqualTo(userId);
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 닉네임이_없는_소셜_계정은_제공자_이름으로_닉네임을_만든다() throws Exception {
        when(naverTokenVerifier.verify("naver-token"))
                .thenReturn(new SocialUserInfo(AuthProvider.NAVER, "naver-1", null, "x@naver.com"));

        MvcResult result = mockMvc.perform(social("/auth/naver", "naver-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname").value("네이버 회원"))
                .andReturn();
        createdUserIds.add(userIdOf(result));
    }

    @Test
    void 소셜_토큰이_틀리면_401_SOCIAL_TOKEN_INVALID() throws Exception {
        when(googleTokenVerifier.verify("bad")).thenThrow(new AuthException(AuthErrorCode.SOCIAL_TOKEN_INVALID));

        mockMvc.perform(social("/auth/google", "bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_TOKEN_INVALID"));
    }

    @Test
    void 소셜_서비스_장애면_502_SOCIAL_PROVIDER_ERROR() throws Exception {
        when(kakaoTokenVerifier.verify("t")).thenThrow(new AuthException(AuthErrorCode.SOCIAL_PROVIDER_ERROR));

        mockMvc.perform(social("/auth/kakao", "t"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SOCIAL_PROVIDER_ERROR"));
    }

    @Test
    void 정지된_회원의_소셜_로그인은_403() throws Exception {
        when(googleTokenVerifier.verify("g"))
                .thenReturn(new SocialUserInfo(AuthProvider.GOOGLE, "google-suspended", "수빈", null));
        MvcResult result = mockMvc.perform(social("/auth/google", "g")).andExpect(status().isOk()).andReturn();
        long userId = userIdOf(result);
        createdUserIds.add(userId);
        transactionTemplate.executeWithoutResult(tx ->
                userRepository.findById(userId).orElseThrow().suspend(Instant.now(), "테스트"));

        mockMvc.perform(social("/auth/google", "g"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_SUSPENDED"));
    }

    @Test
    void 같은_이메일이라도_로그인_수단이_다르면_별도_회원이다() throws Exception {
        when(googleTokenVerifier.verify("g2"))
                .thenReturn(new SocialUserInfo(AuthProvider.GOOGLE, "google-same-email", "수빈", EMAIL));
        MvcResult result = mockMvc.perform(social("/auth/google", "g2")).andExpect(status().isOk()).andReturn();
        long googleUserId = userIdOf(result);
        createdUserIds.add(googleUserId);

        mockMvc.perform(signUp(EMAIL, PASSWORD, "수빈")).andExpect(status().isCreated());

        UserIdentity emailIdentity = userIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL).orElseThrow();
        assertThat(emailIdentity.getUser().getId()).isNotEqualTo(googleUserId);
    }

    // 이메일 ---------------------------------------------------------------------

    @Test
    void 이메일_가입_후_인증_링크를_누르면_로그인할_수_있다() throws Exception {
        mockMvc.perform(signUp(EMAIL, PASSWORD, "수빈"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL));

        UserIdentity identity = userIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL).orElseThrow();
        assertThat(identity.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(identity.isEmailVerified()).isFalse();

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendHtml(eq(EMAIL), anyString(), html.capture());
        assertThat(html.getValue()).contains("/auth/email/verify?token=");

        mockMvc.perform(emailLogin(EMAIL, PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String token = verificationTokenProvider.create(identity, Instant.now());
        mockMvc.perform(get("/auth/email/verify").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("인증이 끝났습니다")));

        mockMvc.perform(emailLogin(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(false))
                .andExpect(jsonPath("$.user.nickname").value("수빈"));
    }

    @Test
    void 인증_링크_토큰이_틀리면_400_HTML이고_액세스_토큰으로도_쓸_수_없다() throws Exception {
        mockMvc.perform(get("/auth/email/verify").param("token", "broken"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("만료되었거나")));

        mockMvc.perform(signUp(EMAIL, PASSWORD, null)).andExpect(status().isCreated());
        UserIdentity identity = userIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL).orElseThrow();
        String token = verificationTokenProvider.create(identity, Instant.now());

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void 비밀번호가_바뀐_뒤에는_이전_인증_링크가_무효다() throws Exception {
        mockMvc.perform(signUp(EMAIL, PASSWORD, null)).andExpect(status().isCreated());
        UserIdentity before = userIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL).orElseThrow();
        String oldLink = verificationTokenProvider.create(before, Instant.now());

        expirePendingSignUp();
        mockMvc.perform(signUp(EMAIL, "newpass99", null)).andExpect(status().isCreated());

        // 예전 비밀번호로 발급된 링크는 새 비밀번호를 인증해 주지 않는다.
        mockMvc.perform(get("/auth/email/verify").param("token", oldLink))
                .andExpect(status().isBadRequest());
        UserIdentity after = userIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL).orElseThrow();
        assertThat(after.isEmailVerified()).isFalse();

        String newLink = verificationTokenProvider.create(after, Instant.now());
        mockMvc.perform(get("/auth/email/verify").param("token", newLink)).andExpect(status().isOk());
        mockMvc.perform(emailLogin(EMAIL, "newpass99")).andExpect(status().isOk());
    }

    @Test
    void 닉네임을_생략하면_이메일_앞부분이_닉네임이다() throws Exception {
        mockMvc.perform(signUp(EMAIL, PASSWORD, null)).andExpect(status().isCreated());

        assertThat(nicknameOf(EMAIL)).isEqualTo("login-test");
    }

    @Test
    void 비밀번호가_틀리거나_계정이_없으면_둘_다_401_EMAIL_LOGIN_FAILED() throws Exception {
        mockMvc.perform(signUp(EMAIL, PASSWORD, "수빈")).andExpect(status().isCreated());
        verifyEmail();

        mockMvc.perform(emailLogin(EMAIL, "wrong-pass1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EMAIL_LOGIN_FAILED"));
        mockMvc.perform(emailLogin("nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EMAIL_LOGIN_FAILED"));
    }

    @Test
    void 같은_주소로_다시_가입하면_인증_대기_중엔_409이고_링크가_만료된_뒤엔_비밀번호를_바꿔_다시_보낸다() throws Exception {
        mockMvc.perform(signUp(EMAIL, PASSWORD, "수빈")).andExpect(status().isCreated());

        // 인증 링크가 살아 있는 24시간 안: 다른 사람이 비밀번호를 바꿔치기할 수 없다.
        mockMvc.perform(signUp(EMAIL, "newpass99", "새닉네임"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_PENDING"));
        verify(mailService, times(1)).sendHtml(eq(EMAIL), anyString(), anyString());
        assertThat(nicknameOf(EMAIL)).isEqualTo("수빈");
        mockMvc.perform(emailLogin(EMAIL, "newpass99"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EMAIL_LOGIN_FAILED"));

        // 링크가 만료된 뒤: 새 비밀번호·닉네임으로 바꾸고 메일을 다시 보낸다.
        expirePendingSignUp();
        mockMvc.perform(signUp(EMAIL, "newpass99", "새닉네임"))
                .andExpect(status().isCreated());
        verify(mailService, times(2)).sendHtml(eq(EMAIL), anyString(), anyString());
        assertThat(nicknameOf(EMAIL)).isEqualTo("새닉네임");

        verifyEmail();
        mockMvc.perform(signUp(EMAIL, PASSWORD, "수빈"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        mockMvc.perform(emailLogin(EMAIL, "newpass99")).andExpect(status().isOk());
    }

    @Test
    void 비밀번호_규칙을_어기면_400() throws Exception {
        mockMvc.perform(signUp(EMAIL, "short1", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(signUp(EMAIL, "onlyletters", null))
                .andExpect(status().isBadRequest());
        mockMvc.perform(signUp("not-an-email", PASSWORD, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 메일_발송이_실패하면_가입이_롤백되고_502() throws Exception {
        doThrow(new MailSendException("smtp down")).when(mailService).sendHtml(eq(EMAIL), anyString(), anyString());

        mockMvc.perform(signUp(EMAIL, PASSWORD, "수빈"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MAIL_SEND_FAILED"));

        assertThat(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL)).isEmpty();
    }

    @Test
    void 인증_메일_다시_보내기는_계정_유무와_관계없이_204이고_1분_안에는_한_번만_보낸다() throws Exception {
        mockMvc.perform(resend("nobody@example.com")).andExpect(status().isNoContent());
        verify(mailService, never()).sendHtml(eq("nobody@example.com"), anyString(), anyString());

        mockMvc.perform(signUp(EMAIL, PASSWORD, null)).andExpect(status().isCreated());
        mockMvc.perform(resend(EMAIL)).andExpect(status().isNoContent());
        verify(mailService, times(1)).sendHtml(eq(EMAIL), anyString(), anyString());

        verifyEmail();
        mockMvc.perform(resend(EMAIL)).andExpect(status().isNoContent());
        verify(mailService, times(1)).sendHtml(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void 이메일은_대소문자를_무시한다() throws Exception {
        mockMvc.perform(signUp("Login-Test@Example.com", PASSWORD, null)).andExpect(status().isCreated());
        verifyEmail();

        mockMvc.perform(emailLogin("LOGIN-TEST@example.com", PASSWORD)).andExpect(status().isOk());
    }

    // helpers --------------------------------------------------------------------

    /** 회원 조회를 트랜잭션 안에서 해야 User 지연 로딩 프록시를 읽을 수 있다. */
    private String nicknameOf(String email) {
        return transactionTemplate.execute(tx -> userIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.EMAIL, email).orElseThrow()
                .getUser().getNickname());
    }

    /** 미인증 가입을 하루 전 것으로 만들어 인증 링크(24시간)가 만료된 상태를 흉내 낸다. */
    private void expirePendingSignUp() {
        jdbcTemplate.update("UPDATE user_identities SET updated_at = now() - interval '2 days'"
                + " WHERE provider = 'EMAIL' AND provider_user_id = ?", EMAIL);
    }

    private void verifyEmail() {
        transactionTemplate.executeWithoutResult(tx -> userIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.EMAIL, EMAIL).orElseThrow()
                .verifyEmail(Instant.now()));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder social(String path, String token) {
        return post(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"deviceId\":\"" + DEVICE + "\"}");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signUp(
            String email, String password, String nickname) {
        String nicknameJson = nickname == null ? "" : ",\"nickname\":\"" + nickname + "\"";
        return post("/auth/email/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"" + nicknameJson + "}");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder emailLogin(
            String email, String password) {
        return post("/auth/email/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"deviceId\":\"" + DEVICE + "\"}");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resend(String email) {
        return post("/auth/email/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}");
    }

    private static long userIdOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"id\":") + 5;
        int end = body.indexOf(',', start);
        return Long.parseLong(body.substring(start, end).trim());
    }
}

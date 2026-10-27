package com.subin.submanager.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.subin.submanager.auth.api.dto.TokenResponse;
import com.subin.submanager.auth.application.AuthTokenService;
import com.subin.submanager.auth.application.RefreshTokenGenerator;
import com.subin.submanager.auth.domain.RefreshToken;
import com.subin.submanager.auth.domain.RefreshTokenRepository;
import com.subin.submanager.auth.jwt.JwtProvider;
import com.subin.submanager.user.domain.User;
import com.subin.submanager.user.domain.UserRepository;
import com.subin.submanager.user.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JWT 필터, 토큰 갱신(회전·재사용 탐지), 로그아웃, 계정 상태 검사를 실제 PostgreSQL과 함께 확인한다.
 * 요청마다 트랜잭션이 커밋되어야 하므로 테스트 자체는 트랜잭션으로 감싸지 않고, 만든 회원을 끝에 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    private static final String DEVICE = "test-device";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AuthTokenService authTokenService;
    @Autowired RefreshTokenGenerator refreshTokenGenerator;
    @Autowired JwtProvider jwtProvider;
    @Autowired TransactionTemplate transactionTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdUserIds.forEach(userRepository::deleteById);
    }

    @Test
    void 토큰_없이_보호_경로를_부르면_401_AUTH_REQUIRED() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void 발급받은_액세스_토큰으로_내_정보를_읽는다() throws Exception {
        User user = newUser();
        TokenResponse tokens = issue(user);

        assertThat(tokens.accessTokenExpiresIn()).isEqualTo(3600);
        assertThat(tokens.refreshToken()).hasSize(43);

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.nickname").value("수빈"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.defaultRemindDaysBefore").value(3));
    }

    @Test
    void 만료된_액세스_토큰은_401_AUTH_TOKEN_EXPIRED() throws Exception {
        User user = newUser();
        String expired = jwtProvider.createAccessToken(user.getId(), UserRole.MEMBER,
                Instant.now().minus(Duration.ofHours(2)));

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void 공개_경로는_틀린_토큰을_붙여도_필터가_막지_않는다() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer broken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"nothing\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 갱신하면_기존_토큰은_폐기되고_새_토큰이_발급된다() throws Exception {
        User user = newUser();
        TokenResponse first = issue(user);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(first.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.user.id").value(user.getId()));

        RefreshToken old = refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(first.refreshToken()))
                .orElseThrow();
        assertThat(old.isRevoked()).isTrue();
        assertThat(liveTokenCount(user)).isEqualTo(1);
    }

    @Test
    void 폐기된_토큰을_다시_쓰면_401_AUTH_REFRESH_REUSED_이고_모든_토큰이_폐기된다() throws Exception {
        User user = newUser();
        TokenResponse phone = issue(user);
        issue(user, "tablet");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(phone.refreshToken())))
                .andExpect(status().isOk());
        assertThat(liveTokenCount(user)).isEqualTo(2);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(phone.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSED"));

        assertThat(liveTokenCount(user)).isZero();
    }

    @Test
    void 만료된_리프레시_토큰은_401_AUTH_REFRESH_EXPIRED() throws Exception {
        User user = newUser();
        String raw = refreshTokenGenerator.generate();
        transactionTemplate.executeWithoutResult(tx -> refreshTokenRepository.save(new RefreshToken(
                userRepository.getReferenceById(user.getId()), refreshTokenGenerator.hash(raw), DEVICE,
                Instant.now().minus(Duration.ofMinutes(1)))));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(raw)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_EXPIRED"));
    }

    @Test
    void 모르는_리프레시_토큰은_401_AUTH_REFRESH_INVALID() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("unknown-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    void 본문이_비면_400_INVALID_REQUEST() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\",\"deviceId\":\"d\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 정지된_회원은_갱신_시_403_USER_SUSPENDED() throws Exception {
        User user = newUser();
        TokenResponse tokens = issue(user);
        transactionTemplate.executeWithoutResult(tx ->
                userRepository.findById(user.getId()).orElseThrow().suspend(Instant.now(), "약관 위반"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.refreshToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_SUSPENDED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("약관 위반")));
    }

    @Test
    void 탈퇴한_회원은_갱신_시_410_USER_WITHDRAWN() throws Exception {
        User user = newUser();
        TokenResponse tokens = issue(user);
        transactionTemplate.executeWithoutResult(tx ->
                userRepository.findById(user.getId()).orElseThrow().withdraw(Instant.now()));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.refreshToken())))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("USER_WITHDRAWN"));
    }

    @Test
    void 로그아웃하면_그_리프레시_토큰은_더_쓸_수_없다() throws Exception {
        User user = newUser();
        TokenResponse tokens = issue(user);

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(liveTokenCount(user)).isZero();

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 같은_기기로_다시_로그인하면_이전_토큰은_폐기된다() {
        User user = newUser();
        issue(user);
        issue(user);

        assertThat(liveTokenCount(user)).isEqualTo(1);
    }

    private User newUser() {
        User user = userRepository.save(User.signUp("수빈"));
        createdUserIds.add(user.getId());
        return user;
    }

    private TokenResponse issue(User user) {
        return issue(user, DEVICE);
    }

    private TokenResponse issue(User user, String deviceId) {
        return transactionTemplate.execute(tx ->
                authTokenService.issueTokens(userRepository.findById(user.getId()).orElseThrow(), deviceId));
    }

    private long liveTokenCount(User user) {
        return refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(user.getId()) && !token.isRevoked())
                .count();
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\",\"deviceId\":\"" + DEVICE + "\"}";
    }
}

package com.subin.submanager.auth.application;

import com.subin.submanager.auth.api.dto.TokenResponse;
import com.subin.submanager.auth.domain.AuthProvider;
import com.subin.submanager.auth.domain.UserIdentity;
import com.subin.submanager.auth.domain.UserIdentityRepository;
import com.subin.submanager.auth.social.GoogleTokenVerifier;
import com.subin.submanager.auth.social.KakaoTokenVerifier;
import com.subin.submanager.auth.social.NaverTokenVerifier;
import com.subin.submanager.auth.social.SocialTokenVerifier;
import com.subin.submanager.auth.social.SocialUserInfo;
import com.subin.submanager.user.domain.User;
import com.subin.submanager.user.domain.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소셜 로그인 공통 흐름: 토큰 검증 → (provider, id)로 회원 찾기, 없으면 가입 → 토큰 발급.
 * 외부 API 호출(검증)은 DB 트랜잭션 밖에서 하고, 회원 조회·가입·토큰 발급만 트랜잭션으로 묶는다.
 */
@Service
public class SocialLoginService {

    private static final int NICKNAME_MAX_LENGTH = 30;

    private final KakaoTokenVerifier kakaoTokenVerifier;
    private final NaverTokenVerifier naverTokenVerifier;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final AuthTokenService authTokenService;
    private final TransactionTemplate transactionTemplate;

    public SocialLoginService(KakaoTokenVerifier kakaoTokenVerifier,
                              NaverTokenVerifier naverTokenVerifier,
                              GoogleTokenVerifier googleTokenVerifier,
                              UserRepository userRepository,
                              UserIdentityRepository userIdentityRepository,
                              AuthTokenService authTokenService,
                              TransactionTemplate transactionTemplate) {
        this.kakaoTokenVerifier = kakaoTokenVerifier;
        this.naverTokenVerifier = naverTokenVerifier;
        this.googleTokenVerifier = googleTokenVerifier;
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.authTokenService = authTokenService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * @throws AuthException SOCIAL_TOKEN_INVALID, SOCIAL_PROVIDER_ERROR, USER_SUSPENDED, USER_WITHDRAWN
     */
    public TokenResponse login(AuthProvider provider, String token, String deviceId) {
        SocialUserInfo info = verifierFor(provider).verify(token);
        try {
            return transactionTemplate.execute(tx -> findOrSignUpAndIssue(info, deviceId));
        } catch (DataIntegrityViolationException e) {
            // 같은 사람의 첫 로그인이 동시에 두 번 들어와 한쪽 가입이 유니크 제약에 걸린 경우. 다시 조회하면 있다.
            return transactionTemplate.execute(tx -> findOrSignUpAndIssue(info, deviceId));
        }
    }

    private SocialTokenVerifier verifierFor(AuthProvider provider) {
        return switch (provider) {
            case KAKAO -> kakaoTokenVerifier;
            case NAVER -> naverTokenVerifier;
            case GOOGLE -> googleTokenVerifier;
            case EMAIL -> throw new IllegalArgumentException("이메일은 소셜 로그인이 아닙니다");
        };
    }

    private TokenResponse findOrSignUpAndIssue(SocialUserInfo info, String deviceId) {
        return userIdentityRepository.findByProviderAndProviderUserId(info.provider(), info.providerUserId())
                .map(identity -> authTokenService.issueTokens(identity.getUser(), deviceId).withNewUser(false))
                .orElseGet(() -> {
                    User user = userRepository.save(User.signUp(nicknameOf(info)));
                    userIdentityRepository.saveAndFlush(
                            UserIdentity.social(user, info.provider(), info.providerUserId(), info.email()));
                    return authTokenService.issueTokens(user, deviceId).withNewUser(true);
                });
    }

    static String nicknameOf(SocialUserInfo info) {
        String nickname = info.nickname() == null ? "" : info.nickname().trim();
        if (nickname.isEmpty()) {
            nickname = providerName(info.provider()) + " 회원";
        }
        return nickname.length() > NICKNAME_MAX_LENGTH ? nickname.substring(0, NICKNAME_MAX_LENGTH) : nickname;
    }

    private static String providerName(AuthProvider provider) {
        return switch (provider) {
            case KAKAO -> "카카오";
            case NAVER -> "네이버";
            case GOOGLE -> "구글";
            case EMAIL -> "이메일";
        };
    }
}

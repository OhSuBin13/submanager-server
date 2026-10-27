package com.subin.submanager.user.application;

import com.subin.submanager.auth.application.AuthErrorCode;
import com.subin.submanager.auth.application.AuthException;
import com.subin.submanager.user.api.dto.MyProfileResponse;
import com.subin.submanager.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 토큰은 유효한데 회원 행이 없는 경우(탈퇴 30일 후 삭제)는 토큰이 무효한 것으로 본다. */
    public MyProfileResponse getMyProfile(Long userId) {
        return userRepository.findById(userId)
                .map(MyProfileResponse::from)
                .orElseThrow(() -> new AuthException(AuthErrorCode.AUTH_TOKEN_INVALID));
    }
}

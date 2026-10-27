package com.subin.submanager.auth.api.dto;

import com.subin.submanager.user.domain.User;
import com.subin.submanager.user.domain.UserRole;

public record UserSummary(Long id, String nickname, UserRole role) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getNickname(), user.getRole());
    }
}

package com.subin.submanager.user.api.dto;

import java.time.LocalTime;

import com.subin.submanager.user.domain.User;
import com.subin.submanager.user.domain.UserRole;

public record MyProfileResponse(
        Long id,
        String nickname,
        UserRole role,
        int defaultRemindDaysBefore,
        LocalTime remindTime,
        boolean pushEnabled) {

    public static MyProfileResponse from(User user) {
        return new MyProfileResponse(user.getId(), user.getNickname(), user.getRole(),
                user.getDefaultRemindDaysBefore(), user.getRemindTime(), user.isPushEnabled());
    }
}

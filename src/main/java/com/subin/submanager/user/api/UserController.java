package com.subin.submanager.user.api;

import com.subin.submanager.auth.jwt.AuthPrincipal;
import com.subin.submanager.user.api.dto.MyProfileResponse;
import com.subin.submanager.user.application.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public MyProfileResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.getMyProfile(principal.userId());
    }
}

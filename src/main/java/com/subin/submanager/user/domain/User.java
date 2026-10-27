package com.subin.submanager.user.domain;

import java.time.Instant;
import java.time.LocalTime;

import com.subin.submanager.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    public static final int DEFAULT_REMIND_DAYS_BEFORE = 3;
    public static final LocalTime DEFAULT_REMIND_TIME = LocalTime.of(9, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(nullable = false)
    private int defaultRemindDaysBefore;

    @Column(nullable = false)
    private LocalTime remindTime;

    @Column(nullable = false)
    private boolean pushEnabled;

    private Instant suspendedAt;

    private String suspensionReason;

    private Instant withdrawnAt;

    private User(String nickname) {
        this.nickname = nickname;
        this.role = UserRole.MEMBER;
        this.status = UserStatus.ACTIVE;
        this.defaultRemindDaysBefore = DEFAULT_REMIND_DAYS_BEFORE;
        this.remindTime = DEFAULT_REMIND_TIME;
        this.pushEnabled = true;
    }

    public static User signUp(String nickname) {
        return new User(nickname);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }

    public boolean isWithdrawn() {
        return status == UserStatus.WITHDRAWN;
    }

    /** 운영자 정지. MVP에서는 DB에서 직접 처리하므로 테스트와 이후 운영 도구에서 쓴다. */
    public void suspend(Instant now, String reason) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedAt = now;
        this.suspensionReason = reason;
    }

    public void withdraw(Instant now) {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.pushEnabled = false;
    }
}

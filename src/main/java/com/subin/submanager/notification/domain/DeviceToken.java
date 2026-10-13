package com.subin.submanager.notification.domain;

import java.time.Instant;

import com.subin.submanager.global.entity.BaseCreatedTimeEntity;
import com.subin.submanager.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "device_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 512)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(length = 100)
    private String deviceModel;

    @Column(length = 30)
    private String appVersion;

    @Column(nullable = false)
    private Instant lastSeenAt;

    public DeviceToken(User user, String fcmToken, DevicePlatform platform, String deviceModel,
                       String appVersion, Instant now) {
        this.user = user;
        this.fcmToken = fcmToken;
        this.platform = platform;
        this.deviceModel = deviceModel;
        this.appVersion = appVersion;
        this.lastSeenAt = now;
    }

    /** 같은 기기 토큰으로 다른 계정이 로그인하면 소유자를 옮긴다 */
    public void refresh(User user, String appVersion, Instant now) {
        this.user = user;
        this.appVersion = appVersion;
        this.lastSeenAt = now;
    }
}

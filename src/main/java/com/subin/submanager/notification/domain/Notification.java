package com.subin.submanager.notification.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.subin.submanager.global.entity.BaseCreatedTimeEntity;
import com.subin.submanager.subscription.domain.Subscription;
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

/**
 * 발송 기록이자 알림 센터 목록. (구독, 대상 결제일, 종류) 유일 제약으로 같은 알림이 두 번 가지 않는다.
 */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseCreatedTimeEntity {

    public static final int MAX_ATTEMPTS = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    private LocalDate billingDate;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SendStatus sendStatus;

    @Column(nullable = false)
    private int attemptCount;

    private Instant sentAt;

    private Instant readAt;

    private Notification(User user, Subscription subscription, NotificationType type, LocalDate billingDate,
                         String title, String body) {
        this.user = user;
        this.subscription = subscription;
        this.type = type;
        this.billingDate = billingDate;
        this.title = title;
        this.body = body;
        this.sendStatus = SendStatus.PENDING;
    }

    public static Notification billingReminder(Subscription subscription, String title, String body) {
        return new Notification(subscription.getUser(), subscription, NotificationType.BILLING_REMINDER,
                subscription.getNextBillingDate(), title, body);
    }

    public static Notification announcement(User user, String title, String body) {
        return new Notification(user, null, NotificationType.ANNOUNCEMENT, null, title, body);
    }

    public void markSent(Instant now) {
        this.attemptCount++;
        this.sendStatus = SendStatus.SENT;
        this.sentAt = now;
    }

    public void markFailed() {
        this.attemptCount++;
        this.sendStatus = SendStatus.FAILED;
    }

    public void skip() {
        this.sendStatus = SendStatus.SKIPPED;
    }

    public boolean canRetry() {
        return sendStatus == SendStatus.FAILED && attemptCount < MAX_ATTEMPTS;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }
}

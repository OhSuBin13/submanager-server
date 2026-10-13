package com.subin.submanager.subscription.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.subin.submanager.catalog.domain.CatalogService;
import com.subin.submanager.global.entity.BaseTimeEntity;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 직접 입력한 구독이면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private CatalogService service;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BillingCycle billingCycle;

    @Column(nullable = false)
    private LocalDate firstBillingDate;

    @Column(nullable = false)
    private LocalDate nextBillingDate;

    /** null이면 회원의 기본 알림일(users.default_remind_days_before)을 쓴다 */
    private Integer remindDaysBefore;

    @Column(length = 500)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Builder
    private Subscription(User user, CatalogService service, String name, BigDecimal amount, String currency,
                         BillingCycle billingCycle, LocalDate firstBillingDate, Integer remindDaysBefore,
                         String memo, LocalDate today) {
        this.user = user;
        this.service = service;
        this.name = name;
        this.amount = amount;
        this.currency = currency == null ? "KRW" : currency;
        this.billingCycle = billingCycle;
        this.firstBillingDate = firstBillingDate;
        this.nextBillingDate = billingCycle.firstBillingDateOnOrAfter(firstBillingDate, today);
        this.remindDaysBefore = remindDaysBefore;
        this.memo = memo;
        this.status = SubscriptionStatus.ACTIVE;
    }

    public int effectiveRemindDaysBefore() {
        return remindDaysBefore != null ? remindDaysBefore : user.getDefaultRemindDaysBefore();
    }

    /** 결제일이 지났으면 오늘 이후(오늘 포함) 첫 결제일로 옮긴다. 매일 자정 작업이 호출한다. */
    public boolean rollBillingDate(LocalDate today) {
        if (!nextBillingDate.isBefore(today)) {
            return false;
        }
        this.nextBillingDate = billingCycle.firstBillingDateOnOrAfter(firstBillingDate, today);
        return true;
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELED;
    }
}

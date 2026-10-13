package com.subin.submanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.subin.submanager.catalog.domain.CatalogServiceRepository;
import com.subin.submanager.notification.domain.Notification;
import com.subin.submanager.notification.domain.NotificationRepository;
import com.subin.submanager.subscription.domain.BillingCycle;
import com.subin.submanager.subscription.domain.Subscription;
import com.subin.submanager.subscription.domain.SubscriptionRepository;
import com.subin.submanager.user.domain.User;
import com.subin.submanager.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway 마이그레이션을 실제 PostgreSQL에 적용하고, JPA 엔티티가 스키마와 맞는지(ddl-auto=validate) 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaValidationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    CatalogServiceRepository catalogServiceRepository;

    @Test
    void 시드된_서비스_목록을_읽는다() {
        assertThat(catalogServiceRepository.findAllByActiveTrueOrderByDisplayOrderAsc())
                .isNotEmpty()
                .first()
                .satisfies(service -> assertThat(service.getName()).isEqualTo("넷플릭스"));
    }

    @Test
    void 같은_구독_결제일_알림은_한_번만_저장된다() {
        User user = userRepository.save(User.signUp("수빈"));
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .user(user)
                .name("넷플릭스")
                .amount(new BigDecimal("13500"))
                .billingCycle(BillingCycle.MONTHLY)
                .firstBillingDate(LocalDate.of(2026, 1, 31))
                .today(LocalDate.of(2026, 10, 13))
                .build());

        assertThat(subscription.getNextBillingDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(subscription.effectiveRemindDaysBefore()).isEqualTo(3);

        notificationRepository.saveAndFlush(Notification.billingReminder(subscription, "결제 예정", "3일 뒤 결제"));

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(
                Notification.billingReminder(subscription, "결제 예정", "3일 뒤 결제")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

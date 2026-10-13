package com.subin.submanager.subscription.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** 회원 데이터는 항상 JWT의 사용자 id를 조건으로 조회한다 */
    List<Subscription> findAllByUserIdOrderByNextBillingDateAsc(Long userId);

    Optional<Subscription> findByIdAndUserId(Long id, Long userId);
}

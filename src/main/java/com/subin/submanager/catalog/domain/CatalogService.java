package com.subin.submanager.catalog.domain;

import java.math.BigDecimal;

import com.subin.submanager.global.entity.BaseTimeEntity;
import com.subin.submanager.subscription.domain.BillingCycle;
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

/**
 * 서비스 카탈로그(넷플릭스, 유튜브 프리미엄 등). 운영자가 DB에서 관리하고 회원은 읽기만 한다.
 * 스프링의 @Service와 헷갈리지 않도록 클래스 이름을 CatalogService로 둔다.
 */
@Getter
@Entity
@Table(name = "services")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogService extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(length = 500)
    private String logoUrl;

    @Column(precision = 12, scale = 2)
    private BigDecimal defaultPrice;

    @Column(length = 3)
    private String defaultCurrency;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private BillingCycle defaultBillingCycle;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;
}

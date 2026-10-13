package com.subin.submanager.catalog.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.subin.submanager.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한국수출입은행 기준 환율. JPY처럼 100단위로 고시되는 통화도 1단위 원화 금액으로 저장한다.
 * 주말·공휴일에는 행이 없으므로 조회할 때 가장 최근 날짜 값을 쓴다.
 */
@Getter
@Entity
@Table(name = "exchange_rates")
@IdClass(ExchangeRateId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate extends BaseCreatedTimeEntity {

    @Id
    @Column(length = 3)
    private String currency;

    @Id
    private LocalDate rateDate;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal krwPerUnit;

    public ExchangeRate(String currency, LocalDate rateDate, BigDecimal krwPerUnit) {
        this.currency = currency;
        this.rateDate = rateDate;
        this.krwPerUnit = krwPerUnit;
    }
}

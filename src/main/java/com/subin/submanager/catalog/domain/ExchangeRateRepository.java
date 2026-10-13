package com.subin.submanager.catalog.domain;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, ExchangeRateId> {

    /** 해당 날짜 또는 그 이전 가장 최근 환율 (주말·공휴일 대비) */
    Optional<ExchangeRate> findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
            String currency, LocalDate date);
}

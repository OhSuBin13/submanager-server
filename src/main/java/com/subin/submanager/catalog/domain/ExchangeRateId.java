package com.subin.submanager.catalog.domain;

import java.io.Serializable;
import java.time.LocalDate;

public record ExchangeRateId(String currency, LocalDate rateDate) implements Serializable {
}

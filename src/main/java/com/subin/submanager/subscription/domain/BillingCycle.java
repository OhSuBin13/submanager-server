package com.subin.submanager.subscription.domain;

import java.time.LocalDate;

public enum BillingCycle {
    MONTHLY(1),
    YEARLY(12);

    private final int months;

    BillingCycle(int months) {
        this.months = months;
    }

    /**
     * 첫 결제일로부터 n번째 주기의 결제일. 매번 첫 결제일을 기준으로 계산하므로
     * 31일 결제가 2월에 28일로 당겨져도 3월에는 다시 31일이 된다.
     */
    public LocalDate nthBillingDate(LocalDate firstBillingDate, int n) {
        return firstBillingDate.plusMonths((long) months * n);
    }

    /** 기준일(포함) 이후 첫 결제일 */
    public LocalDate firstBillingDateOnOrAfter(LocalDate firstBillingDate, LocalDate baseDate) {
        int n = 0;
        LocalDate date = firstBillingDate;
        while (date.isBefore(baseDate)) {
            date = nthBillingDate(firstBillingDate, ++n);
        }
        return date;
    }
}

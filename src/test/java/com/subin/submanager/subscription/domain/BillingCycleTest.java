package com.subin.submanager.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class BillingCycleTest {

    @Test
    void 말일_결제는_짧은_달에_말일로_당기고_다음_달에는_원래_날짜로_돌아온다() {
        LocalDate first = LocalDate.of(2026, 1, 31);

        assertThat(BillingCycle.MONTHLY.nthBillingDate(first, 1)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(BillingCycle.MONTHLY.nthBillingDate(first, 2)).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(BillingCycle.MONTHLY.nthBillingDate(first, 3)).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void 윤년_2월_29일_연간_결제는_평년에_28일이다() {
        LocalDate first = LocalDate.of(2028, 2, 29);

        assertThat(BillingCycle.YEARLY.nthBillingDate(first, 1)).isEqualTo(LocalDate.of(2029, 2, 28));
        assertThat(BillingCycle.YEARLY.nthBillingDate(first, 4)).isEqualTo(LocalDate.of(2032, 2, 29));
    }

    @Test
    void 기준일과_같은_날이면_그날이_다음_결제일이다() {
        LocalDate first = LocalDate.of(2026, 5, 13);

        assertThat(BillingCycle.MONTHLY.firstBillingDateOnOrAfter(first, LocalDate.of(2026, 10, 13)))
                .isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(BillingCycle.MONTHLY.firstBillingDateOnOrAfter(first, LocalDate.of(2026, 10, 14)))
                .isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    void 첫_결제일이_미래면_첫_결제일이_다음_결제일이다() {
        LocalDate first = LocalDate.of(2026, 12, 1);

        assertThat(BillingCycle.YEARLY.firstBillingDateOnOrAfter(first, LocalDate.of(2026, 10, 13)))
                .isEqualTo(first);
    }
}

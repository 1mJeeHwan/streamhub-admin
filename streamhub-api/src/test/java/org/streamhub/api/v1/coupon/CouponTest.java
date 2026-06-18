package org.streamhub.api.v1.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.streamhub.api.v1.coupon.entity.Coupon;
import org.streamhub.api.v1.coupon.entity.DiscountType;

/**
 * Unit tests for the coupon redemption logic — the heart of the previously-missing discount engine:
 * min-order threshold, percent cap, truncation unit, the floor against over-discounting, the valid
 * window, and usage-limit exhaustion.
 */
class CouponTest {

    private Coupon coupon(DiscountType type, int value, int minOrder, Integer cap, int roundUnit) {
        return Coupon.builder()
                .code("TEST").name("test").discountType(type).discountValue(value)
                .minOrderAmount(minOrder).maxDiscountAmount(cap).roundUnit(roundUnit)
                .startAt(LocalDateTime.now().minusDays(1)).endAt(LocalDateTime.now().plusDays(1))
                .useYn("Y").build();
    }

    @ParameterizedTest(name = "[{index}] {0} {1} on {2}원 (min {3}, cap {4}, round {5}) → {6}원")
    @CsvSource({
            // type,    value, order,  min,   cap,   round, expected
            "AMOUNT,    2000,  30000,  10000, ,      0,     2000",   // flat amount
            "AMOUNT,    2000,  5000,   10000, ,      0,     0",      // below min-order → 0
            "PERCENT,   10,    30000,  0,     ,      0,     3000",   // 10% of 30000
            "PERCENT,   10,    30000,  0,     2000,  0,     2000",   // capped at 2000
            "PERCENT,   15,    33333,  0,     ,      100,   4900",   // 4999.95→4999 floored, truncated to 100단위
            "AMOUNT,    50000, 30000,  0,     ,      0,     30000",  // discount never exceeds order
    })
    void computeDiscount_table(String type, int value, long order, int min,
                               Integer cap, int roundUnit, long expected) {
        Coupon c = coupon(DiscountType.valueOf(type), value, min, cap, roundUnit);
        assertThat(c.computeDiscount(order)).isEqualTo(expected);
    }

    @Test
    void isRedeemableAt_respectsWindowEnabledAndUsageLimit() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(coupon(DiscountType.AMOUNT, 1000, 0, null, 0).isRedeemableAt(now)).isTrue();

        Coupon disabled = Coupon.builder().code("D").name("d").discountType(DiscountType.AMOUNT)
                .discountValue(1000).minOrderAmount(0).roundUnit(0)
                .startAt(now.minusDays(1)).endAt(now.plusDays(1)).useYn("N").build();
        assertThat(disabled.isRedeemableAt(now)).isFalse();

        Coupon expired = Coupon.builder().code("E").name("e").discountType(DiscountType.AMOUNT)
                .discountValue(1000).minOrderAmount(0).roundUnit(0)
                .startAt(now.minusDays(10)).endAt(now.minusDays(1)).useYn("Y").build();
        assertThat(expired.isRedeemableAt(now)).isFalse();
    }

    @Test
    void redeem_incrementsUsedCount_andEnforcesLimit() {
        Coupon limited = Coupon.builder().code("L").name("l").discountType(DiscountType.AMOUNT)
                .discountValue(1000).minOrderAmount(0).roundUnit(0)
                .startAt(LocalDateTime.now().minusDays(1)).endAt(LocalDateTime.now().plusDays(1))
                .useYn("Y").usageLimit(1).build();

        assertThat(limited.getUsedCount()).isZero();
        limited.redeem();
        assertThat(limited.getUsedCount()).isEqualTo(1);
        assertThat(limited.isRedeemableAt(LocalDateTime.now())).isFalse(); // exhausted
        assertThatThrownBy(limited::redeem).isInstanceOf(IllegalStateException.class);
    }
}

package org.streamhub.api.v1.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.coupon.entity.Coupon;
import org.streamhub.api.v1.coupon.entity.CouponRedemption;
import org.streamhub.api.v1.coupon.entity.DiscountType;
import org.streamhub.api.v1.coupon.repository.CouponRedemptionRepository;
import org.streamhub.api.v1.coupon.repository.CouponRepository;

/**
 * Unit tests for the coupon redemption guards in {@link CouponService#redeem}: a member cannot
 * redeem the same coupon twice (per-member unique constraint → {@code DataIntegrityViolationException}),
 * and the global usage limit is enforced atomically (the conditional UPDATE returning 0 rows means
 * exhausted). Both reject without applying a discount.
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceRedeemTest {

    @Mock private CouponRepository couponRepository;
    @Mock private CouponRedemptionRepository couponRedemptionRepository;
    @Mock private ActionLogPublisher actionLogPublisher;

    private CouponService service() {
        return new CouponService(couponRepository, couponRedemptionRepository, actionLogPublisher);
    }

    private Coupon coupon(Integer usageLimit) {
        Coupon coupon = Coupon.builder()
                .code("WELCOME").name("환영 쿠폰").discountType(DiscountType.AMOUNT)
                .discountValue(2_000).minOrderAmount(0).roundUnit(0)
                .startAt(LocalDateTime.now().minusDays(1)).endAt(LocalDateTime.now().plusDays(1))
                .useYn("Y").usageLimit(usageLimit).build();
        ReflectionTestUtils.setField(coupon, "id", 42L);
        return coupon;
    }

    @Test
    void redeem_firstTime_succeeds_andIncrementsAtomically() {
        when(couponRepository.findByCode("WELCOME")).thenReturn(Optional.of(coupon(null)));
        when(couponRepository.incrementUsedCount(42L)).thenReturn(1);

        CouponService.RedeemResult result = service().redeem("WELCOME", 30_000L, 1L);

        assertThat(result.discount()).isEqualTo(2_000L);
        verify(couponRedemptionRepository).saveAndFlush(any(CouponRedemption.class));
        verify(couponRepository).incrementUsedCount(42L);
    }

    @Test
    void redeem_sameMemberTwice_isRejected() {
        when(couponRepository.findByCode("WELCOME")).thenReturn(Optional.of(coupon(null)));
        when(couponRedemptionRepository.saveAndFlush(any(CouponRedemption.class)))
                .thenThrow(new DataIntegrityViolationException("uk_coupon_redemption_member"));

        assertThatThrownBy(() -> service().redeem("WELCOME", 30_000L, 1L))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode").isEqualTo(ResultCode.INVALID_PARAMETER);

        // Per-member guard fires before the global increment.
        verify(couponRepository, never()).incrementUsedCount(eq(42L));
    }

    @Test
    void redeem_whenGlobalLimitExhausted_isRejected() {
        when(couponRepository.findByCode("WELCOME")).thenReturn(Optional.of(coupon(1)));
        when(couponRepository.incrementUsedCount(42L)).thenReturn(0); // 0 rows → exhausted

        assertThatThrownBy(() -> service().redeem("WELCOME", 30_000L, 2L))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode").isEqualTo(ResultCode.INVALID_PARAMETER);
    }
}

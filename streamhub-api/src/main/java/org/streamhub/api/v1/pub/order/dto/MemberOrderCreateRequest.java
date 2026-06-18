package org.streamhub.api.v1.pub.order.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Public album-purchase request from a logged-in member.
 *
 * @param albumId     target album id (must be ON_SALE and bridged to a GOODS_ITEM)
 * @param payProvider PG code to attempt ({@code TOSS}/{@code KAKAO}/{@code PAYPAL}/{@code CARD});
 *                    null defaults to {@code TOSS}. All approvals are mock (demo) — no real PG call.
 * @param couponCode  optional discount-coupon code to redeem against this order (null = none)
 */
public record MemberOrderCreateRequest(
        @NotNull(message = "앨범은 필수입니다") Long albumId,
        String payProvider,
        String couponCode) {
}

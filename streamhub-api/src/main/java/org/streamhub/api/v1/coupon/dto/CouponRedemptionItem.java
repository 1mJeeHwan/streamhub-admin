package org.streamhub.api.v1.coupon.dto;

import java.time.LocalDateTime;

/**
 * One row of a coupon's usage history: which member redeemed it and when. Built directly by a JPQL
 * constructor expression that joins {@code COUPON_REDEMPTION} to {@code MEMBER}, so the read needs no
 * extra service-side enrichment.
 *
 * @param id          redemption row id
 * @param memberId    redeeming member id (links to the member detail screen)
 * @param memberName  redeeming member's name
 * @param redeemedAt  redemption timestamp
 */
public record CouponRedemptionItem(
        Long id,
        Long memberId,
        String memberName,
        LocalDateTime redeemedAt) {
}

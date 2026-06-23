package org.streamhub.api.v1.coupon.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.streamhub.api.v1.coupon.dto.CouponRedemptionItem;
import org.streamhub.api.v1.coupon.entity.CouponRedemption;

/**
 * JPA repository for {@link CouponRedemption} (per-member coupon usage ledger). The
 * {@code (coupon_id, member_id)} unique constraint — not application-level checks — is what prevents
 * a member from redeeming the same coupon twice; a duplicate insert raises
 * {@code DataIntegrityViolationException}.
 */
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    /**
     * Deletes the redemption row for a {@code (coupon_id, member_id)} pair, freeing the per-member
     * slot so the member could redeem the coupon again after a cancel/refund. Returns the number of
     * rows deleted — {@code 0} when no row existed (idempotent release).
     */
    @Modifying
    @Query("DELETE FROM CouponRedemption r WHERE r.couponId = :couponId AND r.memberId = :memberId")
    int deleteByCouponIdAndMemberId(@Param("couponId") Long couponId, @Param("memberId") Long memberId);

    /** Coupon ids this member has already redeemed — used to flag {@code used} in the coupon box. */
    @Query("SELECT r.couponId FROM CouponRedemption r WHERE r.memberId = :memberId")
    List<Long> findCouponIdsByMemberId(@Param("memberId") Long memberId);

    /**
     * Usage history for one coupon — who redeemed it and when, newest first. Joins {@code MEMBER} to
     * carry the redeeming member's name so the admin drill-down (사용수 → 사용 내역) needs no extra
     * lookup. An ad-hoc entity join (no mapped relation between the two entities).
     *
     * <p>Coupons are global, but their redemptions expose member identity (PII), so a
     * CHURCH_MANAGER may only see redemptions by its own church's members. Pass {@code churchId}
     * to scope; pass {@code null} (SYSTEM/VIEWER) to see every church's redeemers.
     */
    @Query("SELECT new org.streamhub.api.v1.coupon.dto.CouponRedemptionItem("
            + "r.id, r.memberId, m.name, r.redeemedAt) "
            + "FROM CouponRedemption r JOIN Member m ON m.id = r.memberId "
            + "WHERE r.couponId = :couponId AND (:churchId IS NULL OR m.churchId = :churchId) "
            + "ORDER BY r.redeemedAt DESC")
    List<CouponRedemptionItem> findRedemptions(@Param("couponId") Long couponId,
            @Param("churchId") Long churchId);
}

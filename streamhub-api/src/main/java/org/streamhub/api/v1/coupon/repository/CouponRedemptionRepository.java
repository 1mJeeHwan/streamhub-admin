package org.streamhub.api.v1.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.coupon.entity.CouponRedemption;

/**
 * JPA repository for {@link CouponRedemption} (per-member coupon usage ledger). The
 * {@code (coupon_id, member_id)} unique constraint — not application-level checks — is what prevents
 * a member from redeeming the same coupon twice; a duplicate insert raises
 * {@code DataIntegrityViolationException}.
 */
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
}

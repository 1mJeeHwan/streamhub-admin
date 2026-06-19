package org.streamhub.api.v1.coupon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One member's redemption of one coupon — the per-member usage ledger. The
 * {@code (coupon_id, member_id)} unique constraint is the enforcement point: a member can redeem a
 * given coupon at most once, and a concurrent double-redeem loses the race at the DB and surfaces as
 * a {@code DataIntegrityViolationException} on insert (caught in {@code CouponService.redeem}).
 *
 * <p>Schema note: with {@code ddl-auto=update} Hibernate creates the {@code COUPON_REDEMPTION} table
 * and its unique index ({@code uk_coupon_redemption_member}) automatically on startup — no manual
 * migration required for the demo.
 */
@Entity
@Table(name = "COUPON_REDEMPTION",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_coupon_redemption_member",
                        columnNames = {"coupon_id", "member_id"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "redeemed_at", nullable = false)
    private LocalDateTime redeemedAt;

    @Builder
    private CouponRedemption(Long couponId, Long memberId, LocalDateTime redeemedAt) {
        this.couponId = couponId;
        this.memberId = memberId;
        this.redeemedAt = redeemedAt != null ? redeemedAt : LocalDateTime.now();
    }
}

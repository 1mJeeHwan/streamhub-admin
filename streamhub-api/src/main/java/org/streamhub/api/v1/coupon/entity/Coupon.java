package org.streamhub.api.v1.coupon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A discount coupon. {@code discountType} drives the calculation: {@code AMOUNT} subtracts
 * a flat won amount; {@code PERCENT} subtracts {@code discountValue}% capped at
 * {@code maxDiscountAmount}. {@code roundUnit} is the truncation unit (절사단위) applied to
 * the computed discount. All values are demo/fictional (no real promotion data — PII guard).
 */
@Entity
@Table(name = "COUPON", indexes = {
        @Index(name = "idx_coupon_code", columnList = "code", unique = true),
        @Index(name = "idx_coupon_use", columnList = "use_yn")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    /** 할인 값: {@code AMOUNT}이면 원, {@code PERCENT}이면 퍼센트. */
    @Column(name = "discount_value", nullable = false)
    private int discountValue;

    @Column(name = "min_order_amount", nullable = false)
    private int minOrderAmount;

    /** {@code PERCENT} 할인 상한(원). {@code AMOUNT}이면 null. */
    @Column(name = "max_discount_amount")
    private Integer maxDiscountAmount;

    /** 절사단위(원). 0이면 절사 없음. */
    @Column(name = "round_unit", nullable = false)
    private int roundUnit;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "use_yn", nullable = false, length = 1)
    private String useYn;

    /** 총 사용 가능 횟수. {@code null}이면 무제한. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    /** 지금까지 주문에 적용된(소진된) 횟수. */
    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Coupon(String code, String name, DiscountType discountType, int discountValue,
                   int minOrderAmount, Integer maxDiscountAmount, int roundUnit,
                   LocalDateTime startAt, LocalDateTime endAt, String useYn,
                   Integer usageLimit, Integer usedCount, LocalDateTime createdAt) {
        this.code = code;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.roundUnit = roundUnit;
        this.startAt = startAt;
        this.endAt = endAt;
        this.useYn = useYn != null ? useYn : "Y";
        this.usageLimit = usageLimit;
        this.usedCount = usedCount != null ? usedCount : 0;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /** Updates editable fields (used-count is not editable — it is driven by redemption). */
    public void update(String code, String name, DiscountType discountType, int discountValue,
                       int minOrderAmount, Integer maxDiscountAmount, int roundUnit,
                       LocalDateTime startAt, LocalDateTime endAt, String useYn, Integer usageLimit) {
        this.code = code;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.roundUnit = roundUnit;
        this.startAt = startAt;
        this.endAt = endAt;
        this.useYn = useYn;
        this.usageLimit = usageLimit;
    }

    /**
     * Whether this coupon may be redeemed at {@code now}: enabled, within its valid window, and
     * not exhausted.
     */
    public boolean isRedeemableAt(LocalDateTime now) {
        return "Y".equals(useYn)
                && !now.isBefore(startAt) && !now.isAfter(endAt)
                && (usageLimit == null || usedCount < usageLimit);
    }

    /**
     * Discount (won) this coupon yields for {@code orderAmount}, applying min-order threshold,
     * percent cap, truncation unit, and a floor so the discount can never exceed the order.
     * Returns 0 if the order does not meet {@code minOrderAmount}.
     */
    public long computeDiscount(long orderAmount) {
        if (orderAmount < minOrderAmount) {
            return 0L;
        }
        long raw;
        if (discountType == DiscountType.AMOUNT) {
            raw = discountValue;
        } else {
            raw = orderAmount * discountValue / 100;
            if (maxDiscountAmount != null) {
                raw = Math.min(raw, maxDiscountAmount);
            }
        }
        if (roundUnit > 0) {
            raw = (raw / roundUnit) * roundUnit;
        }
        return Math.max(0L, Math.min(raw, orderAmount));
    }

    /** Consumes one use. Throws if the usage limit is already reached. */
    public void redeem() {
        if (usageLimit != null && usedCount >= usageLimit) {
            throw new IllegalStateException("coupon usage limit reached");
        }
        this.usedCount++;
    }
}

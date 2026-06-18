package org.streamhub.api.v1.coupon.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.coupon.entity.Coupon;
import org.streamhub.api.v1.coupon.entity.DiscountType;

/**
 * A discount coupon row. Used as both the admin create/update input and the list/detail
 * output. All values are demo/fictional (PII guard). Mutable to match the project DTO style.
 */
@Getter
@Setter
@NoArgsConstructor
public class CouponDto {
    private Long id;
    private String code;
    private String name;
    private DiscountType discountType;
    private int discountValue;
    private int minOrderAmount;
    private Integer maxDiscountAmount;
    private int roundUnit;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String useYn;
    /** 총 사용 가능 횟수 (null = 무제한). */
    private Integer usageLimit;
    /** 지금까지 적용된 횟수 (read-only; 주문 적용 시 증가). */
    private int usedCount;
    private LocalDateTime createdAt;

    /** Builds a DTO from a persisted coupon. */
    public static CouponDto from(Coupon coupon) {
        CouponDto dto = new CouponDto();
        dto.id = coupon.getId();
        dto.code = coupon.getCode();
        dto.name = coupon.getName();
        dto.discountType = coupon.getDiscountType();
        dto.discountValue = coupon.getDiscountValue();
        dto.minOrderAmount = coupon.getMinOrderAmount();
        dto.maxDiscountAmount = coupon.getMaxDiscountAmount();
        dto.roundUnit = coupon.getRoundUnit();
        dto.startAt = coupon.getStartAt();
        dto.endAt = coupon.getEndAt();
        dto.useYn = coupon.getUseYn();
        dto.usageLimit = coupon.getUsageLimit();
        dto.usedCount = coupon.getUsedCount();
        dto.createdAt = coupon.getCreatedAt();
        return dto;
    }
}

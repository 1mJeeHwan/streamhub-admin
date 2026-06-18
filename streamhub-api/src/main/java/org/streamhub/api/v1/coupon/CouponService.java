package org.streamhub.api.v1.coupon;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.coupon.dto.CouponDto;
import org.streamhub.api.v1.coupon.dto.CouponSearchRequest;
import org.streamhub.api.v1.coupon.entity.Coupon;
import org.streamhub.api.v1.coupon.entity.DiscountType;
import org.streamhub.api.v1.coupon.repository.CouponRepository;

/**
 * Discount-coupon management: admin CRUD plus a filtered listing. The demo dataset is small,
 * so listing loads all coupons and filters/sorts in memory (newest first) — no query
 * specialization needed.
 */
@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final ActionLogPublisher actionLogPublisher;

    public CouponService(CouponRepository couponRepository, ActionLogPublisher actionLogPublisher) {
        this.couponRepository = couponRepository;
        this.actionLogPublisher = actionLogPublisher;
    }

    /** Admin listing: all coupons newest first, optionally filtered by useYn / keyword. */
    @Transactional(readOnly = true)
    public List<CouponDto> list(CouponSearchRequest request) {
        String useYn = request != null ? request.useYn() : null;
        String keyword = request != null && request.keyword() != null
                ? request.keyword().trim().toLowerCase() : null;
        return couponRepository.findAll().stream()
                .filter(coupon -> useYn == null || useYn.isBlank() || useYn.equals(coupon.getUseYn()))
                .filter(coupon -> keyword == null || keyword.isBlank()
                        || coupon.getCode().toLowerCase().contains(keyword)
                        || coupon.getName().toLowerCase().contains(keyword))
                .sorted(Comparator.comparing(Coupon::getId).reversed())
                .map(CouponDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponDto detail(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        return CouponDto.from(coupon);
    }

    @Transactional
    public CouponDto create(CouponDto request) {
        validate(request);
        if (couponRepository.existsByCode(request.getCode())) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "이미 존재하는 쿠폰 코드입니다");
        }
        Coupon coupon = Coupon.builder()
                .code(request.getCode())
                .name(request.getName())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .minOrderAmount(request.getMinOrderAmount())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .roundUnit(request.getRoundUnit())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .useYn(defaultYn(request.getUseYn()))
                .usageLimit(request.getUsageLimit())
                .build();
        Coupon saved = couponRepository.save(coupon);
        actionLogPublisher.publish("COUPON_CREATE", "COUPON", String.valueOf(saved.getId()), request.getName());
        return CouponDto.from(saved);
    }

    @Transactional
    public CouponDto update(Long id, CouponDto request) {
        validate(request);
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!coupon.getCode().equals(request.getCode()) && couponRepository.existsByCode(request.getCode())) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "이미 존재하는 쿠폰 코드입니다");
        }
        coupon.update(
                request.getCode(), request.getName(), request.getDiscountType(),
                request.getDiscountValue(), request.getMinOrderAmount(), request.getMaxDiscountAmount(),
                request.getRoundUnit(), request.getStartAt(), request.getEndAt(),
                defaultYn(request.getUseYn()), request.getUsageLimit());
        couponRepository.saveAndFlush(coupon);
        actionLogPublisher.publish("COUPON_UPDATE", "COUPON", String.valueOf(id), request.getName());
        return CouponDto.from(coupon);
    }

    @Transactional
    public void delete(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        couponRepository.delete(coupon);
        actionLogPublisher.publish("COUPON_DELETE", "COUPON", String.valueOf(id), coupon.getName());
    }

    /**
     * Validates a coupon code against an order amount and consumes one use. Called from the order
     * flow inside the order's transaction, so a later failure rolls back the {@code usedCount}
     * increment too. Returns the computed discount (won) to apply to the order.
     *
     * @throws ApiException {@code NOT_FOUND} if the code is unknown,
     *                      {@code INVALID_PARAMETER} if expired/disabled/exhausted or below the
     *                      minimum order amount
     */
    @Transactional
    public RedeemResult redeem(String code, long orderAmount) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND, "존재하지 않는 쿠폰입니다"));
        if (!coupon.isRedeemableAt(LocalDateTime.now())) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "사용할 수 없는 쿠폰입니다");
        }
        if (orderAmount < coupon.getMinOrderAmount()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER,
                    "최소 주문 금액(" + coupon.getMinOrderAmount() + "원) 이상이어야 합니다");
        }
        long discount = coupon.computeDiscount(orderAmount);
        if (discount <= 0) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "할인 금액이 없는 쿠폰입니다");
        }
        coupon.redeem();
        couponRepository.saveAndFlush(coupon);
        return new RedeemResult(coupon.getId(), discount);
    }

    /** Computed discount for a successfully redeemed coupon. */
    public record RedeemResult(Long couponId, long discount) {
    }

    // --- helpers -----------------------------------------------------------

    private void validate(CouponDto request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "쿠폰 코드는 필수입니다");
        }
        if (request.getDiscountType() == null) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "할인 유형은 필수입니다");
        }
        if (request.getDiscountValue() <= 0) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "할인 값은 0보다 커야 합니다");
        }
        if (request.getDiscountType() == DiscountType.PERCENT && request.getDiscountValue() > 100) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "정률 할인은 100%를 넘을 수 없습니다");
        }
        if (request.getStartAt() == null || request.getEndAt() == null
                || request.getEndAt().isBefore(request.getStartAt())) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "유효 기간이 올바르지 않습니다");
        }
        if (request.getUsageLimit() != null && request.getUsageLimit() <= 0) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "사용 가능 횟수는 0보다 커야 합니다");
        }
    }

    private String defaultYn(String value) {
        return value == null || value.isBlank() ? "Y" : value;
    }
}

package org.streamhub.api.v1.order.entity;

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
 * A goods order. The table is named {@code ORDERS} because {@code ORDER} is a SQL
 * reserved word; the entity class keeps the natural name {@code Order}.
 */
@Entity
@Table(name = "ORDERS", indexes = {
        @Index(name = "idx_orders_member", columnList = "member_id"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_ordered_at", columnList = "ordered_at"),
        @Index(name = "idx_orders_order_no", columnList = "order_no"),
        @Index(name = "idx_orders_pay_status", columnList = "pay_status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Order number, e.g. {@code YYYYMMDD-XXXXXX}. */
    @Column(name = "order_no", nullable = false, unique = true, length = 30)
    private String orderNo;

    /** FK → MEMBER. */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private OrderStatus status;

    @Column(name = "ordered_name", nullable = false, length = 50)
    private String orderedName;

    @Column(name = "ordered_phone", length = 20)
    private String orderedPhone;

    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

    @Column(name = "receiver_addr", length = 300)
    private String receiverAddr;

    /** Goods subtotal (including option extra charges). */
    @Column(name = "goods_total", nullable = false)
    private Long goodsTotal;

    @Column(name = "ship_fee", nullable = false)
    private Long shipFee;

    @Column(name = "coupon_discount", nullable = false)
    private Long couponDiscount;

    /**
     * FK → COUPON of the coupon actually consumed for this order (a redemption row exists and
     * {@code usedCount} was incremented). Null when no coupon was applied or its consumption is still
     * pending (real-PG prepare → confirm). Set when redemption is committed; read by the
     * cancel/refund path to release the redemption back to the member and global pool.
     */
    @Column(name = "coupon_id")
    private Long couponId;

    /**
     * Coupon code carried from {@code prepare} to {@code confirm} on the real-PG path. On prepare the
     * coupon is only validated and the discount is computed (not consumed); the actual redemption is
     * deferred to the payment-approval transaction in {@code confirm}. Null once consumed (or when no
     * coupon / on the single-transaction demo path, which consumes immediately).
     */
    @Column(name = "pending_coupon_code", length = 40)
    private String pendingCouponCode;

    @Column(name = "point_used", nullable = false)
    private Long pointUsed;

    /** Final total = goodsTotal + shipFee − couponDiscount − pointUsed (floored at 0). */
    @Column(name = "total", nullable = false)
    private Long total;

    /** {@code BANK} / {@code CARD}. */
    @Column(name = "pay_method", nullable = false, length = 20)
    private String payMethod;

    @Column(name = "tracking_no", length = 50)
    private String trackingNo;

    @Column(name = "ship_company", length = 50)
    private String shipCompany;

    /** PG attempted (C4 payment seam). {@code MOCK}/{@code TOSS}/{@code PAYPAL}/{@code KAKAO}/{@code CARD}. */
    @Column(name = "pay_provider", nullable = false, length = 20)
    private String payProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_status", nullable = false, length = 12)
    private PayStatus payStatus;

    /**
     * Transaction id issued at the payment-request step (C4 payment seam). The approve step must
     * present the same id; a mismatch is rejected. Null until a request is recorded.
     */
    @Column(name = "pay_txn_id", length = 60)
    private String payTxnId;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Order(String orderNo, Long memberId, OrderStatus status, String orderedName,
                  String orderedPhone, String receiverName, String receiverPhone,
                  String receiverAddr, Long goodsTotal, Long shipFee, Long couponDiscount,
                  Long couponId, String pendingCouponCode, Long pointUsed, Long total,
                  String payMethod, String trackingNo, String shipCompany, String payProvider,
                  PayStatus payStatus, LocalDateTime orderedAt) {
        this.orderNo = orderNo;
        this.memberId = memberId;
        this.status = status;
        this.orderedName = orderedName;
        this.orderedPhone = orderedPhone;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.receiverAddr = receiverAddr;
        this.goodsTotal = goodsTotal != null ? goodsTotal : 0L;
        this.shipFee = shipFee != null ? shipFee : 0L;
        this.couponDiscount = couponDiscount != null ? couponDiscount : 0L;
        this.couponId = couponId;
        this.pendingCouponCode = pendingCouponCode;
        this.pointUsed = pointUsed != null ? pointUsed : 0L;
        this.total = total;
        this.payMethod = payMethod;
        this.trackingNo = trackingNo;
        this.shipCompany = shipCompany;
        this.payProvider = payProvider != null ? payProvider : "MOCK";
        this.payStatus = payStatus != null ? payStatus : PayStatus.NONE;
        this.orderedAt = orderedAt != null ? orderedAt : LocalDateTime.now();
        this.updatedAt = this.orderedAt;
        if (this.total == null) {
            recalcTotal(this.goodsTotal);
        }
    }

    /**
     * Recomputes {@code total} from the given goods subtotal and the stored fees/discounts.
     * Single source of truth used by the seed generator and future order creation.
     */
    public void recalcTotal(long goodsTotal) {
        this.goodsTotal = goodsTotal;
        long computed = goodsTotal + this.shipFee - this.couponDiscount - this.pointUsed;
        this.total = Math.max(0L, computed);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Records the coupon that was actually consumed for this order and clears any pending code. Called
     * from the order flow once redemption is committed, so the cancel/refund path can later release it.
     */
    public void applyCoupon(Long couponId) {
        this.couponId = couponId;
        this.pendingCouponCode = null;
        this.updatedAt = LocalDateTime.now();
    }

    /** Transitions the order status (transition legality is enforced by the service). */
    public void changeStatus(OrderStatus to) {
        this.status = to;
        this.updatedAt = LocalDateTime.now();
    }

    /** Sets shipment tracking info. */
    public void setTracking(String trackingNo, String shipCompany) {
        this.trackingNo = trackingNo;
        this.shipCompany = shipCompany;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Records a payment request against this order, retaining the issued transaction id so the
     * approve step can verify it (C4 payment seam).
     */
    public void applyPayRequest(String payProvider, String payTxnId) {
        this.payProvider = payProvider;
        this.payTxnId = payTxnId;
        this.payStatus = PayStatus.REQUESTED;
        this.updatedAt = LocalDateTime.now();
    }

    /** Marks the payment as approved (C4 payment seam). */
    public void applyPayApprove() {
        this.payStatus = PayStatus.APPROVED;
        this.updatedAt = LocalDateTime.now();
    }

    /** Marks the payment as canceled (C4 payment seam). */
    public void applyPayCancel() {
        this.payStatus = PayStatus.CANCELED;
        this.updatedAt = LocalDateTime.now();
    }
}

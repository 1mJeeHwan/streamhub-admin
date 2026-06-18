package org.streamhub.api.v1.payment.dto;

import jakarta.validation.constraints.NotNull;
import org.streamhub.api.v1.order.entity.OrderStatus;

/**
 * Payment cancel/refund request (C4 payment seam). Refunds the money at the PG, then reverses the
 * internal ledger + stock by transitioning the order to {@code CANCEL} (default) or {@code RETURN}.
 *
 * @param orderId target order id
 * @param toStatus terminal status to apply — {@code CANCEL} or {@code RETURN}; defaults to
 *                 {@code CANCEL} when null
 * @param reason  human-readable cancel reason (취소 사유) sent to the PG and written to the receipt
 *                memo; may be null
 */
public record PayCancelCommand(
        @NotNull(message = "주문은 필수입니다") Long orderId,
        OrderStatus toStatus,
        String reason) {

    /** The terminal status to transition to: the requested one, or {@code CANCEL} by default. */
    public OrderStatus resolvedStatus() {
        return toStatus == OrderStatus.RETURN ? OrderStatus.RETURN : OrderStatus.CANCEL;
    }
}

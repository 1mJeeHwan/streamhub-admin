package org.streamhub.api.v1.payment.adapter;

import org.streamhub.api.v1.order.entity.PayStatus;

/**
 * Provider-agnostic payment result returned from a {@link PaymentProvider} (C4 payment seam).
 *
 * @param status      resulting {@link PayStatus} ({@code REQUESTED} / {@code APPROVED} / {@code FAILED})
 * @param provider    PG code that produced this result
 * @param txnId       transaction id (mock = {@code MOCK-{orderNo}-{seq}}, Toss = paymentKey,
 *                    Kakao = {@code tid}, PayPal = order id)
 * @param amount      charged amount (echoed back from the request on approval)
 * @param memo        human-readable note (e.g. {@code "MOCK 승인(실거래 아님)"})
 * @param redirectUrl for server-initiated redirect PGs (Kakao/PayPal), the URL the browser must
 *                    navigate to so the user can authorise the payment; {@code null} for
 *                    client-SDK PGs (Toss) and mock
 */
public record PaymentResult(
        PayStatus status, String provider, String txnId, Long amount, String memo, String redirectUrl) {

    /** A successful payment request (status {@link PayStatus#REQUESTED}), no redirect. */
    public static PaymentResult requested(String provider, String txnId) {
        return new PaymentResult(PayStatus.REQUESTED, provider, txnId, null, "결제요청 접수", null);
    }

    /**
     * A successful payment request that requires a browser redirect (Kakao/PayPal): carries the
     * issued transaction id ({@code tid}/order id) and the PG redirect URL.
     */
    public static PaymentResult redirect(String provider, String txnId, String redirectUrl) {
        return new PaymentResult(PayStatus.REQUESTED, provider, txnId, null, "결제창 이동 필요", redirectUrl);
    }

    /** A successful payment approval (status {@link PayStatus#APPROVED}). */
    public static PaymentResult approved(String provider, String txnId, Long amount, String memo) {
        return new PaymentResult(PayStatus.APPROVED, provider, txnId, amount, memo, null);
    }

    /**
     * A successful payment cancel/refund (status {@link PayStatus#CANCELED}); carries the refunded
     * amount and a human-readable note.
     */
    public static PaymentResult canceled(String provider, String txnId, Long amount, String memo) {
        return new PaymentResult(PayStatus.CANCELED, provider, txnId, amount, memo, null);
    }
}

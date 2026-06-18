package org.streamhub.api.v1.payment.adapter;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Default payment provider (demo/test mode). <b>Performs no external call.</b> It synthesises a
 * deterministic transaction id ({@code MOCK-{orderNo}-{seq}}) and always approves. The
 * sequence is process-local and only disambiguates repeated requests for the same order.
 */
@Component
public class MockPaymentProvider implements PaymentProvider {

    private static final String CODE = "MOCK";

    private final AtomicLong seq = new AtomicLong(0);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public PaymentResult requestPayment(PaymentRequest request) {
        String txnId = "MOCK-" + request.orderNo() + "-" + seq.incrementAndGet();
        return PaymentResult.requested(CODE, txnId);
    }

    @Override
    public PaymentResult approve(
            PaymentRequest request, String requestTxnId, String clientToken, String maskedCard) {
        // Mock echoes the request-stage txnId; clientToken equals it in the mock flow.
        String txnId = clientToken != null ? clientToken : requestTxnId;
        String memo = maskedCard == null || maskedCard.isBlank()
                ? "MOCK 승인(실거래 아님)"
                : "MOCK 승인(실거래 아님) " + maskedCard;
        return PaymentResult.approved(CODE, txnId, request.amount(), memo);
    }

    @Override
    public PaymentResult cancel(PaymentRequest request, String txnId, String reason) {
        // No external call — the demo refund path always succeeds at the PG layer.
        return PaymentResult.canceled(CODE, txnId, request.amount(), "MOCK 취소(실거래 아님)");
    }
}

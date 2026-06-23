package org.streamhub.api.v1.payment.adapter;

import com.siot.IamportRestClient.response.Payment;
import org.springframework.stereotype.Component;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.iamport.IamportPaymentService;
import org.streamhub.api.base.iamport.IamportProperty;
import org.streamhub.api.base.response.ResultCode;

/**
 * PortOne(포트원/아임포트) adapter (C4) — <b>real integration</b>, same flow as ng-api. The browser
 * SDK ({@code IMP.request_pay}) authorises the payment and returns an {@code imp_uid}; the server
 * resolves it via the Iamport REST API at {@link #approve} and verifies the PG-recorded status is
 * {@code paid} and the PG-recorded amount equals the server-computed {@code order.total} — so a
 * tampered or unpaid transaction is rejected. Activated by {@code app.payment.provider=PORTONE} plus
 * {@code IAMPORT_*} credentials; with no keys the bean still loads but {@link #approve}/{@link #cancel}
 * fail cleanly (provider stays dormant, mock remains the default).
 */
@Component
public class PortonePaymentProvider implements PaymentProvider {

    private final IamportPaymentService iamport;
    private final IamportProperty property;

    public PortonePaymentProvider(IamportPaymentService iamport, IamportProperty property) {
        this.iamport = iamport;
        this.property = property;
    }

    @Override
    public String code() {
        return "PORTONE";
    }

    @Override
    public PaymentResult requestPayment(PaymentRequest request) {
        // The PortOne payment window runs client-side (IMP.request_pay); the real imp_uid arrives
        // back at approve. Like Toss, the server request step issues nothing.
        return PaymentResult.requested(code(), null);
    }

    @Override
    public PaymentResult approve(
            PaymentRequest request, String requestTxnId, String clientToken, String maskedCard) {
        requireKeys();
        String impUid = clientToken;
        if (impUid == null || impUid.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "imp_uid가 없습니다");
        }

        Payment payment = iamport.findByImpUid(impUid)
                .orElseThrow(() -> new ApiException(ResultCode.INVALID_PARAMETER, "결제 정보를 조회하지 못했습니다"));

        if (!"paid".equalsIgnoreCase(payment.getStatus())) {
            throw new ApiException(ResultCode.INVALID_PARAMETER,
                    "결제가 완료되지 않았습니다 (" + payment.getStatus() + ")");
        }
        // Bind the PG record to THIS order: the merchant_uid the browser passed to IMP.request_pay was
        // the order's orderNo, so the paid record's merchant_uid must equal it. Without this an attacker
        // could replay one paid imp_uid to confirm any other same-amount order. (Re-confirming the same
        // order is already blocked by the payStatus==REQUESTED guard in PaymentService.approve.)
        if (!request.orderNo().equals(payment.getMerchantUid())) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "결제 정보가 주문과 일치하지 않습니다");
        }
        long paid = payment.getAmount() == null ? -1L : payment.getAmount().longValue();
        if (paid != request.amount()) {
            // PG-recorded amount must equal the server total — guards against amount tampering.
            throw new ApiException(ResultCode.INVALID_PARAMETER, "결제 금액이 주문 금액과 일치하지 않습니다");
        }

        String method = payment.getPayMethod() == null ? "" : payment.getPayMethod();
        return PaymentResult.approved(code(), impUid, paid, ("포트원 승인 " + method).trim());
    }

    @Override
    public PaymentResult cancel(PaymentRequest request, String txnId, String reason) {
        requireKeys();
        if (txnId == null || txnId.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "imp_uid가 없습니다");
        }
        Payment canceled = iamport.cancel(txnId, reason)
                .orElseThrow(() -> new ApiException(ResultCode.INVALID_PARAMETER, "포트원 결제 취소에 실패했습니다"));
        long amount = canceled.getAmount() == null ? request.amount() : canceled.getAmount().longValue();
        return PaymentResult.canceled(code(), txnId, amount, "포트원 취소");
    }

    private void requireKeys() {
        if (property.getApikey() == null || property.getApikey().isBlank()
                || property.getSecret() == null || property.getSecret().isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER,
                    "포트원 키 미설정 (IAMPORT_APIKEY / IAMPORT_SECRET)");
        }
    }
}

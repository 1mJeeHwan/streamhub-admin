package org.streamhub.api.v1.payment.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;

/**
 * Toss Payments adapter (C4) — <b>real sandbox integration</b>. Unlike {@link MockPaymentProvider},
 * {@link #approve} calls the live Toss confirm API
 * ({@code POST https://api.tosspayments.com/v1/payments/confirm}) with HTTP Basic auth derived
 * from the configured secret key. The secret key has <b>no default</b> ({@code app.payment.toss.
 * secret-key} is empty unless set): when unset, both {@link #approve} and {@link #cancel} fail
 * cleanly with {@link ResultCode#INVALID_PARAMETER} ("토스 시크릿 키 미설정") rather than calling
 * Toss with empty credentials. Injecting a sandbox/merchant key via {@code PAYMENT_TOSS_SECRET_KEY}
 * activates the live PG without code changes.
 *
 * <p>The Toss v2 payment window runs in the browser and is what actually authorises the card, so
 * the server {@link #requestPayment} is a no-op: the real transaction id ({@code paymentKey}) is
 * issued by the window and arrives here at the {@link #approve} step (as {@code txnId}). The amount
 * confirmed is always the server-computed {@code order.total} carried on {@link PaymentRequest};
 * Toss independently rejects any amount/orderId mismatch.
 */
@Component
public class TossPaymentProvider implements PaymentProvider {

    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String CANCEL_URL = "https://api.tosspayments.com/v1/payments/%s/cancel";

    private final String secretKey;
    private final RestClient restClient;

    public TossPaymentProvider(
            @Value("${app.payment.toss.secret-key:}") String secretKey,
            RestClient.Builder restClientBuilder) {
        this.secretKey = secretKey;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String code() {
        return "TOSS";
    }

    @Override
    public PaymentResult requestPayment(PaymentRequest request) {
        // v2 결제창이 클라이언트에서 결제를 개시하므로 서버 requestPayment는 no-op이다.
        // 실제 거래키(paymentKey)는 결제창 완료 후 successUrl로 전달되어 approve 단계로 들어온다.
        return PaymentResult.requested(code(), null);
    }

    @Override
    public PaymentResult approve(
            PaymentRequest request, String requestTxnId, String clientToken, String maskedCard) {
        // Toss issues nothing at request time (requestTxnId is null); the real paymentKey is the
        // client token the window redirected back with.
        String paymentKey = clientToken;
        if (secretKey == null || secretKey.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER,
                    "토스 시크릿 키 미설정 (PAYMENT_TOSS_SECRET_KEY)");
        }
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "paymentKey가 없습니다");
        }

        String basic = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", request.orderNo(),
                "amount", request.amount());

        // exchange() lets us read Toss's structured {code,message} error body instead of letting
        // RestClient throw an opaque 4xx — so a declined/expired payment surfaces a real message.
        ResponseEntity<JsonNode> response = restClient.post()
                .uri(CONFIRM_URL)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity
                        .status(res.getStatusCode())
                        .body(res.bodyTo(JsonNode.class)));

        JsonNode node = response.getBody();
        if (response.getStatusCode().is2xxSuccessful()) {
            long approved = node != null && node.hasNonNull("totalAmount")
                    ? node.get("totalAmount").asLong() : request.amount();
            String method = node != null && node.hasNonNull("method")
                    ? node.get("method").asText() : "";
            String memo = ("토스 승인(테스트) " + method).trim();
            return PaymentResult.approved(code(), paymentKey, approved, memo);
        }

        throw tossError(node, "토스 결제 승인에 실패했습니다");
    }

    @Override
    public PaymentResult cancel(PaymentRequest request, String txnId, String reason) {
        // The approved Toss paymentKey is the stored txnId; cancel is keyed on it in the URL path.
        if (secretKey == null || secretKey.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER,
                    "토스 시크릿 키 미설정 (PAYMENT_TOSS_SECRET_KEY)");
        }
        if (txnId == null || txnId.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "paymentKey가 없습니다");
        }

        String basic = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        // cancelReason is required by Toss; default to a generic refund reason when none supplied.
        Map<String, Object> body = Map.of(
                "cancelReason", reason == null || reason.isBlank() ? "주문 취소" : reason);

        ResponseEntity<JsonNode> response = restClient.post()
                .uri(String.format(CANCEL_URL, txnId))
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity
                        .status(res.getStatusCode())
                        .body(res.bodyTo(JsonNode.class)));

        JsonNode node = response.getBody();
        if (response.getStatusCode().is2xxSuccessful()) {
            long canceled = node != null && node.hasNonNull("totalAmount")
                    ? node.get("totalAmount").asLong() : request.amount();
            return PaymentResult.canceled(code(), txnId, canceled, "토스 취소(테스트)");
        }

        throw tossError(node, "토스 결제 취소에 실패했습니다");
    }

    /** Builds an {@link ApiException} from Toss's structured {@code {code,message}} error body. */
    private ApiException tossError(JsonNode node, String fallbackMsg) {
        String tossCode = node != null && node.hasNonNull("code")
                ? node.get("code").asText() : "TOSS_ERROR";
        String tossMsg = node != null && node.hasNonNull("message")
                ? node.get("message").asText() : fallbackMsg;
        return new ApiException(ResultCode.INVALID_PARAMETER,
                "토스 결제 실패(" + tossCode + "): " + tossMsg);
    }
}

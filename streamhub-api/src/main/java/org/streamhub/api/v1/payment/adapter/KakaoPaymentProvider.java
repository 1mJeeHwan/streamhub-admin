package org.streamhub.api.v1.payment.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;

/**
 * Kakao Pay adapter (C4) — <b>real single-payment integration</b> against the Kakao Open API
 * ({@code open-api.kakaopay.com}). It is a server-initiated <i>redirect</i> PG (unlike Toss's
 * client SDK): {@link #requestPayment} calls {@code /ready} to get a {@code tid} + a
 * {@code next_redirect_*_url}, the browser is sent to that URL to authorise, and {@link #approve}
 * calls {@code /approve} with the stored {@code tid} + the {@code pg_token} the redirect returns.
 *
 * <p>Registered only when {@code app.payment.kakao.secret-key} is set — without a key the bean is
 * absent and the router falls back to {@link MockPaymentProvider}, so the demo is unaffected. The
 * test merchant code {@code TC0ONETIME} is the default {@code cid}. <b>The real {@code /ready}+
 * {@code /approve} calls are coded but not live-verified in this repo (no committed key).</b>
 */
@Component
@ConditionalOnProperty(name = "app.payment.kakao.secret-key")
public class KakaoPaymentProvider implements PaymentProvider {

    private static final String READY_URL = "https://open-api.kakaopay.com/online/v1/payment/ready";
    private static final String APPROVE_URL = "https://open-api.kakaopay.com/online/v1/payment/approve";

    private final String secretKey;
    private final String cid;
    private final String returnBaseUrl;
    private final RestClient restClient;

    public KakaoPaymentProvider(
            @Value("${app.payment.kakao.secret-key:}") String secretKey,
            @Value("${app.payment.kakao.cid:TC0ONETIME}") String cid,
            @Value("${app.payment.return-base-url:http://localhost:3001}") String returnBaseUrl,
            RestClient.Builder restClientBuilder) {
        this.secretKey = secretKey;
        this.cid = cid;
        this.returnBaseUrl = returnBaseUrl;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String code() {
        return "KAKAO";
    }

    @Override
    public PaymentResult requestPayment(PaymentRequest request) {
        String callbackQuery = "?orderNo=" + request.orderNo() + "&amount=" + request.amount()
                + "&provider=kakao";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", cid);
        body.put("partner_order_id", request.orderNo());
        body.put("partner_user_id", partnerUserId(request.orderNo()));
        body.put("item_name", "StreamHub 음반");
        body.put("quantity", 1);
        body.put("total_amount", request.amount());
        body.put("tax_free_amount", 0);
        body.put("approval_url", returnBaseUrl + "/checkout/success" + callbackQuery);
        body.put("cancel_url", returnBaseUrl + "/checkout/fail?provider=kakao&code=CANCEL");
        body.put("fail_url", returnBaseUrl + "/checkout/fail?provider=kakao&code=FAIL");

        JsonNode node = post(READY_URL, body);
        String tid = text(node, "tid");
        String redirectUrl = text(node, "next_redirect_pc_url");
        if (tid == null || redirectUrl == null) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "카카오페이 결제 준비 응답이 올바르지 않습니다");
        }
        return PaymentResult.redirect(code(), tid, redirectUrl);
    }

    @Override
    public PaymentResult approve(
            PaymentRequest request, String requestTxnId, String clientToken, String maskedCard) {
        // Kakao needs BOTH the server-stored tid (requestTxnId) and the redirect pg_token (clientToken).
        if (requestTxnId == null || requestTxnId.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "카카오페이 tid가 없습니다");
        }
        if (clientToken == null || clientToken.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "pg_token이 없습니다");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", cid);
        body.put("tid", requestTxnId);
        body.put("partner_order_id", request.orderNo());
        body.put("partner_user_id", partnerUserId(request.orderNo()));
        body.put("pg_token", clientToken);

        JsonNode node = post(APPROVE_URL, body);
        long approved = node.hasNonNull("amount") && node.get("amount").hasNonNull("total")
                ? node.get("amount").get("total").asLong() : request.amount();
        return PaymentResult.approved(code(), requestTxnId, approved, "카카오페이 승인(테스트)");
    }

    @Override
    public PaymentResult cancel(PaymentRequest request, String txnId, String reason) {
        // Kakao cancel is /online/v1/payment/cancel with the stored tid + cancel_amount, mirroring
        // the /ready+/approve auth above. Left as a key-gated stub (no committed key to verify the
        // live call), consistent with how this adapter ships its other not-live-verified bits.
        throw new UnsupportedOperationException(
                "카카오페이 결제취소(cancel)는 아직 구현되지 않았습니다 (실 키 미연동)");
    }

    // --- helpers -----------------------------------------------------------

    /** Deterministic, order-scoped user id so /ready and /approve agree (Kakao requires a match). */
    private String partnerUserId(String orderNo) {
        return "u-" + orderNo;
    }

    /** POSTs a JSON body with Kakao SECRET_KEY auth and returns the parsed body, throwing on error. */
    private JsonNode post(String url, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "SECRET_KEY " + secretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity
                        .status(res.getStatusCode())
                        .body(res.bodyTo(JsonNode.class)));

        JsonNode node = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful()) {
            String msg = node != null && node.hasNonNull("error_message")
                    ? node.get("error_message").asText() : "카카오페이 요청 실패";
            throw new ApiException(ResultCode.INVALID_PARAMETER, "카카오페이 실패: " + msg);
        }
        if (node == null) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "카카오페이 응답이 비어 있습니다");
        }
        return node;
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}

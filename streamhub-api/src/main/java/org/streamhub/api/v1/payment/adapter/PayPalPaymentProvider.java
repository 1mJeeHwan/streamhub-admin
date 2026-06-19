package org.streamhub.api.v1.payment.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
 * PayPal adapter (C4) — <b>real Orders v2 integration</b> against the PayPal sandbox
 * ({@code api-m.sandbox.paypal.com}). Like Kakao it is a server-initiated <i>redirect</i> PG:
 * {@link #requestPayment} obtains an OAuth2 token (client-credentials), creates an order
 * ({@code POST /v2/checkout/orders}, intent CAPTURE) and returns the order id + the payer-action
 * (approve) link; the browser is sent there, and {@link #approve} captures the order
 * ({@code POST /v2/checkout/orders/{id}/capture}). The redirect returns the same order id
 * (as {@code token}), so the single-token model fits — {@code requestTxnId == clientToken}.
 *
 * <p>Registered only when {@code app.payment.paypal.client-id} is set; otherwise absent and the
 * router falls back to {@link MockPaymentProvider}. <b>Coded but not live-verified here (no committed
 * key).</b> Note: PayPal does not transact in all currencies the same way — {@code app.payment.
 * paypal.currency} (default USD) sets {@code currency_code}; a real KRW catalogue would map prices
 * to that currency rather than sending the raw won amount.
 */
@Component
@ConditionalOnProperty(name = "app.payment.paypal.client-id")
public class PayPalPaymentProvider implements PaymentProvider {

    private static final String BASE = "https://api-m.sandbox.paypal.com";
    private static final String TOKEN_URL = BASE + "/v1/oauth2/token";
    private static final String ORDERS_URL = BASE + "/v2/checkout/orders";

    private final String clientId;
    private final String secret;
    private final String currency;
    private final String returnBaseUrl;
    private final RestClient restClient;

    public PayPalPaymentProvider(
            @Value("${app.payment.paypal.client-id:}") String clientId,
            @Value("${app.payment.paypal.secret:}") String secret,
            @Value("${app.payment.paypal.currency:USD}") String currency,
            @Value("${app.payment.return-base-url:http://localhost:3001}") String returnBaseUrl,
            RestClient.Builder restClientBuilder) {
        this.clientId = clientId;
        this.secret = secret;
        this.currency = currency;
        this.returnBaseUrl = returnBaseUrl;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String code() {
        return "PAYPAL";
    }

    @Override
    public PaymentResult requestPayment(PaymentRequest request) {
        String token = accessToken();
        String callbackQuery = "?orderNo=" + request.orderNo() + "&amount=" + request.amount()
                + "&provider=paypal";

        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("currency_code", currency);
        amount.put("value", formatAmount(request.amount()));
        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("reference_id", request.orderNo());
        unit.put("amount", amount);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("return_url", returnBaseUrl + "/checkout/success" + callbackQuery);
        context.put("cancel_url", returnBaseUrl + "/checkout/fail?provider=paypal&code=CANCEL");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intent", "CAPTURE");
        body.put("purchase_units", java.util.List.of(unit));
        body.put("payment_source", Map.of("paypal", Map.of("experience_context", context)));

        JsonNode node = postJson(ORDERS_URL, body, token);
        String orderId = text(node, "id");
        String approveUrl = approveLink(node);
        if (orderId == null || approveUrl == null) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "PayPal 주문 생성 응답이 올바르지 않습니다");
        }
        return PaymentResult.redirect(code(), orderId, approveUrl);
    }

    @Override
    public PaymentResult approve(
            PaymentRequest request, String requestTxnId, String clientToken, String maskedCard) {
        // The PayPal order id from create == the redirect token; capture uses it.
        String orderId = requestTxnId != null ? requestTxnId : clientToken;
        if (orderId == null || orderId.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "PayPal order id가 없습니다");
        }
        String token = accessToken();
        JsonNode node = postJson(ORDERS_URL + "/" + orderId + "/capture", Map.of(), token);
        String status = text(node, "status");
        if (!"COMPLETED".equals(status)) {
            throw new ApiException(ResultCode.INVALID_PARAMETER,
                    "PayPal 캡처 실패: " + (status != null ? status : "UNKNOWN"));
        }
        return PaymentResult.approved(code(), orderId, request.amount(), "PayPal 승인(샌드박스)");
    }

    @Override
    public PaymentResult cancel(PaymentRequest request, String txnId, String reason) {
        // PayPal refunds a captured payment via POST /v2/payments/captures/{captureId}/refund, which
        // needs the capture id (not the order id) — captured at approve time. Left as a key-gated
        // stub (no committed key to verify the live call); surface a clear operator-facing reason
        // rather than an opaque 500 so the refund failure is diagnosable in operations.
        throw new ApiException(ResultCode.INVALID_PARAMETER,
                "PayPal 환불 미연동: 결제취소(refund) API가 아직 연동되지 않았습니다");
    }

    // --- helpers -----------------------------------------------------------

    /** Fetches an OAuth2 client-credentials access token (Basic clientId:secret). */
    private String accessToken() {
        String basic = Base64.getEncoder()
                .encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        ResponseEntity<JsonNode> response = restClient.post()
                .uri(TOKEN_URL)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .exchange((req, res) -> ResponseEntity
                        .status(res.getStatusCode())
                        .body(res.bodyTo(JsonNode.class)));
        JsonNode node = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || node == null || !node.hasNonNull("access_token")) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "PayPal 인증 토큰 발급 실패");
        }
        return node.get("access_token").asText();
    }

    /** POSTs a JSON body with a Bearer token and returns the parsed body, throwing on error. */
    private JsonNode postJson(String url, Map<String, Object> body, String token) {
        ResponseEntity<JsonNode> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity
                        .status(res.getStatusCode())
                        .body(res.bodyTo(JsonNode.class)));
        JsonNode node = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful()) {
            String msg = node != null && node.hasNonNull("message")
                    ? node.get("message").asText() : "PayPal 요청 실패";
            throw new ApiException(ResultCode.INVALID_PARAMETER, "PayPal 실패: " + msg);
        }
        if (node == null) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "PayPal 응답이 비어 있습니다");
        }
        return node;
    }

    /** Extracts the approve/payer-action redirect link from the create-order response. */
    private String approveLink(JsonNode node) {
        JsonNode links = node.get("links");
        if (links == null || !links.isArray()) {
            return null;
        }
        for (JsonNode link : links) {
            String rel = link.hasNonNull("rel") ? link.get("rel").asText() : "";
            if (("approve".equals(rel) || "payer-action".equals(rel)) && link.hasNonNull("href")) {
                return link.get("href").asText();
            }
        }
        return null;
    }

    /** Formats the amount for PayPal: whole number for zero-decimal currencies (KRW/JPY), else 2dp. */
    private String formatAmount(long amount) {
        if ("KRW".equalsIgnoreCase(currency) || "JPY".equalsIgnoreCase(currency)) {
            return String.valueOf(amount);
        }
        return String.format("%.2f", amount / 100.0);
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}

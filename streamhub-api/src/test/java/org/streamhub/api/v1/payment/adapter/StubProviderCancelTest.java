package org.streamhub.api.v1.payment.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;

/**
 * Verifies that the not-live-verified refund stubs (Kakao/PayPal) fail with a clear
 * {@link ApiException} ({@link ResultCode#INVALID_PARAMETER}) carrying an operator-facing Korean
 * message — rather than an {@link UnsupportedOperationException} that surfaces as an opaque 500.
 */
class StubProviderCancelTest {

    private static final PaymentRequest REQUEST = new PaymentRequest("20260619-000001", 10_000L, "x");

    private record StubCase(String name, PaymentProvider provider, String messageFragment) {}

    @Test
    void stubCancelThrowsApiExceptionWithOperatorMessage() {
        RestClient.Builder builder = RestClient.builder();
        List<StubCase> cases = List.of(
                new StubCase(
                        "kakao",
                        new KakaoPaymentProvider("key", "TC0ONETIME", "http://localhost:3001", builder),
                        "카카오페이 환불 미연동"),
                new StubCase(
                        "paypal",
                        new PayPalPaymentProvider("cid", "secret", "USD", "http://localhost:3001", builder),
                        "PayPal 환불 미연동"));

        for (StubCase tc : cases) {
            assertThatThrownBy(() -> tc.provider().cancel(REQUEST, "txn-1", "사용자 환불"))
                    .as(tc.name())
                    .isInstanceOfSatisfying(ApiException.class, ex -> {
                        assertThat(ex.getResultCode()).isEqualTo(ResultCode.INVALID_PARAMETER);
                        assertThat(ex.getMessage()).contains(tc.messageFragment());
                    });
        }
    }
}

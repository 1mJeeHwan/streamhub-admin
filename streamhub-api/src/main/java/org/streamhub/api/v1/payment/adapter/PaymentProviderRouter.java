package org.streamhub.api.v1.payment.adapter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;

/**
 * Selects the active {@link PaymentProvider} from all registered beans by PG code (C4 seam).
 *
 * <p>Resolution order: an explicit per-request provider code (if a matching bean is registered),
 * then the configured {@code app.payment.provider} default, then {@link MockPaymentProvider}.
 * In the demo only the {@code MOCK} bean is present (the real adapters are
 * {@code @ConditionalOnProperty}-gated), so every request resolves to mock.
 *
 * <p>When {@code app.payment.test-mode=false} the {@code MockPaymentProvider} bean is absent. If a
 * request still asks for {@code MOCK} (or no real provider can be resolved), {@link #resolve} fails
 * with a clear {@link ApiException} instead of returning {@code null} and triggering an NPE — so a
 * real deployment never silently degrades into a free mock approval.
 */
@Component
public class PaymentProviderRouter {

    private final Map<String, PaymentProvider> byCode;
    private final String defaultProvider;

    public PaymentProviderRouter(List<PaymentProvider> providers,
                                 @Value("${app.payment.provider:mock}") String defaultProvider) {
        this.byCode = providers.stream()
                .collect(Collectors.toMap(p -> p.code().toUpperCase(), Function.identity()));
        this.defaultProvider = defaultProvider == null ? "MOCK" : defaultProvider.toUpperCase();
    }

    /**
     * Resolves the provider for the requested PG code, falling back to the configured default then
     * MOCK. Fails clearly when no provider can be resolved — e.g. {@code MOCK} requested but the mock
     * bean is gated off ({@code test-mode=false}) — instead of returning {@code null}.
     *
     * @throws ApiException {@code INTERNAL_ERROR} if no matching provider bean is registered
     */
    public PaymentProvider resolve(String requestedCode) {
        if (requestedCode != null) {
            PaymentProvider exact = byCode.get(requestedCode.toUpperCase());
            if (exact != null) {
                return exact;
            }
        }
        PaymentProvider configured = byCode.get(defaultProvider);
        if (configured != null) {
            return configured;
        }
        PaymentProvider mock = byCode.get("MOCK");
        if (mock != null) {
            return mock;
        }
        throw new ApiException(ResultCode.INTERNAL_ERROR,
                "결제 수단(" + requestedCode + ")을 사용할 수 없습니다");
    }
}

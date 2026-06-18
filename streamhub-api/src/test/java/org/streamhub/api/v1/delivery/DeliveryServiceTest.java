package org.streamhub.api.v1.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.streamhub.api.v1.delivery.adapter.DeliveryTrackingProvider;
import org.streamhub.api.v1.delivery.adapter.Tracking;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.order.entity.OrderStatus;
import org.streamhub.api.v1.order.repository.OrderRepository;

/**
 * Unit tests for {@link DeliveryService}'s graceful no-invoice degradation: an order without a
 * tracking number must surface a clear <i>pending</i> state (배송조회 데모가 500이 아니라 "아직 송장
 * 미등록"으로 떨어지도록) — never an error — and no courier API call must be made.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private DeliveryTrackingProvider provider;
    @Mock
    private OrderRepository orderRepository;

    private DeliveryService service() {
        return new DeliveryService(provider, orderRepository);
    }

    private Order order(String trackingNo) {
        return Order.builder()
                .orderNo("20260618-000001").memberId(1L).status(OrderStatus.PAID)
                .orderedName("홍길동").receiverName("홍길동").goodsTotal(10_000L).total(10_000L)
                .payMethod("CARD").trackingNo(trackingNo).build();
    }

    @Test
    void trackOrder_withoutInvoice_returnsPendingStateWithoutCallingProvider() {
        Tracking tracking = service().trackOrder(order(null));

        assertThat(tracking.level()).isZero();
        assertThat(tracking.completed()).isFalse();
        assertThat(tracking.invoiceNo()).isNull();
        assertThat(tracking.events()).hasSize(1);
        assertThat(tracking.events().get(0).description()).isEqualTo("아직 송장 미등록");
        verify(provider, never()).track(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(provider, never()).carriers();
    }

    @Test
    void trackOrder_withBlankInvoice_alsoDegradesGracefully() {
        Tracking tracking = service().trackOrder(order("   "));

        assertThat(tracking.level()).isZero();
        assertThat(tracking.events().get(0).description()).isEqualTo("아직 송장 미등록");
        verify(provider, never()).track(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}

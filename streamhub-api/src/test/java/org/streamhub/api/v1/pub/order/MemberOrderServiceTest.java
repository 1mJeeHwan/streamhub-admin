package org.streamhub.api.v1.pub.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.album.repository.AlbumRepository;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.order.entity.OrderStatus;
import org.streamhub.api.v1.order.repository.OrderItemRepository;
import org.streamhub.api.v1.order.repository.OrderReceiptRepository;
import org.streamhub.api.v1.order.repository.OrderRepository;
import org.streamhub.api.v1.payment.PaymentService;
import org.streamhub.api.v1.pub.order.dto.MemberPaymentConfirmRequest;

/**
 * Unit tests for the real-PG confirm guards: the redirect-supplied amount must match the prepared
 * order total (tamper guard) and the order must belong to the confirming member. Both reject before
 * any PG confirm call, so a forged redirect can neither change the charged amount nor confirm
 * someone else's order.
 */
@ExtendWith(MockitoExtension.class)
class MemberOrderServiceTest {

    @Mock private AlbumRepository albumRepository;
    @Mock private GoodsItemRepository goodsItemRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderReceiptRepository orderReceiptRepository;
    @Mock private PaymentService paymentService;
    @Mock private org.streamhub.api.v1.delivery.DeliveryService deliveryService;
    @Mock private org.streamhub.api.v1.coupon.CouponService couponService;

    private MemberOrderService service() {
        return new MemberOrderService(
                albumRepository, goodsItemRepository, memberRepository, orderRepository,
                orderItemRepository, orderReceiptRepository, paymentService, deliveryService,
                couponService, "demo-client-key");
    }

    private Order order(long memberId, long total) {
        Order order = Order.builder()
                .orderNo("20260618-000001").memberId(memberId).status(OrderStatus.PLACED)
                .orderedName("홍길동").receiverName("홍길동").goodsTotal(total).total(total)
                .payMethod("CARD").build();
        ReflectionTestUtils.setField(order, "id", 7L);
        return order;
    }

    @Test
    void confirm_amountMismatch_isRejectedBeforePgCall() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(mock(Member.class)));
        when(orderRepository.findByOrderNo("20260618-000001")).thenReturn(Optional.of(order(1L, 10_000L)));

        assertThatThrownBy(() -> service().confirm(1L,
                new MemberPaymentConfirmRequest("20260618-000001", "pk_real", 9_999L)))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode").isEqualTo(ResultCode.INVALID_PARAMETER);

        verify(paymentService, never()).approve(any());
    }

    @Test
    void confirm_otherMembersOrder_isRejected() {
        when(memberRepository.findById(2L)).thenReturn(Optional.of(mock(Member.class)));
        when(orderRepository.findByOrderNo("20260618-000001")).thenReturn(Optional.of(order(1L, 10_000L)));

        assertThatThrownBy(() -> service().confirm(2L,
                new MemberPaymentConfirmRequest("20260618-000001", "pk_real", 10_000L)))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode").isEqualTo(ResultCode.UNAUTHORIZED);

        verify(paymentService, never()).approve(any());
    }
}

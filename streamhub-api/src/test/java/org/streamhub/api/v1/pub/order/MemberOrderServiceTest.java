package org.streamhub.api.v1.pub.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.album.entity.Album;
import org.streamhub.api.v1.album.entity.AlbumStatus;
import org.streamhub.api.v1.album.repository.AlbumRepository;
import org.streamhub.api.v1.goods.entity.GoodsItem;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.order.entity.OrderStatus;
import org.streamhub.api.v1.order.entity.PayStatus;
import org.streamhub.api.v1.order.repository.OrderItemRepository;
import org.streamhub.api.v1.order.repository.OrderReceiptRepository;
import org.streamhub.api.v1.order.repository.OrderRepository;
import org.streamhub.api.v1.payment.PaymentService;
import org.streamhub.api.v1.payment.dto.PaymentResultDto;
import org.streamhub.api.v1.pub.order.dto.MemberOrderCreateRequest;
import org.streamhub.api.v1.pub.order.dto.MemberOrderResult;
import org.streamhub.api.v1.pub.order.dto.MemberPaymentConfirmRequest;

/**
 * Unit tests for the member purchase guards:
 * <ul>
 *   <li>The one-shot mock {@link MemberOrderService#purchase} is gated on {@code app.payment.test-mode}:
 *       rejected with {@code FORBIDDEN} when test-mode is off (no free bypass in a real deployment),
 *       works when test-mode is on (demo).</li>
 *   <li>The real-PG confirm tamper guards: the redirect-supplied amount must match the prepared order
 *       total and the order must belong to the confirming member — both reject before any PG confirm
 *       call.</li>
 * </ul>
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
        return service(true);
    }

    private MemberOrderService service(boolean paymentTestMode) {
        return new MemberOrderService(
                albumRepository, goodsItemRepository, memberRepository, orderRepository,
                orderItemRepository, orderReceiptRepository, paymentService, deliveryService,
                couponService, "demo-client-key", paymentTestMode);
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
    void purchase_whenTestModeOff_isForbidden_andTouchesNothing() {
        assertThatThrownBy(() -> service(false).purchase(1L,
                new MemberOrderCreateRequest(99L, "MOCK", null)))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode").isEqualTo(ResultCode.FORBIDDEN);

        // Rejected before any domain work — no order created, no payment driven.
        verifyNoInteractions(memberRepository, albumRepository, orderRepository, paymentService);
    }

    @Test
    void purchase_whenTestModeOn_createsPaidOrderViaMock() {
        Album album = Album.builder().goodsItemId(50L).title("데모 앨범").status(AlbumStatus.ON_SALE).build();
        ReflectionTestUtils.setField(album, "id", 99L);
        GoodsItem goods = GoodsItem.builder().name("데모 앨범").price(10_000L).build();
        ReflectionTestUtils.setField(goods, "id", 50L);
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        Order paid = order(1L, 10_000L);
        ReflectionTestUtils.setField(paid, "status", OrderStatus.PAID);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(albumRepository.findById(99L)).thenReturn(Optional.of(album));
        when(goodsItemRepository.findById(50L)).thenReturn(Optional.of(goods));
        when(orderRepository.existsByOrderNo(any())).thenReturn(false);
        when(orderRepository.save(any())).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
        when(orderRepository.findById(7L)).thenReturn(Optional.of(paid));
        when(paymentService.request(any())).thenReturn(
                new PaymentResultDto(7L, "MOCK", PayStatus.REQUESTED, "MOCK-x-1", null, "m", null, true));

        MemberOrderResult result = service(true).purchase(1L,
                new MemberOrderCreateRequest(99L, "MOCK", null));

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        verify(paymentService).approve(any());
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

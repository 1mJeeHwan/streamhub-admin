package org.streamhub.api.v1.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.security.AuthoritiesConstants;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.delivery.DeliveryService;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;
import org.streamhub.api.v1.goods.repository.GoodsOptionRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.order.dto.OrderStatusChangeRequest;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.order.entity.OrderStatus;
import org.streamhub.api.v1.order.entity.PayStatus;
import org.streamhub.api.v1.order.mapper.OrderMapper;
import org.streamhub.api.v1.order.repository.OrderItemRepository;
import org.streamhub.api.v1.order.repository.OrderReceiptRepository;
import org.streamhub.api.v1.order.repository.OrderRepository;
import org.streamhub.api.v1.sms.SmsService;

/**
 * Unit tests for {@link OrderService} church scoping (cross-church IDOR) and the PLACED→PAID
 * payment guard (finding #10): a CHURCH_MANAGER may only read/mutate orders owned by members in
 * their own church; SYSTEM bypasses; and no order may be marked PAID without an APPROVED payment.
 */
@ExtendWith(MockitoExtension.class)
class OrderScopingTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderReceiptRepository orderReceiptRepository;
    @Mock
    private GoodsItemRepository goodsItemRepository;
    @Mock
    private GoodsOptionRepository goodsOptionRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ActionLogPublisher actionLogPublisher;
    @Mock
    private SmsService smsService;
    @Mock
    private DeliveryService deliveryService;
    @Mock
    private org.streamhub.api.v1.coupon.CouponService couponService;

    /** SYSTEM operator — no church filter, bypasses the in-scope checks. */
    private static final AdminPrincipal SYSTEM = new AdminPrincipal(1L, AuthoritiesConstants.SYSTEM, null);
    /** CHURCH_MANAGER pinned to church 100. */
    private static final AdminPrincipal MANAGER_100 =
            new AdminPrincipal(2L, AuthoritiesConstants.CHURCH_MANAGER, 100L);

    private OrderService orderService() {
        return new OrderService(
                orderMapper, orderRepository, orderItemRepository, orderReceiptRepository,
                goodsItemRepository, goodsOptionRepository, memberRepository,
                actionLogPublisher, smsService, deliveryService, couponService);
    }

    private Order order(OrderStatus status, PayStatus payStatus) {
        Order order = Order.builder()
                .orderNo("20260618-000001").memberId(1L).status(status)
                .orderedName("홍길동").receiverName("홍길동").goodsTotal(10_000L).total(10_000L)
                .payMethod("CARD").payStatus(payStatus).build();
        ReflectionTestUtils.setField(order, "id", 7L);
        return order;
    }

    private Member memberInChurch(Long churchId) {
        Member member = Member.builder().churchId(churchId).email("x@x.com").name("x").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    @Test
    void getDetail_onOrderInAnotherChurch_isForbidden() {
        // Manager 100 reading an order whose member is in church 200 → FORBIDDEN (cross-tenant IDOR).
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order(OrderStatus.PLACED, PayStatus.NONE)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(memberInChurch(200L)));

        assertThatThrownBy(() -> orderService().getDetail(7L, MANAGER_100))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.FORBIDDEN);

        verify(orderMapper, never()).selectDetail(any());
    }

    @Test
    void changeStatus_onOrderInAnotherChurch_isForbidden() {
        // Manager 100 mutating another church's order is refused before the transition is applied.
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order(OrderStatus.PAID, PayStatus.APPROVED)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(memberInChurch(200L)));

        assertThatThrownBy(() -> orderService().changeStatus(
                7L, new OrderStatusChangeRequest(OrderStatus.READY, null), MANAGER_100))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.FORBIDDEN);

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeStatus_directToPaidWithoutApprovedPayment_isRejected() {
        // Finding #10: marking an order PAID is refused while payStatus is not APPROVED, so an
        // operator cannot fake a payment by flipping the status directly.
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order(OrderStatus.PLACED, PayStatus.NONE)));

        assertThatThrownBy(() -> orderService().changeStatus(
                7L, new OrderStatusChangeRequest(OrderStatus.PAID, null), SYSTEM))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_PARAMETER);

        // No stock deducted, no receipt written — the transition was blocked.
        verify(orderReceiptRepository, never()).save(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeStatus_toPaidWithApprovedPayment_isAllowed() {
        // The legitimate payment flow sets payStatus=APPROVED before this transition, so it passes.
        Order order = order(OrderStatus.PLACED, PayStatus.APPROVED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(7L)).thenReturn(List.of());
        when(orderMapper.selectDetail(7L)).thenReturn(new org.streamhub.api.v1.order.dto.OrderDetail());

        orderService().changeStatus(7L, new OrderStatusChangeRequest(OrderStatus.PAID, null), SYSTEM);

        verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void changeStatus_toCancel_releasesRedeemedCoupon() {
        // H1: cancelling an order that used a coupon must return the redemption to the pool.
        Order order = order(OrderStatus.PAID, PayStatus.APPROVED);
        order.applyCoupon(55L);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(7L)).thenReturn(List.of());
        when(orderMapper.selectDetail(7L)).thenReturn(new org.streamhub.api.v1.order.dto.OrderDetail());

        orderService().changeStatus(7L, new OrderStatusChangeRequest(OrderStatus.CANCEL, null), SYSTEM);

        verify(couponService).releaseRedemption(55L, 1L);
    }

    @Test
    void changeStatus_toCancelWithoutCoupon_doesNotTouchCoupons() {
        // No coupon on the order → nothing to release.
        Order order = order(OrderStatus.PAID, PayStatus.APPROVED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(7L)).thenReturn(List.of());
        when(orderMapper.selectDetail(7L)).thenReturn(new org.streamhub.api.v1.order.dto.OrderDetail());

        orderService().changeStatus(7L, new OrderStatusChangeRequest(OrderStatus.CANCEL, null), SYSTEM);

        verify(couponService, never()).releaseRedemption(any(), any());
    }

    @Test
    void list_forManager_isPinnedToOwnChurch_ignoringRequestedChurchId() {
        // Manager requests church 999 but is forced to church 100 in the mapper call.
        when(orderMapper.selectList(any(), any(), any(), any(), eq(100L), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(orderMapper.countList(any(), any(), any(), any(), eq(100L), any(), any())).thenReturn(0L);

        orderService().list(new org.streamhub.api.v1.order.dto.OrderSearchRequest(
                0, 10, null, null, null, null, 999L, null, null), MANAGER_100);

        verify(orderMapper).selectList(any(), any(), any(), any(), eq(100L), any(), any(), anyInt(), anyInt());
    }

    @Test
    void list_forSystem_honorsRequestedChurchId() {
        // SYSTEM honors the requested church filter (999) unchanged.
        when(orderMapper.selectList(any(), any(), any(), any(), eq(999L), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(orderMapper.countList(any(), any(), any(), any(), eq(999L), any(), any())).thenReturn(0L);

        orderService().list(new org.streamhub.api.v1.order.dto.OrderSearchRequest(
                0, 10, null, null, null, null, 999L, null, null), SYSTEM);

        verify(orderMapper).selectList(any(), any(), any(), any(), eq(999L), any(), any(), anyInt(), anyInt());
    }
}

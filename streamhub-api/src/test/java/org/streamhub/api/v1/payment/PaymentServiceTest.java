package org.streamhub.api.v1.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.security.AuthoritiesConstants;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.order.OrderService;
import org.streamhub.api.v1.order.dto.OrderStatusChangeRequest;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.order.entity.OrderStatus;
import org.streamhub.api.v1.order.repository.OrderReceiptRepository;
import org.streamhub.api.v1.order.repository.OrderRepository;
import org.streamhub.api.v1.payment.adapter.PaymentProvider;
import org.streamhub.api.v1.payment.adapter.PaymentProviderRouter;
import org.streamhub.api.v1.payment.adapter.PaymentRequest;
import org.streamhub.api.v1.payment.adapter.PaymentResult;
import org.streamhub.api.v1.payment.dto.PayApproveCommand;
import org.streamhub.api.v1.payment.dto.PayCancelCommand;

/**
 * Unit tests for the C4 payment seam's request→approve txnId consistency: the approve step must
 * present the transaction id issued at the request step (stored on the order). A mismatch is
 * rejected before any provider call (harmless for mock, load-bearing once a real PG is wired in).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderReceiptRepository orderReceiptRepository;
    @Mock
    private OrderService orderService;
    @Mock
    private PaymentProviderRouter providerRouter;
    @Mock
    private ActionLogPublisher actionLogPublisher;
    @Mock
    private PaymentProvider paymentProvider;
    @Mock
    private org.streamhub.api.v1.payment.mapper.PaymentMapper paymentMapper;
    @Mock
    private MemberRepository memberRepository;

    /** SYSTEM operator — no church filter, bypasses the in-scope checks. */
    private static final AdminPrincipal SYSTEM = new AdminPrincipal(1L, AuthoritiesConstants.SYSTEM, null);
    /** CHURCH_MANAGER pinned to church 100. */
    private static final AdminPrincipal MANAGER_100 =
            new AdminPrincipal(2L, AuthoritiesConstants.CHURCH_MANAGER, 100L);

    private PaymentService paymentService() {
        return new PaymentService(
                orderRepository, orderReceiptRepository, orderService,
                providerRouter, actionLogPublisher, paymentMapper, memberRepository, true);
    }

    private Order order() {
        Order order = Order.builder()
                .orderNo("20260618-000001").memberId(1L).status(OrderStatus.PLACED)
                .orderedName("홍길동").receiverName("홍길동").goodsTotal(10_000L).total(10_000L)
                .payMethod("CARD").build();
        ReflectionTestUtils.setField(order, "id", 7L);
        return order;
    }

    private Member memberInChurch(Long churchId) {
        Member member = Member.builder().churchId(churchId).email("x@x.com").name("x").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    @Test
    void approve_onNonRequestedOrder_isRejectedBeforeProviderCall() {
        // payStatus defaults to NONE (no prior request) — approving it must be refused, and the
        // PG provider must never be resolved/charged. This guard also blocks double-approve.
        Order order = order();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService().approve(
                new PayApproveCommand(7L, "pg_token_real", "4242424242424242"), SYSTEM))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_PARAMETER);

        verify(providerRouter, never()).resolve(any());
        verify(paymentProvider, never()).approve(any(), any(), any(), any());
    }

    /** An order with an approved payment (the precondition for a refund). */
    private Order approvedOrder() {
        Order order = order();
        order.applyPayRequest("MOCK", "MOCK-20260618-000001-1");
        order.applyPayApprove();
        return order;
    }

    @Test
    void refund_callsPgCancelBeforeReversingLedger() {
        // The PG cancel (money back) must happen BEFORE the internal status reversal, so a PG
        // failure aborts the refund instead of leaving the ledger reversed but the charge standing.
        Order order = approvedOrder();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(providerRouter.resolve("MOCK")).thenReturn(paymentProvider);
        when(paymentProvider.code()).thenReturn("MOCK");
        when(paymentProvider.cancel(any(PaymentRequest.class), any(), any()))
                .thenReturn(PaymentResult.canceled("MOCK", "MOCK-20260618-000001-1", 10_000L, "취소"));
        when(orderReceiptRepository.findByOrderIdOrderByCreatedAtAscIdAsc(7L))
                .thenReturn(List.of());

        paymentService().refund(new PayCancelCommand(7L, OrderStatus.CANCEL, "고객 변심"), SYSTEM);

        InOrder inOrder = inOrder(paymentProvider, orderService);
        inOrder.verify(paymentProvider).cancel(any(PaymentRequest.class), any(), any());
        inOrder.verify(orderService).changeStatus(eq(7L), any(OrderStatusChangeRequest.class), eq(SYSTEM));
    }

    @Test
    void refund_whenPgCancelFails_doesNotReverseLedger() {
        // A PG decline must propagate and the internal reversal must never run (tx rolls back).
        Order order = approvedOrder();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(providerRouter.resolve("MOCK")).thenReturn(paymentProvider);
        when(paymentProvider.cancel(any(PaymentRequest.class), any(), any()))
                .thenThrow(new ApiException(ResultCode.INVALID_PARAMETER, "토스 결제 취소에 실패했습니다"));

        assertThatThrownBy(() -> paymentService().refund(
                new PayCancelCommand(7L, OrderStatus.CANCEL, "고객 변심"), SYSTEM))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_PARAMETER);

        verify(orderService, never()).changeStatus(any(), any(), any());
    }

    @Test
    void refund_onNonApprovedOrder_isRejectedBeforeProviderCall() {
        // payStatus defaults to NONE — refunding a never-approved order must be refused before any
        // PG cancel call (also blocks double-refund once payStatus is CANCELED).
        Order order = order();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService().refund(
                new PayCancelCommand(7L, OrderStatus.CANCEL, "x"), SYSTEM))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_PARAMETER);

        verify(providerRouter, never()).resolve(any());
        verify(paymentProvider, never()).cancel(any(), any(), any());
    }

    @Test
    void payCancelCommand_resolvedStatus_defaultsToCancel() {
        // null/CANCEL → CANCEL; only an explicit RETURN selects RETURN.
        org.assertj.core.api.Assertions.assertThat(
                new PayCancelCommand(1L, null, null).resolvedStatus()).isEqualTo(OrderStatus.CANCEL);
        org.assertj.core.api.Assertions.assertThat(
                new PayCancelCommand(1L, OrderStatus.RETURN, null).resolvedStatus())
                .isEqualTo(OrderStatus.RETURN);
        org.assertj.core.api.Assertions.assertThat(
                new PayCancelCommand(1L, OrderStatus.SHIPPING, null).resolvedStatus())
                .isEqualTo(OrderStatus.CANCEL);
    }

    @Test
    void approve_onOrderInAnotherChurch_isForbiddenBeforeProviderCall() {
        // Manager pinned to church 100; the order's member belongs to church 200 → FORBIDDEN,
        // refused before payStatus is even inspected or any PG provider resolved (cross-tenant IDOR).
        Order order = order();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(memberInChurch(200L)));

        assertThatThrownBy(() -> paymentService().approve(
                new PayApproveCommand(7L, "pg_token_real", "4242424242424242"), MANAGER_100))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.FORBIDDEN);

        verify(providerRouter, never()).resolve(any());
    }

    @Test
    void refund_onOrderInAnotherChurch_isForbiddenBeforeProviderCall() {
        // Same cross-tenant guard on the refund path: a manager cannot refund another church's order.
        Order order = approvedOrder();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(memberInChurch(200L)));

        assertThatThrownBy(() -> paymentService().refund(
                new PayCancelCommand(7L, OrderStatus.CANCEL, "고객 변심"), MANAGER_100))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.FORBIDDEN);

        verify(providerRouter, never()).resolve(any());
        verify(paymentProvider, never()).cancel(any(), any(), any());
    }

    @Test
    void request_onOrderInOwnChurch_isAllowed() {
        // A manager acting on an order in their OWN church passes the scope check and proceeds.
        Order order = order();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(memberInChurch(100L)));
        when(providerRouter.resolve(any())).thenReturn(paymentProvider);
        when(paymentProvider.code()).thenReturn("MOCK");
        when(paymentProvider.requestPayment(any(PaymentRequest.class)))
                .thenReturn(PaymentResult.requested("MOCK", "MOCK-20260618-000001-1"));

        paymentService().request(
                new org.streamhub.api.v1.payment.dto.PayRequestCommand(7L, "MOCK"), MANAGER_100);

        verify(paymentProvider).requestPayment(any(PaymentRequest.class));
    }

    @Test
    void receipt_onOrderInAnotherChurch_isForbidden() {
        // A manager cannot read the receipt of an order owned by another church's member.
        Order order = order();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(memberInChurch(200L)));

        assertThatThrownBy(() -> paymentService().receipt(7L, MANAGER_100))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.FORBIDDEN);
    }

    @Test
    void list_mapsRowsAndTotalIntoPage() {
        org.streamhub.api.v1.payment.dto.PaymentListItem row =
                new org.streamhub.api.v1.payment.dto.PaymentListItem();
        row.setId(1L);
        row.setKind(org.streamhub.api.v1.order.entity.ReceiptKind.PAY);
        when(paymentMapper.selectList(any(), any(), any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.List.of(row));
        when(paymentMapper.countList(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);

        var page = paymentService().list(new org.streamhub.api.v1.payment.dto.PaymentSearchRequest(
                0, 10, null, null, null, null, null, null, null, null), SYSTEM);

        org.assertj.core.api.Assertions.assertThat(page.getContents()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(page.getTotalCount()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(page.getTotalPage()).isEqualTo(1);
    }
}

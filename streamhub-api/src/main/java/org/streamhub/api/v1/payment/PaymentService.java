package org.streamhub.api.v1.payment;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.security.AuthoritiesConstants;
import org.streamhub.api.base.util.SortResolver;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.order.OrderService;
import org.streamhub.api.v1.order.dto.OrderStatusChangeRequest;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.order.entity.OrderReceipt;
import org.streamhub.api.v1.order.entity.OrderStatus;
import org.streamhub.api.v1.order.entity.PayStatus;
import org.streamhub.api.v1.order.entity.ReceiptKind;
import org.streamhub.api.v1.order.repository.OrderReceiptRepository;
import org.streamhub.api.v1.order.repository.OrderRepository;
import org.streamhub.api.v1.payment.adapter.PaymentProvider;
import org.streamhub.api.v1.payment.adapter.PaymentProviderRouter;
import org.streamhub.api.v1.payment.adapter.PaymentRequest;
import org.streamhub.api.v1.payment.adapter.PaymentResult;
import org.streamhub.api.v1.payment.dto.PayApproveCommand;
import org.streamhub.api.v1.payment.dto.PayCancelCommand;
import org.streamhub.api.v1.payment.dto.PayRequestCommand;
import org.streamhub.api.v1.payment.dto.PaymentListItem;
import org.streamhub.api.v1.payment.dto.PaymentReceiptDto;
import org.streamhub.api.v1.payment.dto.PaymentResultDto;
import org.streamhub.api.v1.payment.dto.PaymentSearchRequest;
import org.streamhub.api.v1.payment.mapper.PaymentMapper;

/**
 * Payment orchestration (C4): request → (mock) approve → order state transition + PG receipt
 * backfill. <b>No real PG call is made</b> — the {@link PaymentProvider} (mock by default)
 * synthesises an approval. The charged amount is always the server-computed {@code order.total};
 * card numbers are masked and never stored (spec §3.5).
 */
@Service
public class PaymentService {

    /** Whitelisted sort keys (PaymentListItem field → SQL column) for server-side list sorting. */
    private static final Map<String, String> PAYMENT_SORT_COLUMNS = Map.of(
            "createdAt", "r.created_at",
            "kind", "r.kind",
            "orderNo", "o.order_no",
            "memberName", "m.name",
            "amount", "r.amount",
            "method", "r.method",
            "provider", "r.provider",
            "txnId", "r.txn_id",
            "payStatus", "o.pay_status");

    private final OrderRepository orderRepository;
    private final OrderReceiptRepository orderReceiptRepository;
    private final OrderService orderService;
    private final PaymentProviderRouter providerRouter;
    private final ActionLogPublisher actionLogPublisher;
    private final PaymentMapper paymentMapper;
    private final MemberRepository memberRepository;
    private final boolean testMode;

    /**
     * Unscoped SYSTEM principal for the member-facing storefront checkout, which runs in a member
     * (not admin) security context: the member is acting on the order they just created, so admin
     * church scoping does not apply. Used by the {@code request}/{@code approve} overloads.
     */
    private static final AdminPrincipal SYSTEM_PRINCIPAL =
            new AdminPrincipal(null, AuthoritiesConstants.SYSTEM, null);

    public PaymentService(
            OrderRepository orderRepository,
            OrderReceiptRepository orderReceiptRepository,
            OrderService orderService,
            PaymentProviderRouter providerRouter,
            ActionLogPublisher actionLogPublisher,
            PaymentMapper paymentMapper,
            MemberRepository memberRepository,
            @Value("${app.payment.test-mode:true}") boolean testMode) {
        this.orderRepository = orderRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.orderService = orderService;
        this.providerRouter = providerRouter;
        this.actionLogPublisher = actionLogPublisher;
        this.paymentMapper = paymentMapper;
        this.memberRepository = memberRepository;
        this.testMode = testMode;
    }

    /**
     * Paginated payment-history search (MyBatis): payment/refund receipts joined with their order
     * and the paying member. All filters optional; ordered newest-first. Receipts carry no church
     * column, so the filter is applied through the {@code ORDERS → MEMBER} join; CHURCH_MANAGER
     * operators are pinned to their own church.
     *
     * @param request   search/pagination filters
     * @param principal authenticated operator providing the church scope
     * @return the filtered, paginated payment-history list
     */
    @Transactional(readOnly = true)
    public ResInfinityList<PaymentListItem> list(PaymentSearchRequest request, AdminPrincipal principal) {
        String searchField = blankToNull(request.searchField());
        String keyword = blankToNull(request.keyword());
        String kind = request.kind() == null ? null : request.kind().name();
        String method = blankToNull(request.method());
        String provider = blankToNull(request.provider());
        Long churchId = scopedChurchId(request.churchId(), principal);
        int size = request.pageSizeOrDefault();
        String orderBy = SortResolver.resolve(request.sortBy(), request.sortDir(),
                PAYMENT_SORT_COLUMNS, "r.id", "r.created_at DESC, r.id DESC");

        List<PaymentListItem> rows = paymentMapper.selectList(
                searchField, keyword, kind, method, provider, churchId,
                request.fromDate(), request.toDate(), orderBy, request.offset(), size);
        long total = paymentMapper.countList(
                searchField, keyword, kind, method, provider, churchId,
                request.fromDate(), request.toDate());
        return ResInfinityList.of(rows, total, size);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Member-storefront entry point for {@link #request(PayRequestCommand, AdminPrincipal)}: the
     * member checkout has no admin principal and legitimately spans no church scope (it acts on the
     * order the member just created), so it delegates with an unscoped SYSTEM principal.
     */
    @Transactional
    public PaymentResultDto request(PayRequestCommand command) {
        return request(command, SYSTEM_PRINCIPAL);
    }

    /**
     * Initiates a (mock) payment for an order: resolves the provider, gets a transaction id, and
     * marks the order {@link PayStatus#REQUESTED}.
     *
     * @throws ApiException {@code NOT_FOUND} if the order is missing, {@code INVALID_PARAMETER}
     *                      if it is already approved, or {@code FORBIDDEN} if the order is outside
     *                      the operator's church
     */
    @Transactional
    public PaymentResultDto request(PayRequestCommand command, AdminPrincipal principal) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureMemberInScope(order.getMemberId(), principal);
        if (order.getPayStatus() == PayStatus.APPROVED) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "이미 결제된 주문입니다");
        }

        PaymentProvider adapter = providerRouter.resolve(command.provider());
        PaymentResult result = adapter.requestPayment(
                new PaymentRequest(order.getOrderNo(), order.getTotal(), adapter.code()));

        order.applyPayRequest(adapter.code(), result.txnId());
        orderRepository.saveAndFlush(order);

        actionLogPublisher.publish("PAYMENT_REQUEST", "ORDER",
                String.valueOf(order.getId()), adapter.code());
        return PaymentResultDto.of(order.getId(), result, testMode);
    }

    /**
     * Member-storefront entry point for {@link #approve(PayApproveCommand, AdminPrincipal)}:
     * delegates with an unscoped SYSTEM principal (the member checkout has no admin church scope).
     */
    @Transactional
    public PaymentResultDto approve(PayApproveCommand command) {
        return approve(command, SYSTEM_PRINCIPAL);
    }

    /**
     * Approves a previously requested (mock) payment: confirms via the provider, transitions the
     * order {@code PLACED → PAID} (reusing the order state machine: stock deduction + PAY receipt),
     * backfills the receipt with the PG provider/txnId, and triggers the paid-notification SMS via
     * the order service's shared hook.
     *
     * <p>The order must be in {@link PayStatus#REQUESTED} (so an already-approved order cannot be
     * re-approved). Binding the completion token to the right order is the adapter's responsibility:
     * e.g. {@code PortonePaymentProvider} verifies the PG record's {@code merchant_uid} equals this
     * order's {@code orderNo}, so a paid token cannot be replayed against a different order.
     *
     * @throws ApiException {@code NOT_FOUND} if missing, {@code INVALID_PARAMETER} if the order
     *                      is not in {@link PayStatus#REQUESTED} (or the adapter rejects the token),
     *                      or {@code FORBIDDEN} if the order is outside the operator's church
     */
    @Transactional
    public PaymentResultDto approve(PayApproveCommand command, AdminPrincipal principal) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureMemberInScope(order.getMemberId(), principal);
        if (order.getPayStatus() != PayStatus.REQUESTED) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "결제요청 상태가 아닙니다");
        }

        PaymentProvider adapter = providerRouter.resolve(order.getPayProvider());
        String maskedCard = maskCard(command.cardNo());
        // Pass both the server-stored request-stage id (order.payTxnId — Kakao tid / PayPal order
        // id, null for Toss) and the client-supplied completion token (command.txnId — Toss
        // paymentKey / Kakao pg_token / PayPal token). Each adapter uses what its PG needs and
        // validates server-side; the amount/orderId are always the server values on this request.
        PaymentResult result = adapter.approve(
                new PaymentRequest(order.getOrderNo(), order.getTotal(), adapter.code()),
                order.getPayTxnId(), command.txnId(), maskedCard);

        order.applyPayApprove();
        orderRepository.saveAndFlush(order);

        // Reuse the order state machine: PLACED → PAID (stock deduction + PAY receipt + SMS hook).
        // payStatus is APPROVED above, so the state machine's PLACED→PAID payment guard is satisfied.
        if (order.getStatus() == OrderStatus.PLACED) {
            orderService.changeStatus(order.getId(),
                    new OrderStatusChangeRequest(OrderStatus.PAID, "결제승인(MOCK)"), principal);
        }

        // Backfill the latest PAY receipt with the PG provider/txnId.
        latestPayReceipt(order.getId())
                .ifPresent(receipt -> receipt.setProviderTxn(adapter.code(), result.txnId()));

        actionLogPublisher.publish("PAYMENT_APPROVE", "ORDER",
                String.valueOf(order.getId()), adapter.code());
        return PaymentResultDto.of(order.getId(), result, testMode);
    }

    /**
     * Cancels/refunds an approved payment: calls the PG's cancel/refund API <b>first</b> (the call
     * that actually returns the money once a live PG is enabled), and only on PG success reverses
     * the internal side: the order is transitioned to {@code CANCEL}/{@code RETURN} via the order
     * state machine (stock restore + REFUND receipt), payment is marked {@link PayStatus#CANCELED},
     * and the REFUND receipt is backfilled with the PG provider/txnId.
     *
     * <p>Ordering is load-bearing: doing the PG cancel before the ledger reversal means a PG error
     * propagates and aborts the whole transaction (rolled back), so the books are never reversed
     * while the charge still stands. For the mock provider the PG cancel is a no-op, so the demo
     * refund path is unchanged.
     *
     * @throws ApiException {@code NOT_FOUND} if the order is missing, {@code INVALID_PARAMETER}
     *                      if the payment was not approved or the status transition is illegal,
     *                      or {@code FORBIDDEN} if the order is outside the operator's church; any
     *                      PG decline surfaces as the provider's {@code INVALID_PARAMETER} message
     */
    @Transactional
    public PaymentResultDto refund(PayCancelCommand command, AdminPrincipal principal) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureMemberInScope(order.getMemberId(), principal);
        if (order.getPayStatus() != PayStatus.APPROVED) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "승인된 결제가 아닙니다");
        }

        // 1) PG cancel/refund FIRST — money back. A PG failure throws here and rolls the tx back,
        //    so the internal ledger is never reversed against a still-standing charge.
        PaymentProvider adapter = providerRouter.resolve(order.getPayProvider());
        PaymentResult result = adapter.cancel(
                new PaymentRequest(order.getOrderNo(), order.getTotal(), adapter.code()),
                order.getPayTxnId(), command.reason());

        // 2) Reverse the internal side: stock restore + REFUND receipt via the order state machine.
        OrderStatus to = command.resolvedStatus();
        orderService.changeStatus(order.getId(), new OrderStatusChangeRequest(to, command.reason()), principal);

        // 3) Mark the payment canceled and backfill the REFUND receipt with the PG provider/txnId.
        order.applyPayCancel();
        orderRepository.saveAndFlush(order);
        latestRefundReceipt(order.getId())
                .ifPresent(receipt -> receipt.setProviderTxn(adapter.code(), result.txnId()));

        actionLogPublisher.publish("PAYMENT_REFUND", "ORDER",
                String.valueOf(order.getId()), adapter.code());
        return PaymentResultDto.of(order.getId(), result, testMode);
    }

    /**
     * Returns the latest PAY receipt for an order (the payment receipt). A CHURCH_MANAGER may only
     * read receipts for orders owned by members in their own church.
     */
    @Transactional(readOnly = true)
    public PaymentReceiptDto receipt(Long orderId, AdminPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureMemberInScope(order.getMemberId(), principal);
        return latestPayReceipt(orderId)
                .map(PaymentReceiptDto::from)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
    }

    // --- helpers -----------------------------------------------------------

    /** Resolves the church filter: CHURCH_MANAGER is pinned to its own church. */
    private Long scopedChurchId(Long requestedChurchId, AdminPrincipal principal) {
        return principal.isSystem() ? requestedChurchId : principal.churchId();
    }

    /** Verifies the order's owning member belongs to the operator's church (SYSTEM bypasses). */
    private void ensureMemberInScope(Long memberId, AdminPrincipal principal) {
        if (principal.isSystem()) {
            return;
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!member.getChurchId().equals(principal.churchId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    private java.util.Optional<OrderReceipt> latestPayReceipt(Long orderId) {
        return latestReceiptOfKind(orderId, ReceiptKind.PAY);
    }

    private java.util.Optional<OrderReceipt> latestRefundReceipt(Long orderId) {
        return latestReceiptOfKind(orderId, ReceiptKind.REFUND);
    }

    private java.util.Optional<OrderReceipt> latestReceiptOfKind(Long orderId, ReceiptKind kind) {
        List<OrderReceipt> receipts = orderReceiptRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId);
        OrderReceipt latest = null;
        for (OrderReceipt receipt : receipts) {
            if (receipt.getKind() == kind) {
                latest = receipt;
            }
        }
        return java.util.Optional.ofNullable(latest);
    }

    /** Masks all but the last 4 digits of a card number ({@code **** **** **** 1234}); never stored raw. */
    private String maskCard(String cardNo) {
        if (cardNo == null || cardNo.isBlank()) {
            return null;
        }
        String digits = cardNo.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return null;
        }
        String last4 = digits.substring(digits.length() - 4);
        return "**** **** **** " + last4;
    }
}

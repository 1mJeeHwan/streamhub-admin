package org.streamhub.api.v1.order;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.security.AuthoritiesConstants;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;
import org.streamhub.api.v1.goods.repository.GoodsOptionRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.order.dto.OrderDetail;
import org.streamhub.api.v1.order.dto.OrderItemDto;
import org.streamhub.api.v1.order.dto.OrderListItem;
import org.streamhub.api.v1.order.dto.OrderReceiptDto;
import org.streamhub.api.v1.order.dto.OrderSearchRequest;
import org.streamhub.api.v1.order.dto.OrderStatusChangeRequest;
import org.streamhub.api.v1.order.dto.OrderTrackingRequest;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.order.entity.OrderItem;
import org.streamhub.api.v1.order.entity.OrderReceipt;
import org.streamhub.api.v1.order.entity.OrderStatus;
import org.streamhub.api.v1.order.entity.PayStatus;
import org.streamhub.api.v1.order.entity.ReceiptKind;
import org.streamhub.api.v1.order.mapper.OrderMapper;
import org.streamhub.api.v1.order.repository.OrderItemRepository;
import org.streamhub.api.v1.order.repository.OrderReceiptRepository;
import org.streamhub.api.v1.order.repository.OrderRepository;
import org.streamhub.api.v1.sms.SmsService;
import org.streamhub.api.v1.sms.entity.SmsKind;

/**
 * Order management: paginated search (MyBatis), detail assembly (line items + receipts),
 * and the order state machine ({@link #changeStatus}) which atomically deducts/restores
 * stock, writes payment/refund receipts, and transitions the order — all in one
 * transaction. Shipment tracking is updated by {@link #changeTracking}.
 *
 * <p>On {@code → PAID}/{@code → SHIPPING} a best-effort auto-notification SMS is sent via
 * {@link SmsService} (mock — no real dispatch); a notification failure never breaks the order.
 */
@Slf4j
@Service
public class OrderService {

    /**
     * Authoritative order-status transition map (spec §3.4). The frontend keeps a UX
     * mirror; this map is the single source of truth and rejects illegal transitions.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = buildTransitions();

    /** Unscoped SYSTEM principal for the background delivery-sync batch (no HTTP operator). */
    private static final AdminPrincipal SYSTEM_PRINCIPAL =
            new AdminPrincipal(null, AuthoritiesConstants.SYSTEM, null);

    /** Statuses in which stock has already been deducted (deducted on {@code PAID}). */
    private static final Set<OrderStatus> STOCK_DEDUCTED =
            Set.of(OrderStatus.PAID, OrderStatus.READY, OrderStatus.SHIPPING, OrderStatus.DONE);

    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReceiptRepository orderReceiptRepository;
    private final GoodsItemRepository goodsItemRepository;
    private final GoodsOptionRepository goodsOptionRepository;
    private final MemberRepository memberRepository;
    private final ActionLogPublisher actionLogPublisher;
    private final SmsService smsService;
    private final org.streamhub.api.v1.delivery.DeliveryService deliveryService;

    public OrderService(
            OrderMapper orderMapper,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderReceiptRepository orderReceiptRepository,
            GoodsItemRepository goodsItemRepository,
            GoodsOptionRepository goodsOptionRepository,
            MemberRepository memberRepository,
            ActionLogPublisher actionLogPublisher,
            SmsService smsService,
            org.streamhub.api.v1.delivery.DeliveryService deliveryService) {
        this.orderMapper = orderMapper;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.goodsItemRepository = goodsItemRepository;
        this.goodsOptionRepository = goodsOptionRepository;
        this.memberRepository = memberRepository;
        this.actionLogPublisher = actionLogPublisher;
        this.smsService = smsService;
        this.deliveryService = deliveryService;
    }

    /**
     * Paginated order search. Orders carry no church column, so the filter is applied through the
     * {@code MEMBER} join; CHURCH_MANAGER operators are pinned to their own church.
     *
     * @param request   search/pagination filters
     * @param principal authenticated operator providing the church scope
     * @return the filtered, paginated order list
     */
    @Transactional(readOnly = true)
    public ResInfinityList<OrderListItem> list(OrderSearchRequest request, AdminPrincipal principal) {
        String searchField = blankToNull(request.searchField());
        String keyword = blankToNull(request.keyword());
        String status = request.status() == null ? null : request.status().name();
        String payMethod = blankToNull(request.payMethod());
        Long churchId = scopedChurchId(request.churchId(), principal);
        int size = request.pageSizeOrDefault();

        List<OrderListItem> orders = orderMapper.selectList(
                searchField, keyword, status, payMethod, churchId,
                request.fromDate(), request.toDate(), request.offset(), size);
        long total = orderMapper.countList(
                searchField, keyword, status, payMethod, churchId,
                request.fromDate(), request.toDate());
        return ResInfinityList.of(orders, total, size);
    }

    /**
     * Order detail. Verifies the owning member is in the operator's church first so a
     * CHURCH_MANAGER cannot read another church's order.
     *
     * @param id        order id
     * @param principal authenticated operator providing the church scope
     * @return the assembled order detail (line items + receipts)
     */
    @Transactional(readOnly = true)
    public OrderDetail getDetail(Long id, AdminPrincipal principal) {
        ensureOrderInScope(id, principal);
        OrderDetail detail = orderMapper.selectDetail(id);
        if (detail == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        detail.setItems(loadItems(id));
        detail.setReceipts(loadReceipts(id));
        return detail;
    }

    /**
     * Transitions an order through the state machine, applying the stock and receipt
     * side effects in a single transaction:
     * <ul>
     *   <li>{@code → PAID}: deducts stock (option-first), bumps sale count, writes a PAY receipt.</li>
     *   <li>{@code → CANCEL/RETURN}: restores stock if it was already deducted, writes a REFUND receipt.</li>
     * </ul>
     *
     * <p>A direct transition to {@code PAID} is additionally guarded: it is rejected unless the
     * order's {@link PayStatus} is already {@code APPROVED} (finding #10). This stops an operator
     * marking an order PAID without a real payment having been approved; the legitimate payment
     * flow sets {@code APPROVED} on the order before reaching this transition.
     *
     * @throws ApiException {@code NOT_FOUND} if the order is missing,
     *                      {@code INVALID_PARAMETER} for an illegal transition / insufficient stock /
     *                      a {@code → PAID} without an approved payment, or {@code FORBIDDEN} if the
     *                      order is outside the operator's church
     */
    @Transactional
    @CacheEvict(cacheNames = {"dashboardSummary", "dashboardTimeseries"}, allEntries = true)
    public OrderDetail changeStatus(Long orderId, OrderStatusChangeRequest request, AdminPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureMemberInScope(order.getMemberId(), principal);
        OrderStatus from = order.getStatus();
        OrderStatus to = request.status();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "허용되지 않는 상태 전이");
        }
        if (to == OrderStatus.PAID && order.getPayStatus() != PayStatus.APPROVED) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "결제 승인 없이 PAID로 전이할 수 없습니다");
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        if (to == OrderStatus.PAID) {
            items.forEach(this::decrementStock);
            orderReceiptRepository.save(OrderReceipt.builder()
                    .orderId(orderId)
                    .kind(ReceiptKind.PAY)
                    .amount(order.getTotal())
                    .method(order.getPayMethod())
                    .memo(memoOr(request.memo(), "입금확인"))
                    .build());
        } else if (to == OrderStatus.CANCEL || to == OrderStatus.RETURN) {
            if (STOCK_DEDUCTED.contains(from)) {
                items.forEach(this::restoreStock);
            }
            String defaultMemo = to == OrderStatus.CANCEL ? "주문취소" : "반품환불";
            orderReceiptRepository.save(OrderReceipt.builder()
                    .orderId(orderId)
                    .kind(ReceiptKind.REFUND)
                    .amount(order.getTotal())
                    .method(order.getPayMethod())
                    .memo(memoOr(request.memo(), defaultMemo))
                    .build());
        }

        order.changeStatus(to);
        orderRepository.saveAndFlush(order);
        actionLogPublisher.publish(
                "ORDER_" + to.name(), "ORDER", String.valueOf(orderId), order.getOrderNo());
        notifyStatus(order, to);
        return getDetail(orderId, principal);
    }

    /**
     * Best-effort auto-notification SMS for {@code PAID}/{@code SHIPPING} transitions (C6).
     * Swallows any failure so a notification never breaks the order transaction.
     */
    private void notifyStatus(Order order, OrderStatus to) {
        SmsKind kind = switch (to) {
            case PAID -> SmsKind.ORDER_PAID;
            case SHIPPING -> SmsKind.ORDER_SHIPPING;
            default -> null;
        };
        if (kind == null) {
            return;
        }
        try {
            smsService.sendForOrder(order, kind);
        } catch (RuntimeException e) {
            log.warn("Failed to send order SMS [{}] for {}: {}", kind, order.getOrderNo(), e.getMessage());
        }
    }

    /**
     * Sets shipment tracking info. If the order is in {@code READY} it is auto-promoted
     * to {@code SHIPPING} (spec §3.4). A CHURCH_MANAGER may only update orders owned by
     * members in their own church.
     */
    @Transactional
    public OrderDetail changeTracking(Long orderId, OrderTrackingRequest request, AdminPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureMemberInScope(order.getMemberId(), principal);
        order.setTracking(request.trackingNo(), request.shipCompany());
        boolean promotedToShipping = false;
        if (order.getStatus() == OrderStatus.READY) {
            order.changeStatus(OrderStatus.SHIPPING);
            promotedToShipping = true;
        }
        orderRepository.saveAndFlush(order);
        actionLogPublisher.publish(
                "ORDER_TRACKING", "ORDER", String.valueOf(orderId), request.trackingNo());
        if (promotedToShipping) {
            notifyStatus(order, OrderStatus.SHIPPING);
        }
        return getDetail(orderId, principal);
    }

    /**
     * Syncs an order's status from the live courier tracking (C8): fetches the shipment status and
     * advances the order through the state machine when the carrier reports progress —
     * {@code 배달완료 → DONE} (from SHIPPING), {@code 이동중 → SHIPPING} (from READY). No-op if the
     * carrier status doesn't warrant a transition. Returns the tracking it fetched.
     *
     * @throws ApiException {@code NOT_FOUND} if the order is missing, {@code INVALID_PARAMETER} if it
     *                      has no invoice / the carrier cannot be determined
     */
    @Transactional
    public org.streamhub.api.v1.delivery.adapter.Tracking syncDelivery(Long orderId, AdminPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureMemberInScope(order.getMemberId(), principal);
        org.streamhub.api.v1.delivery.adapter.Tracking tracking = deliveryService.trackOrder(order);
        deliveryDrivenTransition(order.getStatus(), tracking).ifPresent(next ->
                changeStatus(orderId, new OrderStatusChangeRequest(next, "배송상태 자동연동(" + next + ")"), principal));
        return tracking;
    }

    /**
     * System-context delivery sync for the scheduled courier-polling batch (C8), which runs without
     * an authenticated operator and so legitimately spans all churches. Equivalent to
     * {@link #syncDelivery(Long, AdminPrincipal)} with an unscoped SYSTEM principal.
     */
    @Transactional
    public org.streamhub.api.v1.delivery.adapter.Tracking syncDelivery(Long orderId) {
        return syncDelivery(orderId, SYSTEM_PRINCIPAL);
    }

    /**
     * Read-only courier tracking for an order, scoped to the operator's church (no status change).
     * Closes the cross-church tracking leak: a CHURCH_MANAGER may only read tracking for its own
     * church's orders.
     *
     * @throws ApiException {@code NOT_FOUND} if missing, {@code FORBIDDEN} if another church's order
     */
    @Transactional(readOnly = true)
    public org.streamhub.api.v1.delivery.adapter.Tracking trackingInfo(Long orderId, AdminPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureMemberInScope(order.getMemberId(), principal);
        return deliveryService.trackOrder(order);
    }

    /**
     * Pure mapping from a courier tracking result to the order transition it should trigger (if any).
     * Kept side-effect-free so the policy is unit-testable without the order/state-machine wiring.
     *
     * <ul>
     *   <li>{@code SHIPPING} + 배달완료 → {@code DONE}</li>
     *   <li>{@code READY} + 이동중(스캔 이벤트 존재, 미완료) → {@code SHIPPING}</li>
     *   <li>otherwise → empty (no transition)</li>
     * </ul>
     */
    static java.util.Optional<OrderStatus> deliveryDrivenTransition(
            OrderStatus current, org.streamhub.api.v1.delivery.adapter.Tracking tracking) {
        if (tracking == null) {
            return java.util.Optional.empty();
        }
        if (tracking.completed() && current == OrderStatus.SHIPPING) {
            return java.util.Optional.of(OrderStatus.DONE);
        }
        boolean inTransit = tracking.events() != null && !tracking.events().isEmpty();
        if (!tracking.completed() && inTransit && current == OrderStatus.READY) {
            return java.util.Optional.of(OrderStatus.SHIPPING);
        }
        return java.util.Optional.empty();
    }

    // --- helpers -----------------------------------------------------------

    /** Resolves the church filter: CHURCH_MANAGER is pinned to its own church. */
    private Long scopedChurchId(Long requestedChurchId, AdminPrincipal principal) {
        return principal.isSystem() ? requestedChurchId : principal.churchId();
    }

    /** Loads the order and verifies its owning member is in the operator's church. */
    private void ensureOrderInScope(Long orderId, AdminPrincipal principal) {
        if (principal.isSystem()) {
            return;
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureMemberInScope(order.getMemberId(), principal);
    }

    /** Verifies the member belongs to the operator's church (SYSTEM bypasses). */
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

    /**
     * Deducts stock for a line (option stock first, else item stock) via a single atomic,
     * stock-guarded UPDATE. The {@code stock >= qty} guard runs in the database, so two
     * concurrent checkouts cannot both pass — the loser affects 0 rows and is rejected as
     * out-of-stock, eliminating oversell without any read-modify-write window.
     *
     * @throws ApiException {@code INVALID_PARAMETER} if stock is insufficient (0 rows updated)
     */
    private void decrementStock(OrderItem item) {
        int affected = item.getOptionId() != null
                ? goodsOptionRepository.decrementStock(item.getOptionId(), item.getQty())
                : goodsItemRepository.decrementStock(item.getGoodsId(), item.getQty());
        if (affected == 0) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "재고가 부족합니다");
        }
    }

    /**
     * Restores stock for a line (option stock first, else item stock) via an atomic increment;
     * a missing goods/option row simply affects 0 rows and is skipped.
     */
    private void restoreStock(OrderItem item) {
        if (item.getOptionId() != null) {
            goodsOptionRepository.restoreStock(item.getOptionId(), item.getQty());
        } else {
            goodsItemRepository.restoreStock(item.getGoodsId(), item.getQty());
        }
    }

    private List<OrderItemDto> loadItems(Long orderId) {
        return orderItemRepository.findByOrderId(orderId).stream()
                .map(OrderItemDto::from)
                .toList();
    }

    private List<OrderReceiptDto> loadReceipts(Long orderId) {
        return orderReceiptRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId).stream()
                .map(OrderReceiptDto::from)
                .toList();
    }

    private String memoOr(String memo, String fallback) {
        return memo == null || memo.isBlank() ? fallback : memo;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<OrderStatus, Set<OrderStatus>> buildTransitions() {
        Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);
        map.put(OrderStatus.PLACED, Set.of(OrderStatus.PAID, OrderStatus.CANCEL));
        map.put(OrderStatus.PAID, Set.of(OrderStatus.READY, OrderStatus.CANCEL));
        map.put(OrderStatus.READY, Set.of(OrderStatus.SHIPPING, OrderStatus.CANCEL));
        map.put(OrderStatus.SHIPPING, Set.of(OrderStatus.DONE, OrderStatus.RETURN));
        map.put(OrderStatus.DONE, Set.of(OrderStatus.RETURN));
        map.put(OrderStatus.CANCEL, Set.of());
        map.put(OrderStatus.RETURN, Set.of());
        return map;
    }
}

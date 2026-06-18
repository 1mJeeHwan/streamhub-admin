package org.streamhub.api.v1.order;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.goods.entity.GoodsItem;
import org.streamhub.api.v1.goods.entity.GoodsOption;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;
import org.streamhub.api.v1.goods.repository.GoodsOptionRepository;
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

    /** Statuses in which stock has already been deducted (deducted on {@code PAID}). */
    private static final Set<OrderStatus> STOCK_DEDUCTED =
            Set.of(OrderStatus.PAID, OrderStatus.READY, OrderStatus.SHIPPING, OrderStatus.DONE);

    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReceiptRepository orderReceiptRepository;
    private final GoodsItemRepository goodsItemRepository;
    private final GoodsOptionRepository goodsOptionRepository;
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
            ActionLogPublisher actionLogPublisher,
            SmsService smsService,
            org.streamhub.api.v1.delivery.DeliveryService deliveryService) {
        this.orderMapper = orderMapper;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.goodsItemRepository = goodsItemRepository;
        this.goodsOptionRepository = goodsOptionRepository;
        this.actionLogPublisher = actionLogPublisher;
        this.smsService = smsService;
        this.deliveryService = deliveryService;
    }

    @Transactional(readOnly = true)
    public ResInfinityList<OrderListItem> list(OrderSearchRequest request) {
        String searchField = blankToNull(request.searchField());
        String keyword = blankToNull(request.keyword());
        String status = request.status() == null ? null : request.status().name();
        String payMethod = blankToNull(request.payMethod());
        int size = request.pageSizeOrDefault();

        List<OrderListItem> orders = orderMapper.selectList(
                searchField, keyword, status, payMethod,
                request.fromDate(), request.toDate(), request.offset(), size);
        long total = orderMapper.countList(
                searchField, keyword, status, payMethod,
                request.fromDate(), request.toDate());
        return ResInfinityList.of(orders, total, size);
    }

    @Transactional(readOnly = true)
    public OrderDetail getDetail(Long id) {
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
     * @throws ApiException {@code NOT_FOUND} if the order is missing, or
     *                      {@code INVALID_PARAMETER} for an illegal transition or insufficient stock
     */
    @Transactional
    public OrderDetail changeStatus(Long orderId, OrderStatusChangeRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        OrderStatus from = order.getStatus();
        OrderStatus to = request.status();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "허용되지 않는 상태 전이");
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
        return getDetail(orderId);
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
     * to {@code SHIPPING} (spec §3.4).
     */
    @Transactional
    public OrderDetail changeTracking(Long orderId, OrderTrackingRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
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
        return getDetail(orderId);
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
    public org.streamhub.api.v1.delivery.adapter.Tracking syncDelivery(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        org.streamhub.api.v1.delivery.adapter.Tracking tracking = deliveryService.trackOrder(order);
        deliveryDrivenTransition(order.getStatus(), tracking).ifPresent(next ->
                changeStatus(orderId, new OrderStatusChangeRequest(next, "배송상태 자동연동(" + next + ")")));
        return tracking;
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

    /** Deducts stock for a line (option stock first, else item stock) and bumps sale count. */
    private void decrementStock(OrderItem item) {
        try {
            if (item.getOptionId() != null) {
                GoodsOption option = goodsOptionRepository.findById(item.getOptionId())
                        .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
                option.subtractStock(item.getQty());
            } else {
                GoodsItem goods = goodsItemRepository.findById(item.getGoodsId())
                        .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
                goods.subtractStock(item.getQty());
            }
        } catch (IllegalStateException e) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "재고 부족");
        }
    }

    /** Restores stock for a line (option stock first, else item stock); missing goods are skipped. */
    private void restoreStock(OrderItem item) {
        if (item.getOptionId() != null) {
            goodsOptionRepository.findById(item.getOptionId())
                    .ifPresent(option -> option.addStock(item.getQty()));
        } else {
            goodsItemRepository.findById(item.getGoodsId())
                    .ifPresent(goods -> goods.addStock(item.getQty()));
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

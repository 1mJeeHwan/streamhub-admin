package org.streamhub.api.v1.pub.order;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.album.entity.Album;
import org.streamhub.api.v1.album.entity.AlbumStatus;
import org.streamhub.api.v1.album.repository.AlbumRepository;
import org.streamhub.api.v1.coupon.CouponService;
import org.streamhub.api.v1.goods.entity.GoodsItem;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.order.entity.OrderItem;
import org.streamhub.api.v1.order.entity.OrderReceipt;
import org.streamhub.api.v1.order.entity.OrderStatus;
import org.streamhub.api.v1.order.entity.ReceiptKind;
import org.streamhub.api.v1.order.repository.OrderItemRepository;
import org.streamhub.api.v1.order.repository.OrderReceiptRepository;
import org.streamhub.api.v1.order.repository.OrderRepository;
import org.streamhub.api.v1.payment.PaymentService;
import org.streamhub.api.v1.payment.dto.PayApproveCommand;
import org.streamhub.api.v1.payment.dto.PayRequestCommand;
import org.streamhub.api.v1.payment.dto.PaymentResultDto;
import org.streamhub.api.v1.pub.order.dto.MemberOrderCreateRequest;
import org.streamhub.api.v1.pub.order.dto.MemberOrderListItem;
import org.streamhub.api.v1.pub.order.dto.MemberOrderResult;
import org.streamhub.api.v1.pub.order.dto.MemberPaymentConfirmRequest;
import org.streamhub.api.v1.pub.order.dto.MemberPaymentPrepareRequest;
import org.streamhub.api.v1.pub.order.dto.MemberPaymentPrepareResult;

/**
 * Public (member-authenticated) album purchase. Creates a real {@code ORDERS} row + line item from
 * an on-sale album's bridge {@code GOODS_ITEM}, then drives it to {@code PAID} through
 * {@link PaymentService} (request → approve) — reusing the order state machine for stock deduction
 * and the PAY receipt. No new state machine is introduced; the resulting order is visible in the
 * admin order list.
 *
 * <p>Two purchase paths share the same order-creation core:
 * <ul>
 *   <li>{@link #purchase} — one-shot mock: create + request + approve server-side (PAID immediately).</li>
 *   <li>{@link #prepare} → browser PG window → {@link #confirm} — real PG (Toss): the transaction key
 *       only exists after the user completes the window, so approval is a second client-driven call.</li>
 * </ul>
 * The mock path is a demo no-op; the Toss path calls the real Toss sandbox confirm API.
 */
@Service
public class MemberOrderService {

    private static final DateTimeFormatter ORDER_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int ORDER_NO_RETRIES = 8;
    private static final String DEFAULT_PROVIDER = "TOSS";
    private static final String PAY_METHOD = "CARD";

    private final AlbumRepository albumRepository;
    private final GoodsItemRepository goodsItemRepository;
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReceiptRepository orderReceiptRepository;
    private final PaymentService paymentService;
    private final org.streamhub.api.v1.delivery.DeliveryService deliveryService;
    private final CouponService couponService;
    private final String tossClientKey;
    private final SecureRandom random = new SecureRandom();

    public MemberOrderService(
            AlbumRepository albumRepository,
            GoodsItemRepository goodsItemRepository,
            MemberRepository memberRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderReceiptRepository orderReceiptRepository,
            PaymentService paymentService,
            org.streamhub.api.v1.delivery.DeliveryService deliveryService,
            CouponService couponService,
            @org.springframework.beans.factory.annotation.Value("${app.payment.toss.client-key:}")
            String tossClientKey) {
        this.albumRepository = albumRepository;
        this.goodsItemRepository = goodsItemRepository;
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.paymentService = paymentService;
        this.deliveryService = deliveryService;
        this.couponService = couponService;
        this.tossClientKey = tossClientKey;
    }

    /**
     * Live shipment tracking for the member's own order (verifies ownership, then delegates to the
     * delivery seam which calls the courier API).
     *
     * @throws ApiException {@code NOT_FOUND} if missing, {@code UNAUTHORIZED} if it is not the
     *                      member's order
     */
    @Transactional(readOnly = true)
    public org.streamhub.api.v1.delivery.adapter.Tracking trackMyOrder(Long memberId, String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return deliveryService.trackOrder(order);
    }

    /**
     * Purchases an on-sale album as the given member: creates the order + line item, then drives the
     * mock payment (request → approve) so the order ends {@code PAID} with stock deducted and a PAY
     * receipt written — all reusing the existing order/payment domain.
     *
     * @throws ApiException {@code NOT_FOUND} if the album is missing or not on sale,
     *                      {@code INVALID_PARAMETER} if the album is not purchasable (no bridge goods)
     */
    @Transactional
    public MemberOrderResult purchase(Long memberId, MemberOrderCreateRequest request) {
        Member member = requireMember(memberId);
        Order order = createAlbumOrder(member, request.albumId(), request.couponCode());

        // One-shot path is the demo mock: it approves server-side with no payment window, so it can
        // never carry a real PG transaction key. Force MOCK regardless of the requested method —
        // real-PG purchases go through prepare → window → confirm instead.
        approvePayment(order.getId(), "MOCK");

        Order paid = orderRepository.findById(order.getId()).orElseThrow();
        return new MemberOrderResult(
                paid.getOrderNo(), paid.getStatus(), paid.getTotal(),
                paidAt(order.getId()), true);
    }

    /**
     * Phase 1 of a real-PG purchase: creates the order (PLACED) and marks payment {@code REQUESTED}
     * via {@link PaymentService#request}, then returns everything the browser needs to open the PG
     * payment window. No external PG call happens here — for Toss the window (and the later confirm)
     * carry the real transaction. The {@code orderNo}/{@code amount} returned are authoritative.
     */
    @Transactional
    public MemberPaymentPrepareResult prepare(Long memberId, MemberPaymentPrepareRequest request) {
        Member member = requireMember(memberId);
        String provider = resolveProvider(request.provider());
        Order order = createAlbumOrder(member, request.albumId(), request.couponCode());

        PaymentResultDto requested = paymentService.request(new PayRequestCommand(order.getId(), provider));

        String orderName = firstProductName(order.getId());
        String customerKey = "member-" + member.getId();
        // Toss → clientKey for the browser SDK; Kakao/PayPal → redirectUrl from the PG ready/create.
        return new MemberPaymentPrepareResult(
                order.getOrderNo(), orderName, order.getTotal(), provider,
                tossClientKey, customerKey, requested.redirectUrl());
    }

    /**
     * Phase 2 of a real-PG purchase: the payment window redirected back with its transaction key.
     * Re-verifies the order belongs to the member and the reported amount matches the order total
     * (tamper guard), then confirms via {@link PaymentService#approve} — which for Toss calls the
     * real confirm API and, on success, drives the order {@code PLACED → PAID}.
     *
     * @throws ApiException {@code NOT_FOUND} if the order is missing, {@code UNAUTHORIZED} if it
     *                      belongs to another member, {@code INVALID_PARAMETER} on amount mismatch
     *                      or a PG decline (carrying the PG's message)
     */
    @Transactional
    public MemberOrderResult confirm(Long memberId, MemberPaymentConfirmRequest request) {
        requireMember(memberId);
        Order order = orderRepository.findByOrderNo(request.orderNo())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        if (!order.getTotal().equals(request.amount())) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "결제 금액이 일치하지 않습니다");
        }

        paymentService.approve(new PayApproveCommand(order.getId(), request.paymentKey(), null));

        Order paid = orderRepository.findById(order.getId()).orElseThrow();
        return new MemberOrderResult(
                paid.getOrderNo(), paid.getStatus(), paid.getTotal(),
                paidAt(order.getId()), true);
    }

    /**
     * Validates an on-sale album and creates the order + single line item (PLACED). When a
     * {@code couponCode} is supplied it is redeemed against the goods price (server-validated and
     * consumed in this same transaction), and the resulting discount is applied to the order total.
     */
    private Order createAlbumOrder(Member member, Long albumId, String couponCode) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (album.getStatus() != AlbumStatus.ON_SALE) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        if (album.getGoodsItemId() == null) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "구매할 수 없는 앨범입니다");
        }
        GoodsItem goods = goodsItemRepository.findById(album.getGoodsItemId())
                .orElseThrow(() -> new ApiException(ResultCode.INVALID_PARAMETER, "구매할 수 없는 앨범입니다"));
        long price = goods.getPrice();

        long couponDiscount = couponCode != null && !couponCode.isBlank()
                ? couponService.redeem(couponCode.trim(), price).discount()
                : 0L;

        Order order = createOrder(member, price, couponDiscount);
        orderItemRepository.save(OrderItem.builder()
                .orderId(order.getId())
                .goodsId(goods.getId())
                .goodsName(album.getTitle())
                .optionName(null)
                .unitPrice(price)
                .qty(1)
                .lineTotal(price)
                .build());
        return order;
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ResultCode.UNAUTHORIZED));
    }

    /** A member's own purchase history, newest first. */
    @Transactional(readOnly = true)
    public List<MemberOrderListItem> myOrders(Long memberId) {
        return orderRepository.findByMemberIdOrderByOrderedAtDescIdDesc(memberId).stream()
                .map(order -> new MemberOrderListItem(
                        order.getOrderNo(),
                        firstProductName(order.getId()),
                        order.getTotal(),
                        order.getStatus(),
                        order.getOrderedAt()))
                .toList();
    }

    // --- helpers -----------------------------------------------------------

    private Order createOrder(Member member, long price, long couponDiscount) {
        long total = Math.max(0L, price - couponDiscount);
        Order order = Order.builder()
                .orderNo(nextOrderNo())
                .memberId(member.getId())
                .status(OrderStatus.PLACED)
                .orderedName(member.getName())
                .orderedPhone(member.getPhone())
                .receiverName(member.getName())
                .receiverPhone(member.getPhone())
                .goodsTotal(price)
                .shipFee(0L)
                .couponDiscount(couponDiscount)
                .pointUsed(0L)
                .total(total)
                .payMethod(PAY_METHOD)
                .orderedAt(LocalDateTime.now())
                .build();
        return orderRepository.save(order);
    }

    /** Drives the existing mock payment: request → approve (PLACED → PAID, stock + PAY receipt). */
    private void approvePayment(Long orderId, String provider) {
        PaymentResultDto requested = paymentService.request(new PayRequestCommand(orderId, provider));
        paymentService.approve(new PayApproveCommand(orderId, requested.txnId(), null));
    }

    /** Generates a unique {@code YYYYMMDD-XXXXXX} order number, retrying on collision. */
    private String nextOrderNo() {
        String day = LocalDateTime.now().format(ORDER_DAY);
        for (int attempt = 0; attempt < ORDER_NO_RETRIES; attempt++) {
            String candidate = day + "-" + String.format("%06d", random.nextInt(1_000_000));
            if (!orderRepository.existsByOrderNo(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ResultCode.INTERNAL_ERROR, "주문번호 발급에 실패했습니다");
    }

    private String resolveProvider(String requested) {
        return requested == null || requested.isBlank() ? DEFAULT_PROVIDER : requested.toUpperCase();
    }

    private LocalDateTime paidAt(Long orderId) {
        List<OrderReceipt> receipts = orderReceiptRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId);
        LocalDateTime latest = null;
        for (OrderReceipt receipt : receipts) {
            if (receipt.getKind() == ReceiptKind.PAY) {
                latest = receipt.getCreatedAt();
            }
        }
        return latest;
    }

    private String firstProductName(Long orderId) {
        return orderItemRepository.findByOrderId(orderId).stream()
                .findFirst()
                .map(OrderItem::getGoodsName)
                .orElse(null);
    }
}

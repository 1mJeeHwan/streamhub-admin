package org.streamhub.api.v1.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.delivery.DeliveryService;
import org.streamhub.api.v1.delivery.adapter.Carrier;
import org.streamhub.api.v1.delivery.adapter.Tracking;
import org.streamhub.api.v1.order.dto.OrderDetail;
import org.streamhub.api.v1.order.dto.OrderListItem;
import org.streamhub.api.v1.order.dto.OrderSearchRequest;
import org.streamhub.api.v1.order.dto.OrderStatusChangeRequest;
import org.streamhub.api.v1.order.dto.OrderTrackingRequest;

/**
 * Order management endpoints (SYSTEM or CHURCH_MANAGER).
 */
@Tag(name = "Order", description = "주문 관리")
@RestController
@RequestMapping("/v1/order")
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class OrderController {

    private final OrderService orderService;
    private final DeliveryService deliveryService;

    public OrderController(OrderService orderService, DeliveryService deliveryService) {
        this.orderService = orderService;
        this.deliveryService = deliveryService;
    }

    @Operation(summary = "주문 목록", description = "검색/상태/기간 필터 + 페이지네이션된 주문 목록.")
    @PostMapping("/list")
    public ResultDTO<ResInfinityList<OrderListItem>> list(
            @RequestBody OrderSearchRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(orderService.list(request, principal));
    }

    @Operation(summary = "주문 상세", description = "주문 + 주문상품 + 입금/환불 영수증.")
    @GetMapping("/{id}")
    public ResultDTO<OrderDetail> detail(
            @PathVariable Long id, @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(orderService.getDetail(id, principal));
    }

    @Operation(summary = "주문 상태 변경",
            description = "상태머신 전이 + 재고 차감/복원 + 입금/환불 영수증을 한 트랜잭션으로 처리.")
    @PatchMapping("/{id}/status")
    public ResultDTO<OrderDetail> changeStatus(
            @PathVariable Long id, @Valid @RequestBody OrderStatusChangeRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(orderService.changeStatus(id, request, principal));
    }

    @Operation(summary = "운송장 등록", description = "운송장 입력. READY 상태면 SHIPPING으로 자동 승격.")
    @PatchMapping("/{id}/tracking")
    public ResultDTO<OrderDetail> changeTracking(
            @PathVariable Long id, @Valid @RequestBody OrderTrackingRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(orderService.changeTracking(id, request, principal));
    }

    @Operation(summary = "택배사 목록", description = "운송장 등록 드롭다운용 택배사 목록(스마트택배 연동).")
    @GetMapping("/carriers")
    public ResultDTO<List<Carrier>> carriers() {
        return ResultDTO.ok(deliveryService.carriers());
    }

    @Operation(summary = "배송 조회",
            description = "주문의 택배사+운송장번호로 택배사 API를 호출해 실시간 배송 진행상황을 반환한다(상태 변경 없음).")
    @GetMapping("/{id}/tracking-info")
    public ResultDTO<Tracking> trackingInfo(
            @PathVariable Long id, @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(orderService.trackingInfo(id, principal));
    }

    @Operation(summary = "배송 조회·상태 동기화",
            description = "택배사 배송상태를 조회하고, 배달완료면 주문을 DONE으로(이동중이면 SHIPPING으로) 자동 전이한다.")
    @PatchMapping("/{id}/delivery-sync")
    public ResultDTO<Tracking> deliverySync(
            @PathVariable Long id, @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(orderService.syncDelivery(id, principal));
    }
}

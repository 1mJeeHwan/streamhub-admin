package org.streamhub.api.v1.donation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.donation.dto.SubscriptionDetail;
import org.streamhub.api.v1.donation.dto.SubscriptionListItem;
import org.streamhub.api.v1.donation.dto.SubscriptionSearchRequest;
import org.streamhub.api.v1.donation.dto.SubscriptionStatusRequest;

/**
 * Subscription listing and lifecycle endpoints (SYSTEM or CHURCH_MANAGER).
 */
@Tag(name = "Subscription", description = "구독(정기후원) 관리")
@RestController
@RequestMapping("/v1/subscription")
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Operation(summary = "구독 목록", description = "검색/필터/페이지네이션된 구독 목록.")
    @PostMapping("/list")
    public ResultDTO<ResInfinityList<SubscriptionListItem>> list(
            @RequestBody SubscriptionSearchRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(subscriptionService.list(request, principal));
    }

    @Operation(summary = "구독 상세")
    @GetMapping("/{id}")
    public ResultDTO<SubscriptionDetail> detail(
            @PathVariable Long id, @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(subscriptionService.getDetail(id, principal));
    }

    @Operation(summary = "구독 상태 전이", description = "ACTIVE/PAUSED/CANCELED 라이프사이클 전이.")
    @PutMapping("/{id}/status")
    public ResultDTO<SubscriptionDetail> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionStatusRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(subscriptionService.changeStatus(id, request, principal));
    }
}

package org.streamhub.api.v1.pub.me;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.jwt.JwtTokenProvider;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.pub.me.dto.FavoriteAddRequest;
import org.streamhub.api.v1.pub.me.dto.FavoriteItem;
import org.streamhub.api.v1.pub.me.dto.HistoryRecordRequest;
import org.streamhub.api.v1.pub.me.dto.MyInquiryItem;
import org.streamhub.api.v1.pub.me.dto.MyReviewItem;
import org.streamhub.api.v1.pub.me.dto.PurchasedAlbumItem;
import org.streamhub.api.v1.pub.me.dto.WatchHistoryItem;

/**
 * Member "내 정보" (mypage) endpoints under the public ({@code /pub/**}, permitAll) namespace.
 * Like {@link org.streamhub.api.v1.pub.order.MemberOrderController}, the member is resolved by
 * parsing the Bearer member token directly — it never relies on the admin SecurityContext, which
 * deliberately ignores member tokens. A missing/invalid member token is a 401.
 */
@Tag(name = "Member Me", description = "사용자 사이트 내 정보 (회원): 시청기록 / 찜 / 구매음반 / 후기 / 문의")
@RestController
@RequestMapping("/pub/v1/me")
public class MemberMeController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MemberMeService memberMeService;
    private final JwtTokenProvider tokenProvider;

    public MemberMeController(MemberMeService memberMeService, JwtTokenProvider tokenProvider) {
        this.memberMeService = memberMeService;
        this.tokenProvider = tokenProvider;
    }

    @Operation(summary = "시청기록 적재", description = "콘텐츠 시청 이벤트 1건을 기록한다(멱등 아님, best-effort).")
    @PostMapping("/history")
    public ResultDTO<Void> recordHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody HistoryRecordRequest request) {
        memberMeService.recordHistory(resolveMemberId(authorization), request.contentId(), request.watchSeconds());
        return ResultDTO.ok();
    }

    @Operation(summary = "시청기록", description = "로그인 회원의 최근 시청기록을 콘텐츠 정보와 함께 최신순으로 반환한다.")
    @GetMapping("/history")
    public ResultDTO<List<WatchHistoryItem>> history(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResultDTO.ok(memberMeService.history(resolveMemberId(authorization)));
    }

    @Operation(summary = "찜 목록", description = "로그인 회원의 재생목록 찜을 트랙/앨범 정보와 함께 반환한다.")
    @GetMapping("/favorites")
    public ResultDTO<List<FavoriteItem>> favorites(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResultDTO.ok(memberMeService.favorites(resolveMemberId(authorization)));
    }

    @Operation(summary = "찜 추가", description = "트랙을 재생목록 찜에 추가한다(이미 있으면 무시, 멱등).")
    @PostMapping("/favorites")
    public ResultDTO<Void> addFavorite(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody FavoriteAddRequest request) {
        memberMeService.addFavorite(resolveMemberId(authorization), request.trackId());
        return ResultDTO.ok();
    }

    @Operation(summary = "찜 해제", description = "트랙을 재생목록 찜에서 제거한다.")
    @DeleteMapping("/favorites/{trackId}")
    public ResultDTO<Void> removeFavorite(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long trackId) {
        memberMeService.removeFavorite(resolveMemberId(authorization), trackId);
        return ResultDTO.ok();
    }

    @Operation(summary = "구매 음반", description = "로그인 회원의 결제완료 주문에 포함된 앨범 목록(중복 제거)을 반환한다.")
    @GetMapping("/albums")
    public ResultDTO<List<PurchasedAlbumItem>> albums(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResultDTO.ok(memberMeService.purchasedAlbums(resolveMemberId(authorization)));
    }

    @Operation(summary = "내 후기", description = "로그인 회원이 작성한 상품 후기 목록을 반환한다.")
    @GetMapping("/reviews")
    public ResultDTO<List<MyReviewItem>> reviews(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResultDTO.ok(memberMeService.reviews(resolveMemberId(authorization)));
    }

    @Operation(summary = "내 문의", description = "로그인 회원이 작성한 상품 문의 목록을 반환한다.")
    @GetMapping("/inquiries")
    public ResultDTO<List<MyInquiryItem>> inquiries(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResultDTO.ok(memberMeService.inquiries(resolveMemberId(authorization)));
    }

    private Long resolveMemberId(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        DecodedJWT jwt = tokenProvider.verify(authorization.substring(BEARER_PREFIX.length()));
        if (!tokenProvider.isMemberToken(jwt)) {
            throw new ApiException(ResultCode.INVALID_TOKEN);
        }
        return Long.valueOf(jwt.getSubject());
    }
}

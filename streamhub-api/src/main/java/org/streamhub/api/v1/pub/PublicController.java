package org.streamhub.api.v1.pub;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.util.ClientIpResolver;
import org.streamhub.api.v1.album.AlbumService;
import org.streamhub.api.v1.album.dto.AlbumDetail;
import org.streamhub.api.v1.album.dto.AlbumListItem;
import org.streamhub.api.v1.album.dto.AlbumSearchRequest;
import org.streamhub.api.v1.album.dto.PreviewResponse;
import org.streamhub.api.v1.album.entity.AlbumGenre;
import org.streamhub.api.v1.banner.BannerService;
import org.streamhub.api.v1.banner.dto.BannerDto;
import org.streamhub.api.v1.banner.entity.BannerTarget;
import org.streamhub.api.v1.church.ChurchService;
import org.streamhub.api.v1.church.dto.ChurchDetail;
import org.streamhub.api.v1.church.dto.ChurchNearbyItem;
import org.streamhub.api.v1.church.dto.ChurchNearbyRequest;
import org.streamhub.api.v1.church.entity.Denomination;
import org.streamhub.api.v1.content.ContentService;
import org.streamhub.api.v1.content.dto.ContentDetail;
import org.streamhub.api.v1.content.dto.ContentListItem;
import org.streamhub.api.v1.content.dto.ContentSearchRequest;
import org.streamhub.api.v1.content.dto.PublicChannelItem;
import org.streamhub.api.v1.content.entity.ContentType;
import org.streamhub.api.v1.analytics.PublicIngestRateLimiter;
import org.streamhub.api.v1.post.PostService;
import org.streamhub.api.v1.post.dto.PostDetail;
import org.streamhub.api.v1.post.dto.PostListItem;
import org.streamhub.api.v1.post.dto.PostSearchRequest;
import org.streamhub.api.v1.pub.dto.PublicHomeResponse;
import org.streamhub.api.v1.store.StoreService;
import org.streamhub.api.v1.store.dto.StoreDto;
import org.streamhub.api.v1.store.dto.StoreSearchRequest;

/**
 * Public, unauthenticated endpoints consumed by the user site ({@code streamhub-user-web}).
 * Everything here returns PUBLISHED content only. Mapped under {@code /pub/**}, which is
 * permitAll in {@link org.streamhub.api.base.security.SecurityConfig}.
 */
@Tag(name = "Public", description = "사용자 사이트용 공개 API (인증 불필요, PUBLISHED만)")
@RestController
@RequestMapping("/pub/v1")
public class PublicController {

    private static final int HOME_SIZE = 8;

    /**
     * Tokens charged per nearby-church search. Higher than an analytics event (cost 1) because with
     * {@code church.discovery.provider=kakao} a cache-miss triggers a live upstream Kakao call, so
     * this dampens an IP that varies coordinates to bypass the discovery cache and burn quota.
     */
    private static final int CHURCH_NEARBY_TOKEN_COST = 2;

    private final ContentService contentService;
    private final PostService postService;
    private final ChurchService churchService;
    private final AlbumService albumService;
    private final StoreService storeService;
    private final BannerService bannerService;
    private final PublicIngestRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public PublicController(ContentService contentService, PostService postService,
                            ChurchService churchService, AlbumService albumService,
                            StoreService storeService, BannerService bannerService,
                            PublicIngestRateLimiter rateLimiter, ClientIpResolver clientIpResolver) {
        this.contentService = contentService;
        this.postService = postService;
        this.churchService = churchService;
        this.albumService = albumService;
        this.storeService = storeService;
        this.bannerService = bannerService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "홈 묶음", description = "최신 영상/음악/게시글을 한 번에 반환.")
    @GetMapping("/home")
    public ResultDTO<PublicHomeResponse> home() {
        ResInfinityList<ContentListItem> videos =
                contentService.listPublic(new ContentSearchRequest(0, HOME_SIZE, null, ContentType.VIDEO, null, null, null, null));
        ResInfinityList<ContentListItem> musics =
                contentService.listPublic(new ContentSearchRequest(0, HOME_SIZE, null, ContentType.SOUND, null, null, null, null));
        ResInfinityList<PostListItem> posts =
                postService.listPublished(new PostSearchRequest(0, HOME_SIZE, null));
        return ResultDTO.ok(new PublicHomeResponse(
                videos.getContents(), musics.getContents(), posts.getContents()));
    }

    @Operation(summary = "공개 배너 목록", description = "노출 중인 탭 배너(target=VIDEO/SOUND, ALL 포함). 정렬순.")
    @GetMapping("/banners")
    public ResultDTO<List<BannerDto>> banners(@RequestParam(required = false) BannerTarget target) {
        return ResultDTO.ok(bannerService.listPublic(target));
    }

    @Operation(summary = "공개 콘텐츠 목록",
            description = "PUBLISHED 영상/음악 목록(검색·채널 필터·페이지네이션·정렬).")
    @GetMapping("/contents")
    public ResultDTO<ResInfinityList<ContentListItem>> contents(
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        ContentSearchRequest request =
                new ContentSearchRequest(pageNumber, pageSize, keyword, type, null, channelId, sortBy, sortDir);
        return ResultDTO.ok(contentService.listPublic(request));
    }

    @Operation(summary = "공개 채널 목록",
            description = "PUBLISHED 콘텐츠를 가진 채널을 활동순으로 반환(type=VIDEO/SOUND). 채널 브라우징용.")
    @GetMapping("/channels")
    public ResultDTO<List<PublicChannelItem>> channels(
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) Integer limit) {
        return ResultDTO.ok(contentService.listPublicChannels(type, limit == null ? 12 : limit));
    }

    @Operation(summary = "공개 콘텐츠 상세", description = "PUBLISHED만 반환하며 조회수를 1 증가시킨다.")
    @GetMapping("/contents/{id}")
    public ResultDTO<ContentDetail> contentDetail(@PathVariable Long id) {
        return ResultDTO.ok(contentService.getPublicDetail(id));
    }

    @Operation(summary = "공개 게시글 목록", description = "PUBLISHED 게시글 목록(검색·페이지네이션).")
    @GetMapping("/posts")
    public ResultDTO<ResInfinityList<PostListItem>> posts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize) {
        return ResultDTO.ok(postService.listPublished(new PostSearchRequest(pageNumber, pageSize, keyword)));
    }

    @Operation(summary = "공개 게시글 상세")
    @GetMapping("/posts/{id}")
    public ResultDTO<PostDetail> postDetail(@PathVariable Long id) {
        return ResultDTO.ok(postService.getPublishedDetail(id));
    }

    @Operation(summary = "교회 위치검색", description = "현위치(lat/lng) 기준 거리순. 좌표 미제공 시 지역/교단/키워드 필터만 적용. 과도한 요청은 429.")
    @GetMapping("/churches")
    public ResponseEntity<ResultDTO<ResInfinityList<ChurchNearbyItem>>> churchesNearby(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) Denomination denomination,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            HttpServletRequest httpRequest) {
        if (!rateLimiter.tryAcquire("church:" + clientIpResolver.resolve(httpRequest), CHURCH_NEARBY_TOKEN_COST)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ResultDTO.error(ResultCode.INVALID_PARAMETER, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."));
        }
        ChurchNearbyRequest request = new ChurchNearbyRequest(
                lat, lng, radiusKm, denomination, keyword, regionId, pageNumber, pageSize);
        return ResponseEntity.ok(ResultDTO.ok(churchService.nearby(request)));
    }

    @Operation(summary = "공개 교회 상세", description = "노출(use_yn=Y) 교회만 반환하며 예배시간을 포함한다.")
    @GetMapping("/churches/{id}")
    public ResultDTO<ChurchDetail> churchDetail(@PathVariable Long id) {
        return ResultDTO.ok(churchService.getPublicDetail(id));
    }

    @Operation(summary = "공개 앨범 목록",
            description = "ON_SALE 음반 목록(장르 필터·검색·페이지네이션·정렬). "
                    + "sortBy=viewCount(인기)/releaseDate(최신)/createdAt 등, sortDir=asc/desc.")
    @GetMapping("/albums")
    public ResultDTO<ResInfinityList<AlbumListItem>> albums(
            @RequestParam(required = false) AlbumGenre genre,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        AlbumSearchRequest request =
                new AlbumSearchRequest(pageNumber, pageSize, keyword, genre, null, sortBy, sortDir);
        return ResultDTO.ok(albumService.listPublic(request));
    }

    @Operation(summary = "공개 앨범 상세", description = "ON_SALE만 반환하며 트랙리스트를 포함하고 조회수를 1 증가시킨다.")
    @GetMapping("/albums/{id}")
    public ResultDTO<AlbumDetail> albumDetail(@PathVariable Long id) {
        return ResultDTO.ok(albumService.getPublicDetail(id));
    }

    @Operation(summary = "트랙 미리듣기", description = "30초 미리듣기 음원 URL·구간·데모 플래그를 반환한다.")
    @GetMapping("/albums/{albumId}/tracks/{trackId}/preview")
    public ResultDTO<PreviewResponse> trackPreview(
            @PathVariable Long albumId, @PathVariable Long trackId) {
        return ResultDTO.ok(albumService.getPreview(albumId, trackId));
    }

    @Operation(summary = "공개 매장찾기", description = "현위치(lat/lng) 기준 거리순. 좌표 미제공 시 지역 필터만 적용. 데모 가상 매장.")
    @GetMapping("/stores")
    public ResultDTO<List<StoreDto>> stores(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Long regionId) {
        return ResultDTO.ok(storeService.listPublic(new StoreSearchRequest(regionId, lat, lng)));
    }
}

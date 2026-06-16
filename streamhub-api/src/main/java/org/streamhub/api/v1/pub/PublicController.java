package org.streamhub.api.v1.pub;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.content.ContentService;
import org.streamhub.api.v1.content.dto.ContentDetail;
import org.streamhub.api.v1.content.dto.ContentListItem;
import org.streamhub.api.v1.content.dto.ContentSearchRequest;
import org.streamhub.api.v1.content.entity.ContentType;
import org.streamhub.api.v1.post.PostService;
import org.streamhub.api.v1.post.dto.PostDetail;
import org.streamhub.api.v1.post.dto.PostListItem;
import org.streamhub.api.v1.post.dto.PostSearchRequest;
import org.streamhub.api.v1.pub.dto.PublicHomeResponse;

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

    private final ContentService contentService;
    private final PostService postService;

    public PublicController(ContentService contentService, PostService postService) {
        this.contentService = contentService;
        this.postService = postService;
    }

    @Operation(summary = "홈 묶음", description = "최신 영상/음악/게시글을 한 번에 반환.")
    @GetMapping("/home")
    public ResultDTO<PublicHomeResponse> home() {
        ResInfinityList<ContentListItem> videos =
                contentService.listPublic(new ContentSearchRequest(0, HOME_SIZE, null, ContentType.VIDEO, null, null));
        ResInfinityList<ContentListItem> musics =
                contentService.listPublic(new ContentSearchRequest(0, HOME_SIZE, null, ContentType.SOUND, null, null));
        ResInfinityList<PostListItem> posts =
                postService.listPublished(new PostSearchRequest(0, HOME_SIZE, null));
        return ResultDTO.ok(new PublicHomeResponse(
                videos.getContents(), musics.getContents(), posts.getContents()));
    }

    @Operation(summary = "공개 콘텐츠 목록", description = "PUBLISHED 영상/음악 목록(검색·페이지네이션).")
    @GetMapping("/contents")
    public ResultDTO<ResInfinityList<ContentListItem>> contents(
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize) {
        ContentSearchRequest request =
                new ContentSearchRequest(pageNumber, pageSize, keyword, type, null, null);
        return ResultDTO.ok(contentService.listPublic(request));
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
}

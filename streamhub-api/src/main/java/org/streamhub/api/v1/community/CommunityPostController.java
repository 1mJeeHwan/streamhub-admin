package org.streamhub.api.v1.community;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.community.dto.CommunityPostDto;
import org.streamhub.api.v1.community.dto.CommunityPostSaveRequest;
import org.streamhub.api.v1.community.dto.CommunityPostSearchRequest;

/**
 * Community post management endpoints (SYSTEM or CHURCH_MANAGER).
 */
@Tag(name = "Post", description = "커뮤니티 게시글 관리")
@RestController
@RequestMapping("/v1/community-post")
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class CommunityPostController {

    private final CommunityPostService postService;

    public CommunityPostController(CommunityPostService postService) {
        this.postService = postService;
    }

    @Operation(summary = "게시글 목록", description = "게시판/카테고리/검색어 필터, 최신순 전체 목록.")
    @PostMapping("/list")
    public ResultDTO<List<CommunityPostDto>> list(@RequestBody(required = false) CommunityPostSearchRequest request) {
        return ResultDTO.ok(postService.list(request));
    }

    @Operation(summary = "게시글 상세")
    @GetMapping("/{id}")
    public ResultDTO<CommunityPostDto> detail(@PathVariable Long id) {
        return ResultDTO.ok(postService.detail(id));
    }

    @Operation(summary = "게시글 등록", description = "글 관리 작성 화면에서 새 게시글을 등록한다.")
    @PostMapping
    public ResultDTO<CommunityPostDto> create(@Valid @RequestBody CommunityPostSaveRequest request) {
        return ResultDTO.ok(postService.create(request));
    }

    @Operation(summary = "게시글 수정", description = "기존 게시글의 작성 항목을 수정한다(조회수/추천수 유지).")
    @PutMapping("/{id}")
    public ResultDTO<CommunityPostDto> update(
            @PathVariable Long id, @Valid @RequestBody CommunityPostSaveRequest request) {
        return ResultDTO.ok(postService.update(id, request));
    }

    @Operation(summary = "게시글 삭제")
    @DeleteMapping("/{id}")
    public ResultDTO<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResultDTO.ok();
    }
}

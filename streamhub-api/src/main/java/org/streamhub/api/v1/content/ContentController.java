package org.streamhub.api.v1.content;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.v1.content.dto.ContentCreateRequest;
import org.streamhub.api.v1.content.dto.ContentDetail;
import org.streamhub.api.v1.content.dto.ContentListItem;
import org.streamhub.api.v1.content.dto.ContentSearchRequest;
import org.streamhub.api.v1.content.dto.UploadResponse;

/**
 * Content management endpoints (SYSTEM or CHURCH_MANAGER).
 */
@Tag(name = "Content", description = "콘텐츠(영상) 관리")
@RestController
@RequestMapping("/v1/content")
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class ContentController {

    private final ContentService contentService;
    private final StorageService storageService;

    public ContentController(ContentService contentService, StorageService storageService) {
        this.contentService = contentService;
        this.storageService = storageService;
    }

    @Operation(summary = "채널 목록", description = "콘텐츠 등록 폼의 채널 선택용.")
    @GetMapping("/channels")
    public ResultDTO<java.util.List<org.streamhub.api.v1.content.dto.ChannelDto>> channels() {
        return ResultDTO.ok(contentService.listChannels());
    }

    @Operation(summary = "콘텐츠 목록", description = "검색/필터/페이지네이션된 콘텐츠 목록.")
    @PostMapping("/list")
    public ResultDTO<ResInfinityList<ContentListItem>> list(@RequestBody ContentSearchRequest request) {
        return ResultDTO.ok(contentService.list(request));
    }

    @Operation(summary = "콘텐츠 상세")
    @GetMapping("/{id}")
    public ResultDTO<ContentDetail> detail(@PathVariable Long id) {
        return ResultDTO.ok(contentService.getDetail(id));
    }

    @Operation(summary = "콘텐츠 등록")
    @PostMapping
    public ResultDTO<ContentDetail> create(@Valid @RequestBody ContentCreateRequest request) {
        return ResultDTO.ok(contentService.create(request));
    }

    @Operation(summary = "콘텐츠 수정")
    @PutMapping("/{id}")
    public ResultDTO<ContentDetail> update(
            @PathVariable Long id, @Valid @RequestBody ContentCreateRequest request) {
        return ResultDTO.ok(contentService.update(id, request));
    }

    @Operation(summary = "콘텐츠 삭제")
    @DeleteMapping("/{id}")
    public ResultDTO<Void> delete(@PathVariable Long id) {
        contentService.delete(id);
        return ResultDTO.ok();
    }

    @Operation(summary = "파일 업로드", description = "썸네일/파일을 스토리지(MinIO·S3)에 업로드하고 key/url을 반환한다.")
    @PostMapping("/upload")
    public ResultDTO<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        String key = storageService.upload(file, "content");
        return ResultDTO.ok(new UploadResponse(key, storageService.publicUrl(key)));
    }
}

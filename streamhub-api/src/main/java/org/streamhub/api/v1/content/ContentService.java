package org.streamhub.api.v1.content;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.v1.content.dto.ContentCreateRequest;
import org.streamhub.api.v1.content.dto.ContentDetail;
import org.streamhub.api.v1.content.dto.ContentFileDto;
import org.streamhub.api.v1.content.dto.ContentListItem;
import org.streamhub.api.v1.content.dto.ContentSearchRequest;
import org.streamhub.api.v1.content.entity.Content;
import org.streamhub.api.v1.content.entity.ContentFile;
import org.streamhub.api.v1.content.entity.ContentHashtag;
import org.streamhub.api.v1.content.entity.ContentStatus;
import org.streamhub.api.v1.content.entity.Hashtag;
import org.streamhub.api.v1.content.mapper.ContentMapper;
import org.streamhub.api.v1.content.repository.ChannelRepository;
import org.streamhub.api.v1.content.repository.ContentFileRepository;
import org.streamhub.api.v1.content.repository.ContentHashtagRepository;
import org.streamhub.api.v1.content.repository.ContentRepository;
import org.streamhub.api.v1.content.repository.HashtagRepository;

/**
 * Content management: paginated search (MyBatis), CRUD (JPA), hashtag upserts, and
 * thumbnail/file URL resolution via {@link StorageService}.
 */
@Service
public class ContentService {

    private final ContentMapper contentMapper;
    private final ContentRepository contentRepository;
    private final ChannelRepository channelRepository;
    private final HashtagRepository hashtagRepository;
    private final ContentHashtagRepository contentHashtagRepository;
    private final ContentFileRepository contentFileRepository;
    private final StorageService storageService;
    private final org.streamhub.api.v1.actionlog.ActionLogPublisher actionLogPublisher;

    public ContentService(
            ContentMapper contentMapper,
            ContentRepository contentRepository,
            ChannelRepository channelRepository,
            HashtagRepository hashtagRepository,
            ContentHashtagRepository contentHashtagRepository,
            ContentFileRepository contentFileRepository,
            StorageService storageService,
            org.streamhub.api.v1.actionlog.ActionLogPublisher actionLogPublisher) {
        this.contentMapper = contentMapper;
        this.contentRepository = contentRepository;
        this.channelRepository = channelRepository;
        this.hashtagRepository = hashtagRepository;
        this.contentHashtagRepository = contentHashtagRepository;
        this.contentFileRepository = contentFileRepository;
        this.storageService = storageService;
        this.actionLogPublisher = actionLogPublisher;
    }

    @Transactional(readOnly = true)
    public List<org.streamhub.api.v1.content.dto.ChannelDto> listChannels() {
        return channelRepository.findAll().stream()
                .map(org.streamhub.api.v1.content.dto.ChannelDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResInfinityList<ContentListItem> list(ContentSearchRequest request) {
        String type = request.type() == null ? null : request.type().name();
        String status = request.status() == null ? null : request.status().name();
        String keyword = blankToNull(request.keyword());
        int size = request.pageSizeOrDefault();

        List<ContentListItem> contents =
                contentMapper.selectList(keyword, type, status, request.channelId(), request.offset(), size);
        contents.forEach(item -> item.setThumbnailUrl(storageService.publicUrl(item.getThumbnailKey())));
        long total = contentMapper.countList(keyword, type, status, request.channelId());
        return ResInfinityList.of(contents, total, size);
    }

    /** Public list: forces {@code status=PUBLISHED} and ignores any channel filter. */
    @Transactional(readOnly = true)
    public ResInfinityList<ContentListItem> listPublic(ContentSearchRequest request) {
        ContentSearchRequest forced = new ContentSearchRequest(
                request.pageNumber(), request.pageSize(), request.keyword(),
                request.type(), ContentStatus.PUBLISHED, null);
        return list(forced);
    }

    /** Public detail: 404 unless PUBLISHED, and atomically increments the view count. */
    @Transactional
    public ContentDetail getPublicDetail(Long id) {
        ContentDetail detail = getDetail(id); // throws NOT_FOUND if missing; fills url/hashtags/files
        if (detail.getStatus() != ContentStatus.PUBLISHED) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        contentRepository.incrementViewCount(id);
        detail.setViewCount((detail.getViewCount() == null ? 0L : detail.getViewCount()) + 1);
        return detail;
    }

    @Transactional(readOnly = true)
    public ContentDetail getDetail(Long id) {
        ContentDetail detail = contentMapper.selectDetail(id);
        if (detail == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        detail.setThumbnailUrl(storageService.publicUrl(detail.getThumbnailKey()));
        detail.setHashtags(loadHashtagNames(id));
        detail.setFiles(loadFiles(id));
        return detail;
    }

    @Transactional
    public ContentDetail create(ContentCreateRequest request) {
        Content content = Content.builder()
                .channelId(request.channelId())
                .type(request.type())
                .title(request.title())
                .description(request.description())
                .thumbnailKey(request.thumbnailKey())
                .mediaUrl(request.mediaUrl())
                .durationSec(request.durationSec())
                .status(request.status() == null ? ContentStatus.DRAFT : request.status())
                .build();
        Content saved = contentRepository.save(content);
        applyHashtags(saved.getId(), request.hashtags());
        recordThumbnailFile(saved.getId(), request.thumbnailKey());
        actionLogPublisher.publish("CONTENT_CREATE", "CONTENT", String.valueOf(saved.getId()), request.title());
        return getDetail(saved.getId());
    }

    @Transactional
    public ContentDetail update(Long id, ContentCreateRequest request) {
        Content content = contentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        content.update(
                request.title(), request.description(), request.type(), request.channelId(),
                request.mediaUrl(), request.durationSec(), request.thumbnailKey(),
                request.status() == null ? content.getStatus() : request.status());
        contentRepository.saveAndFlush(content);
        applyHashtags(id, request.hashtags());
        actionLogPublisher.publish("CONTENT_UPDATE", "CONTENT", String.valueOf(id), request.title());
        return getDetail(id);
    }

    @Transactional
    public void delete(Long id) {
        Content content = contentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        contentHashtagRepository.deleteByContentId(id);
        contentFileRepository.findByContentId(id).forEach(f -> storageService.delete(f.getS3Key()));
        contentFileRepository.deleteByContentId(id);
        storageService.delete(content.getThumbnailKey());
        contentRepository.delete(content);
        actionLogPublisher.publish("CONTENT_DELETE", "CONTENT", String.valueOf(id), content.getTitle());
    }

    // --- helpers -----------------------------------------------------------

    /** Replaces a content's hashtag links, creating any tags that don't exist yet. */
    private void applyHashtags(Long contentId, List<String> names) {
        contentHashtagRepository.deleteByContentId(contentId);
        if (names == null || names.isEmpty()) {
            return;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String name : names) {
            if (StringUtils.hasText(name)) {
                distinct.add(name.trim());
            }
        }
        for (String name : distinct) {
            Hashtag tag = hashtagRepository.findByName(name)
                    .orElseGet(() -> hashtagRepository.save(Hashtag.builder().name(name).build()));
            contentHashtagRepository.save(
                    ContentHashtag.builder().contentId(contentId).hashtagId(tag.getId()).build());
        }
    }

    private void recordThumbnailFile(Long contentId, String thumbnailKey) {
        if (StringUtils.hasText(thumbnailKey)) {
            contentFileRepository.save(ContentFile.builder()
                    .contentId(contentId)
                    .s3Key(thumbnailKey)
                    .fileType("image")
                    .build());
        }
    }

    private List<String> loadHashtagNames(Long contentId) {
        List<Long> ids = contentHashtagRepository.findByContentId(contentId).stream()
                .map(ContentHashtag::getHashtagId)
                .toList();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return hashtagRepository.findAllById(ids).stream().map(Hashtag::getName).sorted().toList();
    }

    private List<ContentFileDto> loadFiles(Long contentId) {
        return contentFileRepository.findByContentId(contentId).stream()
                .map(f -> ContentFileDto.of(f, storageService.publicUrl(f.getS3Key())))
                .toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

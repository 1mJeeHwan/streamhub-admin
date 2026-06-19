package org.streamhub.api.v1.content;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.security.AuthoritiesConstants;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.base.util.SortResolver;
import org.streamhub.api.v1.content.dto.ContentCreateRequest;
import org.streamhub.api.v1.content.dto.ContentDetail;
import org.streamhub.api.v1.content.dto.ContentFileDto;
import org.streamhub.api.v1.content.dto.ContentListItem;
import org.streamhub.api.v1.content.dto.ContentSearchRequest;
import org.streamhub.api.v1.content.entity.Channel;
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

    /** Unscoped SYSTEM principal for public endpoints (no authenticated operator, all churches). */
    private static final AdminPrincipal SYSTEM_PRINCIPAL =
            new AdminPrincipal(null, AuthoritiesConstants.SYSTEM, null);

    /** Whitelisted sort keys (ContentListItem field → SQL column) for server-side list sorting. */
    private static final Map<String, String> CONTENT_SORT_COLUMNS = Map.of(
            "title", "c.title",
            "type", "c.type",
            "status", "c.status",
            "channelName", "ch.name",
            "viewCount", "c.view_count",
            "durationSec", "c.duration_sec",
            "createdAt", "c.created_at");

    private final ContentMapper contentMapper;
    private final ContentRepository contentRepository;
    private final ChannelRepository channelRepository;
    private final HashtagRepository hashtagRepository;
    private final HashtagWriter hashtagWriter;
    private final ContentHashtagRepository contentHashtagRepository;
    private final ContentFileRepository contentFileRepository;
    private final StorageService storageService;
    private final org.streamhub.api.v1.actionlog.ActionLogPublisher actionLogPublisher;

    public ContentService(
            ContentMapper contentMapper,
            ContentRepository contentRepository,
            ChannelRepository channelRepository,
            HashtagRepository hashtagRepository,
            HashtagWriter hashtagWriter,
            ContentHashtagRepository contentHashtagRepository,
            ContentFileRepository contentFileRepository,
            StorageService storageService,
            org.streamhub.api.v1.actionlog.ActionLogPublisher actionLogPublisher) {
        this.contentMapper = contentMapper;
        this.contentRepository = contentRepository;
        this.channelRepository = channelRepository;
        this.hashtagRepository = hashtagRepository;
        this.hashtagWriter = hashtagWriter;
        this.contentHashtagRepository = contentHashtagRepository;
        this.contentFileRepository = contentFileRepository;
        this.storageService = storageService;
        this.actionLogPublisher = actionLogPublisher;
    }

    /**
     * Channel options for the content form's selector. A CHURCH_MANAGER only sees its own
     * church's channels; SYSTEM sees all.
     *
     * @param principal authenticated operator providing the church scope
     * @return channel options visible to the operator
     */
    @Transactional(readOnly = true)
    public List<org.streamhub.api.v1.content.dto.ChannelDto> listChannels(AdminPrincipal principal) {
        List<Channel> channels = principal.isSystem()
                ? channelRepository.findAll()
                : channelRepository.findByChurchId(principal.churchId());
        return channels.stream()
                .map(org.streamhub.api.v1.content.dto.ChannelDto::from)
                .toList();
    }

    /**
     * Paginated content search. Content carries no church column, so the filter is applied through
     * the {@code CHANNEL} join; a CHURCH_MANAGER operator is pinned to its own church's channels.
     *
     * @param request   search/pagination filters
     * @param principal authenticated operator providing the church scope
     * @return the filtered, paginated content list
     */
    @Transactional(readOnly = true)
    public ResInfinityList<ContentListItem> list(ContentSearchRequest request, AdminPrincipal principal) {
        String type = request.type() == null ? null : request.type().name();
        String status = request.status() == null ? null : request.status().name();
        String keyword = blankToNull(request.keyword());
        // A non-SYSTEM operator must carry a church scope; a null churchId here would degrade the
        // mapper filter to "IS NULL" and leak every church's content, so fail closed instead.
        Long churchId = null;
        if (!principal.isSystem()) {
            churchId = principal.churchId();
            if (churchId == null) {
                throw new ApiException(ResultCode.FORBIDDEN);
            }
        }
        int size = request.pageSizeOrDefault();
        String orderBy = SortResolver.resolve(request.sortBy(), request.sortDir(),
                CONTENT_SORT_COLUMNS, "c.id", "c.created_at DESC, c.id DESC");

        List<ContentListItem> contents = contentMapper.selectList(
                keyword, type, status, request.channelId(), churchId, orderBy, request.offset(), size);
        contents.forEach(item -> item.setThumbnailUrl(storageService.publicUrl(item.getThumbnailKey())));
        long total = contentMapper.countList(keyword, type, status, request.channelId(), churchId);
        return ResInfinityList.of(contents, total, size);
    }

    /** Public list: forces {@code status=PUBLISHED}, ignores any channel filter, and spans all churches. */
    @Transactional(readOnly = true)
    public ResInfinityList<ContentListItem> listPublic(ContentSearchRequest request) {
        ContentSearchRequest forced = new ContentSearchRequest(
                request.pageNumber(), request.pageSize(), request.keyword(),
                request.type(), ContentStatus.PUBLISHED, null,
                request.sortBy(), request.sortDir());
        return list(forced, SYSTEM_PRINCIPAL);
    }

    /** Public detail: 404 unless PUBLISHED, and atomically increments the view count. */
    @Transactional
    public ContentDetail getPublicDetail(Long id) {
        ContentDetail detail = getDetail(id, SYSTEM_PRINCIPAL); // throws NOT_FOUND if missing
        if (detail.getStatus() != ContentStatus.PUBLISHED) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        contentRepository.incrementViewCount(id);
        detail.setViewCount((detail.getViewCount() == null ? 0L : detail.getViewCount()) + 1);
        return detail;
    }

    /**
     * Content detail. Verifies the content's owning channel is in the operator's church first so a
     * CHURCH_MANAGER cannot read another church's content.
     *
     * @param id        content id
     * @param principal authenticated operator providing the church scope
     * @return the assembled content detail (url + hashtags + files)
     */
    @Transactional(readOnly = true)
    public ContentDetail getDetail(Long id, AdminPrincipal principal) {
        ContentDetail detail = contentMapper.selectDetail(id);
        if (detail == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        ensureChannelInScope(detail.getChannelId(), principal);
        detail.setThumbnailUrl(storageService.publicUrl(detail.getThumbnailKey()));
        detail.setHashtags(loadHashtagNames(id));
        detail.setFiles(loadFiles(id));
        return detail;
    }

    /**
     * Creates content. The target channel must belong to the operator's church (SYSTEM bypasses),
     * so a CHURCH_MANAGER cannot plant content under another church's channel.
     *
     * @param request   content fields (including {@code channelId})
     * @param principal authenticated operator providing the church scope
     * @return the created content's detail
     */
    @Transactional
    public ContentDetail create(ContentCreateRequest request, AdminPrincipal principal) {
        ensureChannelInScope(request.channelId(), principal);
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
        return getDetail(saved.getId(), principal);
    }

    /**
     * Updates content. Both the existing content's channel and the requested target channel must
     * belong to the operator's church (SYSTEM bypasses), so a CHURCH_MANAGER can neither edit
     * another church's content nor move its content into another church's channel.
     *
     * @param id        content id
     * @param request   content fields (including the possibly-changed {@code channelId})
     * @param principal authenticated operator providing the church scope
     * @return the updated content's detail
     */
    @Transactional
    public ContentDetail update(Long id, ContentCreateRequest request, AdminPrincipal principal) {
        Content content = contentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureChannelInScope(content.getChannelId(), principal);
        ensureChannelInScope(request.channelId(), principal);
        content.update(
                request.title(), request.description(), request.type(), request.channelId(),
                request.mediaUrl(), request.durationSec(), request.thumbnailKey(),
                request.status() == null ? content.getStatus() : request.status());
        contentRepository.saveAndFlush(content);
        applyHashtags(id, request.hashtags());
        actionLogPublisher.publish("CONTENT_UPDATE", "CONTENT", String.valueOf(id), request.title());
        return getDetail(id, principal);
    }

    /**
     * Deletes content (and its hashtag links, files, thumbnail). The content's channel must belong
     * to the operator's church (SYSTEM bypasses).
     *
     * @param id        content id
     * @param principal authenticated operator providing the church scope
     */
    @Transactional
    public void delete(Long id, AdminPrincipal principal) {
        Content content = contentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureChannelInScope(content.getChannelId(), principal);
        contentHashtagRepository.deleteByContentId(id);
        contentFileRepository.findByContentId(id).forEach(f -> storageService.delete(f.getS3Key()));
        contentFileRepository.deleteByContentId(id);
        storageService.delete(content.getThumbnailKey());
        contentRepository.delete(content);
        actionLogPublisher.publish("CONTENT_DELETE", "CONTENT", String.valueOf(id), content.getTitle());
    }

    // --- helpers -----------------------------------------------------------

    /**
     * Verifies the channel belongs to the operator's church (SYSTEM bypasses). A missing channel or
     * one owned by another church is rejected as {@code NOT_FOUND}, so the same response hides both
     * a non-existent channel and another church's channel (no cross-tenant enumeration).
     */
    private void ensureChannelInScope(Long channelId, AdminPrincipal principal) {
        if (principal.isSystem()) {
            return;
        }
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!channel.getChurchId().equals(principal.churchId())) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
    }

    /**
     * Replaces a content's hashtag links, creating any tags that don't exist yet. Tag creation is a
     * get-or-create that tolerates a concurrent insert: the bulk {@code findByNameIn} resolves
     * existing tags, then each new tag is inserted and a unique collision (another request created
     * the same tag in parallel) is absorbed by re-reading the now-present row — mirroring the
     * {@code DataIntegrityViolationException} conflict handling in WorshipService/CouponService.
     */
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
            Hashtag tag = getOrCreateHashtag(name);
            contentHashtagRepository.save(
                    ContentHashtag.builder().contentId(contentId).hashtagId(tag.getId()).build());
        }
    }

    /**
     * Atomically resolves a hashtag by name: returns the existing tag, or inserts a new one and
     * absorbs a unique-name collision from a concurrent insert by re-reading the row.
     */
    private Hashtag getOrCreateHashtag(String name) {
        return hashtagRepository.findByName(name).orElseGet(() -> {
            try {
                // Isolated insert (REQUIRES_NEW): a collision rolls back only this insert, not the
                // caller's content transaction, so we can absorb it and re-read the committed row.
                return hashtagWriter.insert(name);
            } catch (DataIntegrityViolationException collision) {
                // A parallel request inserted the same tag first — re-read the now-present row.
                return hashtagRepository.findByName(name)
                        .orElseThrow(() -> collision);
            }
        });
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

package org.streamhub.api.v1.community;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.community.dto.CommunityPostDto;
import org.streamhub.api.v1.community.dto.CommunityPostSaveRequest;
import org.streamhub.api.v1.community.dto.CommunityPostSearchRequest;
import org.streamhub.api.v1.community.entity.CommunityPost;
import org.streamhub.api.v1.community.repository.CommunityPostRepository;

/**
 * Community post management: filtered listing plus create/update/detail/delete. The demo dataset
 * is small, so the listing loads all posts and filters/sorts in memory (newest first) — no
 * pagination needed. Write operations publish an action log (best-effort) like other admin paths.
 */
@Service
public class CommunityPostService {

    private static final String TARGET_TYPE = "COMMUNITY_POST";

    private final CommunityPostRepository postRepository;
    private final ActionLogPublisher actionLogPublisher;

    public CommunityPostService(CommunityPostRepository postRepository,
                                ActionLogPublisher actionLogPublisher) {
        this.postRepository = postRepository;
        this.actionLogPublisher = actionLogPublisher;
    }

    /**
     * Listing: optionally filtered by board, category, and title keyword; newest first. All
     * matching rows are returned (small demo dataset).
     */
    @Transactional(readOnly = true)
    public List<CommunityPostDto> list(CommunityPostSearchRequest request) {
        Long boardId = request != null ? request.boardId() : null;
        String category = request != null ? request.category() : null;
        String keyword = request != null && request.keyword() != null
                ? request.keyword().trim().toLowerCase() : null;
        return postRepository.findAll().stream()
                .filter(post -> boardId == null || boardId.equals(post.getBoardId()))
                .filter(post -> category == null || category.isBlank()
                        || category.equals(post.getCategory()))
                .filter(post -> keyword == null || keyword.isBlank()
                        || (post.getTitle() != null && post.getTitle().toLowerCase().contains(keyword)))
                .sorted(Comparator.comparing(CommunityPost::getCreatedAt).reversed()
                        .thenComparing(Comparator.comparing(CommunityPost::getId).reversed()))
                .map(CommunityPostDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunityPostDto detail(Long id) {
        CommunityPost post = postRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        return CommunityPostDto.from(post);
    }

    /** Creates a new post from the authoring screen; counters start at zero. */
    @Transactional
    public CommunityPostDto create(CommunityPostSaveRequest request) {
        CommunityPost post = postRepository.save(CommunityPost.builder()
                .boardId(request.boardId())
                .category(request.category())
                .title(request.title())
                .content(request.content())
                .writerName(request.writerName())
                .secretYn(request.secretYn())
                .build());
        actionLogPublisher.publish("COMMUNITY_POST_CREATE", TARGET_TYPE,
                String.valueOf(post.getId()), post.getTitle());
        return CommunityPostDto.from(post);
    }

    /** Updates an existing post's editorial fields; counters and {@code createdAt} are preserved. */
    @Transactional
    public CommunityPostDto update(Long id, CommunityPostSaveRequest request) {
        CommunityPost post = postRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        post.update(request.boardId(), request.category(), request.title(),
                request.content(), request.writerName(), request.secretYn());
        postRepository.saveAndFlush(post);
        actionLogPublisher.publish("COMMUNITY_POST_UPDATE", TARGET_TYPE,
                String.valueOf(id), post.getTitle());
        return CommunityPostDto.from(post);
    }

    @Transactional
    public void delete(Long id) {
        CommunityPost post = postRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        postRepository.delete(post);
        actionLogPublisher.publish("COMMUNITY_POST_DELETE", TARGET_TYPE,
                String.valueOf(id), post.getTitle());
    }
}

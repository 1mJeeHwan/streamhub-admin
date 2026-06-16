package org.streamhub.api.v1.post;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.v1.post.dto.PostDetail;
import org.streamhub.api.v1.post.dto.PostListItem;
import org.streamhub.api.v1.post.dto.PostSearchRequest;
import org.streamhub.api.v1.post.entity.Post;
import org.streamhub.api.v1.post.entity.PostStatus;
import org.streamhub.api.v1.post.mapper.PostMapper;
import org.streamhub.api.v1.post.repository.PostRepository;

/**
 * Public post queries: paginated list (MyBatis) + detail (JPA). Always restricted to
 * {@link PostStatus#PUBLISHED} so unpublished drafts never leak to the user site.
 */
@Service
public class PostService {

    private final PostMapper postMapper;
    private final PostRepository postRepository;
    private final StorageService storageService;

    public PostService(PostMapper postMapper, PostRepository postRepository, StorageService storageService) {
        this.postMapper = postMapper;
        this.postRepository = postRepository;
        this.storageService = storageService;
    }

    /** Lists published posts only, newest first. */
    @Transactional(readOnly = true)
    public ResInfinityList<PostListItem> listPublished(PostSearchRequest request) {
        String keyword = blankToNull(request.keyword());
        int size = request.pageSizeOrDefault();
        List<PostListItem> posts =
                postMapper.selectList(keyword, PostStatus.PUBLISHED.name(), request.offset(), size);
        posts.forEach(item -> item.setThumbnailUrl(storageService.publicUrl(item.getThumbnailKey())));
        long total = postMapper.countList(keyword, PostStatus.PUBLISHED.name());
        return ResInfinityList.of(posts, total, size);
    }

    /** Returns a published post, or 404 if it is missing or still a draft. */
    @Transactional(readOnly = true)
    public PostDetail getPublishedDetail(Long id) {
        Post post = postRepository.findById(id)
                .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        return PostDetail.of(post, storageService.publicUrl(post.getThumbnailKey()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

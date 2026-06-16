package org.streamhub.api.v1.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.v1.post.dto.PostSearchRequest;
import org.streamhub.api.v1.post.entity.Post;
import org.streamhub.api.v1.post.entity.PostStatus;
import org.streamhub.api.v1.post.mapper.PostMapper;
import org.streamhub.api.v1.post.repository.PostRepository;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostRepository postRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private PostService postService;

    private Post post(PostStatus status) {
        Post p = Post.builder().title("공지").body("본문").status(status).build();
        ReflectionTestUtils.setField(p, "id", 1L);
        return p;
    }

    @Test
    void listPublished_alwaysForcesPublishedStatus() {
        when(postMapper.selectList(isNull(), eq("PUBLISHED"), anyInt(), anyInt())).thenReturn(List.of());
        when(postMapper.countList(isNull(), eq("PUBLISHED"))).thenReturn(0L);

        postService.listPublished(new PostSearchRequest(0, 10, null));

        // The PUBLISHED filter must be non-negotiable so drafts never leak to the public site.
        verify(postMapper).selectList(isNull(), eq("PUBLISHED"), eq(0), eq(10));
        verify(postMapper).countList(isNull(), eq("PUBLISHED"));
    }

    @Test
    void getPublishedDetail_published_returnsDetail() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post(PostStatus.PUBLISHED)));

        assertThat(postService.getPublishedDetail(1L).title()).isEqualTo("공지");
    }

    @Test
    void getPublishedDetail_draft_throwsNotFound() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post(PostStatus.DRAFT)));

        assertThatThrownBy(() -> postService.getPublishedDetail(1L))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void getPublishedDetail_missing_throwsNotFound() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPublishedDetail(99L))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NOT_FOUND);
    }
}

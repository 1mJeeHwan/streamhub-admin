package org.streamhub.api.v1.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.community.dto.CommunityPostDto;
import org.streamhub.api.v1.community.dto.CommunityPostSaveRequest;
import org.streamhub.api.v1.community.entity.CommunityPost;
import org.streamhub.api.v1.community.repository.CommunityPostRepository;

/**
 * Unit tests for {@link CommunityPostService} authoring (글 관리): create persists the requested
 * fields and logs the action; update mutates editorial fields while preserving counters, and
 * surfaces {@code NOT_FOUND} for a missing post.
 */
@ExtendWith(MockitoExtension.class)
class CommunityPostServiceTest {

    @Mock
    private CommunityPostRepository postRepository;
    @Mock
    private ActionLogPublisher actionLogPublisher;

    @InjectMocks
    private CommunityPostService postService;

    private CommunityPostSaveRequest request(Long boardId, String title) {
        return new CommunityPostSaveRequest(boardId, "자유", title, "본문입니다", "관리자", "N");
    }

    @Test
    void create_persistsFieldsAndLogsAction() {
        when(postRepository.save(any(CommunityPost.class))).thenAnswer(inv -> inv.getArgument(0));

        CommunityPostDto result = postService.create(request(7L, "새 글"));

        ArgumentCaptor<CommunityPost> captor = ArgumentCaptor.forClass(CommunityPost.class);
        verify(postRepository).save(captor.capture());
        CommunityPost saved = captor.getValue();
        assertThat(saved.getBoardId()).isEqualTo(7L);
        assertThat(saved.getTitle()).isEqualTo("새 글");
        assertThat(saved.getSecretYn()).isEqualTo("N");
        assertThat(saved.getRecommendCount()).isZero();
        assertThat(saved.getViewCount()).isZero();
        assertThat(result.getTitle()).isEqualTo("새 글");
        verify(actionLogPublisher).publish(eq("COMMUNITY_POST_CREATE"), eq("COMMUNITY_POST"), any(), eq("새 글"));
    }

    @Test
    void update_changesEditorialFieldsButKeepsCounters() {
        CommunityPost existing = CommunityPost.builder()
                .boardId(1L).category("공지").title("이전 제목").content("이전 본문")
                .writerName("관리자").secretYn("N").recommendCount(5).viewCount(42)
                .build();
        when(postRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(postRepository.saveAndFlush(any(CommunityPost.class))).thenAnswer(inv -> inv.getArgument(0));

        CommunityPostDto result = postService.update(3L,
                new CommunityPostSaveRequest(2L, "자유", "수정 제목", "수정 본문", "운영자", "Y"));

        assertThat(result.getBoardId()).isEqualTo(2L);
        assertThat(result.getTitle()).isEqualTo("수정 제목");
        assertThat(result.getSecretYn()).isEqualTo("Y");
        assertThat(existing.getRecommendCount()).isEqualTo(5);
        assertThat(existing.getViewCount()).isEqualTo(42);
        verify(actionLogPublisher).publish(eq("COMMUNITY_POST_UPDATE"), eq("COMMUNITY_POST"), eq("3"), eq("수정 제목"));
    }

    @Test
    void update_missingPost_isNotFound() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.update(99L, request(1L, "x")))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NOT_FOUND);
    }
}

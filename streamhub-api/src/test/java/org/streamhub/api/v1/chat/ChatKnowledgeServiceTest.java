package org.streamhub.api.v1.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.streamhub.api.v1.chat.entity.ChatKnowledge;
import org.streamhub.api.v1.chat.repository.ChatKnowledgeRepository;

/**
 * Unit tests for the chat-provider helpers: keyword matching ({@link ChatKnowledgeService#findAnswer})
 * and the LLM prompt block ({@link ChatKnowledgeService#promptBlock}).
 */
@ExtendWith(MockitoExtension.class)
class ChatKnowledgeServiceTest {

    @Mock private ChatKnowledgeRepository repository;
    @InjectMocks private ChatKnowledgeService service;

    private static ChatKnowledge entry(String question, String keywords, String answer) {
        return ChatKnowledge.builder()
                .question(question).keywords(keywords).answer(answer).enabled(true).sortOrder(0).build();
    }

    @Test
    void findAnswer_matchesAnyKeywordToken_caseInsensitive() {
        when(repository.findByEnabledTrueOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(entry("배송비", "배송비 배송료", "기본 3,000원입니다.")));

        assertThat(service.findAnswer("배송료 얼마예요?")).contains("기본 3,000원입니다.");
        assertThat(service.findAnswer("교환 되나요?")).isEmpty();
    }

    @Test
    void promptBlock_listsActiveEntries_andIsEmptyWhenNone() {
        when(repository.findByEnabledTrueOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(entry("환불", "환불", "7일 이내 가능합니다.")));
        assertThat(service.promptBlock()).contains("환불").contains("7일 이내 가능합니다.");
    }
}

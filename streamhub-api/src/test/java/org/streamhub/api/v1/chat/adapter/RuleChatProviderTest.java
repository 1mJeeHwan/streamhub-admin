package org.streamhub.api.v1.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.v1.chat.ChatKnowledgeService;
import org.streamhub.api.v1.chat.entity.ChatIntent;
import org.streamhub.api.v1.chat.entity.ChatRole;
import org.streamhub.api.v1.chat.feature.FeatureCatalogService;
import org.streamhub.api.v1.chat.mapper.ChatMapper;
import org.streamhub.api.v1.content.ContentService;

/**
 * Rule provider behaviour for the feature-guide path: a "어떤 기능?" question is answered from the
 * catalog with feature-recommendation quick replies, the stateless rule provider ignores the
 * conversation history it now receives, and the feature catalog acts as a safety net so a question
 * that names a real feature never dead-ends as a fallback or a failed content search.
 */
class RuleChatProviderTest {

    private final ChatMapper chatMapper = mock(ChatMapper.class);
    private final FeatureCatalogService catalog = new FeatureCatalogService(new ObjectMapper());
    private final ContentService contentService = mock(ContentService.class);
    private final ChatToolExecutor toolExecutor =
            new ChatToolExecutor(catalog, chatMapper, contentService);
    private final ChatKnowledgeService knowledgeService = mock(ChatKnowledgeService.class);
    private final RuleChatProvider provider =
            new RuleChatProvider(new IntentClassifier(), chatMapper, toolExecutor, knowledgeService);

    {
        // No admin-taught match by default → fall through to the normal rule path.
        when(knowledgeService.findAnswer(anyString())).thenReturn(Optional.empty());
        // Content search finds nothing (no DB) → exercises the feature-catalog safety net.
        @SuppressWarnings("unchecked")
        ResInfinityList<org.streamhub.api.v1.content.dto.ContentListItem> empty =
                mock(ResInfinityList.class);
        when(empty.getContents()).thenReturn(List.of());
        when(contentService.listPublic(any())).thenReturn(empty);
    }

    @Test
    void featureQuestion_answersFromCatalog_withRecommendationQuickReplies() {
        ChatReply reply = provider.reply("교회 어떤 기능이 있어?", List.of());

        assertThat(reply.intent()).isEqualTo(ChatIntent.FEATURE_GUIDE);
        assertThat(reply.text()).contains("교회");
        assertThat(reply.quickReplies()).isNotEmpty();
    }

    @Test
    void ignoresHistory_sameReplyWithOrWithoutContext() {
        ChatReply noHistory = provider.reply("교회 어떤 기능이 있어?", List.of());
        ChatReply withHistory = provider.reply("교회 어떤 기능이 있어?",
                List.of(new ChatTurn(ChatRole.USER, "안녕"), new ChatTurn(ChatRole.BOT, "안녕하세요")));

        assertThat(withHistory.intent()).isEqualTo(noHistory.intent());
        assertThat(withHistory.text()).isEqualTo(noHistory.text());
    }

    /**
     * The reported bug: questions that name a real feature but carry no intent keyword
     * ("찜한 곡 내 재생목록") or are mis-routed to content search ("통합검색 어떻게 해?") used to
     * dead-end. The catalog safety net must answer them instead of a fallback / "찾지 못함".
     */
    @Test
    void featureNamingQuestions_neverDeadEnd() {
        List<String> questions = List.of(
                "찜한 곡 내 재생목록",       // no intent marker → was FALLBACK
                "내 시청 기록 이어보기",      // no intent marker → was FALLBACK
                "통합검색 어떻게 해?",        // "검색" → content search that finds nothing
                "마이페이지 뭐가 있어?",      // overview
                "교회찾기 기능");            // must hit churches, not a wrong feature
        for (String q : questions) {
            ChatReply reply = provider.reply(q, List.of());
            assertThat(reply.intent())
                    .as("\"%s\" must not fall back", q)
                    .isNotEqualTo(ChatIntent.FALLBACK);
            assertThat(reply.text())
                    .as("\"%s\" must not answer '찾지 못함'", q)
                    .doesNotContain("찾지 못");
        }
    }
}

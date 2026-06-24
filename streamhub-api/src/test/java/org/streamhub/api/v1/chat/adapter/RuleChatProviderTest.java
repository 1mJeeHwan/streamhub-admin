package org.streamhub.api.v1.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.streamhub.api.v1.chat.ChatKnowledgeService;
import org.streamhub.api.v1.chat.entity.ChatIntent;
import org.streamhub.api.v1.chat.entity.ChatRole;
import org.streamhub.api.v1.chat.feature.FeatureCatalogService;
import org.streamhub.api.v1.chat.mapper.ChatMapper;

/**
 * Rule provider behaviour for the feature-guide path: a "어떤 기능?" question is answered from the
 * catalog with feature-recommendation quick replies, and the stateless rule provider ignores the
 * conversation history it now receives.
 */
class RuleChatProviderTest {

    private final ChatMapper chatMapper = mock(ChatMapper.class);
    private final FeatureCatalogService catalog = new FeatureCatalogService(new ObjectMapper());
    private final ChatToolExecutor toolExecutor = new ChatToolExecutor(catalog, chatMapper);
    private final ChatKnowledgeService knowledgeService = mock(ChatKnowledgeService.class);
    private final RuleChatProvider provider =
            new RuleChatProvider(new IntentClassifier(), chatMapper, toolExecutor, knowledgeService);

    {
        // No admin-taught match by default → fall through to the normal rule path.
        when(knowledgeService.findAnswer(anyString())).thenReturn(Optional.empty());
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
}

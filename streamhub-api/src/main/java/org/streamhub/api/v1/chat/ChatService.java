package org.streamhub.api.v1.chat;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.chat.adapter.ChatProvider;
import org.streamhub.api.v1.chat.adapter.ChatProviderRouter;
import org.streamhub.api.v1.chat.adapter.ChatReply;
import org.streamhub.api.v1.chat.adapter.ChatTurn;
import org.streamhub.api.v1.chat.dto.ChatHistoryItem;
import org.streamhub.api.v1.chat.dto.ChatReplyDto;
import org.streamhub.api.v1.chat.dto.ChatSendRequest;
import org.streamhub.api.v1.chat.entity.ChatIntent;
import org.streamhub.api.v1.chat.entity.ChatMessage;
import org.streamhub.api.v1.chat.entity.ChatRole;
import org.streamhub.api.v1.chat.entity.ChatSession;
import org.streamhub.api.v1.chat.entity.ChatUnanswered;
import org.streamhub.api.v1.chat.repository.ChatMessageRepository;
import org.streamhub.api.v1.chat.repository.ChatSessionRepository;
import org.streamhub.api.v1.chat.repository.ChatUnansweredRepository;

/**
 * Chatbot orchestration (C5): resolves/creates the session, loads the recent conversation, persists
 * the USER turn, delegates to the configured {@link ChatProvider} (rule by default; Gemini when
 * {@code app.chat.provider=llm}), persists the BOT turn with its intent, and returns the reply.
 *
 * <p>The last {@value #MAX_HISTORY_TURNS} stored turns are passed to the provider as context, so the
 * LLM provider supports multi-turn follow-ups; the rule provider ignores them. {@code testMode} is
 * true only for the rule provider, so the widget can label a real-LLM reply honestly.
 */
@Service
public class ChatService {

    /** Max prior turns fed back to the provider as context (bounds LLM token use). */
    private static final int MAX_HISTORY_TURNS = 10;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatUnansweredRepository chatUnansweredRepository;
    private final ChatProviderRouter chatProviderRouter;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatUnansweredRepository chatUnansweredRepository,
            ChatProviderRouter chatProviderRouter) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatUnansweredRepository = chatUnansweredRepository;
        this.chatProviderRouter = chatProviderRouter;
    }

    /** Handles a user message: persists USER + BOT turns (with prior context) and returns the reply. */
    @Transactional
    public ChatReplyDto send(ChatSendRequest request) {
        ChatProvider provider = chatProviderRouter.resolve();
        ChatSession session = chatSessionRepository.findBySessionKey(request.sessionKey())
                .orElseGet(() -> chatSessionRepository.save(ChatSession.builder()
                        .sessionKey(request.sessionKey())
                        .provider(provider.code())
                        .build()));

        // Prior turns (before this message) become the provider's conversation context.
        List<ChatTurn> history = recentHistory(session.getId());

        chatMessageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(ChatRole.USER)
                .content(request.message())
                .build());

        ChatReply reply = provider.reply(request.message(), history);

        chatMessageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(ChatRole.BOT)
                .intent(reply.intent())
                .content(reply.text())
                .build());

        // Learning queue (A): a FALLBACK means the bot had no answer — collect the question so an
        // operator can review and turn it into knowledge.
        if (reply.intent() == ChatIntent.FALLBACK) {
            chatUnansweredRepository.save(ChatUnanswered.builder()
                    .question(request.message())
                    .sessionKey(session.getSessionKey())
                    .build());
        }

        boolean testMode = !"LLM".equals(provider.code());
        return ChatReplyDto.of(reply, testMode);
    }

    /** Loads the last {@value #MAX_HISTORY_TURNS} stored turns for a session (oldest first). */
    private List<ChatTurn> recentHistory(Long sessionId) {
        List<ChatMessage> messages =
                chatMessageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId);
        int from = Math.max(0, messages.size() - MAX_HISTORY_TURNS);
        return messages.subList(from, messages.size()).stream()
                .map(m -> new ChatTurn(m.getRole(), m.getContent()))
                .toList();
    }

    /** Returns the full message history for a session (oldest first). */
    @Transactional(readOnly = true)
    public List<ChatHistoryItem> history(String sessionKey) {
        ChatSession session = chatSessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(session.getId()).stream()
                .map(ChatHistoryItem::from)
                .toList();
    }
}

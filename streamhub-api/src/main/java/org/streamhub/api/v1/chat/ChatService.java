package org.streamhub.api.v1.chat;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.chat.adapter.ChatProvider;
import org.streamhub.api.v1.chat.adapter.ChatProviderRouter;
import org.streamhub.api.v1.chat.adapter.ChatReply;
import org.streamhub.api.v1.chat.dto.ChatHistoryItem;
import org.streamhub.api.v1.chat.dto.ChatReplyDto;
import org.streamhub.api.v1.chat.dto.ChatSendRequest;
import org.streamhub.api.v1.chat.entity.ChatMessage;
import org.streamhub.api.v1.chat.entity.ChatRole;
import org.streamhub.api.v1.chat.entity.ChatSession;
import org.streamhub.api.v1.chat.repository.ChatMessageRepository;
import org.streamhub.api.v1.chat.repository.ChatSessionRepository;

/**
 * Chatbot orchestration (C5): resolves/creates the session, persists the USER turn, delegates to
 * the configured {@link ChatProvider} (rule-based by default — <b>no LLM call</b>), persists the
 * BOT turn with its intent, and returns the reply. {@code testMode} is always true (데모 챗봇).
 *
 * <p>The reply is <b>stateless</b>: only the current message text is passed to the provider. The
 * stored history is for reload/admin review — it is <i>not</i> read back as conversation context,
 * and there is no escalation/hand-off flow. ({@code ChatSessionRow.unanswered} in the admin
 * console is merely "the last stored turn is from the USER", a derived display flag.)
 */
@Service
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatProviderRouter chatProviderRouter;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatProviderRouter chatProviderRouter) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatProviderRouter = chatProviderRouter;
    }

    /** Handles a user message: persists USER + BOT turns and returns the bot reply. */
    @Transactional
    public ChatReplyDto send(ChatSendRequest request) {
        ChatProvider provider = chatProviderRouter.resolve();
        ChatSession session = chatSessionRepository.findBySessionKey(request.sessionKey())
                .orElseGet(() -> chatSessionRepository.save(ChatSession.builder()
                        .sessionKey(request.sessionKey())
                        .provider(provider.code())
                        .build()));

        chatMessageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(ChatRole.USER)
                .content(request.message())
                .build());

        ChatReply reply = provider.reply(request.message());

        chatMessageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(ChatRole.BOT)
                .intent(reply.intent())
                .content(reply.text())
                .build());

        return ChatReplyDto.of(reply, true);
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

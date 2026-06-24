package org.streamhub.api.v1.chat.admin.dto;

import java.time.LocalDateTime;
import org.streamhub.api.v1.chat.entity.ChatUnanswered;

/**
 * An unanswered (FALLBACK) user question in the learning queue.
 *
 * @param id         row id
 * @param question   the user message the bot couldn't answer
 * @param sessionKey originating session (nullable)
 * @param createdAt  when it was asked
 */
public record ChatUnansweredDto(Long id, String question, String sessionKey,
                                LocalDateTime createdAt) {

    public static ChatUnansweredDto from(ChatUnanswered u) {
        return new ChatUnansweredDto(u.getId(), u.getQuestion(), u.getSessionKey(), u.getCreatedAt());
    }
}

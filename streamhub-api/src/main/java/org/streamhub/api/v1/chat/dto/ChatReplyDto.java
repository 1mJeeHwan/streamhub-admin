package org.streamhub.api.v1.chat.dto;

import java.util.List;
import org.streamhub.api.v1.chat.adapter.ChatReply;
import org.streamhub.api.v1.chat.entity.ChatIntent;

/**
 * Chat reply response (C5). Always carries {@code testMode} so the widget can show the
 * "데모 챗봇 · 룰베이스(실 LLM 미연동)" label (정직 표기, spec §7).
 *
 * @param text         bot reply text (rule-based; not LLM-generated)
 * @param intent       intent classified from this single message
 * @param quickReplies suggested follow-up buttons
 * @param testMode     always true — signals rule-based demo, not a real LLM auto-response
 */
public record ChatReplyDto(
        String text,
        ChatIntent intent,
        List<String> quickReplies,
        boolean testMode) {

    /** Maps an adapter {@link ChatReply} to the API DTO. */
    public static ChatReplyDto of(ChatReply reply, boolean testMode) {
        return new ChatReplyDto(reply.text(), reply.intent(), reply.quickReplies(), testMode);
    }
}

package org.streamhub.api.v1.chat.adapter;

import java.util.List;
import org.streamhub.api.v1.chat.entity.ChatIntent;

/**
 * A bot reply produced by a {@link ChatProvider} (C5 seam).
 *
 * @param text         the reply body
 * @param intent       the classified intent (recorded on the BOT message)
 * @param quickReplies suggested follow-up buttons (may be empty)
 * @param cards        rich-message cards (G) — product/content tiles with deep links (may be empty)
 */
public record ChatReply(String text, ChatIntent intent, List<String> quickReplies,
                        List<ChatCard> cards) {

    /** A reply with quick replies but no cards (back-compat constructor). */
    public ChatReply(String text, ChatIntent intent, List<String> quickReplies) {
        this(text, intent, quickReplies, List.of());
    }

    /** A reply with no quick-reply buttons and no cards. */
    public static ChatReply of(String text, ChatIntent intent) {
        return new ChatReply(text, intent, List.of(), List.of());
    }

    /** A reply with cards (and no quick replies). */
    public static ChatReply withCards(String text, ChatIntent intent, List<ChatCard> cards) {
        return new ChatReply(text, intent, List.of(), cards);
    }
}

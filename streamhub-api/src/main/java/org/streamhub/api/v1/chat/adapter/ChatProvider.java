package org.streamhub.api.v1.chat.adapter;

/**
 * Chatbot reply seam (C5). The default {@link RuleChatProvider} classifies intent with keyword
 * rules and answers from a static FAQ table + order/goods queries — <b>no external LLM call</b>.
 * {@link LlmChatProvider} implements the same interface but is a config-gated <b>stub</b> (it
 * throws); swapping to it would be a config change ({@code app.chat.provider=llm}), not a code
 * branch, but no working LLM provider ships today.
 *
 * <p>The seam is intentionally <b>stateless</b>: {@link #reply(String)} receives a single message
 * and no conversation history, so implementations carry no per-session context window.
 */
public interface ChatProvider {

    /** Provider code this implementation reports ({@code RULE} / {@code LLM}). */
    String code();

    /**
     * Produces a reply for a single user message. No prior turns are supplied — the reply is
     * derived from this message alone (stateless; no context window).
     *
     * @param message the raw user message
     * @return the bot reply (text + classified intent + quick replies)
     */
    ChatReply reply(String message);
}

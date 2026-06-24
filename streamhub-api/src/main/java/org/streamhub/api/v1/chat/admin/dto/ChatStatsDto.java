package org.streamhub.api.v1.chat.admin.dto;

import java.util.List;

/**
 * Chatbot analytics snapshot for the admin dashboard (F).
 *
 * @param totalSessions  total chat sessions
 * @param userQuestions  total USER messages
 * @param botReplies     total BOT replies
 * @param fallbackCount  BOT replies classified FALLBACK (couldn't answer)
 * @param fallbackRate   fallbackCount / botReplies, as a 0–100 percentage
 * @param openUnanswered open items in the learning queue
 * @param intents        BOT-reply counts by intent
 * @param providers      session counts by provider (RULE/LLM)
 * @param daily          session counts per day (recent window, oldest first)
 */
public record ChatStatsDto(
        long totalSessions,
        long userQuestions,
        long botReplies,
        long fallbackCount,
        double fallbackRate,
        long openUnanswered,
        List<CountItem> intents,
        List<CountItem> providers,
        List<DailyItem> daily) {

    /** A label→count pair (intent or provider). */
    public record CountItem(String label, long count) {
    }

    /** A day→count pair for the volume chart ({@code date} = {@code yyyy-MM-dd}). */
    public record DailyItem(String date, long count) {
    }
}

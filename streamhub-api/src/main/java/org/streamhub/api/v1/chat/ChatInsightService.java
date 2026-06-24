package org.streamhub.api.v1.chat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.chat.admin.dto.ChatStatsDto;
import org.streamhub.api.v1.chat.admin.dto.ChatStatsDto.CountItem;
import org.streamhub.api.v1.chat.admin.dto.ChatStatsDto.DailyItem;
import org.streamhub.api.v1.chat.admin.dto.ChatUnansweredDto;
import org.streamhub.api.v1.chat.entity.ChatIntent;
import org.streamhub.api.v1.chat.entity.ChatRole;
import org.streamhub.api.v1.chat.entity.ChatUnanswered;
import org.streamhub.api.v1.chat.repository.ChatMessageRepository;
import org.streamhub.api.v1.chat.repository.ChatSessionRepository;
import org.streamhub.api.v1.chat.repository.ChatUnansweredRepository;

/**
 * Chatbot operational insight: the learning queue (A — unanswered questions to review/resolve) and
 * the analytics snapshot (F — sessions, intent mix, fallback rate, daily volume). Read/aggregate
 * only; the bot itself records unanswered questions via {@link ChatService}.
 */
@Service
public class ChatInsightService {

    private static final int UNANSWERED_LIMIT = 100;
    private static final int DAILY_WINDOW_DAYS = 14;

    private final ChatUnansweredRepository unansweredRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;

    public ChatInsightService(ChatUnansweredRepository unansweredRepository,
                              ChatMessageRepository messageRepository,
                              ChatSessionRepository sessionRepository) {
        this.unansweredRepository = unansweredRepository;
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
    }

    // --- A: learning queue --------------------------------------------------

    @Transactional(readOnly = true)
    public List<ChatUnansweredDto> listUnanswered() {
        return unansweredRepository
                .findByResolvedFalseOrderByCreatedAtDesc(Limit.of(UNANSWERED_LIMIT)).stream()
                .map(ChatUnansweredDto::from)
                .toList();
    }

    @Transactional
    public void resolveUnanswered(Long id) {
        ChatUnanswered entry = unansweredRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        entry.resolve();
        unansweredRepository.save(entry);
    }

    // --- F: analytics -------------------------------------------------------

    @Transactional(readOnly = true)
    public ChatStatsDto stats() {
        long totalSessions = sessionRepository.count();
        long userQuestions = messageRepository.countByRole(ChatRole.USER);
        long botReplies = messageRepository.countByRole(ChatRole.BOT);

        long fallbackCount = 0;
        List<CountItem> intents = new java.util.ArrayList<>();
        for (Object[] row : messageRepository.countByIntent()) {
            ChatIntent intent = (ChatIntent) row[0];
            long count = ((Number) row[1]).longValue();
            if (intent == null) {
                continue;
            }
            intents.add(new CountItem(intent.name(), count));
            if (intent == ChatIntent.FALLBACK) {
                fallbackCount = count;
            }
        }
        double fallbackRate = botReplies > 0 ? (fallbackCount * 100.0) / botReplies : 0.0;

        List<CountItem> providers = sessionRepository.countByProvider().stream()
                .map(row -> new CountItem(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .toList();

        LocalDateTime since = LocalDate.now().minusDays(DAILY_WINDOW_DAYS - 1L).atStartOfDay();
        List<DailyItem> daily = sessionRepository.dailySince(since).stream()
                .map(row -> new DailyItem(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .toList();

        return new ChatStatsDto(totalSessions, userQuestions, botReplies, fallbackCount,
                Math.round(fallbackRate * 10.0) / 10.0, unansweredRepository.countByResolvedFalse(),
                intents, providers, daily);
    }
}

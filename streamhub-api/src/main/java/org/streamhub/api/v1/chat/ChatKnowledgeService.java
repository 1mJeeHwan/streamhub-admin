package org.streamhub.api.v1.chat;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.chat.admin.dto.ChatKnowledgeDto;
import org.streamhub.api.v1.chat.entity.ChatKnowledge;
import org.streamhub.api.v1.chat.repository.ChatKnowledgeRepository;

/**
 * Admin-managed chatbot knowledge (FAQ). CRUD for the admin console, plus two helpers the chat
 * providers use at answer time: {@link #findAnswer(String)} (rule provider — keyword match) and
 * {@link #promptBlock()} (LLM provider — inject active entries into the system prompt). This is how
 * an operator "teaches" the bot without a deploy.
 */
@Service
public class ChatKnowledgeService {

    /** Cap on entries injected into the LLM prompt, so a large table can't blow the context. */
    private static final int PROMPT_MAX_ENTRIES = 50;

    private final ChatKnowledgeRepository repository;

    public ChatKnowledgeService(ChatKnowledgeRepository repository) {
        this.repository = repository;
    }

    // --- Admin CRUD ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ChatKnowledgeDto> list() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(ChatKnowledgeDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatKnowledgeDto getDetail(Long id) {
        return ChatKnowledgeDto.from(find(id));
    }

    @Transactional
    public ChatKnowledgeDto create(ChatKnowledgeDto request) {
        ChatKnowledge saved = repository.save(ChatKnowledge.builder()
                .question(request.getQuestion())
                .keywords(request.getKeywords())
                .answer(request.getAnswer())
                .enabled(request.isEnabled())
                .sortOrder(request.getSortOrder())
                .build());
        return ChatKnowledgeDto.from(saved);
    }

    @Transactional
    public ChatKnowledgeDto update(Long id, ChatKnowledgeDto request) {
        ChatKnowledge entry = find(id);
        entry.update(request.getQuestion(), request.getKeywords(), request.getAnswer(),
                request.isEnabled(), request.getSortOrder());
        return ChatKnowledgeDto.from(repository.saveAndFlush(entry));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    // --- Chat-provider helpers ---------------------------------------------

    /**
     * Returns the answer of the first enabled entry whose any keyword token is contained in the
     * (lowercased) message, or empty when none match. Used by the rule provider.
     */
    @Transactional(readOnly = true)
    public Optional<String> findAnswer(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (ChatKnowledge entry : repository.findByEnabledTrueOrderBySortOrderAscIdAsc()) {
            for (String term : entry.getKeywords().toLowerCase(Locale.ROOT).split("[,\\s]+")) {
                if (!term.isBlank() && lower.contains(term)) {
                    return Optional.of(entry.getAnswer());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Renders active entries as a system-prompt block for the LLM provider (empty string when none).
     * The LLM treats these as authoritative admin-curated answers.
     */
    @Transactional(readOnly = true)
    public String promptBlock() {
        List<ChatKnowledge> active = repository.findByEnabledTrueOrderBySortOrderAscIdAsc();
        if (active.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n관리자 등록 지식(FAQ, 아래 내용을 우선 신뢰해 답한다):\n");
        active.stream().limit(PROMPT_MAX_ENTRIES).forEach(e ->
                sb.append("- ").append(e.getQuestion()).append(": ").append(e.getAnswer()).append('\n'));
        return sb.toString();
    }

    private ChatKnowledge find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
    }
}

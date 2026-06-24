package org.streamhub.api.v1.chat.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.chat.entity.ChatKnowledge;

/**
 * A chatbot knowledge (FAQ) row. Admin create/update input and list output. Validation mirrors the
 * entity column limits so bad input is a {@code 400} (via {@code @Valid}) not a raw DB {@code 500}.
 */
@Getter
@Setter
@NoArgsConstructor
public class ChatKnowledgeDto {

    private Long id;

    @NotBlank
    @Size(max = 200)
    private String question;

    @NotBlank
    @Size(max = 300)
    private String keywords;

    @NotBlank
    @Size(max = 1000)
    private String answer;

    private boolean enabled;

    @PositiveOrZero
    private int sortOrder;

    private LocalDateTime updatedAt;

    public static ChatKnowledgeDto from(ChatKnowledge k) {
        ChatKnowledgeDto dto = new ChatKnowledgeDto();
        dto.id = k.getId();
        dto.question = k.getQuestion();
        dto.keywords = k.getKeywords();
        dto.answer = k.getAnswer();
        dto.enabled = k.isEnabled();
        dto.sortOrder = k.getSortOrder();
        dto.updatedAt = k.getUpdatedAt();
        return dto;
    }
}

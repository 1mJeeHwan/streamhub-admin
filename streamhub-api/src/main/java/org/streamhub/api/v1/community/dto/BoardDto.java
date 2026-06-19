package org.streamhub.api.v1.community.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.community.entity.Board;

/**
 * A community board row. Used as both the admin create/update input and the list output. All
 * values are demo/fictional (PII guard). Mutable to match the project DTO style.
 */
@Getter
@Setter
@NoArgsConstructor
public class BoardDto {
    private Long id;

    @NotBlank(message = "게시판 코드는 필수입니다")
    @Size(max = 40, message = "게시판 코드는 40자 이하입니다")
    private String code;

    @NotBlank(message = "게시판명은 필수입니다")
    @Size(max = 120, message = "게시판명은 120자 이하입니다")
    private String name;

    @Min(value = 1, message = "읽기 권한 레벨은 1 이상이어야 합니다")
    @Max(value = 10, message = "읽기 권한 레벨은 10 이하여야 합니다")
    private int readLevel;

    @Min(value = 1, message = "쓰기 권한 레벨은 1 이상이어야 합니다")
    @Max(value = 10, message = "쓰기 권한 레벨은 10 이하여야 합니다")
    private int writeLevel;

    private String useYn;
    private int sortOrder;
    private LocalDateTime createdAt;

    /** Builds a DTO from a persisted board. */
    public static BoardDto from(Board board) {
        BoardDto dto = new BoardDto();
        dto.id = board.getId();
        dto.code = board.getCode();
        dto.name = board.getName();
        dto.readLevel = board.getReadLevel();
        dto.writeLevel = board.getWriteLevel();
        dto.useYn = board.getUseYn();
        dto.sortOrder = board.getSortOrder();
        dto.createdAt = board.getCreatedAt();
        return dto;
    }
}

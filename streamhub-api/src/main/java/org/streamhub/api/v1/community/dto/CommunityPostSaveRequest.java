package org.streamhub.api.v1.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for the community post authoring screen (글 관리). Used for both POST and
 * PUT; counters and {@code createdAt} are never client-supplied. All values are demo/fictional
 * (no real PII — PII guard).
 *
 * @param boardId    target board (required)
 * @param category   category label (optional, ≤40 chars)
 * @param title      post title (required, ≤200 chars)
 * @param content    post body (optional, ≤2000 chars)
 * @param writerName display author name (optional, ≤60 chars)
 * @param secretYn   {@code "Y"}/{@code "N"} secret flag; {@code null} defaults to {@code "N"}
 */
public record CommunityPostSaveRequest(
        @NotNull(message = "게시판은 필수입니다") Long boardId,
        @Size(max = 40, message = "카테고리는 40자 이하입니다") String category,
        @NotBlank(message = "제목은 필수입니다") @Size(max = 200, message = "제목은 200자 이하입니다") String title,
        @Size(max = 2000, message = "내용은 2000자 이하입니다") String content,
        @Size(max = 60, message = "작성자명은 60자 이하입니다") String writerName,
        @Pattern(regexp = "[YN]", message = "비밀글 여부는 Y 또는 N 입니다") String secretYn) {
}

package org.streamhub.api.v1.member.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * A set of member ids for bulk operations (approve / deny).
 */
public record IdListRequest(
        @NotEmpty(message = "대상을 선택하세요") List<Long> idList) {
}

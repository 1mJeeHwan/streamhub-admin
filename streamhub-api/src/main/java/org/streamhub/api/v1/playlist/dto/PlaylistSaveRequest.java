package org.streamhub.api.v1.playlist.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Create/update request for a playlist. {@code trackIds} is the full ordered track list (replace
 * semantics): the saved order matches the list order.
 *
 * @param title       playlist title (required)
 * @param description optional description
 * @param coverKey    optional cover image storage key
 * @param sortOrder   display order
 * @param useYn       {@code Y}/{@code N} visibility
 * @param trackIds    ordered track ids
 */
public record PlaylistSaveRequest(
        @NotBlank String title,
        String description,
        String coverKey,
        Integer sortOrder,
        String useYn,
        List<Long> trackIds) {
}

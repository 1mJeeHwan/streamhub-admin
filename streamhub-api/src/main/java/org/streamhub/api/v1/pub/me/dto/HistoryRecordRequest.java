package org.streamhub.api.v1.pub.me.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of {@code POST /pub/v1/me/history} — records one watch event. {@code watchSeconds} is the
 * elapsed playback at report time; omitted ({@code null}) means 0 (a bare "started watching" ping).
 *
 * @param contentId    the watched content
 * @param watchSeconds seconds watched so far (optional, default 0)
 */
public record HistoryRecordRequest(
        @NotNull Long contentId,
        @PositiveOrZero Integer watchSeconds) {
}

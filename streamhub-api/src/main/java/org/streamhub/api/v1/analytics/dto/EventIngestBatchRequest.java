package org.streamhub.api.v1.analytics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Wrapper for the public batch ingest endpoint ({@code POST /pub/v1/events/batch}). A single
 * unauthenticated request can otherwise carry an unbounded event list, turning one HTTP call into
 * an arbitrary number of DB inserts — an amplification vector that bypasses the per-request rate
 * limit. The {@link Size} cap bounds the work a single request can request, and the controller
 * additionally charges the limiter one token per event so a maxed-out batch is debited
 * proportionally.
 *
 * @param events the events to ingest (1–{@value #MAX_EVENTS}); individual elements are still parsed
 *               leniently by {@code AnalyticsService}
 */
public record EventIngestBatchRequest(
        @NotNull(message = "이벤트 목록은 필수입니다")
        @Size(min = 1, max = MAX_EVENTS, message = "한 번에 최대 " + MAX_EVENTS + "건까지 전송할 수 있습니다")
        @Valid List<EventIngestRequest> events) {

    /**
     * Maximum number of events accepted in one batch request. Kept equal to the rate limiter's
     * per-IP bucket capacity (60) so a maxed-out batch is charged exactly its work — a larger cap
     * would let one request do more inserts than tokens it is debited (cost is clamped to capacity).
     */
    public static final int MAX_EVENTS = 60;
}

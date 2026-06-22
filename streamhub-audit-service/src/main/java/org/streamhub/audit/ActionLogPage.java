package org.streamhub.audit;

import java.util.List;

/**
 * Paged audit-log response. Mirrors the monolith's {@code ResInfinityList} shape
 * ({@code contents}/{@code totalCount}/{@code totalPage}) so the caller can deserialize it directly.
 */
public record ActionLogPage(List<ActionLogView> contents, long totalCount, int totalPage) {
}

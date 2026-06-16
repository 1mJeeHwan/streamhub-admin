package org.streamhub.api.v1.content.dto;

import org.streamhub.api.v1.content.entity.ContentStatus;
import org.streamhub.api.v1.content.entity.ContentType;

/**
 * Content list search + pagination request. All filters optional.
 */
public record ContentSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String keyword,
        ContentType type,
        ContentStatus status,
        Long channelId) {

    public int pageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    public int offset() {
        int page = pageNumber == null || pageNumber < 0 ? 0 : pageNumber;
        return page * pageSizeOrDefault();
    }
}

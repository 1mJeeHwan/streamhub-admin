package org.streamhub.api.v1.post.dto;

/**
 * Public post list search + pagination request. Keyword optional.
 */
public record PostSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String keyword) {

    public int pageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    public int offset() {
        int page = pageNumber == null || pageNumber < 0 ? 0 : pageNumber;
        return page * pageSizeOrDefault();
    }
}

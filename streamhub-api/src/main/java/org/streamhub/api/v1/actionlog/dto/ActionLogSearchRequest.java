package org.streamhub.api.v1.actionlog.dto;

/**
 * Audit-log list search + pagination. Filters optional.
 *
 * @param action  exact action-code filter
 * @param keyword matched against adminName / detail / targetId (LIKE)
 */
public record ActionLogSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String action,
        String keyword) {

    public int pageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    public int offset() {
        int page = pageNumber == null || pageNumber < 0 ? 0 : pageNumber;
        return page * pageSizeOrDefault();
    }
}

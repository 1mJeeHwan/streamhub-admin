package org.streamhub.api.v1.member.dto;

import org.streamhub.api.v1.member.entity.UserStatus;

/**
 * Member list search + pagination request. All filters are optional.
 *
 * @param pageNumber zero-based page index
 * @param pageSize   rows per page
 * @param keyword    matched against name / email / phone (LIKE)
 * @param userStatus filter by lifecycle status
 * @param churchId   filter by church (SYSTEM only; ignored/overridden for CHURCH_MANAGER)
 * @param sortBy     client sort key (resolved against a server-side whitelist); nullable
 * @param sortDir    {@code asc}/{@code desc}; nullable
 */
public record MemberSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String keyword,
        UserStatus userStatus,
        Long churchId,
        String sortBy,
        String sortDir) {

    public int pageNumberOrDefault() {
        return pageNumber == null || pageNumber < 0 ? 0 : pageNumber;
    }

    public int pageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    public int offset() {
        return pageNumberOrDefault() * pageSizeOrDefault();
    }
}

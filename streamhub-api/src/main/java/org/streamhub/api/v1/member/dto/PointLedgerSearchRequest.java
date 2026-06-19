package org.streamhub.api.v1.member.dto;

/**
 * Point-ledger list search + pagination request. All filters are optional.
 *
 * @param pageNumber zero-based page index
 * @param pageSize   rows per page
 * @param keyword    matched against member name / email / ledger reason (LIKE)
 * @param memberId   restrict to a single member (nullable)
 * @param churchId   filter by church (SYSTEM only; ignored/overridden for CHURCH_MANAGER)
 * @param sortBy     client sort key (resolved against the service whitelist; nullable)
 * @param sortDir    {@code "asc"}/{@code "desc"} (nullable; defaults to DESC)
 */
public record PointLedgerSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String keyword,
        Long memberId,
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

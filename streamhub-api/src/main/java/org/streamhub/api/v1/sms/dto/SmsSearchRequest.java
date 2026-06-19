package org.streamhub.api.v1.sms.dto;

import java.time.LocalDate;
import org.streamhub.api.v1.sms.entity.SmsKind;

/**
 * SMS history search + pagination request (C6). All filters optional.
 *
 * @param pageNumber zero-based page index
 * @param pageSize   page size (default 10)
 * @param keyword    matches content / masked recipient number
 * @param kind       send-trigger filter
 * @param from       inclusive lower bound on {@code sent_at}
 * @param to         inclusive upper bound on {@code sent_at} (whole-day)
 * @param sortBy     list sort key (grid column field); resolved against a server-side whitelist
 * @param sortDir    {@code "asc"}/{@code "desc"} sort direction
 */
public record SmsSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String keyword,
        SmsKind kind,
        LocalDate from,
        LocalDate to,
        String sortBy,
        String sortDir) {

    public int pageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    public int offset() {
        int page = pageNumber == null || pageNumber < 0 ? 0 : pageNumber;
        return page * pageSizeOrDefault();
    }
}

package org.streamhub.api.v1.worship.dto;

import java.time.LocalDate;
import org.streamhub.api.v1.worship.entity.RegistrationStatus;

/**
 * Worship registration list search + pagination request. All filters optional.
 *
 * <p>{@code searchField} selects which column {@code keyword} matches:
 * {@code name} / {@code phone} / {@code regNo}.
 */
public record WorshipSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String searchField,
        String keyword,
        RegistrationStatus status,
        Long churchId,
        LocalDate fromDate,
        LocalDate toDate,
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

package org.streamhub.api.v1.donation.dto;

import java.time.LocalDateTime;
import org.streamhub.api.v1.donation.entity.DonationStatus;
import org.streamhub.api.v1.donation.entity.DonationType;

/**
 * Donation list search + pagination request. All filters optional.
 *
 * @param churchId filter by church (SYSTEM only; ignored/overridden for CHURCH_MANAGER)
 */
public record DonationSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String keyword,
        DonationType type,
        DonationStatus status,
        LocalDateTime from,
        LocalDateTime to,
        Long churchId) {

    public int pageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    public int offset() {
        int page = pageNumber == null || pageNumber < 0 ? 0 : pageNumber;
        return page * pageSizeOrDefault();
    }
}

package org.streamhub.api.v1.donation.dto;

import org.streamhub.api.v1.donation.entity.SubscriptionStatus;

/**
 * Subscription list search + pagination request. All filters optional.
 *
 * @param churchId filter by church (SYSTEM only; ignored/overridden for CHURCH_MANAGER)
 */
public record SubscriptionSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String keyword,
        SubscriptionStatus status,
        Long planId,
        Long churchId) {

    public int pageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    public int offset() {
        int page = pageNumber == null || pageNumber < 0 ? 0 : pageNumber;
        return page * pageSizeOrDefault();
    }
}

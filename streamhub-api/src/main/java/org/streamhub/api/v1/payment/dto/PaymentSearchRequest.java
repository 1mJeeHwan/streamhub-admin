package org.streamhub.api.v1.payment.dto;

import java.time.LocalDate;
import org.streamhub.api.v1.order.entity.ReceiptKind;

/**
 * Payment-history list search + pagination request. All filters optional.
 *
 * <p>{@code searchField} selects which column {@code keyword} matches:
 * {@code orderNo} / {@code memberName} / {@code txnId}.
 *
 * <p>{@code churchId} is honored only for SYSTEM operators; CHURCH_MANAGER operators are pinned
 * to their own church by the service regardless of this value.
 */
public record PaymentSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String searchField,
        String keyword,
        ReceiptKind kind,
        String method,
        String provider,
        Long churchId,
        LocalDate fromDate,
        LocalDate toDate) {

    public int pageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    public int offset() {
        int page = pageNumber == null || pageNumber < 0 ? 0 : pageNumber;
        return page * pageSizeOrDefault();
    }
}

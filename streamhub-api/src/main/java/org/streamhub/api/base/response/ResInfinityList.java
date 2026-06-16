package org.streamhub.api.base.response;

import java.util.List;
import lombok.Getter;

/**
 * Paginated list payload returned by list endpoints.
 *
 * @param <T> element type
 */
@Getter
public class ResInfinityList<T> {

    private final List<T> contents;
    private final long totalCount;
    private final int totalPage;

    private ResInfinityList(List<T> contents, long totalCount, int totalPage) {
        this.contents = contents;
        this.totalCount = totalCount;
        this.totalPage = totalPage;
    }

    /**
     * Builds a page payload, computing the total page count from the requested page size.
     *
     * @param contents   rows for the current page
     * @param totalCount total number of matching rows
     * @param pageSize   page size used by the query (must be &gt; 0)
     */
    public static <T> ResInfinityList<T> of(List<T> contents, long totalCount, int pageSize) {
        int totalPage = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
        return new ResInfinityList<>(contents, totalCount, totalPage);
    }
}

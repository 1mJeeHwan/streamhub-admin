package org.streamhub.api.v1.album.dto;

import org.streamhub.api.v1.album.entity.AlbumGenre;
import org.streamhub.api.v1.album.entity.AlbumStatus;

/**
 * Album list search + pagination request. All filters optional. Mirrors
 * {@code GoodsSearchRequest} (record + offset helpers), with a 12-item default page
 * suited to a cover grid.
 */
public record AlbumSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String keyword,
        AlbumGenre genre,
        AlbumStatus status,
        String sortBy,
        String sortDir) {

    public int pageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 12 : pageSize;
    }

    public int offset() {
        int page = pageNumber == null || pageNumber < 0 ? 0 : pageNumber;
        return page * pageSizeOrDefault();
    }
}

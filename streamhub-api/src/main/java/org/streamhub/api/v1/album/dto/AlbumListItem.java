package org.streamhub.api.v1.album.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.album.entity.AlbumGenre;
import org.streamhub.api.v1.album.entity.AlbumStatus;

/** One row of the album list. {@code coverUrl}/{@code price} filled by the service. */
@Getter
@Setter
@NoArgsConstructor
public class AlbumListItem {
    private Long id;
    private String title;
    private String artist;
    private AlbumGenre genre;
    private AlbumStatus status;
    private String coverKey;
    private String coverUrl; // filled by the service from coverKey
    private Integer trackCount;
    private Long goodsItemId;
    private Long price; // filled by the service from the bridge GOODS_ITEM
    private Long viewCount;
    private LocalDate releaseDate;
    private LocalDateTime createdAt;
}

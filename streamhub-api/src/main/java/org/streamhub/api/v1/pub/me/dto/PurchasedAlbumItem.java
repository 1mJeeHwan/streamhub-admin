package org.streamhub.api.v1.pub.me.dto;

import java.time.LocalDateTime;

/**
 * One row of the member's purchased albums ("구매 음반").
 *
 * @param albumId     the album
 * @param title       album title
 * @param artist      album artist
 * @param coverUrl    public album cover URL ({@code null} if none)
 * @param purchasedAt when it was purchased (earliest paid order)
 */
public record PurchasedAlbumItem(
        Long albumId,
        String title,
        String artist,
        String coverUrl,
        LocalDateTime purchasedAt) {
}

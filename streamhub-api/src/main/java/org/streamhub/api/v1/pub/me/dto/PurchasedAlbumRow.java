package org.streamhub.api.v1.pub.me.dto;

import java.time.LocalDateTime;

/**
 * Internal projection of a purchased album carrying the raw cover <em>key</em>. The service resolves
 * {@code coverKey} to a public URL before exposing it as a {@link PurchasedAlbumItem}.
 *
 * @param albumId     the album
 * @param title       album title
 * @param artist      album artist
 * @param coverKey    album cover storage key
 * @param purchasedAt earliest paid-order time for this album
 */
public record PurchasedAlbumRow(
        Long albumId,
        String title,
        String artist,
        String coverKey,
        LocalDateTime purchasedAt) {
}

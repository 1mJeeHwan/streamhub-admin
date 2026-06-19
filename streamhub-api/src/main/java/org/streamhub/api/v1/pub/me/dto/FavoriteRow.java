package org.streamhub.api.v1.pub.me.dto;

/**
 * Internal projection of a member favorite joined to track + album, carrying the raw cover
 * <em>key</em>. The service resolves {@code coverKey} to a public URL before exposing it as a
 * {@link FavoriteItem} (DTOs never leak storage keys).
 *
 * @param trackId      favorited track
 * @param albumId      its album
 * @param trackTitle   track title
 * @param albumTitle   album title
 * @param artist       album artist
 * @param coverKey     album cover storage key
 * @param hasFullTrack whether the encrypted full track is playable
 */
public record FavoriteRow(
        Long trackId,
        Long albumId,
        String trackTitle,
        String albumTitle,
        String artist,
        String coverKey,
        boolean hasFullTrack) {
}

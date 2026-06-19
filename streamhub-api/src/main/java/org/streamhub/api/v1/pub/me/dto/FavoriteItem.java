package org.streamhub.api.v1.pub.me.dto;

/**
 * One row of the member's playlist favorites ("재생목록 찜"), track + album joined.
 *
 * @param trackId      favorited track
 * @param albumId      its album
 * @param trackTitle   track title
 * @param albumTitle   album title
 * @param artist       album artist
 * @param coverUrl     public album cover URL ({@code null} if none)
 * @param hasFullTrack whether the encrypted full track is playable
 */
public record FavoriteItem(
        Long trackId,
        Long albumId,
        String trackTitle,
        String albumTitle,
        String artist,
        String coverUrl,
        boolean hasFullTrack) {
}

package org.streamhub.api.v1.pub.me.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /pub/v1/me/favorites} — adds a track to the member's playlist favorites.
 *
 * @param trackId the track to favorite
 */
public record FavoriteAddRequest(@NotNull Long trackId) {
}

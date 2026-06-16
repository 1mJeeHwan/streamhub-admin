package org.streamhub.api.v1.pub.dto;

import java.util.List;
import org.streamhub.api.v1.content.dto.ContentListItem;
import org.streamhub.api.v1.post.dto.PostListItem;

/** Home bundle for the public site: latest videos, musics, and posts in one call. */
public record PublicHomeResponse(
        List<ContentListItem> videos,
        List<ContentListItem> musics,
        List<PostListItem> posts) {
}

package org.streamhub.api.v1.banner.entity;

/**
 * What a banner links to. For content types the banner stores a {@code linkRefId} (the content/post
 * id) and the public response resolves it to an internal path (e.g. {@code /video/{id}}); for
 * {@link #URL} the raw {@code linkUrl} is used as-is. A null type = legacy banner (raw {@code linkUrl}).
 */
public enum BannerLinkType {
    VIDEO,
    MUSIC,
    POST,
    URL
}

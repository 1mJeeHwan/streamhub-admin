package org.streamhub.api.v1.banner.entity;

/**
 * Content-tab target of a promo banner shown on the user site's list pages.
 * {@code VIDEO}/{@code SOUND} pin a banner to that tab; {@code ALL} shows it on every content tab.
 * A {@code null} target marks a non-tab banner (e.g. legacy main/side image banners). Stored via
 * {@code @Enumerated(STRING)}.
 */
public enum BannerTarget {
    VIDEO,
    SOUND,
    ALL
}

package org.streamhub.api.v1.banner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A promotional banner shown on the front (main slots, side rail, popup). Display is gated by
 * {@code useYn} and the {@code startAt}/{@code endAt} window. All values are demo/fictional
 * (image URLs are placeholders — no real assets).
 */
@Entity
@Table(name = "BANNER", indexes = {
        @Index(name = "idx_banner_position", columnList = "position"),
        @Index(name = "idx_banner_use", columnList = "use_yn")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "subtitle", length = 255)
    private String subtitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 20)
    private BannerPosition position;

    @Enumerated(EnumType.STRING)
    @Column(name = "device", nullable = false, length = 10)
    private BannerDevice device;

    /** Content-tab target for user-site list pages (null = not a tab banner). */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private BannerTarget targetType;

    /** Optional. Blank/null renders as a gradient text promo on the user site. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /**
     * Structured link target (nullable for legacy/URL banners). VIDEO/MUSIC/POST pair with
     * {@link #linkRefId}; the public response resolves them to an internal path. Null = legacy
     * banner that uses {@link #linkUrl} directly.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", length = 20)
    private BannerLinkType linkType;

    /** Referenced content/post id for VIDEO/MUSIC/POST link types. */
    @Column(name = "link_ref_id")
    private Long linkRefId;

    /** Title of the selected content captured at edit time, for admin-form display. */
    @Column(name = "link_label", length = 200)
    private String linkLabel;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "use_yn", nullable = false, length = 1)
    private String useYn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Banner(String title, String subtitle, BannerPosition position, BannerDevice device,
                   BannerTarget targetType, String imageUrl, String linkUrl, BannerLinkType linkType,
                   Long linkRefId, String linkLabel, LocalDateTime startAt,
                   LocalDateTime endAt, int sortOrder, String useYn, LocalDateTime createdAt) {
        this.title = title;
        this.subtitle = subtitle;
        this.position = position;
        this.device = device;
        this.targetType = targetType;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.linkType = linkType;
        this.linkRefId = linkRefId;
        this.linkLabel = linkLabel;
        this.startAt = startAt;
        this.endAt = endAt;
        this.sortOrder = sortOrder;
        this.useYn = useYn != null ? useYn : "Y";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /** Updates editable fields. */
    public void update(String title, String subtitle, BannerPosition position, BannerDevice device,
                       BannerTarget targetType, String imageUrl, String linkUrl, BannerLinkType linkType,
                       Long linkRefId, String linkLabel, LocalDateTime startAt,
                       LocalDateTime endAt, int sortOrder, String useYn) {
        this.title = title;
        this.subtitle = subtitle;
        this.position = position;
        this.device = device;
        this.targetType = targetType;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.linkType = linkType;
        this.linkRefId = linkRefId;
        this.linkLabel = linkLabel;
        this.startAt = startAt;
        this.endAt = endAt;
        this.sortOrder = sortOrder;
        this.useYn = useYn;
    }

    /** Updates only the display order (drag-to-reorder). */
    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}

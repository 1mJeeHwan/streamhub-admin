package org.streamhub.api.v1.campaign.entity;

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
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;

/**
 * A campaign/event (C-campaign): special donations, new-release pre-orders, events and
 * seasonal promotions. Optionally links to goods and carries a fundraising target. All
 * values are demo/fictional (no real business data — PII guard).
 *
 * <p><b>Demo scope (honesty note):</b> this is a campaign-<em>definition</em> and lifecycle
 * catalog only. There is <b>no execution engine</b> — defining or activating a campaign does
 * not deliver anything to an audience and does not track funds raised. Real audience delivery
 * would require the SMS/push providers (which are demo-gated), and fund tracking would require
 * the donation/payment ledger; neither is wired here on purpose. {@link #targetAmount} is a
 * descriptive goal, not a live tally.
 *
 * <p>The lifecycle is a strict state machine owned by this entity: {@code DRAFT → ACTIVE → ENDED}
 * (a draft may also be abandoned directly to {@code ENDED}). {@code ENDED} is terminal
 * (absorbing). {@link #changeStatus(CampaignStatus)} enforces the legal transitions and rejects
 * illegal jumps with {@link ApiException}; the client {@code NEXT_STATUS} map is only a UX mirror.
 */
@Entity
@Table(name = "CAMPAIGN", indexes = {
        @Index(name = "idx_campaign_type", columnList = "type"),
        @Index(name = "idx_campaign_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign {

    /**
     * Authoritative campaign-status transition map. This is the single source of truth for
     * lifecycle legality; the frontend keeps a UX mirror only. Terminal states map to an empty
     * set (absorbing): once {@code ENDED}, no further transition is allowed.
     */
    private static final Map<CampaignStatus, Set<CampaignStatus>> TRANSITIONS = buildTransitions();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private CampaignType type;

    @Column(name = "description", length = 1000, columnDefinition = "TEXT")
    private String description;

    /** Banner image URL (demo picsum); nullable. */
    @Column(name = "banner_image_url", length = 300)
    private String bannerImageUrl;

    /** Comma-separated linked goods ids (e.g. {@code "1,2,3"}); nullable. */
    @Column(name = "linked_goods_ids", length = 300)
    private String linkedGoodsIds;

    /** Fundraising target amount for donation campaigns; nullable. */
    @Column(name = "target_amount")
    private Long targetAmount;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CampaignStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Campaign(String title, CampaignType type, String description, String bannerImageUrl,
                     String linkedGoodsIds, Long targetAmount, LocalDateTime startAt,
                     LocalDateTime endAt, CampaignStatus status, LocalDateTime createdAt) {
        this.title = title;
        this.type = type;
        this.description = description;
        this.bannerImageUrl = bannerImageUrl;
        this.linkedGoodsIds = linkedGoodsIds;
        this.targetAmount = targetAmount;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = status != null ? status : CampaignStatus.DRAFT;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /** Updates editable fields. */
    public void update(String title, CampaignType type, String description, String bannerImageUrl,
                       String linkedGoodsIds, Long targetAmount, LocalDateTime startAt,
                       LocalDateTime endAt, CampaignStatus status) {
        this.title = title;
        this.type = type;
        this.description = description;
        this.bannerImageUrl = bannerImageUrl;
        this.linkedGoodsIds = linkedGoodsIds;
        this.targetAmount = targetAmount;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = status;
    }

    /**
     * Transitions the campaign to a new lifecycle state, enforcing the legal state machine.
     * Terminal states are absorbing, so any transition out of {@code ENDED} is rejected.
     *
     * @param to the target status
     * @throws ApiException {@code INVALID_PARAMETER} if the {@code from → to} transition is illegal
     */
    public void changeStatus(CampaignStatus to) {
        if (to == null || !isLegalTransition(this.status, to)) {
            throw new ApiException(ResultCode.INVALID_PARAMETER,
                    "허용되지 않는 캠페인 상태 전이입니다: " + this.status + " → " + to);
        }
        this.status = to;
    }

    /**
     * Whether {@code from → to} is a legal campaign transition. Pure and static so the policy is
     * unit-testable without constructing or persisting an entity. A null {@code from} (never
     * persisted) permits only the initial {@code DRAFT}.
     */
    public static boolean isLegalTransition(CampaignStatus from, CampaignStatus to) {
        if (to == null) {
            return false;
        }
        if (from == null) {
            return to == CampaignStatus.DRAFT;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    private static Map<CampaignStatus, Set<CampaignStatus>> buildTransitions() {
        Map<CampaignStatus, Set<CampaignStatus>> map = new EnumMap<>(CampaignStatus.class);
        map.put(CampaignStatus.DRAFT, Set.of(CampaignStatus.ACTIVE, CampaignStatus.ENDED));
        map.put(CampaignStatus.ACTIVE, Set.of(CampaignStatus.ENDED));
        map.put(CampaignStatus.ENDED, Set.of());
        return map;
    }
}

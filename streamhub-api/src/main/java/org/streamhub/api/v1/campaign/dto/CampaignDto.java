package org.streamhub.api.v1.campaign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.campaign.entity.Campaign;
import org.streamhub.api.v1.campaign.entity.CampaignStatus;
import org.streamhub.api.v1.campaign.entity.CampaignType;

/**
 * A campaign/event row. Used as both the admin create/update input and the list/detail
 * output. All values are demo/fictional (PII guard). Mutable to match the project DTO style.
 *
 * <p>Bean Validation constraints below mirror the entity's column limits so bad input fails as a
 * {@code 400} (via {@code @Valid} on the controller) rather than as a raw DB {@code 500}.
 * Output-only / server-derived fields ({@code id}, {@code createdAt}) and the optional
 * {@code status} (defaults to {@code DRAFT} on create) carry no input constraints.
 */
@Getter
@Setter
@NoArgsConstructor
public class CampaignDto {
    private Long id;

    @NotBlank
    @Size(max = 150)
    private String title;

    @NotNull
    private CampaignType type;

    @Size(max = 1000)
    private String description;

    @Size(max = 300)
    private String bannerImageUrl;

    @Size(max = 300)
    private String linkedGoodsIds;

    @PositiveOrZero
    private Long targetAmount;

    @NotNull
    private LocalDateTime startAt;

    @NotNull
    private LocalDateTime endAt;

    private CampaignStatus status;
    private LocalDateTime createdAt;

    /** Builds a DTO from a persisted campaign. */
    public static CampaignDto from(Campaign campaign) {
        CampaignDto dto = new CampaignDto();
        dto.id = campaign.getId();
        dto.title = campaign.getTitle();
        dto.type = campaign.getType();
        dto.description = campaign.getDescription();
        dto.bannerImageUrl = campaign.getBannerImageUrl();
        dto.linkedGoodsIds = campaign.getLinkedGoodsIds();
        dto.targetAmount = campaign.getTargetAmount();
        dto.startAt = campaign.getStartAt();
        dto.endAt = campaign.getEndAt();
        dto.status = campaign.getStatus();
        dto.createdAt = campaign.getCreatedAt();
        return dto;
    }
}

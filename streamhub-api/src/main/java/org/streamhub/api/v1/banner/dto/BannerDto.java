package org.streamhub.api.v1.banner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.banner.entity.Banner;
import org.streamhub.api.v1.banner.entity.BannerDevice;
import org.streamhub.api.v1.banner.entity.BannerPosition;
import org.streamhub.api.v1.banner.entity.BannerTarget;

/**
 * A banner row. Used as both the admin create/update input and the list/detail output. All
 * values are demo/fictional. Mutable to match the project DTO style.
 *
 * <p>Bean Validation constraints below mirror the entity's column limits/nullability so bad input
 * fails as a {@code 400} (via {@code @Valid} on the controller) rather than as a raw DB {@code 500}.
 * The display window ({@code startAt}/{@code endAt}) is optional (nullable column), and
 * {@code useYn} defaults to {@code "Y"} when omitted, so they carry no {@code @NotNull}.
 */
@Getter
@Setter
@NoArgsConstructor
public class BannerDto {
    private Long id;

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 255)
    private String subtitle;

    @NotNull
    private BannerPosition position;

    @NotNull
    private BannerDevice device;

    /** Content-tab target for the user site (VIDEO/SOUND/ALL); null = not a tab banner. */
    private BannerTarget targetType;

    /** Optional. Blank renders as a gradient text promo on the user site. */
    @Size(max = 500)
    private String imageUrl;

    @Size(max = 500)
    private String linkUrl;

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    @PositiveOrZero
    private int sortOrder;

    @Size(max = 1)
    private String useYn;

    private LocalDateTime createdAt;

    /** Builds a DTO from a persisted banner. */
    public static BannerDto from(Banner banner) {
        BannerDto dto = new BannerDto();
        dto.id = banner.getId();
        dto.title = banner.getTitle();
        dto.subtitle = banner.getSubtitle();
        dto.position = banner.getPosition();
        dto.device = banner.getDevice();
        dto.targetType = banner.getTargetType();
        dto.imageUrl = banner.getImageUrl();
        dto.linkUrl = banner.getLinkUrl();
        dto.startAt = banner.getStartAt();
        dto.endAt = banner.getEndAt();
        dto.sortOrder = banner.getSortOrder();
        dto.useYn = banner.getUseYn();
        dto.createdAt = banner.getCreatedAt();
        return dto;
    }
}

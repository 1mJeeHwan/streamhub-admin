package org.streamhub.api.v1.album.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.album.entity.Track;

/**
 * A track row. Used both as a create/update input (dynamic rows) and as a detail
 * output (id/previewUrl populated). The {@code previewUrl} returned to clients is
 * resolved by the {@code MusicPreviewProvider} seam in the service. Mutable to match
 * the MyBatis/JPA mapping style ({@code GoodsOptionDto} pattern).
 */
@Getter
@Setter
@NoArgsConstructor
public class TrackDto {
    private Long id;
    private Integer trackNo;
    @NotBlank(message = "곡명을 입력하세요")
    private String title;
    private Integer durationSec;
    private String previewUrl;
    private Integer previewStartSec;
    private Integer previewLengthSec;
    /** True when an AES-128 encrypted full-track HLS stream exists (purchasers can play it). */
    private boolean hasFullTrack;
    /** True when a public (unencrypted) preview HLS stream exists; else the client uses previewUrl. */
    private boolean hasPreviewHls;

    /** Builds a detail DTO from a persisted track. {@code previewUrl} is set by the service. */
    public static TrackDto from(Track track) {
        TrackDto dto = new TrackDto();
        dto.id = track.getId();
        dto.trackNo = track.getTrackNo();
        dto.title = track.getTitle();
        dto.durationSec = track.getDurationSec();
        dto.previewUrl = track.getPreviewUrl();
        dto.previewStartSec = track.getPreviewStartSec();
        dto.previewLengthSec = track.getPreviewLengthSec();
        dto.hasFullTrack = track.isHasFullTrack();
        dto.hasPreviewHls = track.getPreviewHlsPrefix() != null;
        return dto;
    }
}

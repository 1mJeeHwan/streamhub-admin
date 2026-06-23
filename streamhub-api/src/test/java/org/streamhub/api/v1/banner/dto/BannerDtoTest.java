package org.streamhub.api.v1.banner.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.streamhub.api.v1.banner.entity.Banner;
import org.streamhub.api.v1.banner.entity.BannerDevice;
import org.streamhub.api.v1.banner.entity.BannerLinkType;
import org.streamhub.api.v1.banner.entity.BannerPosition;

/** Verifies the public link target resolution (content types → internal path, legacy/URL → raw url). */
class BannerDtoTest {

    private Banner.BannerBuilder base() {
        return Banner.builder()
                .title("t").position(BannerPosition.MAIN_TOP).device(BannerDevice.ALL).sortOrder(0);
    }

    @Test
    void contentTypes_resolveToInternalPath() {
        assertThat(BannerDto.from(base().linkType(BannerLinkType.VIDEO).linkRefId(7L).build()).getLinkUrl())
                .isEqualTo("/video/7");
        assertThat(BannerDto.from(base().linkType(BannerLinkType.MUSIC).linkRefId(3L).build()).getLinkUrl())
                .isEqualTo("/music/3");
        assertThat(BannerDto.from(base().linkType(BannerLinkType.POST).linkRefId(1L).build()).getLinkUrl())
                .isEqualTo("/posts/1");
    }

    @Test
    void urlType_usesRawLinkUrl() {
        Banner b = base().linkType(BannerLinkType.URL).linkUrl("https://example.com").build();
        assertThat(BannerDto.from(b).getLinkUrl()).isEqualTo("https://example.com");
    }

    @Test
    void legacyBanner_nullType_keepsRawLinkUrl() {
        // Backward compat: existing banners have no linkType — must keep their stored linkUrl.
        Banner b = base().linkUrl("/churches").build();
        assertThat(BannerDto.from(b).getLinkUrl()).isEqualTo("/churches");
    }
}

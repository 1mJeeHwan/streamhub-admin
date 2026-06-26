package org.streamhub.api.v1.pub;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.v1.content.ContentService;
import org.streamhub.api.v1.content.dto.ContentDetail;
import org.streamhub.api.v1.content.dto.PublicContentDetail;
import org.streamhub.api.v1.content.dto.ContentListItem;
import org.streamhub.api.v1.content.entity.ContentStatus;
import org.streamhub.api.v1.content.entity.ContentType;
import org.streamhub.api.v1.post.PostService;
import org.streamhub.api.v1.church.ChurchService;
import org.streamhub.api.v1.album.AlbumService;
import org.streamhub.api.v1.store.StoreService;
import org.streamhub.api.v1.banner.BannerService;
import org.streamhub.api.v1.announcement.AnnouncementService;
import org.streamhub.api.v1.analytics.PublicIngestRateLimiter;
import org.streamhub.api.base.util.ClientIpResolver;

/**
 * Web-layer test for the public API using a standalone MockMvc (no Spring context) — focuses on
 * routing, delegation, and the {@code ResultDTO} JSON contract without dragging in MyBatis/security.
 */
class PublicControllerTest {

    private final ContentService contentService = mock(ContentService.class);
    private final PostService postService = mock(PostService.class);
    private final ChurchService churchService = mock(ChurchService.class);
    private final AlbumService albumService = mock(AlbumService.class);
    private final StoreService storeService = mock(StoreService.class);
    private final BannerService bannerService = mock(BannerService.class);
    private final AnnouncementService announcementService = mock(AnnouncementService.class);
    private final PublicIngestRateLimiter rateLimiter = mock(PublicIngestRateLimiter.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                new PublicController(contentService, postService, churchService, albumService,
                        storeService, bannerService, announcementService, rateLimiter, clientIpResolver)).build();
    }

    private ContentListItem videoItem() {
        ContentListItem item = new ContentListItem();
        item.setId(1L);
        item.setTitle("주일 예배 실황");
        item.setType(ContentType.VIDEO);
        item.setStatus(ContentStatus.PUBLISHED);
        return item;
    }

    @Test
    void home_returnsVideosMusicsPostsBundle() throws Exception {
        when(contentService.listPublic(any()))
                .thenReturn(ResInfinityList.of(List.of(videoItem()), 1, 8));
        when(postService.listPublished(any()))
                .thenReturn(ResInfinityList.of(List.of(), 0, 8));

        mvc.perform(get("/pub/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("0000"))
                .andExpect(jsonPath("$.resultObject.videos").isArray())
                .andExpect(jsonPath("$.resultObject.videos[0].title").value("주일 예배 실황"))
                .andExpect(jsonPath("$.resultObject.musics").isArray())
                .andExpect(jsonPath("$.resultObject.posts").isArray());
    }

    @Test
    void contentDetail_delegatesToServiceAndWrapsResult() throws Exception {
        ContentDetail detail = new ContentDetail();
        detail.setId(7L);
        detail.setTitle("찬양 모음");
        detail.setType(ContentType.SOUND);
        detail.setStatus(ContentStatus.PUBLISHED);
        detail.setThumbnailKey("internal/key/7.jpg");
        detail.setViewCount(42L);
        when(contentService.getPublicDetail(7L)).thenReturn(PublicContentDetail.from(detail));

        mvc.perform(get("/pub/v1/contents/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultObject.id").value(7))
                .andExpect(jsonPath("$.resultObject.viewCount").value(42))
                // Internal fields must not be exposed on the public endpoint.
                .andExpect(jsonPath("$.resultObject.status").doesNotExist())
                .andExpect(jsonPath("$.resultObject.thumbnailKey").doesNotExist())
                .andExpect(jsonPath("$.resultObject.updatedAt").doesNotExist());
    }

    @Test
    void contents_returnsPagedList() throws Exception {
        when(contentService.listPublic(any()))
                .thenReturn(ResInfinityList.of(List.of(videoItem()), 1, 12));

        mvc.perform(get("/pub/v1/contents").param("type", "VIDEO").param("pageSize", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultObject.totalCount").value(1));
    }
}

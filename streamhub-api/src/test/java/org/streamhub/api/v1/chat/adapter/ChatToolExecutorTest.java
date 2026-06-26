package org.streamhub.api.v1.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.streamhub.api.v1.chat.dto.ChatGoodsRow;
import org.streamhub.api.v1.chat.dto.ChatOrderRow;
import org.streamhub.api.v1.chat.feature.FeatureCatalogService;
import org.streamhub.api.v1.chat.mapper.ChatMapper;
import org.streamhub.api.v1.order.entity.OrderStatus;

/**
 * Tests the shared chatbot tool layer: feature search/how-to come from the real catalog, order
 * lookup enforces both order number and name, and product search formats DB rows. The catalog is
 * the real classpath JSON; the DB mapper is mocked.
 */
class ChatToolExecutorTest {

    private final ChatMapper chatMapper = mock(ChatMapper.class);
    private final FeatureCatalogService catalog = new FeatureCatalogService(new ObjectMapper());
    private final ChatToolExecutor executor =
            new ChatToolExecutor(catalog, chatMapper, mock(org.streamhub.api.v1.content.ContentService.class));

    @Test
    void searchFeatures_knownKeyword_returnsHowTo() {
        String result = executor.searchFeatures("쿠폰");
        assertThat(result).contains("쿠폰").contains("사용법");
    }

    @Test
    void searchFeatures_unknown_saysNotFound() {
        assertThat(executor.searchFeatures("존재하지않는기능zzz")).contains("찾지 못");
    }

    @Test
    void getFeature_byId_returnsDetail() {
        assertThat(executor.getFeature("orders")).contains("주문").contains("사용법");
    }

    @Test
    void featureGuide_broadQuestion_returnsDomainOverview() {
        String overview = executor.featureGuide("어떤 기능이 있어?");
        assertThat(overview).contains("주요 기능").contains("굿즈샵");
    }

    @Test
    void featureGuide_specificKeyword_returnsHowTo() {
        String guide = executor.featureGuide("쿠폰");
        assertThat(guide).contains("쿠폰").contains("사용법");
    }

    @Test
    void featureOverview_listsUserFacingTitles_notAdminOnes() {
        String overview = executor.featureOverview();
        assertThat(overview).contains("후원·구독").contains("굿즈샵").contains("주문·배송 조회");
        // Admin-only features must not appear in the public overview.
        assertThat(overview).doesNotContain("통합 운영 대시보드").doesNotContain("회원 관리")
                .doesNotContain("감사 로그");
    }

    @Test
    void featureAnswers_exposeNoRouteOrPath() {
        // The chatbot must never surface an internal route/path. Check the how-to, search, overview
        // and per-id detail surfaces for any "경로:" label or a "/route" token.
        List<String> surfaces = List.of(
                executor.featureGuide("쿠폰"),
                executor.featureGuide("마이페이지"),
                executor.searchFeatures("주문"),
                executor.getFeature("goods"),
                executor.featureOverview());
        for (String text : surfaces) {
            assertThat(text).as("no '경로:' label").doesNotContain("경로:");
            assertThat(text).as("no URL path token in: %s", text).doesNotContainPattern("/[a-z]");
        }
    }

    @Test
    void maskTracking_hidesAllButLastFour() {
        assertThat(ChatToolExecutor.maskTracking("650000001432")).isEqualTo("********1432");
        assertThat(ChatToolExecutor.maskTracking("1432")).isEqualTo("****");
        assertThat(ChatToolExecutor.maskTracking("")).isEmpty();
        assertThat(ChatToolExecutor.maskTracking(null)).isNull();
    }

    @Test
    void lookupOrder_withoutOrderNo_asksForIt() {
        assertThat(executor.lookupOrder("주문 알려줘", "홍길동")).contains("주문번호");
    }

    @Test
    void lookupOrder_withoutName_asksForName() {
        assertThat(executor.lookupOrder("20240501-000123", "")).contains("주문자명");
    }

    @Test
    void lookupOrder_match_returnsStatus() {
        ChatOrderRow row = new ChatOrderRow();
        row.setOrderNo("20240501-000123");
        row.setStatus(OrderStatus.DONE);
        row.setTotal(30000L);
        when(chatMapper.selectOrderByNoAndName(eq("20240501-000123"), eq("홍길동"))).thenReturn(row);

        String result = executor.lookupOrder("주문 20240501-000123 입니다", "홍길동");
        assertThat(result).contains("20240501-000123").contains("DONE");
    }

    @Test
    void searchProducts_formatsRows() {
        ChatGoodsRow row = new ChatGoodsRow();
        row.setName("찬양 앨범");
        row.setPrice(15000L);
        row.setStock(5);
        when(chatMapper.selectGoodsByKeyword(eq("앨범"), anyInt())).thenReturn(List.of(row));

        assertThat(executor.searchProducts("앨범")).contains("찬양 앨범").contains("15000");
    }
}

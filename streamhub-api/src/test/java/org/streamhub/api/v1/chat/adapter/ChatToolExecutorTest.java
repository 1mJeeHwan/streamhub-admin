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
    private final ChatToolExecutor executor = new ChatToolExecutor(catalog, chatMapper);

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

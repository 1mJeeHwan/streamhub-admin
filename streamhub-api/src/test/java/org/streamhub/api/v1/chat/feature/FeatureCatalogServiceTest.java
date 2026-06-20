package org.streamhub.api.v1.chat.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Loads the real {@code feature-catalog.json} from the classpath and checks the keyword search the
 * chatbot relies on: known features are found, ids resolve, and an unknown query returns empty (so
 * the bot can honestly say a feature is not offered).
 */
class FeatureCatalogServiceTest {

    private final FeatureCatalogService service = new FeatureCatalogService(new ObjectMapper());

    @Test
    void loadsCatalogEntries() {
        assertThat(service.all()).isNotEmpty();
        assertThat(service.get("coupons")).isNotNull();
        assertThat(service.get("coupons").howTo()).isNotBlank();
    }

    @Test
    void search_findsByKeyword() {
        List<FeatureInfo> hits = service.search("쿠폰", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits).anyMatch(f -> f.id().equals("coupons"));
    }

    @Test
    void search_unknownKeyword_isEmpty() {
        assertThat(service.search("존재하지않는기능zzz", 5)).isEmpty();
    }

    @Test
    void search_blankQuery_returnsCappedList() {
        assertThat(service.search("  ", 3)).hasSize(3);
    }

    @Test
    void get_unknownId_isNull() {
        assertThat(service.get("no-such-feature")).isNull();
    }
}

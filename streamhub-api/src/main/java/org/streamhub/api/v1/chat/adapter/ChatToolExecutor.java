package org.streamhub.api.v1.chat.adapter;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.chat.dto.ChatGoodsRow;
import org.streamhub.api.v1.chat.dto.ChatOrderRow;
import org.streamhub.api.v1.chat.feature.FeatureCatalogService;
import org.streamhub.api.v1.chat.feature.FeatureInfo;
import org.streamhub.api.v1.chat.mapper.ChatMapper;

/**
 * Shared chatbot tool layer (C5) — the read-only "tools" both the rule provider and the LLM
 * provider invoke, so every reply is grounded in the same data (feature catalog + order/goods DB)
 * with no hallucination. Each method returns a compact, human-readable string: the rule provider
 * surfaces it directly, and the LLM provider feeds it back as a function-call result.
 *
 * <p>Order lookup deliberately requires both the order number and the orderer name (cross-member
 * leakage prevention), exactly as the original rule provider did.
 */
@Component
public class ChatToolExecutor {

    /** {@code YYYYMMDD-XXXXXX} order-number pattern. */
    public static final Pattern ORDER_NO_PATTERN = Pattern.compile("\\d{8}-\\d{4,6}");
    private static final int FEATURE_TOP_N = 5;
    private static final int PRODUCT_TOP_N = 3;

    private final FeatureCatalogService featureCatalog;
    private final ChatMapper chatMapper;

    public ChatToolExecutor(FeatureCatalogService featureCatalog, ChatMapper chatMapper) {
        this.featureCatalog = featureCatalog;
        this.chatMapper = chatMapper;
    }

    /**
     * Lists features matching a keyword query (existence + how-to). Returns a "없음" line when
     * nothing matches, so the caller can honestly say the feature is not offered.
     */
    public String searchFeatures(String query) {
        List<FeatureInfo> hits = featureCatalog.search(query, FEATURE_TOP_N);
        if (hits.isEmpty()) {
            return "\"" + safe(query) + "\"에 해당하는 기능을 찾지 못했습니다. (지원하지 않는 기능일 수 있습니다)";
        }
        StringBuilder sb = new StringBuilder();
        for (FeatureInfo f : hits) {
            sb.append(formatFeature(f)).append('\n');
        }
        return sb.toString().trim();
    }

    /** Returns the full how-to for one feature id, or a not-found line. */
    public String getFeature(String id) {
        FeatureInfo f = featureCatalog.get(id);
        if (f == null) {
            return "해당 id의 기능을 찾지 못했습니다: " + safe(id);
        }
        return formatFeature(f);
    }

    /**
     * Looks up an order by number + orderer name (both required). Accepts free text and extracts
     * the order number itself, so the LLM can pass the raw user message.
     */
    public String lookupOrder(String orderNo, String name) {
        String resolvedNo = extractOrderNo(orderNo);
        if (resolvedNo == null) {
            return "주문번호(YYYYMMDD-XXXXXX) 형식을 찾지 못했습니다. 예) 20240501-000123";
        }
        if (name == null || name.isBlank()) {
            return "본인 확인을 위해 주문자명이 필요합니다.";
        }
        ChatOrderRow order = chatMapper.selectOrderByNoAndName(resolvedNo, name.trim());
        if (order == null) {
            return "주문번호와 주문자명이 일치하는 주문을 찾지 못했습니다.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("주문 ").append(order.getOrderNo()).append(" 상태: ").append(order.getStatus())
                .append(" / 결제금액 ₩").append(order.getTotal());
        if (order.getTrackingNo() != null && !order.getTrackingNo().isBlank()) {
            sb.append(" / 운송장 ").append(order.getShipCompany()).append(' ').append(order.getTrackingNo());
        }
        return sb.toString();
    }

    /** Top-N active products matching a keyword (name), with price + stock/sold-out. */
    public String searchProducts(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        List<ChatGoodsRow> rows = chatMapper.selectGoodsByKeyword(kw, PRODUCT_TOP_N);
        if (rows.isEmpty()) {
            return "\"" + safe(kw) + "\" 관련 상품을 찾지 못했습니다.";
        }
        StringBuilder sb = new StringBuilder("\"").append(safe(kw)).append("\" 관련 상품:\n");
        for (ChatGoodsRow row : rows) {
            boolean soldOut = "Y".equalsIgnoreCase(row.getSoldOut())
                    || (row.getStock() != null && row.getStock() <= 0);
            sb.append("• ").append(row.getName())
                    .append(" / ₩").append(row.getPrice())
                    .append(soldOut ? " (품절)" : " (재고 " + (row.getStock() == null ? 0 : row.getStock()) + "개)")
                    .append('\n');
        }
        return sb.toString().trim();
    }

    /** Extracts a {@code YYYYMMDD-XXXXXX} order number from free text, or null. */
    public String extractOrderNo(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = ORDER_NO_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    private String formatFeature(FeatureInfo f) {
        return "• " + f.title() + " [" + statusLabel(f.status()) + "] — " + f.summary()
                + "\n  사용법: " + f.howTo() + " (경로: " + f.href() + ")";
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "live" -> "실동작";
            case "demo" -> "데모";
            case "external" -> "외부연동 대기";
            default -> status;
        };
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}

package org.streamhub.api.v1.chat.adapter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.chat.dto.ChatGoodsRow;
import org.streamhub.api.v1.chat.dto.ChatOrderRow;
import org.streamhub.api.v1.chat.feature.FeatureCatalogService;
import org.streamhub.api.v1.chat.feature.FeatureInfo;
import org.streamhub.api.v1.chat.mapper.ChatMapper;
import org.streamhub.api.v1.content.ContentService;
import org.streamhub.api.v1.content.dto.ContentListItem;
import org.streamhub.api.v1.content.dto.ContentSearchRequest;
import org.streamhub.api.v1.content.entity.ContentType;

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
    /** Per-domain cap when listing the whole-catalog overview. */
    private static final int OVERVIEW_PER_DOMAIN = 4;
    /** Markers for a broad "what can I do / what features are there" question → overview, not search. */
    private static final String[] BROAD_MARKERS = {"어떤", "전체", "모든", "뭐가", "뭘", "무슨", "무엇", "전부"};

    /** Domain code → Korean label, in display order (mirrors the admin catalog domains). */
    private static final Map<String, String> DOMAIN_LABELS = buildDomainLabels();

    private static Map<String, String> buildDomainLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("support", "후원·구독");
        m.put("shop", "굿즈샵");
        m.put("content", "콘텐츠");
        m.put("member", "회원");
        m.put("community", "소통");
        m.put("marketing", "마케팅");
        m.put("settings", "설정");
        return m;
    }

    /** Top-N content results returned when searching on the user's behalf. */
    private static final int CONTENT_TOP_N = 4;

    private final FeatureCatalogService featureCatalog;
    private final ChatMapper chatMapper;
    private final ContentService contentService;

    public ChatToolExecutor(FeatureCatalogService featureCatalog, ChatMapper chatMapper,
                            ContentService contentService) {
        this.featureCatalog = featureCatalog;
        this.chatMapper = chatMapper;
        this.contentService = contentService;
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

    /**
     * Feature-guide answer for the chatbot: a broad "어떤 기능?" question (or one that matches nothing
     * specific) returns the whole-catalog {@link #featureOverview()}; otherwise the matching
     * features' how-to. Single entry point so the rule provider and the LLM share one behaviour.
     */
    public String featureGuide(String message) {
        List<FeatureInfo> hits = featureCatalog.search(message, FEATURE_TOP_N);
        if (hits.isEmpty() || isBroadQuery(message)) {
            return featureOverview();
        }
        StringBuilder sb = new StringBuilder();
        for (FeatureInfo f : hits) {
            sb.append(formatFeature(f)).append('\n');
        }
        return sb.toString().trim();
    }

    /** A grouped, domain-by-domain overview of the whole catalog (capped per domain). */
    public String featureOverview() {
        Map<String, List<String>> byDomain = new LinkedHashMap<>();
        for (String domain : DOMAIN_LABELS.keySet()) {
            byDomain.put(domain, new java.util.ArrayList<>());
        }
        for (FeatureInfo f : featureCatalog.all()) {
            List<String> bucket = byDomain.get(f.domain());
            if (bucket != null && bucket.size() < OVERVIEW_PER_DOMAIN) {
                bucket.add(f.title());
            }
        }
        StringBuilder sb = new StringBuilder("그레이스온 주요 기능입니다:\n");
        for (Map.Entry<String, String> e : DOMAIN_LABELS.entrySet()) {
            List<String> titles = byDomain.get(e.getKey());
            if (titles != null && !titles.isEmpty()) {
                sb.append("• [").append(e.getValue()).append("] ")
                        .append(String.join(", ", titles)).append('\n');
            }
        }
        sb.append("궁금한 기능 이름을 말씀하시면 사용법을 안내해 드려요.");
        return sb.toString();
    }

    private boolean isBroadQuery(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String marker : BROAD_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
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

    /**
     * Top-N matching products as rich-message cards (G), each deep-linking to {@code /goods/{id}}.
     * Shared by the rule provider and the LLM provider (which attaches them when it calls
     * {@code searchProducts}). Empty when nothing matches.
     */
    public List<ChatCard> productCards(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        return chatMapper.selectGoodsByKeyword(kw, PRODUCT_TOP_N).stream().map(row -> {
            boolean soldOut = "Y".equalsIgnoreCase(row.getSoldOut())
                    || (row.getStock() != null && row.getStock() <= 0);
            int stock = row.getStock() == null ? 0 : row.getStock();
            return new ChatCard(
                    row.getName(),
                    "₩" + row.getPrice() + (soldOut ? " · 품절" : " · 재고 " + stock + "개"),
                    null,
                    "/goods/" + row.getId(),
                    soldOut ? "품절" : null);
        }).toList();
    }

    /** Top-N PUBLISHED videos/music matching a keyword, as a compact text list (LLM tool result). */
    public String searchContents(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        List<ContentListItem> rows = contentService.listPublic(
                new ContentSearchRequest(0, CONTENT_TOP_N, kw, null, null, null, null, null)).getContents();
        if (rows.isEmpty()) {
            return "\"" + safe(kw) + "\" 관련 영상/음악을 찾지 못했습니다.";
        }
        StringBuilder sb = new StringBuilder("\"").append(safe(kw)).append("\" 검색 결과:\n");
        for (ContentListItem c : rows) {
            sb.append("• ").append(c.getType() == ContentType.VIDEO ? "[영상] " : "[음악] ")
                    .append(c.getTitle());
            if (c.getChannelName() != null) {
                sb.append(" / ").append(c.getChannelName());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * "Search on the user's behalf" (대신 검색): top-N content results as deep-link cards plus a final
     * card to the pre-filled global search page ({@code /search?q=...}). Empty when nothing matches.
     */
    public List<ChatCard> contentCards(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        List<ContentListItem> rows = contentService.listPublic(
                new ContentSearchRequest(0, CONTENT_TOP_N, kw, null, null, null, null, null)).getContents();
        List<ChatCard> cards = new ArrayList<>();
        for (ContentListItem c : rows) {
            boolean video = c.getType() == ContentType.VIDEO;
            String sub = (video ? "영상" : "음악")
                    + (c.getChannelName() != null ? " · " + c.getChannelName() : "");
            cards.add(new ChatCard(c.getTitle(), sub, c.getThumbnailUrl(),
                    (video ? "/video/" : "/music/") + c.getId(), null));
        }
        if (!cards.isEmpty() && !kw.isBlank()) {
            cards.add(new ChatCard("\"" + kw + "\" 전체 검색결과 보기", "영상·음악·소식 통합검색", null,
                    "/search?q=" + URLEncoder.encode(kw, StandardCharsets.UTF_8), null));
        }
        return cards;
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

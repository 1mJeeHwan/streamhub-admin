package org.streamhub.api.v1.chat.adapter;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.chat.ChatKnowledgeService;
import org.streamhub.api.v1.chat.dto.ChatGoodsRow;
import org.streamhub.api.v1.chat.dto.ChatOrderRow;
import org.streamhub.api.v1.chat.entity.ChatIntent;
import org.streamhub.api.v1.chat.mapper.ChatMapper;

/**
 * Default chatbot provider (demo/test mode): rule-based intent classification + a static FAQ
 * table + order/goods lookups via {@link ChatMapper}. <b>No external LLM call.</b> Order lookup
 * requires both the order number and the orderer name (cross-member leakage prevention).
 */
@Component
public class RuleChatProvider implements ChatProvider {

    private static final String CODE = "RULE";
    private static final int PRODUCT_TOP_N = 3;

    /** {@code YYYYMMDD-XXXXXX} order-number pattern used to extract a number from free text. */
    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("\\d{8}-\\d{4,6}");

    /** Static FAQ table (keyword → answer). DB-free per spec §5. */
    private static final List<Faq> FAQ_TABLE = List.of(
            new Faq("배송비", "배송비는 기본 3,000원이며 5만원 이상 구매 시 무료입니다. (데모)"),
            new Faq("환불", "환불은 상품 수령 후 7일 이내 신청 가능하며, 단순변심은 왕복 배송비가 부과됩니다. (데모)"),
            new Faq("교환", "교환은 동일상품 한정으로 7일 이내 가능합니다. 고객센터로 접수해 주세요. (데모)"),
            new Faq("반품", "반품은 상품 미개봉 상태에서 7일 이내 가능합니다. (데모)"),
            new Faq("회원", "회원가입은 무료이며, 가입 즉시 적립 혜택이 제공됩니다. (데모)"),
            new Faq("포인트", "포인트는 후원/구매 금액의 1%가 적립되며 1포인트=1원으로 사용 가능합니다. (데모)"),
            new Faq("쿠폰", "보유 쿠폰은 마이페이지 > 쿠폰함에서 확인하실 수 있습니다. (데모)"),
            new Faq("예배", "주일예배는 오전 11시, 수요예배는 저녁 7시 30분에 진행됩니다. (데모)"));

    private static final List<String> FALLBACK_QUICK_REPLIES =
            List.of("어떤 기능이 있나요?", "주문 배송 조회", "상품 재고 문의");

    /**
     * Feature-recommendation shortcuts shown after a feature-guide answer. Each is phrased to route
     * back to {@code FEATURE_GUIDE} (carries a feature marker, no FAQ/order/product keyword) so a tap
     * reliably drills into another feature's how-to.
     */
    private static final List<String> FEATURE_QUICK_REPLIES =
            List.of("교회찾기 기능", "음반 미리듣기 어떻게?", "이벤트 기능 있어?", "마이페이지 뭐가 있어?");

    private final IntentClassifier intentClassifier;
    private final ChatMapper chatMapper;
    private final ChatToolExecutor toolExecutor;
    private final ChatKnowledgeService knowledgeService;

    public RuleChatProvider(IntentClassifier intentClassifier, ChatMapper chatMapper,
                            ChatToolExecutor toolExecutor, ChatKnowledgeService knowledgeService) {
        this.intentClassifier = intentClassifier;
        this.chatMapper = chatMapper;
        this.toolExecutor = toolExecutor;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public ChatReply reply(String message, java.util.List<ChatTurn> history) {
        // Rule provider is stateless — history is ignored (kept for the context-aware LLM provider).
        // Admin-taught knowledge wins first: any enabled keyword match returns the curated answer,
        // regardless of intent, so operators can add answers without touching the classifier.
        Optional<String> taught = knowledgeService.findAnswer(message);
        if (taught.isPresent()) {
            return ChatReply.of(taught.get(), ChatIntent.FAQ);
        }
        ChatIntent intent = intentClassifier.classify(message);
        return switch (intent) {
            case ORDER_LOOKUP -> replyOrderLookup(message);
            case PRODUCT_INQUIRY -> replyProductInquiry(message);
            case FAQ -> replyFaq(message);
            case FEATURE_GUIDE -> replyFeatureGuide(message);
            case FALLBACK -> replyFallback();
        };
    }

    /**
     * Answers a "이 기능 있나요?/어떻게 쓰나요?" question from the feature catalog (existence + how-to).
     * Keyword-searches the catalog; when nothing matches, the tool's "없음" line is returned so the
     * bot honestly says the feature is not offered.
     */
    private ChatReply replyFeatureGuide(String message) {
        String body = toolExecutor.featureGuide(message);
        return new ChatReply(body, ChatIntent.FEATURE_GUIDE, FEATURE_QUICK_REPLIES);
    }

    private ChatReply replyOrderLookup(String message) {
        Matcher matcher = ORDER_NO_PATTERN.matcher(message);
        if (!matcher.find()) {
            return new ChatReply(
                    "주문 조회를 위해 주문번호(YYYYMMDD-XXXXXX)와 주문자명을 함께 알려주세요. 예) 20240501-000123 홍길동",
                    ChatIntent.ORDER_LOOKUP,
                    List.of("주문번호 입력 방법"));
        }
        String orderNo = matcher.group();
        String name = extractName(message, matcher);
        if (name == null) {
            return ChatReply.of(
                    "본인 확인을 위해 주문자명도 함께 입력해 주세요. 예) " + orderNo + " 홍길동",
                    ChatIntent.ORDER_LOOKUP);
        }
        ChatOrderRow order = chatMapper.selectOrderByNoAndName(orderNo, name);
        if (order == null) {
            return ChatReply.of(
                    "입력하신 주문번호와 주문자명이 일치하는 주문을 찾지 못했습니다. 다시 확인해 주세요.",
                    ChatIntent.ORDER_LOOKUP);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("주문 ").append(order.getOrderNo()).append(" 상태: ").append(order.getStatus())
                .append(" / 결제금액 ₩").append(order.getTotal());
        if (order.getTrackingNo() != null && !order.getTrackingNo().isBlank()) {
            sb.append(" / 운송장 ").append(order.getShipCompany()).append(' ').append(order.getTrackingNo());
        }
        return ChatReply.of(sb.toString(), ChatIntent.ORDER_LOOKUP);
    }

    private ChatReply replyProductInquiry(String message) {
        String keyword = productKeyword(message);
        List<ChatGoodsRow> rows = chatMapper.selectGoodsByKeyword(keyword, PRODUCT_TOP_N);
        if (rows.isEmpty()) {
            return ChatReply.of(
                    "\"" + keyword + "\" 관련 상품을 찾지 못했습니다. 다른 키워드로 검색해 보세요.",
                    ChatIntent.PRODUCT_INQUIRY);
        }
        StringBuilder sb = new StringBuilder("\"").append(keyword).append("\" 관련 상품입니다:\n");
        for (ChatGoodsRow row : rows) {
            boolean soldOut = "Y".equalsIgnoreCase(row.getSoldOut())
                    || (row.getStock() != null && row.getStock() <= 0);
            sb.append("• ").append(row.getName())
                    .append(" / ₩").append(row.getPrice())
                    .append(soldOut ? " (품절)" : " (재고 " + (row.getStock() == null ? 0 : row.getStock()) + "개)")
                    .append('\n');
        }
        return ChatReply.of(sb.toString().trim(), ChatIntent.PRODUCT_INQUIRY);
    }

    private ChatReply replyFaq(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        for (Faq faq : FAQ_TABLE) {
            if (lower.contains(faq.keyword())) {
                return ChatReply.of(faq.answer(), ChatIntent.FAQ);
            }
        }
        return replyFallback();
    }

    private ChatReply replyFallback() {
        return new ChatReply(
                "무엇을 도와드릴까요? 아래 빠른 답변을 선택하시거나 질문을 입력해 주세요. (데모 챗봇)",
                ChatIntent.FALLBACK,
                FALLBACK_QUICK_REPLIES);
    }

    /** Extracts a probable Korean name token after the order number (2–4 Hangul characters). */
    private String extractName(String message, Matcher orderMatcher) {
        String tail = message.substring(orderMatcher.end());
        Matcher nameMatcher = Pattern.compile("[가-힣]{2,4}").matcher(tail);
        if (nameMatcher.find()) {
            return nameMatcher.group();
        }
        Matcher anyName = Pattern.compile("[가-힣]{2,4}").matcher(message);
        return anyName.find() ? anyName.group() : null;
    }

    /** Picks a search keyword: the longest Hangul token, else the whole trimmed message. */
    private String productKeyword(String message) {
        Matcher matcher = Pattern.compile("[가-힣A-Za-z]{2,}").matcher(message);
        String best = null;
        while (matcher.find()) {
            String token = matcher.group();
            if (best == null || token.length() > best.length()) {
                best = token;
            }
        }
        return best != null ? best : message.trim();
    }

    /** One static FAQ entry. */
    private record Faq(String keyword, String answer) {
    }
}

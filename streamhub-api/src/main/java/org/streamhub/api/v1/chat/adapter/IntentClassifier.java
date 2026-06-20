package org.streamhub.api.v1.chat.adapter;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.chat.entity.ChatIntent;

/**
 * Rule-based intent classifier (C5). Lower-cases the message and matches keyword sets in priority
 * order: {@code FAQ} → {@code ORDER_LOOKUP} → {@code PRODUCT_INQUIRY} → {@code FEATURE_GUIDE} →
 * {@code FALLBACK}. FAQ is checked first so a specific phrase like "배송비" wins over the broader
 * order keyword "배송"; {@code FEATURE_GUIDE} sits just before fallback (broadest markers) so a
 * "이 기능 있어요?/어떻게 쓰나요?" question that doesn't hit a more specific intent is answered from
 * the feature catalog instead of a generic fallback.
 *
 * <p>This is the core branch of the rule chatbot, so it is covered by a table-based unit test.
 */
@Component
public class IntentClassifier {

    private static final List<String> ORDER_KEYWORDS =
            List.of("주문", "배송", "조회", "택배", "운송장", "order", "tracking");
    private static final List<String> PRODUCT_KEYWORDS =
            List.of("상품", "가격", "재고", "구매", "앨범", "굿즈", "product", "price", "stock");
    private static final List<String> FAQ_KEYWORDS =
            List.of("배송비", "환불", "교환", "반품", "회원", "예배", "시간", "포인트", "쿠폰", "faq");
    /**
     * Feature existence / how-to / location markers. Last before fallback (broadest), so specific
     * intents win. Includes "어디/위치" so a "○○ 어디서 하나요?" navigation question is answered from
     * the catalog (and, with the LLM, the site map) instead of falling back.
     */
    private static final List<String> FEATURE_KEYWORDS =
            List.of("기능", "사용법", "어떻게", "방법", "메뉴", "있나요", "있어", "가능", "지원",
                    "뭐가", "무슨", "어떤", "어디", "위치", "feature", "how");

    /** Classifies a user message into a {@link ChatIntent}. Null/blank ⇒ {@code FALLBACK}. */
    public ChatIntent classify(String message) {
        if (message == null || message.isBlank()) {
            return ChatIntent.FALLBACK;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (containsAny(lower, FAQ_KEYWORDS)) {
            return ChatIntent.FAQ;
        }
        if (containsAny(lower, ORDER_KEYWORDS)) {
            return ChatIntent.ORDER_LOOKUP;
        }
        if (containsAny(lower, PRODUCT_KEYWORDS)) {
            return ChatIntent.PRODUCT_INQUIRY;
        }
        if (containsAny(lower, FEATURE_KEYWORDS)) {
            return ChatIntent.FEATURE_GUIDE;
        }
        return ChatIntent.FALLBACK;
    }

    private boolean containsAny(String lower, List<String> keywords) {
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

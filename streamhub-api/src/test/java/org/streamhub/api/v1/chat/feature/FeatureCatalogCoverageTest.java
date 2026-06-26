package org.streamhub.api.v1.chat.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.streamhub.api.v1.chat.adapter.IntentClassifier;
import org.streamhub.api.v1.chat.entity.ChatIntent;

/**
 * Coverage guard for the chatbot's question classification (C5). Asserts that <b>every</b> catalog
 * feature is reachable by a representative user-phrased question, and that every recommended-question
 * chip the widget shows resolves to a concrete answer path (a catalog match or a concrete intent) —
 * never the "그런 기능은 없습니다 / 찾지 못했습니다" dead end the user reported.
 *
 * <p>This is the regression net behind "권장 질문/모든 기능을 예상 질문 키워드로 분류": add a feature or a
 * quick-reply chip and this test forces a matching keyword to exist.
 */
class FeatureCatalogCoverageTest {

    private final FeatureCatalogService catalog = new FeatureCatalogService(new ObjectMapper());
    private final IntentClassifier intentClassifier = new IntentClassifier();

    /**
     * Representative user question → the feature id it must reach. The questions are phrased the way a
     * user actually types (colloquial, not the admin screen name), so this doubles as the "expected
     * question keyword" spec for each feature.
     */
    private static final Map<String, String> QUESTION_TO_FEATURE = buildQuestionMap();

    private static Map<String, String> buildQuestionMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("운영 대시보드 보고 싶어", "dashboard");
        m.put("회원 관리 어떻게 해?", "members");
        m.put("예배 영상 어디서 봐?", "contents");
        m.put("감사 로그 기능 있어?", "action-log");
        m.put("내 주변 교회 찾기 지도", "churches");
        m.put("음반 미리듣기 어떻게?", "albums");
        m.put("오프라인 매장 찾기", "store");
        m.put("새가족 예배 신청하고 싶어", "worship");
        m.put("문자 발송 되나요?", "sms");
        m.put("결제 내역 영수증 확인", "payment-seam");
        m.put("챗봇 상담 기능", "chat-bot");
        m.put("정기후원 구독 현황", "subscription");
        m.put("멤버십 플랜 등급", "subscription-plans");
        m.put("정기결제 일정 캘린더", "subscription-calendar");
        m.put("후원 헌금 하고 싶어", "donation");
        m.put("주문 배송 조회", "orders");
        m.put("굿즈샵 상품 구경", "goods");
        m.put("상품 카테고리 분류", "goods-category");
        m.put("재고 품절 재입고 알림", "goods-stock");
        m.put("굿즈 문의 남기고 싶어", "goods-inquiry");
        m.put("상품 후기 리뷰 평점", "goods-review");
        m.put("쿠폰함 할인 쿠폰", "coupons");
        m.put("포인트 적립 어떻게?", "points");
        m.put("접속 통계 방문자", "visits");
        m.put("콘텐츠 조회수 통계", "content-stats");
        m.put("게시판 관리", "boards");
        m.put("공지 나눔 기도제목 소식", "posts");
        m.put("1:1 문의 고객센터", "inquiry");
        m.put("메인 배너 광고", "banners");
        m.put("진행 중인 이벤트 있어?", "campaigns");
        m.put("받은 알림 알림센터", "notifications");
        m.put("마이페이지 뭐가 있어?", "mypage");
        m.put("찜한 곡 내 재생목록", "favorites");
        m.put("내 시청 기록 이어보기", "watch-history");
        m.put("통합검색 어떻게 해?", "search");
        m.put("회원가입 로그인 본인인증", "signup");
        return m;
    }

    @Test
    void everyFeatureIsReachableByAnExpectedQuestion() {
        // Sanity: the table must cover the whole catalog (no feature left unverifiable).
        assertThat(QUESTION_TO_FEATURE.values())
                .containsExactlyInAnyOrderElementsOf(catalog.all().stream().map(FeatureInfo::id).toList());

        QUESTION_TO_FEATURE.forEach((question, expectedId) -> {
            List<FeatureInfo> hits = catalog.search(question, 5);
            assertThat(hits)
                    .as("question \"%s\" should reach feature \"%s\"", question, expectedId)
                    .anyMatch(f -> f.id().equals(expectedId));
        });
    }

    /**
     * Every quick-reply chip the widget shows must resolve, not dead-end. Feature chips must hit the
     * catalog; intent chips must classify to a concrete (non-fallback) intent.
     */
    @Test
    void everyRecommendedQuestionResolves() {
        // Backend FEATURE_QUICK_REPLIES (RuleChatProvider) — must each match a catalog feature.
        List<String> featureChips = List.of(
                "교회찾기 기능", "음반 미리듣기 어떻게?", "이벤트 기능 있어?", "마이페이지 뭐가 있어?");
        for (String chip : featureChips) {
            assertThat(catalog.search(chip, 5))
                    .as("feature chip \"%s\" must match a catalog feature (not '찾지 못함')", chip)
                    .isNotEmpty();
        }

        // Intent chips (INITIAL/FALLBACK/GUIDE quick replies) — must classify to a concrete intent.
        Map<String, ChatIntent> intentChips = new LinkedHashMap<>();
        intentChips.put("어떤 기능이 있나요?", ChatIntent.FEATURE_GUIDE);
        intentChips.put("주문 배송 조회", ChatIntent.ORDER_LOOKUP);
        intentChips.put("상품 재고 문의", ChatIntent.PRODUCT_INQUIRY);
        intentChips.put("주문 조회하고 싶어요", ChatIntent.ORDER_LOOKUP);
        intentChips.forEach((chip, expected) ->
                assertThat(intentClassifier.classify(chip))
                        .as("intent chip \"%s\" should classify as %s", chip, expected)
                        .isEqualTo(expected));
    }
}

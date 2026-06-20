package org.streamhub.api.v1.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.streamhub.api.v1.chat.entity.ChatIntent;

/** Table-based tests for the rule chatbot intent classifier (spec §3.3 core branch). */
class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    @DisplayName("키워드 룰에 따라 의도를 분류한다")
    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource({
            "'주문 배송 조회하고 싶어요', ORDER_LOOKUP",
            "'운송장 번호 알려주세요', ORDER_LOOKUP",
            "'20240501-000123 홍길동 주문 조회', ORDER_LOOKUP",
            "'배송비 얼마예요?', FAQ",
            "'환불 어떻게 하나요', FAQ",
            "'예배 시간 언제인가요', FAQ",
            "'포인트 적립 되나요', FAQ",
            "'찬양 앨범 재고 있나요?', PRODUCT_INQUIRY",
            "'이 상품 가격이 궁금해요', PRODUCT_INQUIRY",
            "'어떤 기능이 있나요?', FEATURE_GUIDE",
            "'마이페이지 어디에 있어요?', FEATURE_GUIDE",
            "'안녕하세요', FALLBACK",
            "'그냥 인사드려요', FALLBACK"
    })
    void classify_byKeywordRules(String message, ChatIntent expected) {
        assertThat(classifier.classify(message)).isEqualTo(expected);
    }

    @DisplayName("FAQ 키워드가 주문 키워드보다 우선한다(배송비 vs 배송)")
    @ParameterizedTest
    @ValueSource(strings = {"배송비 문의", "배송비가 궁금합니다"})
    void classify_faqWinsOverOrder(String message) {
        assertThat(classifier.classify(message)).isEqualTo(ChatIntent.FAQ);
    }

    @DisplayName("null/빈 입력은 FALLBACK")
    @ParameterizedTest
    @NullAndEmptySource
    void classify_nullOrBlank(String message) {
        assertThat(classifier.classify(message)).isEqualTo(ChatIntent.FALLBACK);
    }
}

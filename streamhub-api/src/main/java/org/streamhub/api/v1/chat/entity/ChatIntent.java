package org.streamhub.api.v1.chat.entity;

/** Classified user intent for a bot reply (C5). Stored via {@code @Enumerated(STRING)}. */
public enum ChatIntent {
    /** 상품/가격/재고 문의. */
    PRODUCT_INQUIRY,
    /** 주문/배송 조회. */
    ORDER_LOOKUP,
    /** 자주 묻는 질문(배송비/환불/회원 등). */
    FAQ,
    /** 기능 유무·사용법 안내(기능 카탈로그 기반). */
    FEATURE_GUIDE,
    /** 미분류 — 안내 + 빠른답변. */
    FALLBACK
}

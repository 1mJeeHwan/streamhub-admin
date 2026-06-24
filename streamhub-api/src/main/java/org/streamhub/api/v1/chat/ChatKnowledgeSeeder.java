package org.streamhub.api.v1.chat;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.chat.entity.ChatKnowledge;
import org.streamhub.api.v1.chat.repository.ChatKnowledgeRepository;

/**
 * Seeds the starter chatbot knowledge (FAQ) so the admin has editable rows out of the box — the same
 * entries that used to be hard-coded in {@code RuleChatProvider}. Idempotent (skips when rows exist)
 * and wrapped in try/catch — a seeder must never crash production boot.
 */
@Slf4j
@Component
@Order(42)
public class ChatKnowledgeSeeder implements CommandLineRunner {

    private record Seed(String question, String keywords, String answer) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("배송비", "배송비 배송료", "배송비는 기본 3,000원이며 5만원 이상 구매 시 무료입니다. (데모)"),
            new Seed("환불", "환불", "환불은 상품 수령 후 7일 이내 신청 가능하며, 단순변심은 왕복 배송비가 부과됩니다. (데모)"),
            new Seed("교환", "교환", "교환은 동일상품 한정으로 7일 이내 가능합니다. 고객센터로 접수해 주세요. (데모)"),
            new Seed("반품", "반품", "반품은 상품 미개봉 상태에서 7일 이내 가능합니다. (데모)"),
            new Seed("회원가입", "회원 가입", "회원가입은 무료이며, 가입 즉시 적립 혜택이 제공됩니다. (데모)"),
            new Seed("포인트", "포인트 적립", "포인트는 후원/구매 금액의 1%가 적립되며 1포인트=1원으로 사용 가능합니다. (데모)"),
            new Seed("쿠폰", "쿠폰", "보유 쿠폰은 마이페이지 > 쿠폰함에서 확인하실 수 있습니다. (데모)"),
            new Seed("예배 시간", "예배 주일예배 수요예배 시간", "주일예배는 오전 11시, 수요예배는 저녁 7시 30분에 진행됩니다. (데모)"));

    private final ChatKnowledgeRepository repository;

    public ChatKnowledgeSeeder(ChatKnowledgeRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        try {
            if (repository.count() > 0) {
                return;
            }
            int order = 0;
            for (Seed seed : SEEDS) {
                repository.save(ChatKnowledge.builder()
                        .question(seed.question())
                        .keywords(seed.keywords())
                        .answer(seed.answer())
                        .enabled(true)
                        .sortOrder(order++)
                        .build());
            }
            log.info("Seeded {} chatbot knowledge entries", SEEDS.size());
        } catch (RuntimeException e) {
            log.warn("Chat knowledge seeding skipped: {}", e.getMessage());
        }
    }
}

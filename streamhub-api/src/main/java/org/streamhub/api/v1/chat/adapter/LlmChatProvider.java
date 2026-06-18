package org.streamhub.api.v1.chat.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LLM chatbot adapter — <b>config-gated stub, not a working provider</b>. Registered only when
 * {@code app.chat.provider=llm}; its {@link #reply(String)} throws, so no LLM auto-response exists.
 * It only marks the seam (and the API-key injection point) where a real LLM integration would
 * slot in. The default demo deployment uses {@link RuleChatProvider}.
 */
@Component
@ConditionalOnProperty(name = "app.chat.provider", havingValue = "llm")
public class LlmChatProvider implements ChatProvider {

    /** ← 실 LLM(Anthropic/OpenAI) API key 주입점. */
    @Value("${app.chat.llm.api-key:}")
    private String apiKey;

    @Override
    public String code() {
        return "LLM";
    }

    @Override
    public ChatReply reply(String message) {
        // TODO(실키): Anthropic/OpenAI Tool Calling으로 ChatMapper(주문조회/상품검색)를 tool로 노출하고
        //   모델이 의도분류 + 도구호출 + 자연어 응답을 생성하도록 위임. ChatService는 무변경(seam 교체).
        throw new UnsupportedOperationException("실 LLM 미연동(데모) — app.chat.provider=rule 사용");
    }
}

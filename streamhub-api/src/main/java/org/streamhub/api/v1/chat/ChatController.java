package org.streamhub.api.v1.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.chat.dto.ChatHistoryItem;
import org.streamhub.api.v1.chat.dto.ChatReplyDto;
import org.streamhub.api.v1.chat.dto.ChatSendRequest;

/**
 * Public chatbot widget endpoints (C5). Rule-based FAQ + DB-lookup provider — there is <b>no LLM
 * call and no "auto-response" model</b>; {@code testMode=true} so the widget labels it as a demo.
 * Replies are <b>stateless</b>: each turn classifies only the latest message — the persisted
 * per-session history is for reload/console review, not fed back as conversation context.
 *
 * <p><b>Security note:</b> these endpoints are public, so {@code /v1/chat/send} and
 * {@code /v1/chat/*&#47;history} are whitelisted in {@code SecurityConfig.PUBLIC_PATHS}. No
 * class-level {@code @PreAuthorize} is declared here on purpose; the admin console
 * ({@code /v1/chat-admin}) stays authenticated. Order lookup itself requires both the order
 * number and the orderer name, so no anonymous enumeration is possible even though the endpoint
 * is open.
 */
@Tag(name = "Chat", description = "공개 챗봇 위젯 (룰베이스 FAQ/DB조회, 인증 불필요)")
@RestController
@RequestMapping("/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "챗봇 메시지 전송",
            description = "최신 메시지 한 건만 룰 기반으로 분류해 응답한다(이전 대화 맥락 미반영). USER/BOT 턴은 세션에 저장된다.")
    @PostMapping("/send")
    public ResultDTO<ChatReplyDto> send(@Valid @RequestBody ChatSendRequest request) {
        return ResultDTO.ok(chatService.send(request));
    }

    @Operation(summary = "챗봇 대화 이력", description = "세션키 기준 전체 대화 이력(오래된 순).")
    @GetMapping("/{sessionKey}/history")
    public ResultDTO<List<ChatHistoryItem>> history(@PathVariable String sessionKey) {
        return ResultDTO.ok(chatService.history(sessionKey));
    }
}

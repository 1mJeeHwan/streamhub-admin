package org.streamhub.api.v1.chat.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.chat.ChatInsightService;
import org.streamhub.api.v1.chat.ChatKnowledgeService;
import org.streamhub.api.v1.chat.admin.dto.ChatKnowledgeDto;
import org.streamhub.api.v1.chat.admin.dto.ChatMessageRow;
import org.streamhub.api.v1.chat.admin.dto.ChatReplyRequest;
import org.streamhub.api.v1.chat.admin.dto.ChatSessionRow;
import org.streamhub.api.v1.chat.admin.dto.ChatStatsDto;
import org.streamhub.api.v1.chat.admin.dto.ChatUnansweredDto;

/**
 * Admin chat-console endpoints (SYSTEM or CHURCH_MANAGER). Lets operators browse chatbot
 * sessions, read the full thread, post a manual reply, and manage the chatbot knowledge (FAQ) the
 * bot answers from. Separate from the public widget controller ({@code ChatController}), which stays
 * {@code permitAll}.
 */
@Tag(name = "ChatAdmin", description = "관리자 챗봇 상담 콘솔 · 지식관리")
@RestController
@RequestMapping("/v1/chat-admin")
@PreAuthorize("hasAuthority('chat:read')") // class default = read; mutations require chat:write
public class ChatAdminController {

    private final ChatAdminService chatAdminService;
    private final ChatKnowledgeService knowledgeService;
    private final ChatInsightService insightService;

    public ChatAdminController(ChatAdminService chatAdminService,
                              ChatKnowledgeService knowledgeService,
                              ChatInsightService insightService) {
        this.chatAdminService = chatAdminService;
        this.knowledgeService = knowledgeService;
        this.insightService = insightService;
    }

    @Operation(summary = "상담 세션 목록", description = "최근 활동순 세션 목록. 마지막 메시지/미응답 여부 포함.")
    @PostMapping("/sessions")
    public ResultDTO<List<ChatSessionRow>> sessions() {
        return ResultDTO.ok(chatAdminService.listSessions());
    }

    @Operation(summary = "세션 대화 이력", description = "세션키 기준 전체 대화 스레드(오래된 순).")
    @GetMapping("/sessions/{sessionKey}/messages")
    public ResultDTO<List<ChatMessageRow>> messages(@PathVariable String sessionKey) {
        return ResultDTO.ok(chatAdminService.messages(sessionKey));
    }

    @Operation(summary = "상담원 수동 답변", description = "해당 세션에 운영자(BOT) 답변을 추가한다.")
    @PreAuthorize("hasAuthority('chat:write')")
    @PostMapping("/sessions/{sessionKey}/reply")
    public ResultDTO<ChatMessageRow> reply(
            @PathVariable String sessionKey, @Valid @RequestBody ChatReplyRequest request) {
        return ResultDTO.ok(chatAdminService.reply(sessionKey, request));
    }

    // --- Chatbot knowledge (FAQ) management --------------------------------

    @Operation(summary = "챗봇 지식 목록", description = "챗봇이 답변에 사용하는 지식(FAQ) 목록(정렬순).")
    @GetMapping("/knowledge")
    public ResultDTO<List<ChatKnowledgeDto>> knowledgeList() {
        return ResultDTO.ok(knowledgeService.list());
    }

    @Operation(summary = "챗봇 지식 등록")
    @PreAuthorize("hasAuthority('chat:write')")
    @PostMapping("/knowledge")
    public ResultDTO<ChatKnowledgeDto> knowledgeCreate(@Valid @RequestBody ChatKnowledgeDto request) {
        return ResultDTO.ok(knowledgeService.create(request));
    }

    @Operation(summary = "챗봇 지식 수정")
    @PreAuthorize("hasAuthority('chat:write')")
    @PutMapping("/knowledge/{id}")
    public ResultDTO<ChatKnowledgeDto> knowledgeUpdate(
            @PathVariable Long id, @Valid @RequestBody ChatKnowledgeDto request) {
        return ResultDTO.ok(knowledgeService.update(id, request));
    }

    @Operation(summary = "챗봇 지식 삭제")
    @PreAuthorize("hasAuthority('chat:write')")
    @DeleteMapping("/knowledge/{id}")
    public ResultDTO<Void> knowledgeDelete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ResultDTO.ok();
    }

    // --- Learning queue (A) + analytics (F) --------------------------------

    @Operation(summary = "미답변 질문 목록", description = "봇이 답하지 못한(FALLBACK) 질문 큐(미처리, 최신순).")
    @GetMapping("/unanswered")
    public ResultDTO<List<ChatUnansweredDto>> unanswered() {
        return ResultDTO.ok(insightService.listUnanswered());
    }

    @Operation(summary = "미답변 처리완료", description = "지식 등록/무시 후 학습 큐에서 제거(처리완료 표시).")
    @PreAuthorize("hasAuthority('chat:write')")
    @PutMapping("/unanswered/{id}/resolve")
    public ResultDTO<Void> resolveUnanswered(@PathVariable Long id) {
        insightService.resolveUnanswered(id);
        return ResultDTO.ok();
    }

    @Operation(summary = "챗봇 분석 통계", description = "세션·인텐트 분포·폴백률·일별 추이.")
    @GetMapping("/stats")
    public ResultDTO<ChatStatsDto> stats() {
        return ResultDTO.ok(insightService.stats());
    }
}

package org.streamhub.api.v1.chat.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.streamhub.api.v1.chat.entity.ChatIntent;
import org.streamhub.api.v1.chat.entity.ChatRole;

/**
 * Real LLM chatbot adapter backed by <b>Google Gemini</b> with function calling (C5). Registered
 * only when {@code app.chat.provider=llm}; otherwise the demo runs on {@link RuleChatProvider}.
 *
 * <p>The model is given four read-only tools (declared as Gemini {@code functionDeclarations}) that
 * delegate to {@link ChatToolExecutor}: {@code searchFeatures}/{@code getFeature} (feature
 * existence + how-to, grounded in the catalog) and {@code lookupOrder}/{@code searchProducts}
 * (live DB). The model classifies intent, calls tools, and writes a natural-language answer
 * grounded only in tool results — so it can tell the user what features exist and how to use them
 * without hallucinating.
 *
 * <p><b>Free tier:</b> Gemini's free tier needs no payment; only an API key
 * ({@code app.chat.llm.api-key}). The provider is <b>fail-safe</b>: a missing key, an API/quota
 * error, or any exception falls back to the deterministic {@link RuleChatProvider}, so the widget
 * never breaks.
 *
 * <p><b>Note:</b> built against the Gemini v1beta {@code generateContent} REST contract and cannot
 * be exercised offline; verify against a live key. If the API rejects the {@code "function"} role
 * on tool-result turns, switch {@link #FUNCTION_ROLE} to {@code "user"}.
 */
@Component
@ConditionalOnProperty(name = "app.chat.provider", havingValue = "llm")
public class LlmChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(LlmChatProvider.class);

    private static final String CODE = "LLM";
    /** Role used for tool-result turns (see class note). */
    private static final String FUNCTION_ROLE = "function";
    /** Cap on model↔tool round-trips per message, so a tool-loop can't run away. */
    private static final int MAX_TOOL_ITERS = 4;
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 12_000;

    private static final String SYSTEM_PROMPT = """
            너는 그레이스온(GraceOn, 교회·CCM 예배 미디어/커머스 플랫폼)의 한국어 상담 도우미다.
            역할: 기능의 유무·사용법 안내, "어디서 하나요?" 같은 위치 안내, 주문/배송 조회와 상품 문의를 돕는다.

            사용자 사이트 구조(위치 안내에 사용):
            - 하단 탭: 영상(예배·찬양 영상), 음악(찬양 스트리밍), 소식(공지/게시글), MY(마이페이지, 로그인 필요)
            - 상단 아이콘: 음반(앨범 구매·전체듣기, /albums), 굿즈샵(/goods), 이벤트(/campaigns), 교회찾기(지도, /churches), 통합검색
            - 매장찾기 /stores, 로그인 /login
            - 마이페이지(로그인): 구매내역·영수증, 포인트, 쿠폰함, 정기후원·구독, 알림, 찜(재생목록), 시청기록, 후기/문의

            규칙:
            - 기능 유무·사용법은 반드시 도구(listFeatures, searchFeatures, getFeature)를 호출해 얻은 정보로만 답한다.
              추측으로 없는 기능을 지어내지 않고, 도구 결과에 없으면 "현재 지원하지 않는 기능"이라 정직하게 말한다.
            - "어디서/어디에/위치" 같은 위치 질문은 위 사이트 구조로 구체적인 메뉴 위치를 알려준다.
            - 주문/상품은 lookupOrder/searchProducts 도구를 쓴다. 주문 조회는 주문번호와 주문자명이 모두 있어야 하며 없으면 정중히 요청한다.
            - 회원 본인 데이터(내 포인트/쿠폰/주문 등)는 로그인이 필요하다고 안내한다(여기서는 직접 조회 불가).
            - 이전 대화 맥락을 반영한다. "구매는?", "그건?"처럼 짧은 후속 질문은 직전 주제에 이어서 해석한다.
            - 기능 상태가 데모/외부연동 대기면 그 점을 함께 알려준다.
            - 답변은 간결하고 자연스러운 한국어로, 조사를 올바르게 쓰고, 필요하면 단계로 안내한다.
            - 사용자의 질문에 제대로 답하지 못했거나 의도를 이해하지 못한 경우, 답변 맨 끝에 정확히
              [UNRESOLVED] 토큰을 덧붙여라(운영자가 제거하며, 미답변 학습 큐 수집에 쓰인다). 정상적으로
              답했을 때는 절대 붙이지 않는다.
            """;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ChatToolExecutor toolExecutor;
    private final RuleChatProvider fallback;
    private final org.streamhub.api.v1.chat.ChatKnowledgeService knowledgeService;
    private final ArrayNode toolsNode;

    public LlmChatProvider(
            @Value("${app.chat.llm.api-key:}") String apiKey,
            @Value("${app.chat.llm.model:gemini-2.5-flash}") String model,
            @Value("${app.chat.llm.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ChatToolExecutor toolExecutor,
            RuleChatProvider fallback,
            org.streamhub.api.v1.chat.ChatKnowledgeService knowledgeService) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.toolExecutor = toolExecutor;
        this.fallback = fallback;
        this.knowledgeService = knowledgeService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = restClientBuilder.requestFactory(factory).build();
        this.toolsNode = buildToolDeclarations();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public ChatReply reply(String message, List<ChatTurn> history) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("Gemini api-key 미설정 — RuleChatProvider로 폴백");
            return fallback.reply(message, history);
        }
        try {
            return geminiReply(message, history);
        } catch (Exception e) {
            log.warn("Gemini 호출 실패 — RuleChatProvider로 폴백: {}", e.toString());
            return fallback.reply(message, history);
        }
    }

    /** Runs the model↔tool loop and returns the final natural-language reply. */
    private ChatReply geminiReply(String message, List<ChatTurn> history) {
        ArrayNode contents = objectMapper.createArrayNode();
        // Prior turns first (multi-turn context), then the current user message.
        for (ChatTurn turn : history) {
            contents.add(textContent(turn.role() == ChatRole.USER ? "user" : "model", turn.content()));
        }
        contents.add(textContent("user", message));

        List<String> calledTools = new ArrayList<>();
        List<ChatCard> cards = new ArrayList<>();
        for (int iter = 0; iter < MAX_TOOL_ITERS; iter++) {
            JsonNode response = callGemini(contents);
            JsonNode content = response.path("candidates").path(0).path("content");
            JsonNode parts = content.path("parts");

            List<JsonNode> functionCalls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (JsonNode part : parts) {
                if (part.has("functionCall")) {
                    functionCalls.add(part.get("functionCall"));
                } else if (part.has("text")) {
                    text.append(part.get("text").asText());
                }
            }

            if (functionCalls.isEmpty()) {
                String body = text.toString().isBlank()
                        ? "죄송합니다. 답변을 생성하지 못했습니다. 다시 질문해 주세요."
                        : text.toString().trim();
                // Self-reported gap (A): strip the sentinel and mark FALLBACK so the question is
                // collected into the learning queue.
                ChatIntent intent = inferIntent(calledTools);
                if (body.contains("[UNRESOLVED]")) {
                    body = body.replace("[UNRESOLVED]", "").trim();
                    intent = ChatIntent.FALLBACK;
                }
                return new ChatReply(body, intent, List.of(), cards);
            }

            // Echo the model's function-call turn, then append each tool result and loop again.
            contents.add(content.deepCopy());
            ArrayNode resultParts = objectMapper.createArrayNode();
            for (JsonNode call : functionCalls) {
                String name = call.path("name").asText();
                calledTools.add(name);
                // Rich-message cards (G): attach product tiles when the model looks up products.
                if ("searchProducts".equals(name)) {
                    cards.addAll(toolExecutor.productCards(call.path("args").path("keyword").asText("")));
                }
                String result = dispatchTool(name, call.path("args"));
                resultParts.add(functionResponsePart(name, result));
            }
            ObjectNode toolTurn = objectMapper.createObjectNode();
            toolTurn.put("role", FUNCTION_ROLE);
            toolTurn.set("parts", resultParts);
            contents.add(toolTurn);
        }
        // Tool loop exhausted without a final text answer — fall back.
        log.warn("Gemini 도구 루프 한도 초과 — RuleChatProvider로 폴백");
        return fallback.reply(message, history);
    }

    /** Executes one tool call by name, returning the tool's text result. */
    private String dispatchTool(String name, JsonNode args) {
        return switch (name) {
            case "listFeatures" -> toolExecutor.featureOverview();
            case "searchFeatures" -> toolExecutor.searchFeatures(args.path("query").asText(""));
            case "getFeature" -> toolExecutor.getFeature(args.path("id").asText(""));
            case "lookupOrder" -> toolExecutor.lookupOrder(
                    args.path("orderNo").asText(""), args.path("name").asText(""));
            case "searchProducts" -> toolExecutor.searchProducts(args.path("keyword").asText(""));
            default -> "알 수 없는 도구입니다: " + name;
        };
    }

    /** Best-effort intent for stored metadata, from the tools the model chose to call. */
    private ChatIntent inferIntent(List<String> calledTools) {
        if (calledTools.contains("lookupOrder")) {
            return ChatIntent.ORDER_LOOKUP;
        }
        if (calledTools.contains("searchProducts")) {
            return ChatIntent.PRODUCT_INQUIRY;
        }
        if (calledTools.contains("searchFeatures") || calledTools.contains("getFeature")) {
            return ChatIntent.FEATURE_GUIDE;
        }
        return ChatIntent.FAQ;
    }

    private JsonNode callGemini(ArrayNode contents) {
        ObjectNode request = objectMapper.createObjectNode();
        ObjectNode sys = objectMapper.createObjectNode();
        ArrayNode sysParts = objectMapper.createArrayNode();
        // Base instruction + admin-taught knowledge (FAQ), so operators can teach the bot live.
        sysParts.add(objectMapper.createObjectNode()
                .put("text", SYSTEM_PROMPT + knowledgeService.promptBlock()));
        sys.set("parts", sysParts);
        request.set("systemInstruction", sys);
        request.set("contents", contents);
        request.set("tools", toolsNode);

        String uri = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;
        JsonNode body = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        if (body == null) {
            throw new IllegalStateException("Gemini 응답 본문이 비어 있습니다");
        }
        return body;
    }

    private ObjectNode textContent(String role, String text) {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("role", role);
        ArrayNode parts = objectMapper.createArrayNode();
        parts.add(objectMapper.createObjectNode().put("text", text));
        content.set("parts", parts);
        return content;
    }

    private ObjectNode functionResponsePart(String name, String result) {
        ObjectNode part = objectMapper.createObjectNode();
        ObjectNode fr = objectMapper.createObjectNode();
        fr.put("name", name);
        fr.set("response", objectMapper.createObjectNode().put("result", result));
        part.set("functionResponse", fr);
        return part;
    }

    /** Builds the Gemini {@code tools[0].functionDeclarations} array once at startup. */
    private ArrayNode buildToolDeclarations() {
        ArrayNode decls = objectMapper.createArrayNode();
        decls.add(declaration("listFeatures",
                "그레이스온의 전체 기능 개요를 도메인별로 나열한다. '어떤 기능이 있어?' 같은 광범위한 질문에 사용.",
                noParams()));
        decls.add(declaration("searchFeatures",
                "그레이스온에 어떤 기능이 있는지, 사용법은 무엇인지 기능 카탈로그에서 검색한다.",
                stringParams("query", "찾을 기능 키워드. 예: 쿠폰, 주문, 포인트 적립, 교회찾기")));
        decls.add(declaration("getFeature",
                "특정 기능 id의 사용법 상세를 가져온다.",
                stringParams("id", "기능 슬러그 id. 예: orders, coupons, churches")));
        decls.add(declaration("lookupOrder",
                "주문번호와 주문자명으로 주문/배송 상태를 조회한다(둘 다 필수).",
                twoStringParams("orderNo", "주문번호(YYYYMMDD-XXXXXX)",
                        "name", "주문자명")));
        decls.add(declaration("searchProducts",
                "상품명 키워드로 판매 상품의 가격·재고를 조회한다.",
                stringParams("keyword", "상품명 키워드")));

        ArrayNode tools = objectMapper.createArrayNode();
        ObjectNode tool = objectMapper.createObjectNode();
        tool.set("functionDeclarations", decls);
        tools.add(tool);
        return tools;
    }

    private ObjectNode declaration(String name, String description, ObjectNode parameters) {
        ObjectNode decl = objectMapper.createObjectNode();
        decl.put("name", name);
        decl.put("description", description);
        decl.set("parameters", parameters);
        return decl;
    }

    private ObjectNode noParams() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "OBJECT");
        params.set("properties", objectMapper.createObjectNode());
        return params;
    }

    private ObjectNode stringParams(String field, String description) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "OBJECT");
        ObjectNode props = objectMapper.createObjectNode();
        props.set(field, objectMapper.createObjectNode().put("type", "STRING").put("description", description));
        params.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add(field);
        params.set("required", required);
        return params;
    }

    private ObjectNode twoStringParams(String f1, String d1, String f2, String d2) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "OBJECT");
        ObjectNode props = objectMapper.createObjectNode();
        props.set(f1, objectMapper.createObjectNode().put("type", "STRING").put("description", d1));
        props.set(f2, objectMapper.createObjectNode().put("type", "STRING").put("description", d2));
        params.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add(f1);
        required.add(f2);
        params.set("required", required);
        return params;
    }
}

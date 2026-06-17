// Chatbot client (C5). The public site does NOT use orval — this is a hand-written fetch
// layer mirroring src/lib/api.ts conventions (ResultDTO envelope, ApiError, base URL).
//
// Backend seam: the real endpoint POST /v1/chat/send already exists (org.streamhub.api.v1.chat),
// but at the time of writing /v1/chat/** is not yet in SecurityConfig.PUBLIC_PATHS, so a live
// call returns 4010 "인증이 필요합니다". To keep the demo widget working today AND auto-upgrade to
// the backend once the path is whitelisted, `sendChat` tries the backend first and transparently
// falls back to the local rule-based mock on any failure. Remove the try/catch fallback once the
// endpoint is public to make the backend authoritative.

import { ApiError } from "./api";
import type { ResultDTO } from "./types";

const BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";
const SUCCESS_CODE = "0000";

/** Classified user intent — mirrors backend org.streamhub.api.v1.chat.entity.ChatIntent. */
export type ChatIntent = "PRODUCT_INQUIRY" | "ORDER_LOOKUP" | "FAQ" | "FALLBACK";

/** Author of a chat message — mirrors backend ChatRole. */
export type ChatRole = "USER" | "BOT";

/** Backend reply payload (ChatReplyDto). `testMode` is always true in the demo. */
export interface ChatReply {
  text: string;
  intent: ChatIntent;
  quickReplies: string[];
  testMode: boolean;
  /** True when produced by the local mock rather than the backend (UI hint only). */
  mocked?: boolean;
}

/** A single rendered chat bubble in the widget. */
export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  intent?: ChatIntent;
}

interface ChatSendRequest {
  sessionKey: string;
  message: string;
}

/**
 * Sends a user message. Tries the backend first; on any error (e.g. 4010 before the path is
 * whitelisted, or network failure) returns a rule-based mock reply so the demo always responds.
 */
export async function sendChat(sessionKey: string, message: string): Promise<ChatReply> {
  try {
    return await sendChatBackend({ sessionKey, message });
  } catch {
    // Backend unavailable or not yet public — degrade to the local rule-based scenario.
    return mockReply(message);
  }
}

/** Direct backend call (no fallback). Throws ApiError on failure. */
async function sendChatBackend(body: ChatSendRequest): Promise<ChatReply> {
  let res: Response;
  try {
    res = await fetch(`${BASE}/v1/chat/send`, {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch {
    throw new ApiError("서버에 연결할 수 없습니다.", 0);
  }

  let envelope: ResultDTO<ChatReply> | null = null;
  try {
    envelope = (await res.json()) as ResultDTO<ChatReply>;
  } catch {
    envelope = null;
  }

  if (!res.ok || !envelope || envelope.resultCode !== SUCCESS_CODE) {
    throw new ApiError(envelope?.resultMessage ?? "챗봇 응답에 실패했습니다.", res.status);
  }
  return { ...envelope.resultObject, mocked: false };
}

// ── Rule-based mock (client scenario) ────────────────────────────────────────
// Mirrors the backend's IntentClassifier + RuleChatProvider intent set: FAQ · 주문조회 · 상품/교회문의.
// Keyword sets are lowercase-matched. This is the offline twin of the server rules.

interface Rule {
  intent: ChatIntent;
  keywords: string[];
  reply: string;
  quickReplies: string[];
}

const QUICK_DEFAULT = ["배송비 얼마예요?", "주문 조회하고 싶어요", "예배 시간 알려주세요"];

const RULES: Rule[] = [
  {
    intent: "FAQ",
    keywords: ["배송비", "배송", "택배", "얼마"],
    reply:
      "배송비는 3,000원이며, 30,000원 이상 구매 시 무료배송입니다. 보통 결제 후 2~3일 내 발송됩니다. (데모 응답)",
    quickReplies: ["환불 규정이 궁금해요", "주문 조회하고 싶어요", QUICK_DEFAULT[2]],
  },
  {
    intent: "FAQ",
    keywords: ["환불", "반품", "취소"],
    reply:
      "단순 변심 환불은 수령 후 7일 이내 가능하며 왕복 배송비가 부과됩니다. 불량/오배송은 전액 환불됩니다. (데모 응답)",
    quickReplies: ["배송비 얼마예요?", "주문 조회하고 싶어요", QUICK_DEFAULT[2]],
  },
  {
    intent: "FAQ",
    keywords: ["예배", "시간", "예배시간", "주일"],
    reply:
      "주일예배는 오전 11시, 수요예배는 저녁 7시 30분입니다. 온라인 생중계는 StreamHub 영상 탭에서 보실 수 있어요. (데모 응답)",
    quickReplies: ["헌금은 어떻게 하나요?", "배송비 얼마예요?", "주문 조회하고 싶어요"],
  },
  {
    intent: "FAQ",
    keywords: ["헌금", "후원", "기부"],
    reply:
      "온라인 헌금/후원은 마이페이지에서 가능합니다. 현재는 데모 모드라 실제 결제는 이뤄지지 않습니다. (데모 응답)",
    quickReplies: ["예배 시간 알려주세요", "배송비 얼마예요?", "주문 조회하고 싶어요"],
  },
  {
    intent: "ORDER_LOOKUP",
    keywords: ["주문", "배송조회", "조회", "운송장", "어디"],
    reply:
      "주문 조회를 도와드릴게요. 주문번호(예: 20260617-0001)와 주문자명을 함께 알려주시면 상태를 확인해 드립니다. (데모 — 실제 조회는 로그인 후 가능)",
    quickReplies: ["배송비 얼마예요?", "환불 규정이 궁금해요", QUICK_DEFAULT[2]],
  },
  {
    intent: "PRODUCT_INQUIRY",
    keywords: ["앨범", "굿즈", "상품", "재고", "가격", "음반", "찬양"],
    reply:
      "현재 인기 상품은 '찬양 1집 앨범(₩15,000)', '워십 머그컵(₩12,000)', '말씀 다이어리(₩9,000)' 입니다. 자세한 재고는 굿즈 페이지에서 확인해 주세요. (데모 응답)",
    quickReplies: ["배송비 얼마예요?", "주문 조회하고 싶어요", "환불 규정이 궁금해요"],
  },
];

/** Classifies a message against the rule keyword sets and returns a mock reply. */
export function mockReply(message: string): ChatReply {
  const text = message.toLowerCase();
  const matched = RULES.find((rule) => rule.keywords.some((kw) => text.includes(kw)));

  if (matched) {
    return {
      text: matched.reply,
      intent: matched.intent,
      quickReplies: matched.quickReplies,
      testMode: true,
      mocked: true,
    };
  }

  return {
    text:
      "무엇을 도와드릴까요? 배송/환불 같은 자주 묻는 질문, 주문 조회, 상품/예배 안내를 도와드릴 수 있어요. 아래 버튼을 눌러보세요. (데모 챗봇)",
    intent: "FALLBACK",
    quickReplies: QUICK_DEFAULT,
    testMode: true,
    mocked: true,
  };
}

/** Greeting shown when the widget first opens. */
export function greeting(): ChatMessage {
  return {
    id: "greeting",
    role: "BOT",
    content:
      "안녕하세요! StreamHub 도우미예요. 배송·환불·주문조회·상품/예배 안내를 도와드릴게요. 무엇이 궁금하세요?",
    intent: "FALLBACK",
  };
}

/** Default quick-reply chips for the opening state. */
export const INITIAL_QUICK_REPLIES = QUICK_DEFAULT;

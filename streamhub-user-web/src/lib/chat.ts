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
import { meApi } from "./me";
import { orderApi, ORDER_STATUS_LABELS } from "./orders";

const BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";
const SUCCESS_CODE = "0000";

/** Classified user intent — mirrors backend org.streamhub.api.v1.chat.entity.ChatIntent. */
export type ChatIntent =
  | "PRODUCT_INQUIRY"
  | "CONTENT_SEARCH"
  | "ORDER_LOOKUP"
  | "FAQ"
  | "FEATURE_GUIDE"
  | "FALLBACK";

/** Author of a chat message — mirrors backend ChatRole. */
export type ChatRole = "USER" | "BOT";

/** A rich-message card (G) — product/content tile with a deep link. Mirrors backend ChatCard. */
export interface ChatCard {
  title: string;
  subtitle: string | null;
  imageUrl: string | null;
  href: string | null;
  badge: string | null;
}

/** Backend reply payload (ChatReplyDto). `testMode` is always true in the demo. */
export interface ChatReply {
  text: string;
  intent: ChatIntent;
  quickReplies: string[];
  /** Rich-message cards (product/content tiles); empty/absent for plain replies. */
  cards?: ChatCard[];
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
  cards?: ChatCard[];
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
      "주일예배는 오전 11시, 수요예배는 저녁 7시 30분입니다. 예배 영상은 은혜온 영상 탭에서 다시 보실 수 있어요. (데모 응답)",
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

// ── Personalised "check my …" answers (client-side, auth-aware) ──────────────
// The widget calls answerLocally() before the backend, but it now handles ONLY the logged-in
// "check my data" questions (points/coupons/orders/…) that need the member token. Everything else —
// FAQ, order lookup, product search, and all feature existence/how-to guidance — is answered
// authoritatively by the backend chatbot (feature catalog + LLM), so the site has a single source
// of truth. Returns null when nothing personal matches → caller falls through to sendChat.

/** A summarising fetch over a member endpoint (logged-in "check my …" answers). */
interface StatusCheck {
  keys: string[];
  /** Display label for the login-needed prompt (correct subject particle is added automatically). */
  label: string;
  /** Returns a one-line summary string. */
  summarise: (token: string) => Promise<string>;
}

const STATUS_CHECKS: StatusCheck[] = [
  {
    keys: ["포인트"],
    label: "포인트",
    summarise: async (token) => {
      const p = await meApi.points(token, 0, 1);
      return `현재 보유 포인트는 ${p.balance.toLocaleString()}P 입니다. 자세한 적립·사용 내역은 마이페이지 '포인트'에서 볼 수 있어요.`;
    },
  },
  {
    keys: ["쿠폰"],
    label: "쿠폰",
    summarise: async (token) => {
      const list = await meApi.coupons(token);
      const usable = list.filter((c) => !c.used).length;
      return `사용 가능한 쿠폰이 ${usable}장 있습니다(보유 ${list.length}장). 마이페이지 '쿠폰함'에서 확인하세요.`;
    },
  },
  {
    keys: ["주문", "구매내역", "구매 내역", "배송"],
    label: "구매 내역",
    summarise: async (token) => {
      const page = await orderApi.list(token, 0, 1);
      if (page.totalCount === 0) return "아직 주문 내역이 없어요. 음반을 둘러보고 구매해 보세요!";
      const latest = page.contents[0];
      const status = ORDER_STATUS_LABELS[latest.status] ?? latest.status;
      return `총 ${page.totalCount}건의 주문이 있어요. 최근 주문: ${latest.productName} (${status}). 마이페이지 '구매 내역'에서 영수증까지 볼 수 있어요.`;
    },
  },
  {
    keys: ["후원", "구독", "정기"],
    label: "정기후원·구독",
    summarise: async (token) => {
      const list = await meApi.donations(token);
      const active = list.filter((d) => d.status === "ACTIVE").length;
      return list.length === 0
        ? "진행 중인 정기후원·구독이 없습니다. 마이페이지에서 신청할 수 있어요."
        : `정기후원·구독 ${list.length}건 중 진행중 ${active}건이에요. 마이페이지 '정기후원·구독'에서 관리하세요.`;
    },
  },
  {
    keys: ["알림"],
    label: "알림",
    summarise: async (token) => {
      const page = await meApi.notifications(token, 0, 1);
      return `받은 알림이 ${page.totalCount}건 있어요. 마이페이지 '알림'에서 확인하세요.`;
    },
  },
  {
    keys: ["찜", "재생목록", "플레이리스트"],
    label: "찜한 곡",
    summarise: async (token) => {
      const list = await meApi.favorites(token);
      return `찜한 곡이 ${list.length}곡 있어요. 마이페이지 '내 재생목록'에서 모아 들을 수 있어요.`;
    },
  },
  {
    keys: ["시청", "본 영상", "시청기록"],
    label: "시청 기록",
    summarise: async (token) => {
      const page = await meApi.history(token, 0, 1);
      return `시청 기록이 ${page.totalCount}건 있어요. 마이페이지 '시청 기록'에서 이어보세요.`;
    },
  },
  {
    keys: ["문의", "후기", "리뷰"],
    label: "내 문의·후기",
    summarise: async (token) => {
      const [inq, rev] = await Promise.all([meApi.inquiries(token), meApi.reviews(token)]);
      const answered = inq.filter((i) => i.status === "ANSWERED").length;
      return `내 문의 ${inq.length}건(답변완료 ${answered}건), 작성한 후기 ${rev.length}건이에요. 음반 상세에서 새 문의·후기를 남길 수 있어요.`;
    },
  },
];

// A "check my data" intent must look personal — an explicit first-person possessive OR a clear
// check verb. (We deliberately avoid loose substrings like bare "내", which falsely matches "구매내역".)
const POSSESSIVE = /(^|\s)(내|제|나의|내\s?거|내\s?꺼)\s*/;
const CHECK_VERBS = ["얼마", "몇", "남은", "현황", "확인", "조회", "보여", "알려"];
// Location / "어디서" questions are NOT personal fetches — the backend (catalog + LLM site map)
// answers them, so a navigation question never gets a login wall.
const NAV_MARKERS = ["어디", "위치", "어느 메뉴", "어느 탭", "어느 화면"];

/** Quick replies shown alongside a personal-status answer. */
const GUIDE_QUICK = ["내 포인트 확인", "쿠폰함 보여줘", "주문 조회", "어떤 기능이 있나요?"];

/** Appends the correct Korean subject particle (은/는) from the last character's final consonant. */
function withTopicParticle(word: string): string {
  const code = word.charCodeAt(word.length - 1);
  const hasFinal = code >= 0xac00 && code <= 0xd7a3 && (code - 0xac00) % 28 !== 0;
  return word + (hasFinal ? "은" : "는");
}

function loginNeeded(label: string): ChatReply {
  return {
    text: `${withTopicParticle(label)} 로그인 후 확인할 수 있어요. 하단 'MY' 탭에서 로그인한 뒤 다시 물어봐 주세요.`,
    intent: "FALLBACK",
    quickReplies: ["어떤 기능이 있나요?", ...QUICK_DEFAULT.slice(0, 2)],
    testMode: true,
    mocked: true,
  };
}

/**
 * Client-side resolver for logged-in "check my …" questions ONLY (my points/coupons/orders/…),
 * which need the member token and so can't be answered by the public backend chatbot. Everything
 * else — location ("어디서?"), feature how-to, FAQ, order/product lookup and any follow-up — returns
 * null and is answered by the backend (single brain, single memory), so multi-turn context holds.
 */
export async function answerLocally(message: string, token: string | null): Promise<ChatReply | null> {
  const text = message.toLowerCase().trim();

  // Location/where questions → backend (never a login wall).
  if (NAV_MARKERS.some((m) => text.includes(m))) return null;

  // A bare order number → guest order-lookup (backend lookupOrder, no login needed), not a fetch.
  const hasOrderNo = /\d{8}-\d{3,6}/.test(message);
  // Personal data fetch — only on a clear possessive or check verb, and a matching data area.
  const check = STATUS_CHECKS.find((c) => c.keys.some((k) => text.includes(k)));
  const isPersonalFetch = POSSESSIVE.test(message) || CHECK_VERBS.some((v) => text.includes(v));
  if (check && isPersonalFetch && !hasOrderNo) {
    if (!token) return loginNeeded(check.label);
    try {
      const summary = await check.summarise(token);
      return { text: summary, intent: "FAQ", quickReplies: GUIDE_QUICK, testMode: true, mocked: true };
    } catch {
      return { text: "정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.", intent: "FALLBACK", quickReplies: GUIDE_QUICK, testMode: true, mocked: true };
    }
  }

  return null;
}

/** Greeting shown when the widget first opens. */
export function greeting(): ChatMessage {
  return {
    id: "greeting",
    role: "BOT",
    content:
      "안녕하세요! 은혜온 도우미예요. 사이트의 모든 기능 안내는 물론, 로그인하시면 내 포인트·쿠폰·주문 같은 정보도 바로 확인해 드려요. 무엇이 궁금하세요?",
    intent: "FALLBACK",
  };
}

/** Default quick-reply chips for the opening state. */
export const INITIAL_QUICK_REPLIES = ["어떤 기능이 있나요?", "내 포인트 확인", "주문 조회하고 싶어요"];

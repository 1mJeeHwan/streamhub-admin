import {
  FileVideo,
  HeartHandshake,
  Megaphone,
  MessageSquare,
  Settings,
  ShoppingBag,
  Users,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

/** Seven fixed business domains the admin surfaces span. */
export type Domain =
  | "support"
  | "shop"
  | "content"
  | "member"
  | "community"
  | "marketing"
  | "settings";

/**
 * Honesty status for a catalog card. Every card has a real route + admin API; the
 * status describes how *complete for real operations* the feature is:
 * - `live`: genuinely production-shaped — real business logic, persistence, validation.
 * - `demo`: the screen + API work, but a headline capability is intentionally not built
 *   out (e.g. campaign has no audience-send engine; the chatbot is rule-based, not LLM).
 * - `external`: code-complete behind an adapter seam, but real operation needs a paid
 *   external service key that is not provisioned (e.g. SMS gateway, push delivery).
 */
export type FeatureStatus = "live" | "demo" | "external";

/** A single admin screen entry rendered as a catalog card. */
export interface FeatureCard {
  id: string;
  domain: Domain;
  title: string;
  summary: string;
  status: FeatureStatus;
  href: string;
  gnuboard?: string;
  repoPath?: string;
  thumb: string;
  highlights: string[];
}

/** Filter-chip metadata (label + icon) for a domain. */
export interface DomainMeta {
  key: Domain;
  label: string;
  icon: LucideIcon;
}

/** Honesty-badge metadata for a status. */
export interface StatusMeta {
  key: FeatureStatus;
  label: string;
  emoji: string;
  className: string;
}

export const DOMAIN_META: DomainMeta[] = [
  { key: "support", label: "후원·구독", icon: HeartHandshake },
  { key: "shop", label: "굿즈샵", icon: ShoppingBag },
  { key: "content", label: "콘텐츠", icon: FileVideo },
  { key: "member", label: "회원", icon: Users },
  { key: "community", label: "소통", icon: MessageSquare },
  { key: "marketing", label: "마케팅", icon: Megaphone },
  { key: "settings", label: "설정", icon: Settings },
];

const DOMAIN_BY_KEY: Record<Domain, DomainMeta> = DOMAIN_META.reduce(
  (acc, meta) => {
    acc[meta.key] = meta;
    return acc;
  },
  {} as Record<Domain, DomainMeta>,
);

/** Returns the domain metadata for a key (label + icon). */
export function domainMeta(key: Domain): DomainMeta {
  return DOMAIN_BY_KEY[key];
}

export const STATUS_META: Record<FeatureStatus, StatusMeta> = {
  live: {
    key: "live",
    label: "실동작",
    emoji: "✅",
    className: "bg-emerald-100 text-emerald-700",
  },
  demo: {
    key: "demo",
    label: "데모",
    emoji: "🟡",
    className: "bg-amber-100 text-amber-700",
  },
  external: {
    key: "external",
    label: "외부연동 대기",
    emoji: "🔌",
    className: "bg-sky-100 text-sky-700",
  },
};

/**
 * Full catalog. Every card has a real route + admin API in this repo. The `status`
 * is honest about operational completeness: most are `live` (real logic + persistence
 * + validation); a few are `demo` (screen works, a headline capability is intentionally
 * not built — e.g. campaign audience-send, LLM chat) or `external` (code-complete behind
 * an adapter seam, awaiting a paid external key — e.g. SMS gateway, push delivery).
 * Each card's repoPath points at its page source.
 */
export const FEATURES: FeatureCard[] = [
  // ---- live — verified routes in this repo ----
  {
    id: "dashboard",
    domain: "support",
    title: "통합 운영 대시보드",
    summary: "후원·매출·회원 KPI를 한 화면에 집계",
    status: "live",
    href: "/dashboard",
    gnuboard: "영카트 §5.1 sale1.php",
    repoPath: "streamhub-web/src/app/(protected)/dashboard/page.tsx",
    thumb: "/catalog/dashboard.png",
    highlights: ["KPI 카드", "ApexCharts", "Redis 캐시 집계"],
  },
  {
    id: "members",
    domain: "member",
    title: "회원 관리",
    summary: "동적 검색·일괄 승인/거부가 가능한 회원 목록",
    status: "live",
    href: "/member",
    gnuboard: "§2.1 member_list.php",
    repoPath: "streamhub-web/src/app/(protected)/member/page.tsx",
    thumb: "/catalog/members.png",
    highlights: ["동적 검색", "JPA+MyBatis 하이브리드", "일괄 승인/거부"],
  },
  {
    id: "contents",
    domain: "content",
    title: "설교·음원 관리",
    summary: "업로드·해시태그·복합 조인을 갖춘 콘텐츠 관리",
    status: "live",
    href: "/content",
    gnuboard: "(레퍼런스 서비스 고유)",
    repoPath: "streamhub-web/src/app/(protected)/content/page.tsx",
    thumb: "/catalog/contents.png",
    highlights: ["MinIO 업로드(S3 SDK)", "해시태그 다대다", "복합 조인"],
  },
  {
    id: "action-log",
    domain: "settings",
    title: "감사 로그",
    summary: "SQS 비동기 적재 기반 운영자 감사 로그",
    status: "live",
    href: "/action-log",
    gnuboard: "§2.3 visit_list.php",
    repoPath: "streamhub-web/src/app/(protected)/action-log/page.tsx",
    thumb: "/catalog/action-log.png",
    highlights: ["SQS 비동기 적재", "액션 색상 배지", "SYSTEM 전용"],
  },

  // ---- new live domains (churches / albums / worship / sms) ----
  {
    id: "churches",
    domain: "member",
    title: "교회 찾기·관리",
    summary: "교단·지역 검색과 좌표·예배시간을 갖춘 교회 마스터",
    status: "live",
    href: "/churches",
    gnuboard: "(레퍼런스 서비스 고유)",
    repoPath: "streamhub-web/src/app/(protected)/churches/page.tsx",
    thumb: "/catalog/churches.png",
    highlights: ["교단/지역 검색", "위경도 좌표", "예배시간 다건", "썸네일 업로드"],
  },
  {
    id: "albums",
    domain: "shop",
    title: "CCM 음반 관리",
    summary: "장르·트랙·미리듣기를 갖춘 음반과 굿즈 연동",
    status: "live",
    href: "/albums",
    gnuboard: "(레퍼런스 서비스 고유)",
    repoPath: "streamhub-web/src/app/(protected)/albums/page.tsx",
    thumb: "/catalog/albums.png",
    highlights: ["장르 분류", "트랙 다건(미리듣기)", "굿즈 연동", "커버 업로드"],
  },
  {
    id: "store",
    domain: "shop",
    title: "오프라인 매장 관리",
    summary: "음반 판매 매장의 위치·운영시간 마스터",
    status: "live",
    href: "/stores",
    gnuboard: "(레퍼런스 서비스 고유)",
    repoPath: "streamhub-web/src/app/(protected)/stores/page.tsx",
    thumb: "/catalog/stores.png",
    highlights: ["매장 위치", "운영시간", "단순 CRUD"],
  },
  {
    id: "worship",
    domain: "support",
    title: "예배(새가족) 신청 관리",
    summary: "접수→연락→완료 상태머신을 갖춘 신청 접수함",
    status: "live",
    href: "/worship",
    gnuboard: "(레퍼런스 서비스 고유)",
    repoPath: "streamhub-web/src/app/(protected)/worship/page.tsx",
    thumb: "/catalog/worship.png",
    highlights: ["접수 상태머신", "부서/세례 구분", "동반가족", "기간 검색"],
  },
  {
    id: "sms",
    domain: "marketing",
    title: "문자 발송·이력",
    summary: "SMS/LMS 자동 분기와 발송 이력 (게이트웨이 키 대기 — 데모 발송)",
    status: "external",
    href: "/sms",
    gnuboard: "SMS §7.x history_list.php",
    repoPath: "streamhub-web/src/app/(protected)/sms/page.tsx",
    thumb: "/catalog/sms.png",
    highlights: ["SMS/LMS 자동 분기", "Aligo/Solapi 어댑터 seam", "발송 이력", "실 게이트웨이 키 대기"],
  },
  {
    id: "payment-seam",
    domain: "shop",
    title: "결제 내역 관리",
    summary: "결제·환불 영수증을 주문·회원과 조인해 조회하는 결제 원장",
    status: "live",
    href: "/payment",
    gnuboard: "영카트 §6.x paylist.php",
    repoPath: "streamhub-web/src/app/(protected)/payment/page.tsx",
    thumb: "/catalog/payment.png",
    highlights: ["결제/환불 구분", "PG·거래번호", "기간/수단 필터", "요청→승인 seam"],
  },
  {
    id: "chat-bot",
    domain: "community",
    title: "챗봇 상담",
    summary: "룰베이스 FAQ + 주문/상품 DB 조회 챗봇 (실 LLM 미연동)",
    status: "demo",
    href: "/chat",
    gnuboard: "(레퍼런스 서비스 고유)",
    repoPath: "streamhub-web/src/app/(protected)/chat/page.tsx",
    thumb: "/catalog/chat-bot.png",
    highlights: ["룰베이스 FAQ", "주문/상품 DB 조회", "세션 이력 저장", "LLM 어댑터 seam(키 대기)"],
  },

  // ---- support (후원·구독) ----
  {
    id: "subscription",
    domain: "support",
    title: "정기후원·구독 현황",
    summary: "빌링키·회차·상태를 추적하는 정기결제 현황",
    status: "live",
    href: "/subscription",
    gnuboard: "정기결제 §6.4 paylist.php",
    repoPath: "streamhub-web/src/app/(protected)/subscription/page.tsx",
    thumb: "/catalog/subscription.png",
    highlights: ["빌링키(마스킹)", "회차 추적", "ACTIVE⇄PAUSED 상태머신"],
  },
  {
    id: "subscription-plans",
    domain: "support",
    title: "멤버십 플랜 관리",
    summary: "등급별 혜택과 가격 정책을 정의",
    status: "live",
    href: "/subscription-plan",
    gnuboard: "정기결제 §6.3 itemlist.php",
    repoPath: "streamhub-web/src/app/(protected)/subscription-plan/page.tsx",
    thumb: "/catalog/subscription-plan.png",
    highlights: ["등급별 혜택 JSON", "가격 정책"],
  },
  {
    id: "subscription-calendar",
    domain: "support",
    title: "정기결제 일정",
    summary: "CRON 청구 시뮬레이션과 캘린더 뷰",
    status: "live",
    href: "/billing-calendar",
    gnuboard: "정기결제 §6.5 calendar.php",
    repoPath: "streamhub-web/src/app/(protected)/billing-calendar/page.tsx",
    thumb: "/catalog/billing-calendar.png",
    highlights: ["CRON 청구 시뮬", "캘린더 UI", "공휴일 이동"],
  },
  {
    id: "donation",
    domain: "support",
    title: "후원 내역",
    summary: "단건·정기 후원과 캠페인 연동, 영수증 발행",
    status: "live",
    href: "/donation",
    gnuboard: "영카트 §4.2 / §5.1",
    repoPath: "streamhub-web/src/app/(protected)/donation/page.tsx",
    thumb: "/catalog/donation.png",
    highlights: ["단건/정기 분리", "캠페인 연동", "영수증"],
  },

  // ---- shop (굿즈샵) ----
  {
    id: "orders",
    domain: "shop",
    title: "주문 관리",
    summary: "주문 상태머신과 합계 재계산을 갖춘 주문 목록",
    status: "live",
    href: "/order",
    gnuboard: "영카트 §4.2 orderlist.php",
    repoPath: "streamhub-web/src/app/(protected)/order/page.tsx",
    thumb: "/catalog/order.png",
    highlights: ["주문 상태머신(PLACED→DONE)", "합계 재계산", "AG Grid"],
  },
  {
    id: "goods",
    domain: "shop",
    title: "굿즈 관리",
    summary: "옵션·이미지·인라인 일괄수정이 가능한 상품 관리",
    status: "live",
    href: "/goods",
    gnuboard: "영카트 §4.5 itemlist.php",
    repoPath: "streamhub-web/src/app/(protected)/goods/page.tsx",
    thumb: "/catalog/goods.png",
    highlights: ["인라인 일괄수정", "옵션/이미지", "파레토 분포"],
  },
  {
    id: "goods-category",
    domain: "shop",
    title: "카테고리 관리",
    summary: "계층 코드 기반 3단 트리와 드래그 정렬",
    status: "live",
    href: "/goods/category",
    gnuboard: "영카트 §4.4 categorylist.php",
    repoPath: "streamhub-web/src/app/(protected)/goods/category/page.tsx",
    thumb: "/catalog/goods-category.png",
    highlights: ["3단 트리", "계층 코드", "드래그 정렬"],
  },
  {
    id: "goods-stock",
    domain: "shop",
    title: "옵션·재고 관리",
    summary: "재고·통보수량 인라인 편집과 재입고 알림",
    status: "live",
    href: "/goods/stock",
    gnuboard: "영카트 §4.9/4.11 stocklist",
    repoPath: "streamhub-web/src/app/(protected)/goods/stock/page.tsx",
    thumb: "/catalog/goods-stock.png",
    highlights: ["재고 인라인", "품절 토글", "재입고 알림"],
  },
  {
    id: "goods-inquiry",
    domain: "shop",
    title: "굿즈 문의",
    summary: "답변 상태와 미답변 SLA를 추적",
    status: "live",
    href: "/goods/inquiry",
    gnuboard: "영카트 §4.7 itemqalist.php",
    repoPath: "streamhub-web/src/app/(protected)/goods/inquiry/page.tsx",
    thumb: "/catalog/goods-inquiry.png",
    highlights: ["답변 상태", "미답변 SLA 강조"],
  },
  {
    id: "goods-review",
    domain: "shop",
    title: "굿즈 후기",
    summary: "평점 인라인 편집과 노출 승인 토글",
    status: "live",
    href: "/goods/review",
    gnuboard: "영카트 §4.8 itemuselist.php",
    repoPath: "streamhub-web/src/app/(protected)/goods/review/page.tsx",
    thumb: "/catalog/goods-review.png",
    highlights: ["평점 인라인", "노출 승인 토글"],
  },
  {
    id: "coupons",
    domain: "shop",
    title: "쿠폰 관리",
    summary: "주문에 실제 적용되는 정액·정률 할인 — 사용한도·기간·최소주문 검증",
    status: "live",
    href: "/coupons",
    gnuboard: "영카트 §4.12 couponlist.php",
    repoPath: "streamhub-web/src/app/(protected)/coupons/page.tsx",
    thumb: "/catalog/coupons.png",
    highlights: ["주문 결제 시 적용", "사용한도/기간 검증", "최소주문·최대할인", "절사단위"],
  },

  // ---- member (회원) ----
  {
    id: "points",
    domain: "member",
    title: "포인트(은혜) 원장",
    summary: "증감 원장과 누적 동기화·만료 배치",
    status: "live",
    href: "/point",
    gnuboard: "§2.5 point_list.php",
    repoPath: "streamhub-web/src/app/(protected)/point/page.tsx",
    thumb: "/catalog/point.png",
    highlights: ["증감 원장", "누적 동기화 트랜잭션", "만료 배치"],
  },
  {
    id: "visits",
    domain: "member",
    title: "접속 통계",
    summary: "기간 검색과 IP/UA 파싱, 일별 집계",
    status: "live",
    href: "/visits",
    gnuboard: "§2.3/2.4 visit_search.php",
    repoPath: "streamhub-web/src/app/(protected)/visits/page.tsx",
    thumb: "/catalog/visits.png",
    highlights: ["기간 검색", "IP/UA 파싱", "일별 집계"],
  },

  // ---- content (콘텐츠) ----
  {
    id: "content-stats",
    domain: "content",
    title: "콘텐츠 통계",
    summary: "조회수 Top N과 실 트래킹 이벤트 기반 채널별 시청시간 집계",
    status: "live",
    href: "/content/stats",
    gnuboard: "§3.8 write_count.php",
    repoPath: "streamhub-web/src/app/(protected)/content/stats/page.tsx",
    thumb: "/catalog/content-stats.png",
    highlights: ["조회수 Top N", "실 이벤트(ANALYTICS_EVENT) 시청시간"],
  },

  // ---- community (소통) ----
  {
    id: "boards",
    domain: "community",
    title: "게시판 관리",
    summary: "레벨 권한과 그룹/전체 적용 전파",
    status: "live",
    href: "/boards",
    gnuboard: "§3.1/3.2 board_list/form",
    repoPath: "streamhub-web/src/app/(protected)/boards/page.tsx",
    thumb: "/catalog/boards.png",
    highlights: ["레벨 권한(1~10)", "그룹/전체 전파"],
  },
  {
    id: "posts",
    domain: "community",
    title: "공지·나눔·기도제목",
    summary: "카테고리·비밀글·추천/조회를 갖춘 글 관리",
    status: "live",
    href: "/posts",
    gnuboard: "§3.x write / content",
    repoPath: "streamhub-web/src/app/(protected)/posts/page.tsx",
    thumb: "/catalog/posts.png",
    highlights: ["카테고리", "비밀글", "추천/조회"],
  },
  {
    id: "inquiry",
    domain: "community",
    title: "1:1 문의",
    summary: "답변 상태머신과 미답변 큐",
    status: "live",
    href: "/inquiry",
    gnuboard: "§3.6 qa_config / qa_content",
    repoPath: "streamhub-web/src/app/(protected)/inquiry/page.tsx",
    thumb: "/catalog/inquiry.png",
    highlights: ["답변 상태머신(OPEN→ANSWERED)", "미답변 큐"],
  },

  // ---- marketing (마케팅) ----
  {
    id: "banners",
    domain: "marketing",
    title: "배너 관리",
    summary: "위치·디바이스·노출기간 필터와 순서 정렬",
    status: "live",
    href: "/banners",
    gnuboard: "영카트 §5.6 bannerlist.php",
    repoPath: "streamhub-web/src/app/(protected)/banners/page.tsx",
    thumb: "/catalog/banners.png",
    highlights: ["위치/디바이스 필터", "노출기간", "순서 정렬"],
  },
  {
    id: "campaigns",
    domain: "marketing",
    title: "캠페인·이벤트",
    summary: "캠페인 정의·상태머신 관리 (대상 발송 실행엔진 없음 — 정의 카탈로그)",
    status: "demo",
    href: "/campaigns",
    gnuboard: "영카트 §5.5 itemevent.php",
    repoPath: "streamhub-web/src/app/(protected)/campaigns/page.tsx",
    thumb: "/catalog/campaigns.png",
    highlights: ["상태머신(DRAFT→ACTIVE→ENDED)", "연결상품/기간", "실행엔진 없음"],
  },
  {
    id: "notifications",
    domain: "marketing",
    title: "알림센터(발송 로그)",
    summary: "채널별 발송 로그 조회 (FCM/SMTP 미연동 — 실발송 없음)",
    status: "external",
    href: "/notifications",
    gnuboard: "SMS §7.3 history_list.php",
    repoPath: "streamhub-web/src/app/(protected)/notifications/page.tsx",
    thumb: "/catalog/notifications.png",
    highlights: ["SMS/PUSH/EMAIL 로그", "발송 채널 키 대기", "실발송 없음"],
  },
];

/**
 * assertUniqueIds throws on duplicate card ids so a careless copy-paste fails
 * loudly at module load instead of silently rendering two cards with the same
 * React key. Honors the project's assertUniqueValue gate.
 */
export function assertUniqueIds(cards: FeatureCard[]): void {
  const seen = new Set<string>();
  for (const card of cards) {
    if (seen.has(card.id)) {
      throw new Error(`features.catalog: 중복된 카드 id "${card.id}"`);
    }
    seen.add(card.id);
  }
}

assertUniqueIds(FEATURES);

/** Counts per honesty status, for the catalog subhead. */
export const LIVE_COUNT = FEATURES.filter((card) => card.status === "live").length;
export const DEMO_COUNT = FEATURES.filter((card) => card.status === "demo").length;
export const EXTERNAL_COUNT = FEATURES.filter((card) => card.status === "external").length;

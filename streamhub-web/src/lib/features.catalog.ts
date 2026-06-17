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
 * Honesty status for a catalog card.
 * - `live`: backed by a real route + API in this codebase.
 * - `mock`: designed and seeded, route not yet wired (button disabled).
 * - `wip`: in progress, not navigable.
 */
export type FeatureStatus = "live" | "mock" | "wip";

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
  mock: {
    key: "mock",
    label: "목업",
    emoji: "🟡",
    className: "bg-amber-100 text-amber-700",
  },
  wip: {
    key: "wip",
    label: "진행중",
    emoji: "🔧",
    className: "bg-slate-200 text-slate-600",
  },
};

const PLACEHOLDER_THUMB = "/catalog/placeholder.png";

/**
 * Full catalog. Only the four routes that genuinely exist in this codebase
 * (dashboard / member / content / action-log) are `live`; everything else is
 * `mock`/`wip` so viewers never hit a broken deep link.
 */
export const FEATURES: FeatureCard[] = [
  // ---- live (4) — verified routes in this repo ----
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
    gnuboard: "(PalmPlus 고유)",
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
    thumb: PLACEHOLDER_THUMB,
    highlights: ["SQS 비동기 적재", "액션 색상 배지", "SYSTEM 전용"],
  },

  // ---- support (후원·구독) ----
  {
    id: "subscription",
    domain: "support",
    title: "정기후원·구독 현황",
    summary: "빌링키·회차·상태를 추적하는 정기결제 현황",
    status: "mock",
    href: "/subscription",
    gnuboard: "정기결제 §6.4 paylist.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["빌링키(마스킹)", "회차 추적", "ACTIVE⇄PAUSED 상태머신"],
  },
  {
    id: "subscription-plans",
    domain: "support",
    title: "멤버십 플랜 관리",
    summary: "등급별 혜택과 가격 정책을 정의",
    status: "wip",
    href: "/subscription/plans",
    gnuboard: "정기결제 §6.3 itemlist.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["등급별 혜택 JSON", "가격 정책"],
  },
  {
    id: "subscription-calendar",
    domain: "support",
    title: "정기결제 일정",
    summary: "CRON 청구 시뮬레이션과 캘린더 뷰",
    status: "mock",
    href: "/subscription/calendar",
    gnuboard: "정기결제 §6.5 calendar.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["CRON 청구 시뮬", "캘린더 UI", "공휴일 이동"],
  },
  {
    id: "donation",
    domain: "support",
    title: "후원 내역",
    summary: "단건·정기 후원과 캠페인 연동, 영수증 발행",
    status: "mock",
    href: "/donation",
    gnuboard: "영카트 §4.2 / §5.1",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["단건/정기 분리", "캠페인 연동", "영수증"],
  },

  // ---- shop (굿즈샵) ----
  {
    id: "orders",
    domain: "shop",
    title: "주문 관리",
    summary: "주문 상태머신과 합계 재계산을 갖춘 주문 목록",
    status: "mock",
    href: "/order",
    gnuboard: "영카트 §4.2 orderlist.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["주문 상태머신(PLACED→DONE)", "합계 재계산", "AG Grid"],
  },
  {
    id: "goods",
    domain: "shop",
    title: "굿즈 관리",
    summary: "옵션·이미지·인라인 일괄수정이 가능한 상품 관리",
    status: "mock",
    href: "/goods",
    gnuboard: "영카트 §4.5 itemlist.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["인라인 일괄수정", "옵션/이미지", "파레토 분포"],
  },
  {
    id: "goods-category",
    domain: "shop",
    title: "카테고리 관리",
    summary: "계층 코드 기반 3단 트리와 드래그 정렬",
    status: "mock",
    href: "/goods/category",
    gnuboard: "영카트 §4.4 categorylist.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["3단 트리", "계층 코드", "드래그 정렬"],
  },
  {
    id: "goods-stock",
    domain: "shop",
    title: "옵션·재고 관리",
    summary: "재고·통보수량 인라인 편집과 재입고 알림",
    status: "mock",
    href: "/goods/stock",
    gnuboard: "영카트 §4.9/4.11 stocklist",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["재고 인라인", "품절 토글", "재입고 알림"],
  },
  {
    id: "goods-inquiry",
    domain: "shop",
    title: "굿즈 문의",
    summary: "답변 상태와 미답변 SLA를 추적",
    status: "mock",
    href: "/goods/inquiry",
    gnuboard: "영카트 §4.7 itemqalist.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["답변 상태", "미답변 SLA 강조"],
  },
  {
    id: "goods-review",
    domain: "shop",
    title: "굿즈 후기",
    summary: "평점 인라인 편집과 노출 승인 토글",
    status: "mock",
    href: "/goods/review",
    gnuboard: "영카트 §4.8 itemuselist.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["평점 인라인", "노출 승인 토글"],
  },
  {
    id: "coupons",
    domain: "shop",
    title: "쿠폰 관리",
    summary: "정액·정률 할인과 최소주문·최대할인 정책",
    status: "mock",
    href: "/coupons",
    gnuboard: "영카트 §4.12 couponlist.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["정액/정률", "최소주문·최대할인", "절사단위"],
  },

  // ---- member (회원) ----
  {
    id: "points",
    domain: "member",
    title: "포인트(은혜) 원장",
    summary: "증감 원장과 누적 동기화·만료 배치",
    status: "mock",
    href: "/point",
    gnuboard: "§2.5 point_list.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["증감 원장", "누적 동기화 트랜잭션", "만료 배치"],
  },
  {
    id: "visits",
    domain: "member",
    title: "접속 통계",
    summary: "기간 검색과 IP/UA 파싱, 일별 집계",
    status: "mock",
    href: "/visits",
    gnuboard: "§2.3/2.4 visit_search.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["기간 검색", "IP/UA 파싱", "일별 집계"],
  },

  // ---- content (콘텐츠) ----
  {
    id: "content-stats",
    domain: "content",
    title: "콘텐츠 통계",
    summary: "조회수 Top N과 채널별 시청시간 집계",
    status: "mock",
    href: "/content/stats",
    gnuboard: "§3.8 write_count.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["조회수 Top N", "채널별 시청시간 집계"],
  },

  // ---- community (소통) ----
  {
    id: "boards",
    domain: "community",
    title: "게시판 관리",
    summary: "레벨 권한과 그룹/전체 적용 전파",
    status: "mock",
    href: "/boards",
    gnuboard: "§3.1/3.2 board_list/form",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["레벨 권한(1~10)", "그룹/전체 전파"],
  },
  {
    id: "posts",
    domain: "community",
    title: "공지·나눔·기도제목",
    summary: "카테고리·비밀글·추천/조회를 갖춘 글 관리",
    status: "mock",
    href: "/posts",
    gnuboard: "§3.x write / content",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["카테고리", "비밀글", "추천/조회"],
  },
  {
    id: "inquiry",
    domain: "community",
    title: "1:1 문의",
    summary: "답변 상태머신과 미답변 큐",
    status: "mock",
    href: "/inquiry",
    gnuboard: "§3.6 qa_config / qa_content",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["답변 상태머신(OPEN→ANSWERED)", "미답변 큐"],
  },

  // ---- marketing (마케팅) ----
  {
    id: "banners",
    domain: "marketing",
    title: "배너 관리",
    summary: "위치·디바이스·노출기간 필터와 순서 정렬",
    status: "mock",
    href: "/banners",
    gnuboard: "영카트 §5.6 bannerlist.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["위치/디바이스 필터", "노출기간", "순서 정렬"],
  },
  {
    id: "campaigns",
    domain: "marketing",
    title: "캠페인·이벤트",
    summary: "특별헌금·신간 등 연결상품 일괄 캠페인",
    status: "wip",
    href: "/campaigns",
    gnuboard: "영카트 §5.5 itemevent.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["특별헌금/신간", "연결상품 일괄", "스파이크 연동"],
  },
  {
    id: "notifications",
    domain: "marketing",
    title: "알림센터(발송 로그)",
    summary: "채널별 발송 로그와 성공/실패 추적 (실발송 X)",
    status: "mock",
    href: "/notifications",
    gnuboard: "SMS §7.3 history_list.php",
    thumb: PLACEHOLDER_THUMB,
    highlights: ["SMS/PUSH/EMAIL", "성공/실패", "실발송 없음"],
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

/** Count of genuinely live (route-backed) cards, for the honesty subhead. */
export const LIVE_COUNT = FEATURES.filter((card) => card.status === "live").length;

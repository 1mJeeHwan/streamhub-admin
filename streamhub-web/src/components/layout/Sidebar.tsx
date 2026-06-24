"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSession } from "next-auth/react";
import {
  LayoutDashboard,
  Gauge,
  LayoutGrid,
  Users,
  Coins,
  FileVideo,
  ShoppingBag,
  ClipboardList,
  Gem,
  CreditCard,
  HeartHandshake,
  CalendarClock,
  ScrollText,
  Church,
  HandHeart,
  Disc3,
  Store,
  MessageSquare,
  BarChart3,
  Activity,
  HeartPulse,
  FolderTree,
  Boxes,
  MessageCircleQuestion,
  Star,
  Ticket,
  FileText,
  Headset,
  Bot,
  BookOpen,
  Image as ImageIcon,
  Images,
  ListMusic,
  Megaphone,
  Info,
  Bell,
  TrendingUp,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { canViewSystem } from "@/lib/auth-utils";

interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  systemOnly?: boolean;
}

interface NavSection {
  title: string;
  items: NavItem[];
}

export const NAV_SECTIONS: NavSection[] = [
  {
    title: "운영",
    items: [
      { label: "통합 운영 대시보드", href: "/admin-ops", icon: Gauge },
      { label: "대시보드", href: "/dashboard", icon: LayoutDashboard },
      { label: "기능 카탈로그", href: "/catalog", icon: LayoutGrid },
      { label: "전체 기능도", href: "/feature-map", icon: FolderTree },
      { label: "접속 통계", href: "/visits", icon: Activity },
      { label: "콘텐츠 분석", href: "/analytics", icon: TrendingUp },
    ],
  },
  {
    title: "회원·콘텐츠",
    items: [
      { label: "회원관리", href: "/member", icon: Users },
      { label: "포인트 원장", href: "/point", icon: Coins },
      { label: "콘텐츠관리", href: "/content", icon: FileVideo },
      { label: "콘텐츠 통계", href: "/content/stats", icon: BarChart3 },
      { label: "플레이리스트", href: "/playlists", icon: ListMusic },
    ],
  },
  {
    title: "교회·예배",
    items: [
      { label: "교회관리", href: "/churches", icon: Church },
      { label: "예배신청", href: "/worship", icon: HandHeart },
    ],
  },
  {
    title: "커머스",
    items: [
      { label: "굿즈관리", href: "/goods", icon: ShoppingBag },
      { label: "카테고리 관리", href: "/goods/category", icon: FolderTree },
      { label: "옵션·재고 관리", href: "/goods/stock", icon: Boxes },
      { label: "굿즈 문의", href: "/goods/inquiry", icon: MessageCircleQuestion },
      { label: "굿즈 후기", href: "/goods/review", icon: Star },
      { label: "주문관리", href: "/order", icon: ClipboardList },
      { label: "앨범관리", href: "/albums", icon: Disc3 },
      { label: "매장관리", href: "/stores", icon: Store },
      { label: "쿠폰 관리", href: "/coupons", icon: Ticket },
      { label: "결제내역", href: "/payment", icon: CreditCard },
    ],
  },
  {
    title: "후원·구독",
    items: [
      { label: "멤버십 플랜", href: "/subscription-plan", icon: Gem, systemOnly: true },
      { label: "구독 현황", href: "/subscription", icon: CreditCard },
      { label: "후원 내역", href: "/donation", icon: HeartHandshake, systemOnly: true },
      { label: "결제일정", href: "/billing-calendar", icon: CalendarClock },
    ],
  },
  {
    title: "소통",
    items: [
      { label: "게시판 관리", href: "/boards", icon: ClipboardList },
      { label: "게시글 관리", href: "/posts", icon: FileText },
      { label: "1:1 문의", href: "/inquiry", icon: Headset },
      { label: "챗봇 상담", href: "/chat", icon: Bot },
      { label: "챗봇 지식관리", href: "/chat-knowledge", icon: BookOpen },
      { label: "챗봇 분석", href: "/chat-analytics", icon: BarChart3 },
      { label: "SMS", href: "/sms", icon: MessageSquare },
    ],
  },
  {
    title: "마케팅",
    items: [
      { label: "배너 관리", href: "/banners", icon: ImageIcon },
      { label: "미디어 라이브러리", href: "/media", icon: Images },
      { label: "안내창 관리", href: "/announcement", icon: Info },
      { label: "캠페인·이벤트", href: "/campaigns", icon: Megaphone },
      { label: "알림센터", href: "/notifications", icon: Bell },
    ],
  },
  {
    title: "시스템",
    items: [
      { label: "시스템 상태", href: "/system", icon: HeartPulse, systemOnly: true },
      {
        label: "감사 로그",
        href: "/action-log",
        icon: ScrollText,
        systemOnly: true,
      },
    ],
  },
];

/**
 * Sidebar renders the primary navigation for the protected shell.
 * System-only items are shown to roles that can view system screens (SYSTEM + read-only VIEWER);
 * CHURCH_MANAGER (no system :read) does not see them.
 */
export default function Sidebar() {
  const pathname = usePathname();
  const { data: session } = useSession();
  const showSystem = canViewSystem(session?.user?.role);

  const sections = NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter((item) => !item.systemOnly || showSystem),
  })).filter((section) => section.items.length > 0);

  return (
    <aside className="flex h-screen w-60 flex-col border-r border-slate-200 bg-white">
      <div className="flex h-14 items-center border-b border-slate-200 px-5">
        <span className="text-base font-semibold text-slate-900">
          GraceOn
        </span>
      </div>
      <nav className="flex-1 space-y-4 overflow-y-auto p-3">
        {sections.map((section) => (
          <div key={section.title} className="space-y-1">
            <p className="px-3 pb-1 text-xs font-semibold uppercase tracking-wide text-slate-400">
              {section.title}
            </p>
            {section.items.map((item) => {
              const isActive =
                pathname === item.href || pathname.startsWith(`${item.href}/`);
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition ${
                    isActive
                      ? "bg-brand text-white"
                      : "text-slate-600 hover:bg-slate-100"
                  }`}
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>
    </aside>
  );
}

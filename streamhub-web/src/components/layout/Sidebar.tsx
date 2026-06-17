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
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { isSystem } from "@/lib/auth-utils";

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

const NAV_SECTIONS: NavSection[] = [
  {
    title: "운영",
    items: [
      { label: "통합 운영 대시보드", href: "/admin-ops", icon: Gauge },
      { label: "대시보드", href: "/dashboard", icon: LayoutDashboard },
      { label: "기능 카탈로그", href: "/catalog", icon: LayoutGrid },
    ],
  },
  {
    title: "회원·콘텐츠",
    items: [
      { label: "회원관리", href: "/member", icon: Users },
      { label: "포인트 원장", href: "/point", icon: Coins },
      { label: "콘텐츠관리", href: "/content", icon: FileVideo },
    ],
  },
  {
    title: "커머스",
    items: [
      { label: "굿즈관리", href: "/goods", icon: ShoppingBag },
      { label: "주문관리", href: "/order", icon: ClipboardList },
    ],
  },
  {
    title: "후원·구독",
    items: [
      { label: "멤버십 플랜", href: "/subscription-plan", icon: Gem },
      { label: "구독 현황", href: "/subscription", icon: CreditCard },
      { label: "후원 내역", href: "/donation", icon: HeartHandshake },
      { label: "결제일정", href: "/billing-calendar", icon: CalendarClock },
    ],
  },
  {
    title: "시스템",
    items: [
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
 * System-only items are hidden from non-SYSTEM operators.
 */
export default function Sidebar() {
  const pathname = usePathname();
  const { data: session } = useSession();
  const isSystemRole = isSystem(session?.user?.role);

  const sections = NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter((item) => !item.systemOnly || isSystemRole),
  })).filter((section) => section.items.length > 0);

  return (
    <aside className="flex h-screen w-60 flex-col border-r border-slate-200 bg-white">
      <div className="flex h-14 items-center border-b border-slate-200 px-5">
        <span className="text-base font-semibold text-slate-900">
          StreamHub
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

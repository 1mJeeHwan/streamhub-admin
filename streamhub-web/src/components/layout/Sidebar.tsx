"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSession } from "next-auth/react";
import { LayoutDashboard, Users, FileVideo, ScrollText } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { isSystem } from "@/lib/auth-utils";

interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  systemOnly?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { label: "대시보드", href: "/dashboard", icon: LayoutDashboard },
  { label: "회원관리", href: "/member", icon: Users },
  { label: "콘텐츠관리", href: "/content", icon: FileVideo },
  { label: "감사 로그", href: "/action-log", icon: ScrollText, systemOnly: true },
];

/**
 * Sidebar renders the primary navigation for the protected shell.
 * System-only items are hidden from non-SYSTEM operators.
 */
export default function Sidebar() {
  const pathname = usePathname();
  const { data: session } = useSession();
  const items = NAV_ITEMS.filter(
    (item) => !item.systemOnly || isSystem(session?.user?.role),
  );

  return (
    <aside className="flex h-screen w-60 flex-col border-r border-slate-200 bg-white">
      <div className="flex h-14 items-center border-b border-slate-200 px-5">
        <span className="text-base font-semibold text-slate-900">
          StreamHub
        </span>
      </div>
      <nav className="flex-1 space-y-1 p-3">
        {items.map((item) => {
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
      </nav>
    </aside>
  );
}

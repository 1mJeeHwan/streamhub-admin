"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Home, Film, Music, Store, User } from "lucide-react";
import clsx from "clsx";

const TABS = [
  { href: "/", label: "홈", icon: Home, exact: true },
  { href: "/video", label: "영상", icon: Film },
  { href: "/music", label: "음악", icon: Music },
  // 스토어 허브 — 음반/굿즈/이벤트를 한 곳에 모음. 그 카탈로그 페이지에서도 탭을 활성 표시한다.
  { href: "/store", label: "스토어", icon: Store, alsoMatch: ["/albums", "/goods", "/campaigns"] },
  { href: "/mypage", label: "MY", icon: User },
];

/** True if the path is `prefix` itself or nested under it (`prefix/...`). */
function underPath(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

/** Bottom tab bar, fixed to the centered phone frame (production-style). Search lives in the AppBar. */
export function TabBar() {
  const pathname = usePathname();

  return (
    <nav className="fixed bottom-0 left-1/2 z-30 w-full max-w-[480px] -translate-x-1/2 border-t border-border/60 bg-bg/90 pb-[env(safe-area-inset-bottom)] backdrop-blur-md">
      <ul className="flex items-center justify-around px-2 py-2">
        {TABS.map(({ href, label, icon: Icon, exact, alsoMatch }) => {
          const active = exact
            ? pathname === href
            : underPath(pathname, href) ||
              (alsoMatch?.some((p) => underPath(pathname, p)) ?? false) ||
              (href === "/mypage" && pathname === "/login");
          return (
            <li key={href}>
              <Link
                href={href}
                className={clsx(
                  "flex w-14 flex-col items-center gap-1 py-1 text-[11px] font-medium transition-colors",
                  active ? "text-primary" : "text-inactive",
                )}
              >
                <Icon className="h-5 w-5" strokeWidth={active ? 2.4 : 2} />
                {label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

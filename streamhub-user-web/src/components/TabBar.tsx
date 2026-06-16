"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Home, Film, Music, Newspaper, User } from "lucide-react";
import clsx from "clsx";

const TABS = [
  { href: "/", label: "홈", icon: Home, exact: true },
  { href: "/video", label: "영상", icon: Film },
  { href: "/music", label: "음악", icon: Music },
  { href: "/posts", label: "소식", icon: Newspaper },
  { href: "/mypage", label: "MY", icon: User },
];

/** Bottom tab bar, fixed to the centered phone frame (production-style). Search lives in the AppBar. */
export function TabBar() {
  const pathname = usePathname();

  return (
    <nav className="fixed bottom-0 left-1/2 z-30 w-full max-w-[480px] -translate-x-1/2 border-t border-border/60 bg-bg/90 pb-[env(safe-area-inset-bottom)] backdrop-blur-md">
      <ul className="flex items-center justify-around px-2 py-2">
        {TABS.map(({ href, label, icon: Icon, exact }) => {
          const active = exact
            ? pathname === href
            : pathname === href || pathname.startsWith(`${href}/`) || (href === "/mypage" && pathname === "/login");
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

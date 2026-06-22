"use client";

import Link from "next/link";
import { MapPin, Search } from "lucide-react";
import { ThemeToggle } from "./ThemeToggle";
import { Logo } from "./Logo";

/**
 * Top app bar: logo left; 교회찾기·검색·테마 right. Commerce entries (음반/굿즈/이벤트) moved to the
 * bottom 스토어 탭, so the bar stays uncluttered for the two occasional-use utilities.
 */
export function AppBar() {
  return (
    <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-border/60 bg-bg/85 px-5 backdrop-blur-md">
      <Link href="/" aria-label="StreamHub 홈">
        <Logo />
      </Link>
      <nav className="flex items-center gap-4">
        <Link href="/churches" aria-label="교회찾기" className="text-active transition-colors hover:text-primary">
          <MapPin className="h-5 w-5" />
        </Link>
        <Link href="/search" aria-label="검색" className="text-active transition-colors hover:text-primary">
          <Search className="h-5 w-5" />
        </Link>
        <ThemeToggle />
      </nav>
    </header>
  );
}

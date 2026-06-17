"use client";

import Link from "next/link";
import { Disc3, MapPin, Search } from "lucide-react";

/** Top app bar: logo left, search right. Sticky within the phone frame. */
export function AppBar() {
  return (
    <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-border/60 bg-bg/85 px-5 backdrop-blur-md">
      <Link href="/" className="flex items-center gap-2">
        <span className="grid h-7 w-7 place-items-center rounded-lg bg-primary text-bg">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
            <path d="M8 5v14l11-7z" />
          </svg>
        </span>
        <span className="text-[17px] font-bold tracking-tight">
          Stream<span className="text-primary">Hub</span>
        </span>
      </Link>
      <nav className="flex items-center gap-4">
        <Link href="/churches" aria-label="교회찾기" className="text-active transition-colors hover:text-primary">
          <MapPin className="h-5 w-5" />
        </Link>
        <Link href="/albums" aria-label="음반" className="text-active transition-colors hover:text-primary">
          <Disc3 className="h-5 w-5" />
        </Link>
        <Link href="/search" aria-label="검색" className="text-active transition-colors hover:text-primary">
          <Search className="h-5 w-5" />
        </Link>
      </nav>
    </header>
  );
}

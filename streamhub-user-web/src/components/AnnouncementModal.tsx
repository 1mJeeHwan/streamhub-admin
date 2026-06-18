"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import Link from "next/link";
import { Megaphone, X } from "lucide-react";

/** Remembers the last announcement text the visitor dismissed (shows once per message). */
const SEEN_KEY = "streamhub.announcement.seen";

/**
 * First-visit announcement modal, driven by the admin site-config. Pops up once per browser
 * for a given announcement message (re-shows when the admin changes the text). Portals to
 * <body> so its fixed overlay anchors to the viewport regardless of transformed ancestors.
 */
export function AnnouncementModal({
  enabled,
  text,
  link,
}: {
  enabled: boolean;
  text: string;
  link?: string;
}) {
  const [mounted, setMounted] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    setMounted(true);
    if (!enabled || !text.trim()) return;
    let seen: string | null = null;
    try {
      seen = window.localStorage.getItem(SEEN_KEY);
    } catch {
      /* storage blocked — just show it */
    }
    if (seen !== text) setOpen(true);
  }, [enabled, text]);

  const close = () => {
    try {
      window.localStorage.setItem(SEEN_KEY, text);
    } catch {
      /* ignore */
    }
    setOpen(false);
  };

  if (!mounted || !open || !enabled || !text.trim()) return null;

  return createPortal(
    <div
      className="fixed inset-0 z-[70] flex items-center justify-center bg-black/60 px-6"
      role="dialog"
      aria-modal="true"
      aria-label="공지"
      onClick={close}
    >
      <div
        className="w-full max-w-[360px] overflow-hidden rounded-2xl border border-border bg-bg animate-fade-up"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-border bg-surface px-4 py-3">
          <div className="flex items-center gap-2">
            <span className="grid h-7 w-7 place-items-center rounded-full bg-primary/15">
              <Megaphone className="h-4 w-4 text-primary" />
            </span>
            <h2 className="text-sm font-bold text-active">공지</h2>
          </div>
          <button
            type="button"
            aria-label="닫기"
            onClick={close}
            className="rounded-lg p-1.5 text-inactive transition active:bg-card"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="px-5 py-7">
          <p className="whitespace-pre-line text-center text-[15px] leading-relaxed text-active">{text}</p>
        </div>

        <div className="flex gap-2 border-t border-border bg-surface px-4 py-3">
          {link ? (
            <>
              <button
                type="button"
                onClick={close}
                className="flex-1 rounded-xl border border-border py-2.5 text-sm font-medium text-inactive active:bg-card"
              >
                닫기
              </button>
              <Link href={link} onClick={close} className="btn-primary flex-1 py-2.5 text-sm">
                자세히 보기
              </Link>
            </>
          ) : (
            <button type="button" onClick={close} className="btn-primary w-full py-2.5 text-sm">
              확인
            </button>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}

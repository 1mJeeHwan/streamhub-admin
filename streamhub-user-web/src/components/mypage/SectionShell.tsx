"use client";

import { useState, type ReactNode } from "react";
import { ChevronDown } from "lucide-react";
import clsx from "clsx";
import type { LucideIcon } from "lucide-react";

/**
 * Collapsible row for the "내 정보" feature menu. Renders a tappable header (icon + title + optional
 * count badge + chevron) and reveals its body — the section's loading / error / empty / content
 * states — only when expanded. Default-collapsed so the mypage stays a short, scannable menu; the
 * data hook still runs while collapsed, so the count badge is available immediately. Designed to sit
 * inside a single divided container (the mypage menu card).
 */
export function SectionShell({
  icon: Icon,
  title,
  isLoading,
  isError,
  isEmpty,
  errorMessage,
  emptyIcon: EmptyIcon,
  emptyMessage,
  skeletonHeight = 68,
  skeletonCount = 2,
  count,
  countTone = "default",
  defaultOpen = false,
  children,
}: {
  icon: LucideIcon;
  title: string;
  isLoading: boolean;
  isError: boolean;
  isEmpty: boolean;
  errorMessage: string;
  emptyIcon: LucideIcon;
  emptyMessage: string;
  skeletonHeight?: number;
  skeletonCount?: number;
  /** Badge text on the collapsed row (e.g. "5건", "1,200P"). Hidden when null/empty. */
  count?: ReactNode;
  /** "danger" tints the badge with the accent color (e.g. unread notifications). */
  countTone?: "default" | "danger";
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors active:bg-card"
      >
        <Icon className="h-4.5 w-4.5 shrink-0 text-primary" />
        <span className="flex-1 text-sm font-bold text-active">{title}</span>
        {count != null && count !== "" && (
          <span
            className={clsx(
              "shrink-0 rounded-full px-2 py-0.5 text-[11px] font-bold",
              countTone === "danger" ? "bg-point/15 text-point" : "bg-card text-inactive",
            )}
          >
            {count}
          </span>
        )}
        <ChevronDown
          className={clsx("h-4 w-4 shrink-0 text-inactive transition-transform", open && "rotate-180")}
        />
      </button>

      {open && (
        <div className="px-4 pb-4">
          {isLoading ? (
            <div className="space-y-2.5">
              {Array.from({ length: skeletonCount }).map((_, i) => (
                <div key={i} className="skeleton rounded-card" style={{ height: skeletonHeight }} />
              ))}
            </div>
          ) : isError ? (
            <p className="rounded-card border border-border bg-surface px-4 py-5 text-center text-sm text-inactive">
              {errorMessage}
            </p>
          ) : isEmpty ? (
            <div className="rounded-card border border-border bg-surface px-4 py-7 text-center">
              <EmptyIcon className="mx-auto h-7 w-7 text-inactive" />
              <p className="mt-2 text-sm text-inactive">{emptyMessage}</p>
            </div>
          ) : (
            children
          )}
        </div>
      )}
    </div>
  );
}

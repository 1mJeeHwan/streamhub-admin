"use client";

import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";

/**
 * Shared chrome for every "내 정보" section: titled header + uniform loading / error / empty /
 * content states. Keeps the four mypage sections visually consistent with the existing
 * OrderHistorySection design tokens (surface card, divide-border, primary accents).
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
  children: ReactNode;
}) {
  return (
    <section className="mt-7">
      <h2 className="flex items-center gap-2 pb-3 text-base font-bold text-active">
        <Icon className="h-4.5 w-4.5 text-primary" />
        {title}
      </h2>

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
    </section>
  );
}

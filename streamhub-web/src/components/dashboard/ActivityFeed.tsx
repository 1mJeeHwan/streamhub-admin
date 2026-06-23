"use client";

import { useEffect, useRef, useState } from "react";
import { Loader2 } from "lucide-react";

import { useDashboardFeed } from "@/apis/query/dashboard/dashboard";
import type { FeedItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDateTime } from "@/lib/format";
import { relativeTime } from "@/lib/relative-time";

const FEED_LIMIT = 30;
// Genuine near-real-time: refetch every 5s (+ on window focus), so new activity surfaces within
// seconds rather than being faked by a staggered reveal of stale data.
const POLL_INTERVAL_MS = 5000;
// Re-render every 10s so the relative-time labels keep ticking between fetches (a "5분 전" row
// becomes "6분 전" without waiting for new data).
const TICK_MS = 10000;
// Rows newer than this get the "live" emphasis (pulsing dot + emerald label).
const RECENT_MS = 60000;

// kind별 좌측 점 색. action-log/page.tsx의 actionColor 분류 철학을 따른다.
function kindDotColor(kind?: string): string {
  switch (kind) {
    case "CREATE":
    case "CONTENT":
      return "bg-emerald-500";
    case "MEMBER":
    case "MEMBER_JOIN":
      return "bg-blue-500";
    case "ORDER":
      return "bg-sky-500";
    case "DONATION":
      return "bg-violet-500";
    case "LOGIN":
      return "bg-slate-400";
    default:
      return "bg-amber-500";
  }
}

function occurredMs(occurredAt?: string): number {
  if (!occurredAt) return NaN;
  return new Date(occurredAt).getTime();
}

/**
 * ActivityFeed renders the operations feed from `/v1/dashboard/feed`, newest first. It refetches
 * every {@link POLL_INTERVAL_MS} (and on window focus), so genuinely new orders/subscriptions/
 * donations appear within seconds and are flagged "방금 전" with a live dot; everything older shows
 * an honest relative age ("12분 전", "3시간 전", "5일 전"). Only rows not seen on a previous fetch
 * animate in — no cosmetic ticker over stale data.
 */
export default function ActivityFeed() {
  const { data, isPending, isError, dataUpdatedAt } = useDashboardFeed(
    { limit: FEED_LIMIT },
    { query: { refetchInterval: POLL_INTERVAL_MS, refetchOnWindowFocus: true } },
  );

  const items = data?.resultObject ?? [];

  // Newest-first for display.
  const ordered = [...items].sort((a, b) =>
    (b.occurredAt ?? "").localeCompare(a.occurredAt ?? ""),
  );

  // Tick the clock so relative labels (and the "갱신" indicator) stay fresh between fetches.
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(id);
  }, []);

  // Track ids seen on a previous render so only brand-new rows animate in (not the whole list
  // on every poll). ponytail: the set is per-session and bounded by the feed's id space.
  const seenRef = useRef<Set<string>>(new Set());
  const seen = seenRef.current;
  const firstLoadRef = useRef(true);
  useEffect(() => {
    ordered.forEach((i) => i.id && seen.add(i.id));
    if (ordered.length > 0) firstLoadRef.current = false;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  });

  const updatedSec =
    dataUpdatedAt > 0 ? Math.max(0, Math.round((now - dataUpdatedAt) / 1000)) : null;

  return (
    <section className="flex flex-col rounded-md border border-slate-200 bg-white p-5">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-900">실시간 활동</h2>
        <span
          className="flex items-center gap-1.5 text-xs text-slate-400"
          title={updatedSec !== null ? `${updatedSec}초 전 갱신 · 5초마다 자동 갱신` : undefined}
        >
          <span className="relative flex h-2 w-2">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
            <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
          </span>
          {updatedSec !== null && updatedSec < 10 ? "실시간" : `${updatedSec ?? ""}초 전 갱신`}
        </span>
      </div>

      <div className="h-[320px] overflow-y-auto pr-1">
        {isPending ? (
          <div className="flex h-full items-center justify-center">
            <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
          </div>
        ) : isError ? (
          <div className="flex h-full items-center justify-center">
            <p className="text-sm text-red-600">활동을 불러오지 못했습니다.</p>
          </div>
        ) : ordered.length === 0 ? (
          <div className="flex h-full items-center justify-center">
            <p className="text-sm text-slate-400">최근 활동이 없습니다.</p>
          </div>
        ) : (
          <ul className="space-y-3">
            {ordered.map((item: FeedItem) => {
              const ms = occurredMs(item.occurredAt);
              const isRecent = !Number.isNaN(ms) && now - ms < RECENT_MS;
              // Animate only rows that are new since a previous fetch (and not the initial paint).
              const isNew = !firstLoadRef.current && !!item.id && !seen.has(item.id);
              return (
                <li
                  key={item.id}
                  className={`flex items-start gap-3 text-sm ${isNew ? "feed-row" : ""}`}
                >
                  <span className="relative mt-1.5 flex h-2 w-2 shrink-0">
                    {isRecent && (
                      <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                    )}
                    <span
                      className={`relative h-2 w-2 rounded-full ${kindDotColor(item.kind)}`}
                    />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-slate-800">{item.message ?? "-"}</p>
                    <p className="mt-0.5 text-xs text-slate-400">{item.actorName ?? "시스템"}</p>
                  </div>
                  <time
                    className={`shrink-0 whitespace-nowrap text-xs ${
                      isRecent ? "font-semibold text-emerald-600" : "text-slate-400"
                    }`}
                    title={formatDateTime(item.occurredAt)}
                  >
                    {relativeTime(item.occurredAt)}
                  </time>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <style jsx>{`
        .feed-row {
          animation: feed-slide-in 0.35s ease-out both;
        }
        @keyframes feed-slide-in {
          from {
            opacity: 0;
            transform: translateY(-6px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }
      `}</style>
    </section>
  );
}

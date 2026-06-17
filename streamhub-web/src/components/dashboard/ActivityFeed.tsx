"use client";

import { useEffect, useRef, useState } from "react";
import { Loader2 } from "lucide-react";

import { useFeed } from "@/apis/query/dashboard/dashboard";
import type { FeedItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDateTime } from "@/lib/format";
import { relativeTime } from "@/lib/relative-time";

const FEED_LIMIT = 30;
const POLL_INTERVAL_MS = 15000;
const REVEAL_INTERVAL_MS = 1200;

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

/**
 * ActivityFeed renders the real-time operations feed from `/v1/dashboard/feed`.
 * Rows are not all painted at once: on each fetch the items are sorted oldest →
 * newest and revealed one at a time from the top with a slide-in, giving the
 * "control room ticker" feel. The query polls every 15s; relative timestamps
 * are computed client-side against the server's absolute `occurredAt`.
 */
export default function ActivityFeed() {
  const { data, isPending, isError } = useFeed(
    { limit: FEED_LIMIT },
    { query: { refetchInterval: POLL_INTERVAL_MS } },
  );

  const items = data?.resultObject ?? [];
  const [revealCount, setRevealCount] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Newest-first for display.
  const ordered = [...items].sort((a, b) =>
    (b.occurredAt ?? "").localeCompare(a.occurredAt ?? ""),
  );

  // Reveal rows progressively whenever the fetched set grows.
  useEffect(() => {
    if (ordered.length === 0) {
      setRevealCount(0);
      return;
    }

    if (timerRef.current !== null) {
      clearInterval(timerRef.current);
    }

    timerRef.current = setInterval(() => {
      setRevealCount((prev) => {
        if (prev >= ordered.length) {
          if (timerRef.current !== null) {
            clearInterval(timerRef.current);
          }
          return prev;
        }
        return prev + 1;
      });
    }, REVEAL_INTERVAL_MS);

    return () => {
      if (timerRef.current !== null) {
        clearInterval(timerRef.current);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ordered.length]);

  const visible = ordered.slice(0, revealCount);

  return (
    <section className="flex flex-col rounded-md border border-slate-200 bg-white p-5">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-900">실시간 활동</h2>
        <span className="flex items-center gap-1.5 text-xs text-slate-400">
          <span className="relative flex h-2 w-2">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
            <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
          </span>
          실시간
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
            {visible.map((item: FeedItem) => (
              <li
                key={item.id}
                className="feed-row flex items-start gap-3 text-sm"
              >
                <span
                  className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${kindDotColor(
                    item.kind,
                  )}`}
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-slate-800">{item.message ?? "-"}</p>
                  <p className="mt-0.5 text-xs text-slate-400">
                    {item.actorName ?? "시스템"}
                  </p>
                </div>
                <time
                  className="shrink-0 whitespace-nowrap text-xs text-slate-400"
                  title={formatDateTime(item.occurredAt)}
                >
                  {relativeTime(item.occurredAt)}
                </time>
              </li>
            ))}
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

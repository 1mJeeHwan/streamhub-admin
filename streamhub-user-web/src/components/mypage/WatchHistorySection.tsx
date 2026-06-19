"use client";

import Link from "next/link";
import { History, Music2, Video } from "lucide-react";
import { useMyHistory, type WatchHistoryItem } from "@/lib/me";
import { formatDate, formatDuration } from "@/lib/format";
import { SectionShell } from "./SectionShell";
import { CoverThumb } from "./CoverThumb";

/** Detail-page route for a watched item, keyed by content type (SOUND→/music, VIDEO→/video). */
function detailHref(item: WatchHistoryItem): string {
  return item.type === "SOUND" ? `/music/${item.contentId}` : `/video/${item.contentId}`;
}

/** The member's recent watch history (newest first), linking back to each content's detail page. */
export function WatchHistorySection({ token }: { token: string }) {
  const { data, isLoading, isError } = useMyHistory(token);
  const history = data ?? [];

  return (
    <SectionShell
      icon={History}
      title="시청 기록"
      isLoading={isLoading}
      isError={isError}
      isEmpty={history.length === 0}
      errorMessage="시청 기록을 불러오지 못했습니다."
      emptyIcon={History}
      emptyMessage="아직 시청한 콘텐츠가 없습니다."
    >
      <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
        {history.map((item) => (
          <li key={`${item.contentId}-${item.watchedAt}`}>
            <Link href={detailHref(item)} className="flex items-center gap-3 px-4 py-3">
              <CoverThumb src={item.thumbnailUrl} alt={item.title} />
              <div className="min-w-0 flex-1">
                <p className="ellipsis-1 text-sm font-bold text-active">{item.title}</p>
                <p className="mt-0.5 flex items-center gap-1.5 text-[11px] text-inactive">
                  {item.type === "SOUND" ? (
                    <Music2 className="h-3 w-3" />
                  ) : (
                    <Video className="h-3 w-3" />
                  )}
                  <span>{formatDate(item.watchedAt)}</span>
                  {item.watchSeconds != null && <span>· {formatDuration(item.watchSeconds)} 시청</span>}
                </p>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </SectionShell>
  );
}

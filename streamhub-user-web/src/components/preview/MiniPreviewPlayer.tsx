"use client";

import { Music, Pause, Play, X } from "lucide-react";
import clsx from "clsx";
import { DemoBadge } from "../DemoBadge";
import { usePreviewPlayer } from "./PreviewPlayerProvider";

/**
 * Bottom-fixed mini player, shown only while a preview track is loaded. Sits just above the
 * TabBar inside the phone frame. Shows cover, title/artist, a 30s progress bar, play/pause and close.
 */
export function MiniPreviewPlayer() {
  const { current, isPlaying, elapsed, lengthSec, loading, loadProgress, hasError, toggle, stop } =
    usePreviewPlayer();
  if (!current) return null;

  const playbackPct = lengthSec > 0 ? Math.min(100, (elapsed / lengthSec) * 100) : 0;
  // While preparing the stream the top bar shows load progress; then it tracks playback.
  const barPct = hasError ? 100 : loading ? Math.max(5, loadProgress) : playbackPct;

  return (
    <div className="fixed bottom-[60px] left-1/2 z-40 w-full max-w-[480px] -translate-x-1/2 px-3 pb-1">
      <div className="overflow-hidden rounded-card border border-border bg-card/95 shadow-lg backdrop-blur-md">
        <div className="h-0.5 w-full bg-border">
          <div
            className={clsx(
              "h-full transition-[width] duration-200 ease-linear",
              hasError ? "bg-point" : loading ? "bg-primary/60" : "bg-primary",
            )}
            style={{ width: `${barPct}%` }}
          />
        </div>
        <div className="flex items-center gap-3 px-3 py-2.5">
          <div className="h-10 w-10 shrink-0 overflow-hidden rounded-md bg-surface">
            {current.coverUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={current.coverUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              <div className="grid h-full w-full place-items-center">
                <Music className="h-4 w-4 text-inactive" />
              </div>
            )}
          </div>

          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5">
              <p className="ellipsis-1 text-[13px] font-bold text-active">{current.track.title}</p>
              <DemoBadge className="shrink-0" label="데모" />
            </div>
            <p className={clsx("ellipsis-1 text-[11px]", hasError ? "text-point" : "text-inactive")}>
              {hasError
                ? "미리듣기를 불러오지 못했습니다"
                : loading
                  ? `재생 준비 중 ${Math.round(loadProgress)}%`
                  : `${current.artist} · ${Math.floor(elapsed)}초 / ${lengthSec}초`}
            </p>
          </div>

          <button
            onClick={toggle}
            disabled={hasError || loading}
            aria-label={isPlaying ? "일시정지" : "재생"}
            className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-primary text-bg transition active:scale-95 disabled:opacity-40"
          >
            {isPlaying ? <Pause className="h-4 w-4" /> : <Play className="ml-0.5 h-4 w-4" />}
          </button>
          <button
            onClick={stop}
            aria-label="닫기"
            className="grid h-8 w-8 shrink-0 place-items-center rounded-full text-inactive active:text-active"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
      </div>
    </div>
  );
}

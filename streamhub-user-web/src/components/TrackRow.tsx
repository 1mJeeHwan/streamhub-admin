"use client";

import { Pause, Play } from "lucide-react";
import clsx from "clsx";
import { formatDuration } from "@/lib/format";
import type { TrackDto } from "@/lib/albums";
import { usePreviewPlayer, type PreviewTrack } from "./preview/PreviewPlayerProvider";

/**
 * One track row: number · title · duration + a ▶/⏸ preview button. Clicking the button hands
 * the track to the global preview player (which stops any other track already playing).
 */
export function TrackRow({
  track,
  albumId,
  albumTitle,
  artist,
  coverUrl,
}: {
  track: TrackDto;
  albumId: number;
  albumTitle: string;
  artist: string;
  coverUrl: string | null;
}) {
  const { play, toggle, isPlaying, isCurrent } = usePreviewPlayer();
  const active = isCurrent(albumId, track.id);
  const playing = active && isPlaying;
  const hasPreview = Boolean(track.previewUrl);

  const onClick = () => {
    if (!hasPreview) return;
    if (active) {
      toggle();
      return;
    }
    const payload: PreviewTrack = { albumId, albumTitle, artist, coverUrl, track };
    play(payload);
  };

  return (
    <div
      className={clsx(
        "flex items-center gap-3 rounded-lg px-2 py-2.5 transition-colors",
        active && "bg-card",
      )}
    >
      <span
        className={clsx(
          "w-5 shrink-0 text-center text-sm tabular-nums",
          active ? "font-bold text-primary" : "text-inactive",
        )}
      >
        {track.trackNo}
      </span>
      <p className={clsx("ellipsis-1 min-w-0 flex-1 text-sm", active ? "font-bold text-active" : "text-active")}>
        {track.title}
      </p>
      {track.durationSec != null && (
        <span className="shrink-0 text-xs tabular-nums text-inactive">
          {formatDuration(track.durationSec)}
        </span>
      )}
      <button
        onClick={onClick}
        disabled={!hasPreview}
        aria-label={playing ? "미리듣기 정지" : "30초 미리듣기"}
        className={clsx(
          "grid h-8 w-8 shrink-0 place-items-center rounded-full transition-colors",
          hasPreview
            ? active
              ? "bg-primary text-bg"
              : "border border-border text-active active:bg-card"
            : "cursor-not-allowed border border-border/50 text-inactive/50",
        )}
      >
        {playing ? <Pause className="h-4 w-4" /> : <Play className="ml-0.5 h-4 w-4" />}
      </button>
    </div>
  );
}

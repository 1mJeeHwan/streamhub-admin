"use client";

import { useState } from "react";
import { Heart, Pause, Play } from "lucide-react";
import clsx from "clsx";
import { formatDuration } from "@/lib/format";
import type { TrackDto } from "@/lib/albums";
import { useAuth } from "@/lib/auth";
import { meApi } from "@/lib/me";
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
  favorited = false,
}: {
  track: TrackDto;
  albumId: number;
  albumTitle: string;
  artist: string;
  coverUrl: string | null;
  /** Initial 찜 state, if the caller already knows it (else starts un-favorited). */
  favorited?: boolean;
}) {
  const { play, toggle, isPlaying, isCurrent } = usePreviewPlayer();
  const { token } = useAuth();
  const active = isCurrent(albumId, track.id);
  const playing = active && isPlaying;
  const hasPreview = Boolean(track.previewUrl);

  // Optimistic 찜 toggle: flip immediately, fire the API, revert on failure. Heart is shown
  // only to signed-in members (token present); anonymous visitors don't see it.
  const [isFavorited, setIsFavorited] = useState(favorited);
  const [pending, setPending] = useState(false);

  const onClick = () => {
    if (!hasPreview) return;
    if (active) {
      toggle();
      return;
    }
    const payload: PreviewTrack = { albumId, albumTitle, artist, coverUrl, track };
    play(payload);
  };

  const onToggleFavorite = async () => {
    if (!token || pending) return;
    const next = !isFavorited;
    setIsFavorited(next);
    setPending(true);
    try {
      if (next) {
        await meApi.addFavorite(track.id, token);
      } else {
        await meApi.removeFavorite(track.id, token);
      }
    } catch {
      setIsFavorited(!next); // revert on failure
    } finally {
      setPending(false);
    }
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
      {token && (
        <button
          onClick={onToggleFavorite}
          disabled={pending}
          aria-label={isFavorited ? "찜 해제" : "찜하기"}
          aria-pressed={isFavorited}
          className={clsx(
            "grid h-8 w-8 shrink-0 place-items-center rounded-full transition-colors",
            isFavorited ? "text-point" : "border border-border text-inactive active:bg-card",
            pending && "opacity-50",
          )}
        >
          <Heart className={clsx("h-4 w-4", isFavorited && "fill-point")} />
        </button>
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

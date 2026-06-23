"use client";

import { useState } from "react";
import { ListMusic, Pause, Play } from "lucide-react";
import { usePlaylist, type PlaylistTrack } from "@/lib/playlists";
import { ApiError } from "@/lib/api";
import { BackLink } from "@/components/BackLink";
import {
  fullTrackSource,
  previewSource,
  useAudioPlayer,
} from "@/components/player/AudioPlayerProvider";
import { EmptyState, ErrorState } from "@/components/States";

function formatDuration(sec: number | null): string {
  if (!sec || sec <= 0) return "";
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

/** One playlist track: tapping plays the full track (free) or, if unpackaged, the preview. */
function PlaylistTrackRow({ track, index }: { track: PlaylistTrack; index: number }) {
  const { play, toggle, isPlaying, isCurrent } = useAudioPlayer();
  const sourceId = track.hasFullTrack
    ? `full:${track.albumId}:${track.id}`
    : `preview:${track.albumId}:${track.id}`;
  const active = isCurrent(sourceId);
  const playing = active && isPlaying;

  const onPlay = () => {
    if (active) {
      toggle();
      return;
    }
    if (track.hasFullTrack) {
      play(
        fullTrackSource({
          albumId: track.albumId,
          trackId: track.id,
          title: track.title,
          artist: track.artist ?? "",
          coverUrl: track.coverUrl,
        }),
      );
    } else {
      play(
        previewSource({
          albumId: track.albumId,
          trackId: track.id,
          title: track.title,
          artist: track.artist ?? "",
          coverUrl: track.coverUrl,
          previewUrl: track.previewUrl,
          previewStartSec: track.previewStartSec ?? 0,
          previewLengthSec: track.previewLengthSec ?? 30,
          hasPreviewHls: track.hasPreviewHls,
        }),
      );
    }
  };

  return (
    <button
      onClick={onPlay}
      className="flex w-full items-center gap-3 px-5 py-3 text-left active:bg-card"
    >
      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-primary/10 text-primary">
        {playing ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
      </span>
      <span className="min-w-0 flex-1">
        <span className="ellipsis-1 block text-sm font-bold text-active">
          {index + 1}. {track.title}
        </span>
        <span className="ellipsis-1 block text-xs text-inactive">
          {track.artist}
          {track.albumTitle ? ` · ${track.albumTitle}` : ""}
        </span>
      </span>
      <span className="shrink-0 text-xs text-inactive">{formatDuration(track.durationSec)}</span>
    </button>
  );
}

export default function PlaylistDetailPage({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  const { data, isLoading, isError, error, refetch } = usePlaylist(id);
  const [coverFailed, setCoverFailed] = useState(false);

  return (
    <div className="animate-fade-up">
      <div className="px-5 pt-4">
        <BackLink href="/music" label="음악" />
      </div>

      {isLoading ? (
        <div className="px-5 pt-4">
          <div className="skeleton mx-auto aspect-square w-full max-w-[240px] rounded-card" />
          <div className="skeleton mx-auto mt-5 h-6 w-2/5 rounded" />
        </div>
      ) : isError ? (
        <div className="pt-3">
          {(error as ApiError)?.status === 404 ? (
            <EmptyState message="플레이리스트를 찾을 수 없습니다." />
          ) : (
            <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />
          )}
        </div>
      ) : data ? (
        <article className="pt-3">
          <div className="px-5">
            <div className="mx-auto aspect-square w-full max-w-[240px] overflow-hidden rounded-card bg-gradient-to-br from-primary/30 via-card to-surface">
              {data.coverUrl && !coverFailed ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={data.coverUrl}
                  alt={data.title}
                  onError={() => setCoverFailed(true)}
                  className="h-full w-full object-cover"
                />
              ) : (
                <div className="grid h-full w-full place-items-center">
                  <ListMusic className="h-12 w-12 text-inactive" />
                </div>
              )}
            </div>
            <div className="mt-5 text-center">
              <h1 className="text-xl font-bold text-active">{data.title}</h1>
              {data.description && (
                <p className="mt-1 text-sm text-inactive">{data.description}</p>
              )}
              <p className="mt-1 text-xs text-inactive">{data.tracks.length}곡 · 무료 감상</p>
            </div>
          </div>

          <div className="mt-6 divide-y divide-border/40">
            {data.tracks.length === 0 ? (
              <EmptyState message="수록곡이 없습니다." />
            ) : (
              data.tracks.map((track, i) => (
                <PlaylistTrackRow key={`${track.albumId}-${track.id}`} track={track} index={i} />
              ))
            )}
          </div>
          <div className="h-7" />
        </article>
      ) : null}
    </div>
  );
}

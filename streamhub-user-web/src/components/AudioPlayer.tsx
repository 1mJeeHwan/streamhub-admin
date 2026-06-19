"use client";

import { useRef } from "react";
import { Music } from "lucide-react";
import { useHlsAudio } from "@/lib/useHlsAudio";
import { HlsLoadingBar } from "./HlsLoadingBar";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

/**
 * Album-style audio player: artwork + native HTML5 audio controls.
 *
 * Streams via public (unencrypted) HLS when {@code hlsAvailable} — showing a "preparing to play"
 * bar until ready — and falls back to the direct {@code src} when HLS is unavailable or its stream
 * fatally fails (so unpackaged content still plays).
 */
export function AudioPlayer({
  src,
  contentId,
  hlsAvailable,
  title,
  channelName,
  thumbnailUrl,
}: {
  src: string;
  contentId: number;
  hlsAvailable: boolean;
  title: string;
  channelName: string | null;
  thumbnailUrl: string | null;
}) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const playlistUrl = hlsAvailable
    ? `${API_BASE}/pub/v1/contents/${contentId}/hls/index.m3u8`
    : null;
  const { loading, progress, error } = useHlsAudio(audioRef, { playlistUrl, withKeyAuth: false });
  // HLS path unless it isn't available, or its stream fatally failed (then fall back to direct src).
  const useHls = hlsAvailable && error !== "fatal";

  return (
    <div className="px-5">
      <div className="mx-auto mt-2 aspect-square w-full max-w-[280px] overflow-hidden rounded-card bg-gradient-to-br from-primary/30 via-surface to-bg">
        {thumbnailUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={thumbnailUrl} alt={title} className="h-full w-full object-cover" />
        ) : (
          <div className="grid h-full w-full place-items-center">
            <Music className="h-14 w-14 text-inactive" />
          </div>
        )}
      </div>
      <div className="mt-5 text-center">
        <h1 className="text-lg font-bold text-active">{title}</h1>
        {channelName && <p className="mt-1 text-sm text-inactive">{channelName}</p>}
      </div>
      {useHls ? (
        <>
          {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
          <audio ref={audioRef} controls preload="metadata" className="mt-4 w-full" aria-label={title} />
          {loading && <HlsLoadingBar progress={progress} />}
        </>
      ) : (
        <audio key={src} controls preload="metadata" className="mt-4 w-full" aria-label={title}>
          <source src={src} />
          브라우저가 오디오 재생을 지원하지 않습니다.
        </audio>
      )}
    </div>
  );
}

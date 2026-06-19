"use client";

import { useRef } from "react";
import Link from "next/link";
import { Lock, ShieldCheck } from "lucide-react";
import { useHlsAudio } from "@/lib/useHlsAudio";
import { HlsLoadingBar } from "./HlsLoadingBar";

// Mirror the private BASE in src/lib/api.ts — the encrypted-HLS endpoints live under the same
// public API host. The playlist/segments are public; only the AES key request is gated.
const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

/**
 * Full-track player for an AES-128 encrypted HLS stream (purchasers only).
 *
 * Delegates loading to {@link useHlsAudio} (withKeyAuth) — hls.js fetches the public
 * {@code index.m3u8} + cross-origin encrypted {@code .ts} segments from the CDN and requests the
 * relative {@code key}, onto which the member Bearer token is attached. A 403 on the key means the
 * member hasn't purchased the album. A "preparing to play" bar shows until the stream can play.
 */
export function HlsTrackPlayer({
  albumId,
  trackId,
  title,
}: {
  albumId: number;
  trackId: number;
  title: string;
}) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const playlistUrl = `${API_BASE}/pub/v1/albums/${albumId}/tracks/${trackId}/hls/index.m3u8`;
  const { loading, progress, error } = useHlsAudio(audioRef, { playlistUrl, withKeyAuth: true });

  return (
    <div className="mt-2 rounded-lg border border-primary/30 bg-primary/5 px-3 py-3">
      <div className="flex items-center justify-between gap-2">
        <p className="ellipsis-1 min-w-0 text-sm font-bold text-active">{title}</p>
        <span className="flex shrink-0 items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-bold text-primary">
          <ShieldCheck className="h-3 w-3" />
          암호화 스트리밍 (HLS·AES-128)
        </span>
      </div>

      {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
      <audio ref={audioRef} controls className="mt-2.5 w-full" />

      {loading && !error && <HlsLoadingBar progress={progress} />}

      {error === "gated" && (
        <div className="mt-2.5 rounded-lg bg-point/10 px-3 py-2.5 text-center">
          <p className="flex items-center justify-center gap-1.5 text-xs font-bold text-point">
            <Lock className="h-3.5 w-3.5" />
            구매한 회원만 전체 재생할 수 있습니다
          </p>
          <Link
            href={`/albums/${albumId}`}
            className="btn-primary mt-2.5 inline-flex px-4 py-2 text-xs font-bold"
          >
            구매하고 전체 듣기
          </Link>
        </div>
      )}

      {error === "fatal" && (
        <p className="mt-2.5 text-center text-xs text-inactive">재생할 수 없습니다.</p>
      )}
    </div>
  );
}

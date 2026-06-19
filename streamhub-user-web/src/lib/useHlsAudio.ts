"use client";

import { useEffect, useState, type RefObject } from "react";
import Hls from "hls.js";
import { getStoredToken } from "@/lib/auth";

export type HlsAudioError = "gated" | "fatal";

export interface HlsAudioState {
  /** True while the stream is being prepared (until it can play). */
  loading: boolean;
  /** 0→100 "preparing to play" progress, driven by hls.js (or native buffering) events. */
  progress: number;
  error: HlsAudioError | null;
}

/**
 * Loads an HLS playlist into the given {@code <audio>} element via hls.js (Safari native fallback)
 * and exposes a 0→100% "preparing to play" progress until the stream can play.
 *
 * <p>The member Bearer token is attached only to the gated {@code /hls/key} request and only when
 * {@code withKeyAuth} is set (encrypted full tracks). Public preview/content streams take no auth.
 * Passing a null {@code playlistUrl} disables the hook (caller falls back to a direct source).
 */
export function useHlsAudio(
  audioRef: RefObject<HTMLAudioElement>,
  opts: { playlistUrl: string | null; withKeyAuth?: boolean; autoPlay?: boolean },
): HlsAudioState {
  const { playlistUrl, withKeyAuth = false, autoPlay = false } = opts;
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<HlsAudioError | null>(null);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !playlistUrl) return;

    setLoading(true);
    setProgress(0);
    setError(null);

    // Progress only moves forward — events can arrive out of order across segments.
    const bump = (p: number) => setProgress((prev) => (p > prev ? p : prev));
    const onCanPlay = () => {
      setProgress(100);
      setLoading(false);
    };
    audio.addEventListener("canplay", onCanPlay);

    if (Hls.isSupported()) {
      const hls = new Hls({
        xhrSetup: (xhr, url) => {
          if (withKeyAuth && url.includes("/hls/key")) {
            const token = getStoredToken();
            if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
          }
        },
      });
      let firstFrag = true;
      hls.on(Hls.Events.MANIFEST_LOADING, () => bump(15));
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        bump(45);
        if (autoPlay) void audio.play().catch(() => {});
      });
      hls.on(Hls.Events.FRAG_LOADED, () => {
        if (firstFrag) {
          firstFrag = false;
          bump(70);
        }
      });
      hls.on(Hls.Events.FRAG_BUFFERED, () => bump(90));
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data.fatal) return;
        const isKeyLoadError = data.details === Hls.ErrorDetails.KEY_LOAD_ERROR;
        const is403 = data.response?.code === 403;
        setError(isKeyLoadError && is403 ? "gated" : "fatal");
        setLoading(false);
      });
      hls.loadSource(playlistUrl);
      hls.attachMedia(audio);
      return () => {
        audio.removeEventListener("canplay", onCanPlay);
        hls.destroy();
      };
    }

    // Safari native HLS — no key-injection hook, so a gated key returns 403 (full tracks won't
    // authorize here); public preview/content streams play fine.
    if (audio.canPlayType("application/vnd.apple.mpegurl")) {
      const onLoadStart = () => bump(20);
      const onProgress = () => {
        try {
          if (audio.duration > 0 && audio.buffered.length > 0) {
            const pct = (audio.buffered.end(0) / audio.duration) * 100;
            bump(Math.min(95, Math.max(20, pct)));
          }
        } catch {
          // buffered may throw before metadata loads
        }
      };
      const onErr = () => {
        setError(withKeyAuth ? "gated" : "fatal");
        setLoading(false);
      };
      audio.addEventListener("loadstart", onLoadStart);
      audio.addEventListener("progress", onProgress);
      audio.addEventListener("error", onErr);
      audio.src = playlistUrl;
      if (autoPlay) void audio.play().catch(() => {});
      return () => {
        audio.removeEventListener("canplay", onCanPlay);
        audio.removeEventListener("loadstart", onLoadStart);
        audio.removeEventListener("progress", onProgress);
        audio.removeEventListener("error", onErr);
      };
    }

    setError("fatal");
    setLoading(false);
    return () => audio.removeEventListener("canplay", onCanPlay);
  }, [audioRef, playlistUrl, withKeyAuth, autoPlay]);

  return { loading, progress, error };
}

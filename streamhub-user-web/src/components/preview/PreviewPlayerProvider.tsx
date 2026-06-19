"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import Hls from "hls.js";
import type { TrackDto } from "@/lib/albums";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

/** The track currently loaded into the single global preview audio element. */
export interface PreviewTrack {
  albumId: number;
  albumTitle: string;
  artist: string;
  coverUrl: string | null;
  track: TrackDto;
}

interface PreviewContextValue {
  current: PreviewTrack | null;
  isPlaying: boolean;
  /** elapsed seconds into the 30s preview window (0..lengthSec). */
  elapsed: number;
  lengthSec: number;
  /** True while the stream is being prepared (HLS), before it can play. */
  loading: boolean;
  /** 0→100 "preparing to play" progress (HLS only). */
  loadProgress: number;
  /** True when the loaded preview source failed (e.g. sample CDN unreachable). */
  hasError: boolean;
  /** Start (or restart) preview for a track. Replaces any track already playing. */
  play: (next: PreviewTrack) => void;
  toggle: () => void;
  stop: () => void;
  /** True while this exact track is the loaded one (regardless of play/pause). */
  isCurrent: (albumId: number, trackId: number) => boolean;
}

const PreviewContext = createContext<PreviewContextValue | null>(null);

const DEFAULT_LENGTH = 30;

function previewPlaylistUrl(t: PreviewTrack): string {
  return `${API_BASE}/pub/v1/albums/${t.albumId}/tracks/${t.track.id}/preview/index.m3u8`;
}

/**
 * App-wide single-audio preview engine. Exactly one HTMLAudioElement exists, so starting a new
 * track inherently stops the previous one. Each preview is clamped to the preview window and
 * auto-paused at the cutoff. When a track exposes {@code hasPreviewHls} the clip is streamed via
 * hls.js (public, unencrypted HLS) with a "preparing to play" progress; otherwise it falls back to
 * the legacy direct {@code previewUrl}. Audio is created lazily inside the user gesture (mobile
 * autoplay policy).
 */
export function PreviewPlayerProvider({ children }: { children: React.ReactNode }) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const hlsRef = useRef<Hls | null>(null);
  // Whether the current playback is an HLS preview clip (which always starts at 0) vs a direct URL
  // (which seeks to previewStartSec). Read by the long-lived timeupdate listener.
  const isHlsRef = useRef(false);
  const [current, setCurrent] = useState<PreviewTrack | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadProgress, setLoadProgress] = useState(0);
  const [hasError, setHasError] = useState(false);

  // Latest current track kept in a ref so the long-lived audio listeners read fresh values.
  const currentRef = useRef<PreviewTrack | null>(null);
  currentRef.current = current;

  const destroyHls = useCallback(() => {
    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }
  }, []);

  const ensureAudio = useCallback((): HTMLAudioElement => {
    if (audioRef.current) return audioRef.current;
    const audio = new Audio();
    audio.preload = "metadata";

    audio.addEventListener("timeupdate", () => {
      const cur = currentRef.current;
      if (!cur) return;
      const start = isHlsRef.current ? 0 : cur.track.previewStartSec ?? 0;
      const len = cur.track.previewLengthSec || DEFAULT_LENGTH;
      const into = audio.currentTime - start;
      setElapsed(Math.max(0, Math.min(into, len)));
      // cutoff: stop once the preview window is exhausted.
      if (into >= len) {
        audio.pause();
        audio.currentTime = start;
        setElapsed(0);
        setIsPlaying(false);
      }
    });
    audio.addEventListener("play", () => setIsPlaying(true));
    audio.addEventListener("pause", () => setIsPlaying(false));
    audio.addEventListener("ended", () => setIsPlaying(false));
    audio.addEventListener("canplay", () => {
      setLoadProgress(100);
      setLoading(false);
    });
    // Surface load failures instead of hanging at 0초.
    audio.addEventListener("error", () => {
      setIsPlaying(false);
      setLoading(false);
      setHasError(true);
    });

    audioRef.current = audio;
    return audio;
  }, []);

  const play = useCallback(
    (next: PreviewTrack) => {
      const audio = ensureAudio();
      const sameTrack = currentRef.current?.track.id === next.track.id;

      currentRef.current = next;
      setCurrent(next);
      setElapsed(0);
      setHasError(false);

      const useHlsJs = !!next.track.hasPreviewHls && Hls.isSupported();
      const useNativeHls =
        !!next.track.hasPreviewHls &&
        !Hls.isSupported() &&
        audio.canPlayType("application/vnd.apple.mpegurl") !== "";

      // Re-arm the source only when switching tracks (or switching playback modes).
      if (!sameTrack) {
        destroyHls();
      }

      if (useHlsJs) {
        isHlsRef.current = true;
        if (!sameTrack || !hlsRef.current) {
          destroyHls();
          setLoading(true);
          setLoadProgress(0);
          const hls = new Hls();
          hlsRef.current = hls;
          let firstFrag = true;
          const bump = (p: number) => setLoadProgress((prev) => (p > prev ? p : prev));
          hls.on(Hls.Events.MANIFEST_LOADING, () => bump(15));
          hls.on(Hls.Events.MANIFEST_PARSED, () => {
            bump(45);
            try {
              audio.currentTime = 0;
            } catch {
              // currentTime may throw before metadata; canplay path covers it
            }
            void audio.play().catch(() => setIsPlaying(false));
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
            setLoading(false);
            setHasError(true);
          });
          hls.loadSource(previewPlaylistUrl(next));
          hls.attachMedia(audio);
        } else {
          // same HLS track already attached — just restart the clip
          try {
            audio.currentTime = 0;
          } catch {
            // ignore
          }
          void audio.play().catch(() => setIsPlaying(false));
        }
        return;
      }

      // Native HLS (Safari) or direct previewUrl fallback.
      isHlsRef.current = useNativeHls;
      destroyHls();
      const src = useNativeHls ? previewPlaylistUrl(next) : next.track.previewUrl;
      if (!src) {
        setHasError(true);
        return;
      }
      if (useNativeHls) {
        setLoading(true);
        setLoadProgress(30);
      } else {
        setLoading(false);
      }
      const startSec = useNativeHls ? 0 : next.track.previewStartSec ?? 0;
      const startPlayback = () => {
        try {
          audio.currentTime = startSec;
        } catch {
          // currentTime may throw before metadata loads; the loadedmetadata path covers it.
        }
        void audio.play().catch(() => setIsPlaying(false));
      };
      if (!sameTrack || audio.src !== src) {
        audio.src = src;
      }
      if (!sameTrack && audio.readyState < 1) {
        audio.addEventListener("loadedmetadata", startPlayback, { once: true });
        audio.load();
      } else {
        startPlayback();
      }
    },
    [ensureAudio, destroyHls],
  );

  const toggle = useCallback(() => {
    const audio = audioRef.current;
    if (!audio || !currentRef.current) return;
    if (audio.paused) void audio.play().catch(() => setIsPlaying(false));
    else audio.pause();
  }, []);

  const stop = useCallback(() => {
    const audio = audioRef.current;
    destroyHls();
    isHlsRef.current = false;
    if (audio) {
      audio.pause();
      audio.removeAttribute("src");
      audio.load();
    }
    currentRef.current = null;
    setCurrent(null);
    setElapsed(0);
    setIsPlaying(false);
    setLoading(false);
    setLoadProgress(0);
    setHasError(false);
  }, [destroyHls]);

  const isCurrent = useCallback(
    (albumId: number, trackId: number) =>
      currentRef.current?.albumId === albumId && currentRef.current?.track.id === trackId,
    [],
  );

  // Tear down the audio element + hls if the provider ever unmounts.
  useEffect(() => {
    return () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
      audioRef.current?.pause();
      audioRef.current = null;
    };
  }, []);

  const value = useMemo<PreviewContextValue>(
    () => ({
      current,
      isPlaying,
      elapsed,
      lengthSec: current?.track.previewLengthSec || DEFAULT_LENGTH,
      loading,
      loadProgress,
      hasError,
      play,
      toggle,
      stop,
      isCurrent,
    }),
    [current, isPlaying, elapsed, loading, loadProgress, hasError, play, toggle, stop, isCurrent],
  );

  return <PreviewContext.Provider value={value}>{children}</PreviewContext.Provider>;
}

/** Access the global preview player. Must be used under <PreviewPlayerProvider>. */
export function usePreviewPlayer(): PreviewContextValue {
  const ctx = useContext(PreviewContext);
  if (!ctx) throw new Error("usePreviewPlayer must be used within <PreviewPlayerProvider>");
  return ctx;
}

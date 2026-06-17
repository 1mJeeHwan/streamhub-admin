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
import type { TrackDto } from "@/lib/albums";

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
  /** Start (or restart) preview for a track. Replaces any track already playing. */
  play: (next: PreviewTrack) => void;
  toggle: () => void;
  stop: () => void;
  /** True while this exact track is the loaded one (regardless of play/pause). */
  isCurrent: (albumId: number, trackId: number) => boolean;
}

const PreviewContext = createContext<PreviewContextValue | null>(null);

const DEFAULT_LENGTH = 30;

/**
 * App-wide single-audio preview engine (spec §4.4). Exactly one HTMLAudioElement exists, so
 * starting a new track inherently stops the previous one — only one preview ever plays at a time.
 * Each preview is clamped to [previewStartSec, previewStartSec + previewLengthSec) and auto-paused
 * at the cutoff. Audio is created lazily inside the user gesture (mobile autoplay policy).
 */
export function PreviewPlayerProvider({ children }: { children: React.ReactNode }) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [current, setCurrent] = useState<PreviewTrack | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [elapsed, setElapsed] = useState(0);

  // Latest current track kept in a ref so the long-lived audio listeners read fresh values.
  const currentRef = useRef<PreviewTrack | null>(null);
  currentRef.current = current;

  const ensureAudio = useCallback((): HTMLAudioElement => {
    if (audioRef.current) return audioRef.current;
    const audio = new Audio();
    audio.preload = "metadata";

    audio.addEventListener("timeupdate", () => {
      const cur = currentRef.current;
      if (!cur) return;
      const start = cur.track.previewStartSec ?? 0;
      const len = cur.track.previewLengthSec || DEFAULT_LENGTH;
      const into = audio.currentTime - start;
      setElapsed(Math.max(0, Math.min(into, len)));
      // 30s cutoff: stop once the preview window is exhausted.
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

    audioRef.current = audio;
    return audio;
  }, []);

  const play = useCallback(
    (next: PreviewTrack) => {
      const audio = ensureAudio();
      const start = next.track.previewStartSec ?? 0;
      const sameTrack = currentRef.current?.track.id === next.track.id;

      currentRef.current = next;
      setCurrent(next);
      setElapsed(0);

      if (!sameTrack && next.track.previewUrl) {
        audio.src = next.track.previewUrl;
      }
      const startPlayback = () => {
        try {
          audio.currentTime = start;
        } catch {
          // currentTime may throw before metadata loads; the loadedmetadata path covers it.
        }
        void audio.play().catch(() => setIsPlaying(false));
      };

      if (!sameTrack && audio.readyState < 1) {
        audio.addEventListener("loadedmetadata", startPlayback, { once: true });
        audio.load();
      } else {
        startPlayback();
      }
    },
    [ensureAudio],
  );

  const toggle = useCallback(() => {
    const audio = audioRef.current;
    if (!audio || !currentRef.current) return;
    if (audio.paused) void audio.play().catch(() => setIsPlaying(false));
    else audio.pause();
  }, []);

  const stop = useCallback(() => {
    const audio = audioRef.current;
    if (audio) {
      audio.pause();
      audio.removeAttribute("src");
      audio.load();
    }
    currentRef.current = null;
    setCurrent(null);
    setElapsed(0);
    setIsPlaying(false);
  }, []);

  const isCurrent = useCallback(
    (albumId: number, trackId: number) =>
      currentRef.current?.albumId === albumId && currentRef.current?.track.id === trackId,
    [],
  );

  // Tear down the audio element if the provider ever unmounts.
  useEffect(() => {
    return () => {
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
      play,
      toggle,
      stop,
      isCurrent,
    }),
    [current, isPlaying, elapsed, play, toggle, stop, isCurrent],
  );

  return <PreviewContext.Provider value={value}>{children}</PreviewContext.Provider>;
}

/** Access the global preview player. Must be used under <PreviewPlayerProvider>. */
export function usePreviewPlayer(): PreviewContextValue {
  const ctx = useContext(PreviewContext);
  if (!ctx) throw new Error("usePreviewPlayer must be used within <PreviewPlayerProvider>");
  return ctx;
}

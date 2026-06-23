// Curated playlists — public surface for the user site's music tab.
// Types mirror the backend public DTOs (org.streamhub.api.v1.playlist). Hand-synced (no codegen).

"use client";

import { useQuery } from "@tanstack/react-query";
import { request } from "./api";
import { fixImageUrl } from "./image";

/** One track inside a playlist — enough to stream it via the album preview/full HLS endpoints. */
export interface PlaylistTrack {
  id: number;
  albumId: number;
  trackNo: number | null;
  title: string;
  artist: string | null;
  albumTitle: string | null;
  coverUrl: string | null;
  durationSec: number | null;
  hasFullTrack: boolean;
  hasPreviewHls: boolean;
  previewUrl: string | null;
  previewStartSec: number | null;
  previewLengthSec: number | null;
}

/** Playlist summary (GET /pub/v1/playlists). */
export interface PlaylistSummary {
  id: number;
  title: string;
  description: string | null;
  coverUrl: string | null;
  trackCount: number;
}

/** Playlist with its ordered tracks (GET /pub/v1/playlists/{id}). */
export interface PlaylistDetail {
  id: number;
  title: string;
  description: string | null;
  coverUrl: string | null;
  tracks: PlaylistTrack[];
}

export const playlistApi = {
  list: () =>
    request<PlaylistSummary[]>("/pub/v1/playlists").then((items) =>
      items.map((p) => ({ ...p, coverUrl: fixImageUrl(p.coverUrl) })),
    ),
  detail: (id: number) =>
    request<PlaylistDetail>(`/pub/v1/playlists/${id}`).then((d) => ({
      ...d,
      coverUrl: fixImageUrl(d.coverUrl),
      tracks: d.tracks.map((t) => ({ ...t, coverUrl: fixImageUrl(t.coverUrl) })),
    })),
};

export function usePlaylists() {
  return useQuery({
    queryKey: ["playlists"],
    queryFn: () => playlistApi.list(),
    staleTime: 5 * 60 * 1000,
  });
}

export function usePlaylist(id: number) {
  return useQuery({
    queryKey: ["playlist", id],
    queryFn: () => playlistApi.detail(id),
    enabled: Number.isFinite(id) && id > 0,
  });
}

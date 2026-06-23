/**
 * Hand-written client for curated playlists (/v1/playlist) + the album/track picker source. Ships
 * after the current Orval spec, so issued directly through customInstance (same auth/refresh).
 */
import { customInstance } from "./custom-instance";
import type { ApiResponse } from "@/types/api";

export interface PlaylistTrackItem {
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
}

export interface PlaylistDto {
  id: number;
  title: string;
  description: string | null;
  coverKey: string | null;
  coverUrl: string | null;
  sortOrder: number;
  useYn: string;
  trackCount: number;
}

export interface PlaylistDetail {
  id: number;
  title: string;
  description: string | null;
  coverKey: string | null;
  coverUrl: string | null;
  sortOrder: number;
  useYn: string;
  tracks: PlaylistTrackItem[];
}

export interface PlaylistSaveRequest {
  title: string;
  description?: string | null;
  coverKey?: string | null;
  sortOrder?: number;
  useYn?: string;
  trackIds: number[];
}

export const playlistList = (signal?: AbortSignal) =>
  customInstance<ApiResponse<PlaylistDto[]>>({ url: "/v1/playlist", method: "GET", signal });

export const playlistDetail = (id: number, signal?: AbortSignal) =>
  customInstance<ApiResponse<PlaylistDetail>>({ url: `/v1/playlist/${id}`, method: "GET", signal });

export const playlistCreate = (data: PlaylistSaveRequest) =>
  customInstance<ApiResponse<PlaylistDetail>>({ url: "/v1/playlist", method: "POST", data });

export const playlistUpdate = (id: number, data: PlaylistSaveRequest) =>
  customInstance<ApiResponse<PlaylistDetail>>({ url: `/v1/playlist/${id}`, method: "PUT", data });

export const playlistDelete = (id: number) =>
  customInstance<ApiResponse<void>>({ url: `/v1/playlist/${id}`, method: "DELETE" });

// --- album/track picker source (public endpoints) ---------------------------

export interface AlbumPick {
  id: number;
  title: string;
  artist: string;
}

export interface TrackPick {
  id: number;
  trackNo: number;
  title: string;
}

interface InfinityList<T> {
  contents: T[];
  totalCount: number;
  totalPage: number;
}

/** Albums for the picker dropdown (public list, large page). */
export const pickerAlbums = (signal?: AbortSignal) =>
  customInstance<ApiResponse<InfinityList<AlbumPick>>>({
    url: "/pub/v1/albums?pageSize=200",
    method: "GET",
    signal,
  });

/** Tracks of one album for the picker. */
export const pickerAlbumTracks = (albumId: number, signal?: AbortSignal) =>
  customInstance<ApiResponse<{ tracks: TrackPick[] }>>({
    url: `/pub/v1/albums/${albumId}`,
    method: "GET",
    signal,
  });

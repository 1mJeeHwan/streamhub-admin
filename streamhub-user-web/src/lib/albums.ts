// CCM album / track / store domain — public surface for the user site.
// Types mirror the backend public DTOs (org.streamhub.api.v1.album / .store). Kept in sync by
// hand (no codegen here, unlike the admin site), reusing the shared request/query helpers.

"use client";

import { useQuery } from "@tanstack/react-query";
import { query, request } from "./api";
import type { InfinityList } from "./types";

/** Album genre enum — mirrors org.streamhub.api.v1.album.entity.AlbumGenre. */
export type AlbumGenre =
  | "WORSHIP"
  | "HYMN"
  | "GOSPEL"
  | "CCM"
  | "CAROL"
  | "INSTRUMENTAL"
  | "KIDS";

export type AlbumStatus = "ON_SALE" | "HIDDEN";

/** Korean labels for each genre, in display order. Drives the filter chips. */
export const GENRE_LABELS: Record<AlbumGenre, string> = {
  WORSHIP: "워십",
  HYMN: "찬송가",
  GOSPEL: "복음성가",
  CCM: "CCM",
  CAROL: "캐롤",
  INSTRUMENTAL: "연주",
  KIDS: "어린이",
};

export const GENRE_ORDER: AlbumGenre[] = [
  "WORSHIP",
  "HYMN",
  "GOSPEL",
  "CCM",
  "CAROL",
  "INSTRUMENTAL",
  "KIDS",
];

/** One row of the album list (GET /pub/v1/albums). */
export interface AlbumListItem {
  id: number;
  title: string;
  artist: string;
  genre: AlbumGenre;
  status: AlbumStatus;
  coverKey: string | null;
  coverUrl: string | null;
  trackCount: number;
  goodsItemId: number | null;
  price: number | null;
  releaseDate: string | null;
  createdAt: string;
}

/** A single track within an album detail (with 30s-preview metadata). */
export interface TrackDto {
  id: number;
  trackNo: number;
  title: string;
  durationSec: number | null;
  previewUrl: string | null;
  previewStartSec: number;
  previewLengthSec: number;
  /** True when an AES-128 encrypted full-track HLS stream exists for purchasers. */
  hasFullTrack?: boolean;
  /** True when a public (unencrypted) preview HLS stream exists; else play previewUrl directly. */
  hasPreviewHls?: boolean;
}

/** Full album detail (GET /pub/v1/albums/{id}). */
export interface AlbumDetail {
  id: number;
  goodsItemId: number | null;
  title: string;
  artist: string;
  label: string | null;
  genre: AlbumGenre;
  status: AlbumStatus;
  releaseDate: string | null;
  description: string | null;
  coverKey: string | null;
  coverUrl: string | null;
  trackCount: number;
  viewCount: number;
  source: "SEED" | "EXTERNAL";
  price: number | null;
  stock: number | null;
  tracks: TrackDto[];
}

/** Preview resolution (GET /pub/v1/albums/{albumId}/tracks/{trackId}/preview). */
export interface PreviewResponse {
  previewUrl: string;
  startSec: number;
  lengthSec: number;
  demo: boolean;
}

/** Offline store (GET /pub/v1/stores). distanceKm is filled when lat/lng are sent. */
export interface StoreDto {
  id: number;
  regionId: number;
  name: string;
  address: string | null;
  phone: string | null;
  lat: number | null;
  lng: number | null;
  openHours: string | null;
  useYn: string;
  distanceKm: number | null;
  createdAt: string;
}

export interface AlbumListParams {
  genre?: AlbumGenre;
  keyword?: string;
  // Must match the backend AlbumSearchRequest fields (pageNumber/pageSize). Sending
  // page/size silently fell back to page 0 on the server — the broken-pagination bug.
  pageNumber?: number;
  pageSize?: number;
}

export interface StoreListParams {
  lat?: number;
  lng?: number;
  regionId?: number;
}

export const albumApi = {
  list: (p: AlbumListParams = {}) =>
    request<InfinityList<AlbumListItem>>(`/pub/v1/albums${query({ ...p })}`),
  detail: (id: number) => request<AlbumDetail>(`/pub/v1/albums/${id}`),
  preview: (albumId: number, trackId: number) =>
    request<PreviewResponse>(`/pub/v1/albums/${albumId}/tracks/${trackId}/preview`),
  stores: (p: StoreListParams = {}) => request<StoreDto[]>(`/pub/v1/stores${query({ ...p })}`),
};

export const albumKeys = {
  list: (p: AlbumListParams) => ["albums", p] as const,
  detail: (id: number) => ["album", id] as const,
  stores: (p: StoreListParams) => ["stores", p] as const,
};

export function useAlbums(params: AlbumListParams) {
  return useQuery({
    queryKey: albumKeys.list(params),
    queryFn: () => albumApi.list(params),
    placeholderData: (prev) => prev, // no flicker between pages/filters
  });
}

export function useAlbum(id: number) {
  return useQuery({
    queryKey: albumKeys.detail(id),
    queryFn: () => albumApi.detail(id),
    enabled: Number.isFinite(id) && id > 0,
  });
}

export function useStores(params: StoreListParams) {
  return useQuery({
    queryKey: albumKeys.stores(params),
    queryFn: () => albumApi.stores(params),
  });
}

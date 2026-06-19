// Member "내 정보" domain — the signed-in member's watch history, track favorites, purchased
// albums, and review/inquiry surface. Mirrors the backend public DTOs (GET/POST /pub/v1/me/*).
// Every call requires a member Bearer token (401 when missing/anonymous); reuses the shared
// request helper, matching the orders.ts convention.

"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { request } from "./api";
import type { ContentType } from "./types";

/** One watch-history row (GET /pub/v1/me/history), joined to the content, newest first. */
export interface WatchHistoryItem {
  contentId: number;
  title: string;
  type: ContentType;
  thumbnailUrl: string | null;
  watchedAt: string;
  watchSeconds: number | null;
}

/** One favorited track (GET /pub/v1/me/favorites). albumId backs the "전체듣기" album link. */
export interface FavoriteItem {
  trackId: number;
  albumId: number;
  trackTitle: string;
  albumTitle: string;
  artist: string;
  coverUrl: string | null;
  hasFullTrack: boolean;
}

/** One purchased album (GET /pub/v1/me/albums) — albums of the member's PAID orders. */
export interface PurchasedAlbumItem {
  albumId: number;
  title: string;
  artist: string;
  coverUrl: string | null;
  purchasedAt: string;
}

/** One of the member's product reviews (GET /pub/v1/me/reviews). */
export interface MyReviewItem {
  goodsId: number;
  goodsName: string;
  rating: number;
  content: string;
  createdAt: string;
}

/** Status of a product inquiry — ANSWERED once the admin has replied. */
export type InquiryStatus = "PENDING" | "ANSWERED";

/** One of the member's product inquiries (GET /pub/v1/me/inquiries). */
export interface MyInquiryItem {
  goodsId: number;
  goodsName: string;
  question: string;
  answer: string | null;
  status: InquiryStatus;
  createdAt: string;
}

/** Body of POST /pub/v1/me/history — records one (best-effort, non-idempotent) watch event. */
export interface RecordWatchInput {
  contentId: number;
  watchSeconds?: number;
}

export const meApi = {
  /** Signed-in member's watch history, newest first. */
  history: (token: string) => request<WatchHistoryItem[]>("/pub/v1/me/history", { token }),
  /** Record one watch event (best-effort; the server does not dedupe). */
  recordWatch: (input: RecordWatchInput, token: string) =>
    request<void>("/pub/v1/me/history", { method: "POST", body: input, token }),
  /** The member's favorited tracks. */
  favorites: (token: string) => request<FavoriteItem[]>("/pub/v1/me/favorites", { token }),
  /** Favorite a track (idempotent — already-favorited is a no-op server-side). */
  addFavorite: (trackId: number, token: string) =>
    request<void>("/pub/v1/me/favorites", { method: "POST", body: { trackId }, token }),
  /** Un-favorite a track. */
  removeFavorite: (trackId: number, token: string) =>
    request<void>(`/pub/v1/me/favorites/${trackId}`, { method: "DELETE", token }),
  /** Albums from the member's PAID orders. */
  albums: (token: string) => request<PurchasedAlbumItem[]>("/pub/v1/me/albums", { token }),
  /** The member's product reviews. */
  reviews: (token: string) => request<MyReviewItem[]>("/pub/v1/me/reviews", { token }),
  /** The member's product inquiries. */
  inquiries: (token: string) => request<MyInquiryItem[]>("/pub/v1/me/inquiries", { token }),
};

export const meKeys = {
  history: ["me", "history"] as const,
  favorites: ["me", "favorites"] as const,
  albums: ["me", "albums"] as const,
  reviews: ["me", "reviews"] as const,
  inquiries: ["me", "inquiries"] as const,
};

/** Watch history — enabled only when a member token is present. */
export function useMyHistory(token: string | null) {
  return useQuery({
    queryKey: meKeys.history,
    queryFn: () => meApi.history(token as string),
    enabled: token != null,
  });
}

/** Favorited tracks — enabled only when a member token is present. */
export function useMyFavorites(token: string | null) {
  return useQuery({
    queryKey: meKeys.favorites,
    queryFn: () => meApi.favorites(token as string),
    enabled: token != null,
  });
}

/** Purchased albums — enabled only when a member token is present. */
export function useMyAlbums(token: string | null) {
  return useQuery({
    queryKey: meKeys.albums,
    queryFn: () => meApi.albums(token as string),
    enabled: token != null,
  });
}

/** My product reviews — enabled only when a member token is present. */
export function useMyReviews(token: string | null) {
  return useQuery({
    queryKey: meKeys.reviews,
    queryFn: () => meApi.reviews(token as string),
    enabled: token != null,
  });
}

/** My product inquiries — enabled only when a member token is present. */
export function useMyInquiries(token: string | null) {
  return useQuery({
    queryKey: meKeys.inquiries,
    queryFn: () => meApi.inquiries(token as string),
    enabled: token != null,
  });
}

/** Remove a favorite and refresh the favorites list (used by the playlist section). */
export function useRemoveFavorite(token: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (trackId: number) => meApi.removeFavorite(trackId, token),
    onSuccess: () => qc.invalidateQueries({ queryKey: meKeys.favorites }),
  });
}

/**
 * Best-effort watch recorder for the detail pages: fire-and-forget POST that swallows errors
 * (a failed history write must never break playback). No-op when token is null (anonymous).
 */
export function recordWatch(contentId: number, token: string | null, watchSeconds?: number): void {
  if (!token) return;
  void meApi.recordWatch({ contentId, watchSeconds }, token).catch(() => {
    /* best-effort: history is non-critical */
  });
}

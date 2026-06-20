// Member "내 정보" domain — the signed-in member's watch history, favorites, purchased albums,
// reviews/inquiries (read + write), points, coupons, notifications and donations. Mirrors the
// backend public DTOs (GET/POST /pub/v1/me/*). Every call requires a member Bearer token (401 when
// missing/anonymous); reuses the shared request helper, matching the orders.ts convention.

"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { query, request } from "./api";
import type { ContentType, InfinityList } from "./types";

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
  id: number;
  goodsId: number;
  goodsName: string;
  rating: number;
  content: string;
  createdAt: string;
}

/** Inquiry answer status — mirrors the backend AnswerStatus (WAITING until the admin replies). */
export type InquiryStatus = "WAITING" | "ANSWERED";

/** One of the member's product inquiries (GET /pub/v1/me/inquiries). */
export interface MyInquiryItem {
  id: number;
  goodsId: number;
  goodsName: string;
  title: string;
  question: string;
  answer: string | null;
  status: InquiryStatus;
  createdAt: string;
}

/** Body of POST /pub/v1/me/inquiries — opens a product inquiry. */
export interface InquiryCreateInput {
  goodsItemId: number;
  title: string;
  content: string;
}

/** Body of POST /pub/v1/me/reviews — posts a product review. */
export interface ReviewCreateInput {
  goodsItemId: number;
  rating: number;
  content: string;
}

/** One point-ledger row (signed amount: positive earn, negative spend). */
export interface PointLedgerItem {
  id: number;
  amount: number;
  type: string;
  memo: string | null;
  createdAt: string;
}

/** Member point summary (GET /pub/v1/me/points): current balance + paged ledger. */
export interface MemberPoints {
  balance: number;
  ledger: InfinityList<PointLedgerItem>;
}

/** One coupon in the member's wallet (GET /pub/v1/me/coupons). */
export interface MyCouponItem {
  id: number;
  code: string;
  name: string;
  discountType: string;
  discountValue: number;
  minOrderAmount: number;
  validFrom: string | null;
  validUntil: string | null;
  used: boolean;
}

/** One notification (GET /pub/v1/me/notifications). */
export interface NotificationItem {
  id: number;
  title: string;
  body: string | null;
  read: boolean;
  createdAt: string;
}

/** One recurring donation / subscription (GET /pub/v1/me/donations). */
export interface MyDonationItem {
  id: number;
  name: string | null;
  amount: number;
  cycle: string | null;
  status: string;
  nextBillingAt: string | null;
  startedAt: string | null;
}

/** Body of POST /pub/v1/me/history — records one (best-effort, non-idempotent) watch event. */
export interface RecordWatchInput {
  contentId: number;
  watchSeconds?: number;
}

export const meApi = {
  /** A page of the member's watch history, newest first. */
  history: (token: string, pageNumber: number, pageSize: number) =>
    request<InfinityList<WatchHistoryItem>>(
      `/pub/v1/me/history${query({ pageNumber, pageSize })}`,
      { token },
    ),
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
  /** A page of albums from the member's PAID orders. */
  albums: (token: string, pageNumber: number, pageSize: number) =>
    request<InfinityList<PurchasedAlbumItem>>(
      `/pub/v1/me/albums${query({ pageNumber, pageSize })}`,
      { token },
    ),
  /** The member's product reviews. */
  reviews: (token: string) => request<MyReviewItem[]>("/pub/v1/me/reviews", { token }),
  /** Post a product review. */
  createReview: (input: ReviewCreateInput, token: string) =>
    request<MyReviewItem>("/pub/v1/me/reviews", { method: "POST", body: input, token }),
  /** The member's product inquiries. */
  inquiries: (token: string) => request<MyInquiryItem[]>("/pub/v1/me/inquiries", { token }),
  /** Open a product inquiry. */
  createInquiry: (input: InquiryCreateInput, token: string) =>
    request<MyInquiryItem>("/pub/v1/me/inquiries", { method: "POST", body: input, token }),
  /** Point balance + a page of the ledger. */
  points: (token: string, pageNumber: number, pageSize: number) =>
    request<MemberPoints>(`/pub/v1/me/points${query({ pageNumber, pageSize })}`, { token }),
  /** The member's coupon wallet (active coupons; `used` is per-member). */
  coupons: (token: string) => request<MyCouponItem[]>("/pub/v1/me/coupons", { token }),
  /** A page of the member's notifications. */
  notifications: (token: string, pageNumber: number, pageSize: number) =>
    request<InfinityList<NotificationItem>>(
      `/pub/v1/me/notifications${query({ pageNumber, pageSize })}`,
      { token },
    ),
  /** Count of the member's unread notifications (badge). */
  notificationsUnreadCount: (token: string) =>
    request<number>("/pub/v1/me/notifications/unread-count", { token }),
  /** Mark one notification read. */
  markNotificationRead: (id: number, token: string) =>
    request<void>(`/pub/v1/me/notifications/${id}/read`, { method: "POST", token }),
  /** Mark all of the member's notifications read. */
  markAllNotificationsRead: (token: string) =>
    request<void>("/pub/v1/me/notifications/read-all", { method: "POST", token }),
  /** The member's recurring donations / subscriptions. */
  donations: (token: string) => request<MyDonationItem[]>("/pub/v1/me/donations", { token }),
};

export const meKeys = {
  history: (pageNumber: number) => ["me", "history", pageNumber] as const,
  favorites: ["me", "favorites"] as const,
  albums: (pageNumber: number) => ["me", "albums", pageNumber] as const,
  reviews: ["me", "reviews"] as const,
  inquiries: ["me", "inquiries"] as const,
  points: (pageNumber: number) => ["me", "points", pageNumber] as const,
  coupons: ["me", "coupons"] as const,
  notifications: (pageNumber: number) => ["me", "notifications", pageNumber] as const,
  notificationsUnread: ["me", "notifications", "unread"] as const,
  donations: ["me", "donations"] as const,
};

/** A page of watch history — enabled only when a member token is present. */
export function useMyHistory(token: string | null, pageNumber: number, pageSize: number) {
  return useQuery({
    queryKey: meKeys.history(pageNumber),
    queryFn: () => meApi.history(token as string, pageNumber, pageSize),
    enabled: token != null,
    placeholderData: (prev) => prev,
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

/** A page of purchased albums — enabled only when a member token is present. */
export function useMyAlbums(token: string | null, pageNumber: number, pageSize: number) {
  return useQuery({
    queryKey: meKeys.albums(pageNumber),
    queryFn: () => meApi.albums(token as string, pageNumber, pageSize),
    enabled: token != null,
    placeholderData: (prev) => prev, // no flicker between pages
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

/** Point balance + ledger page — enabled only when a member token is present. */
export function useMyPoints(token: string | null, pageNumber: number, pageSize: number) {
  return useQuery({
    queryKey: meKeys.points(pageNumber),
    queryFn: () => meApi.points(token as string, pageNumber, pageSize),
    enabled: token != null,
    placeholderData: (prev) => prev,
  });
}

/** Coupon wallet — enabled only when a member token is present. */
export function useMyCoupons(token: string | null) {
  return useQuery({
    queryKey: meKeys.coupons,
    queryFn: () => meApi.coupons(token as string),
    enabled: token != null,
  });
}

/** Notifications page — enabled only when a member token is present. */
export function useMyNotifications(token: string | null, pageNumber: number, pageSize: number) {
  return useQuery({
    queryKey: meKeys.notifications(pageNumber),
    queryFn: () => meApi.notifications(token as string, pageNumber, pageSize),
    enabled: token != null,
    placeholderData: (prev) => prev,
  });
}

/** Unread notification count — enabled only when a member token is present. */
export function useMyNotificationsUnread(token: string | null) {
  return useQuery({
    queryKey: meKeys.notificationsUnread,
    queryFn: () => meApi.notificationsUnreadCount(token as string),
    enabled: token != null,
  });
}

/** Recurring donations — enabled only when a member token is present. */
export function useMyDonations(token: string | null) {
  return useQuery({
    queryKey: meKeys.donations,
    queryFn: () => meApi.donations(token as string),
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

/** Open a product inquiry, then refresh the inquiry list. */
export function useCreateInquiry(token: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: InquiryCreateInput) => meApi.createInquiry(input, token),
    onSuccess: () => qc.invalidateQueries({ queryKey: meKeys.inquiries }),
  });
}

/** Post a product review, then refresh the review list. */
export function useCreateReview(token: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: ReviewCreateInput) => meApi.createReview(input, token),
    onSuccess: () => qc.invalidateQueries({ queryKey: meKeys.reviews }),
  });
}

/** Mark a notification read, then refresh the notification list + unread count. */
export function useMarkNotificationRead(token: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => meApi.markNotificationRead(id, token),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["me", "notifications"] }),
  });
}

/** Mark all notifications read, then refresh the notification list + unread count. */
export function useMarkAllNotificationsRead(token: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => meApi.markAllNotificationsRead(token),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["me", "notifications"] }),
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

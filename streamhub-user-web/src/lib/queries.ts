"use client";

import { useQuery } from "@tanstack/react-query";
import { api, type ContentListParams, type PostListParams } from "./api";
import type { BannerTarget } from "./types";

export const queryKeys = {
  home: ["home"] as const,
  contents: (p: ContentListParams) => ["contents", p] as const,
  content: (id: number) => ["content", id] as const,
  posts: (p: PostListParams) => ["posts", p] as const,
  post: (id: number) => ["post", id] as const,
  banners: (target: BannerTarget) => ["banners", target] as const,
};

export function useHome() {
  return useQuery({ queryKey: queryKeys.home, queryFn: api.home });
}

/** Active promo banners for a content tab (admin-managed). Empty/failed → no banner shown. */
export function useBanners(target: BannerTarget) {
  return useQuery({
    queryKey: queryKeys.banners(target),
    queryFn: () => api.banners(target),
    staleTime: 5 * 60 * 1000,
  });
}

export function useContents(params: ContentListParams) {
  return useQuery({
    queryKey: queryKeys.contents(params),
    queryFn: () => api.contents(params),
    placeholderData: (prev) => prev, // keep prior page visible while the next loads (no flicker)
  });
}

export function useContent(id: number) {
  return useQuery({
    queryKey: queryKeys.content(id),
    queryFn: () => api.content(id),
    enabled: Number.isFinite(id) && id > 0,
  });
}

export function usePosts(params: PostListParams) {
  return useQuery({
    queryKey: queryKeys.posts(params),
    queryFn: () => api.posts(params),
    placeholderData: (prev) => prev,
  });
}

export function usePost(id: number) {
  return useQuery({
    queryKey: queryKeys.post(id),
    queryFn: () => api.post(id),
    enabled: Number.isFinite(id) && id > 0,
  });
}

"use client";

// Church-finder + worship-registration data layer: typed fetch functions over the
// public /pub/v1/churches & /pub/v1/worship endpoints, plus React Query hooks.
// Reuses the shared request()/query() helpers from api.ts (same envelope/ApiError path).

import { useMutation, useQuery } from "@tanstack/react-query";
import { query, request } from "./api";
import type { InfinityList } from "./types";
import type {
  ChurchDetail,
  ChurchNearbyItem,
  ChurchOption,
  WorshipRegisterRequest,
  WorshipRegisterResponse,
} from "./churchTypes";

export interface ChurchNearbyParams {
  lat?: number;
  lng?: number;
  radiusKm?: number;
  denomination?: string;
  keyword?: string;
  regionId?: number;
  pageNumber?: number;
  pageSize?: number;
}

export const churchApi = {
  /** Distance-sorted nearby churches. Omit lat/lng for the location-denied fallback. */
  nearby: (p: ChurchNearbyParams = {}) =>
    request<InfinityList<ChurchNearbyItem>>(`/pub/v1/churches${query({ ...p })}`),
  detail: (id: number) => request<ChurchDetail>(`/pub/v1/churches/${id}`),
  /** Church options for the worship-registration select. */
  worshipChurches: () => request<ChurchOption[]>("/pub/v1/worship/churches"),
  registerWorship: (body: WorshipRegisterRequest) =>
    request<WorshipRegisterResponse>("/pub/v1/worship", { method: "POST", body }),
};

export const churchKeys = {
  nearby: (p: ChurchNearbyParams) => ["churches-nearby", p] as const,
  detail: (id: number) => ["church", id] as const,
  worshipChurches: ["worship-churches"] as const,
};

/** Nearby churches. `enabled` lets callers wait until geolocation resolves. */
export function useNearbyChurches(params: ChurchNearbyParams, enabled = true) {
  return useQuery({
    queryKey: churchKeys.nearby(params),
    queryFn: () => churchApi.nearby(params),
    placeholderData: (prev) => prev, // keep prior results visible while refetching (no flicker)
    enabled,
  });
}

export function useChurchDetail(id: number) {
  return useQuery({
    queryKey: churchKeys.detail(id),
    queryFn: () => churchApi.detail(id),
    enabled: Number.isFinite(id) && id > 0,
  });
}

export function useWorshipChurches() {
  return useQuery({
    queryKey: churchKeys.worshipChurches,
    queryFn: churchApi.worshipChurches,
  });
}

export function useRegisterWorship() {
  return useMutation({
    mutationFn: (body: WorshipRegisterRequest) => churchApi.registerWorship(body),
  });
}

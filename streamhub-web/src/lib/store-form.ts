/** Shared helpers + Zod schema for the store create/edit form. */

import { z } from "zod";

import type { StoreDto } from "@/apis/query/streamHubAdminAPI.schemas";

/** Common input styling shared across the store form (mirrors goods FIELD_CLASS). */
export const FIELD_CLASS =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

/** Required latitude/longitude validator: must be present and numeric (mirrors the server @NotNull). */
const coordSchema = z
  .string()
  .min(1, "좌표를 입력하세요.")
  .refine((value) => Number.isFinite(Number(value)), "숫자를 입력하세요.");

/**
 * storeFormSchema validates the store create/edit form. Numeric fields are kept
 * as strings (native input values) and coerced at submit time via buildPayload.
 * regionId/lat/lng/openHours are required to match the server-side DTO validation.
 */
export const storeFormSchema = z.object({
  name: z.string().min(1, "매장명을 입력하세요."),
  regionId: z
    .string()
    .min(1, "지역을 선택하세요.")
    .refine((value) => Number.isFinite(Number(value)), "지역이 올바르지 않습니다."),
  address: z.string().optional(),
  phone: z.string().optional(),
  lat: coordSchema,
  lng: coordSchema,
  openHours: z.string().min(1, "운영시간을 입력하세요."),
  useYn: z.enum(["Y", "N"]),
});

export type StoreFormValues = z.infer<typeof storeFormSchema>;

/** Builds default form values from an existing store (or an empty form). */
export function buildStoreDefaults(store?: StoreDto): StoreFormValues {
  return {
    name: store?.name ?? "",
    regionId: store?.regionId != null ? String(store.regionId) : "",
    address: store?.address ?? "",
    phone: store?.phone ?? "",
    lat: store?.lat != null ? String(store.lat) : "",
    lng: store?.lng != null ? String(store.lng) : "",
    openHours: store?.openHours ?? "",
    useYn: store?.useYn === "N" ? "N" : "Y",
  };
}

/** Builds the API payload from validated form values (id supplied separately). */
export function buildStorePayload(values: StoreFormValues): StoreDto {
  return {
    name: values.name.trim(),
    regionId: Number(values.regionId),
    address: values.address?.trim() || undefined,
    phone: values.phone?.trim() || undefined,
    lat: Number(values.lat),
    lng: Number(values.lng),
    openHours: values.openHours.trim(),
    useYn: values.useYn,
  };
}

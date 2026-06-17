/** Shared helpers + Zod schema for the goods create/edit forms (add + [id]). */

import { z } from "zod";

import type {
  GoodsCreateRequest,
  GoodsDetail,
  GoodsOptionDto,
  GoodsImageDto,
} from "@/apis/query/streamHubAdminAPI.schemas";

/** Common input styling used across the goods forms (mirrors content FIELD_CLASS). */
export const FIELD_CLASS =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

/** Option row in the dynamic form (string-typed for RHF inputs). */
const optionRowSchema = z.object({
  name: z.string().min(1, "옵션명을 입력하세요."),
  optionType: z.string().optional(),
  extraPrice: z.string().optional(),
  stock: z.string().optional(),
  useYn: z.enum(["Y", "N"]),
});

/** Image row in the dynamic form. s3Key is filled in by the upload widget. */
const imageRowSchema = z.object({
  s3Key: z.string().min(1, "이미지를 업로드하세요."),
  url: z.string().optional(),
  sort: z.string().optional(),
});

/**
 * goodsFormSchema validates the goods create/edit form. Numeric fields are kept
 * as strings (native input values) and coerced at submit time via buildPayload.
 */
export const goodsFormSchema = z.object({
  name: z.string().min(1, "상품명을 입력하세요."),
  code: z.string().min(1, "상품코드를 입력하세요."),
  categoryId: z
    .string()
    .min(1, "분류를 선택하세요.")
    .refine((value) => Number.isFinite(Number(value)), "분류를 선택하세요."),
  description: z.string().optional(),
  price: z
    .string()
    .min(1, "판매가를 입력하세요.")
    .refine((value) => Number(value) >= 0, "판매가는 0 이상이어야 합니다."),
  listPrice: z.string().optional(),
  stock: z.string().optional(),
  notiQty: z.string().optional(),
  soldOut: z.enum(["Y", "N"]),
  useYn: z.enum(["Y", "N"]),
  status: z.enum(["SELLING", "PAUSED"]),
  badges: z.string().optional(),
  options: z.array(optionRowSchema),
  images: z.array(imageRowSchema),
});

export type GoodsFormValues = z.infer<typeof goodsFormSchema>;
export type GoodsOptionRow = z.infer<typeof optionRowSchema>;
export type GoodsImageRow = z.infer<typeof imageRowSchema>;

/** Parses a comma-separated badges string into a clean list (HIT, NEW, SALE → [...]). */
export function parseBadges(input?: string): string[] | undefined {
  if (!input) {
    return undefined;
  }
  const list = input
    .split(",")
    .map((badge) => badge.trim().toUpperCase())
    .filter((badge) => badge.length > 0);
  return list.length > 0 ? list : undefined;
}

/** Converts an optional string input to a finite number, or undefined when blank. */
function toNumber(value?: string): number | undefined {
  if (value == null || value.trim() === "") {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

/** Builds default form values from an existing detail (or an empty form). */
export function buildGoodsDefaults(detail?: GoodsDetail): GoodsFormValues {
  return {
    name: detail?.name ?? "",
    code: detail?.code ?? "",
    categoryId: detail?.categoryId != null ? String(detail.categoryId) : "",
    description: detail?.description ?? "",
    price: detail?.price != null ? String(detail.price) : "",
    listPrice: detail?.listPrice != null ? String(detail.listPrice) : "",
    stock: detail?.stock != null ? String(detail.stock) : "",
    notiQty: detail?.notiQty != null ? String(detail.notiQty) : "",
    soldOut: detail?.soldOut === "Y" ? "Y" : "N",
    useYn: detail?.useYn === "N" ? "N" : "Y",
    status: detail?.status === "PAUSED" ? "PAUSED" : "SELLING",
    badges: detail?.badges ?? "",
    options: (detail?.options ?? []).map((option) => ({
      name: option.name ?? "",
      optionType: option.optionType ?? "",
      extraPrice: option.extraPrice != null ? String(option.extraPrice) : "",
      stock: option.stock != null ? String(option.stock) : "",
      useYn: option.useYn === "N" ? "N" : "Y",
    })),
    images: (detail?.images ?? []).map((image) => ({
      s3Key: image.s3Key ?? "",
      url: image.url ?? "",
      sort: image.sort != null ? String(image.sort) : "",
    })),
  };
}

/** Builds the API payload from validated form values + the chosen thumbnail key. */
export function buildGoodsPayload(
  values: GoodsFormValues,
  thumbnailKey?: string,
): GoodsCreateRequest {
  const options: GoodsOptionDto[] = values.options.map((row, index) => ({
    name: row.name.trim(),
    optionType: row.optionType?.trim() || undefined,
    extraPrice: toNumber(row.extraPrice) ?? 0,
    stock: toNumber(row.stock) ?? 0,
    useYn: row.useYn,
    sort: index,
  }));

  const images: GoodsImageDto[] = values.images.map((row, index) => ({
    s3Key: row.s3Key,
    sort: toNumber(row.sort) ?? index,
  }));

  return {
    categoryId: Number(values.categoryId),
    name: values.name.trim(),
    code: values.code.trim(),
    description: values.description?.trim() || undefined,
    price: toNumber(values.price) ?? 0,
    listPrice: toNumber(values.listPrice),
    stock: toNumber(values.stock),
    notiQty: toNumber(values.notiQty),
    soldOut: values.soldOut,
    useYn: values.useYn,
    status: values.status,
    thumbnailKey: thumbnailKey || undefined,
    badges: parseBadges(values.badges),
    options,
    images,
  };
}

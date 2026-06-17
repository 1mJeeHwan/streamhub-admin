/** Shared helpers + schema for the manual point-grant form. */

import { z } from "zod";

/** Common input styling used across the point screens (mirrors content-form). */
export const FIELD_CLASS =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

/**
 * grantSchema validates the manual grant/deduct form. All fields are strings
 * (native inputs) and coerced to numbers on submit. delta must be a non-zero
 * integer; negative means deduction. expireDays empty → undefined (무기한).
 */
export const grantSchema = z.object({
  memberId: z
    .string()
    .min(1, "회원 ID를 입력하세요.")
    .refine((value) => Number.isFinite(Number(value)), "숫자만 입력하세요."),
  delta: z
    .string()
    .min(1, "포인트를 입력하세요.")
    .refine(
      (value) => Number.isInteger(Number(value)) && Number(value) !== 0,
      "0이 아닌 정수를 입력하세요.",
    ),
  reason: z.string().min(1, "사유를 입력하세요."),
  expireDays: z
    .string()
    .optional()
    .refine(
      (value) =>
        !value || (Number.isInteger(Number(value)) && Number(value) > 0),
      "유효기간은 1 이상의 정수로 입력하세요.",
    ),
});

export type GrantFormValues = z.infer<typeof grantSchema>;

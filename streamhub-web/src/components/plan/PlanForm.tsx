"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";

import {
  PlanCreateRequestGrade,
  type PlanCreateRequest,
  type PlanResponse,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { FIELD_CLASS } from "@/lib/content-form";
import { GRADE_LABEL, type PlanGrade } from "@/lib/subscription-status";

const planSchema = z.object({
  name: z.string().min(1, "플랜명을 입력하세요."),
  grade: z.enum(["BRONZE", "SILVER", "GOLD", "ANGEL"], {
    errorMap: () => ({ message: "등급을 선택하세요." }),
  }),
  price: z.coerce.number().int().positive("금액은 0보다 커야 합니다."),
  periodMonths: z.coerce.number().int().positive("결제 주기는 1 이상이어야 합니다."),
  pointRate: z.coerce
    .number()
    .int()
    .min(0, "0 이상이어야 합니다.")
    .max(100, "100 이하여야 합니다."),
  benefit: z.string().optional(),
  active: z.enum(["Y", "N"]),
});

type PlanFormValues = z.infer<typeof planSchema>;

const GRADE_ORDER: PlanGrade[] = ["BRONZE", "SILVER", "GOLD", "ANGEL"];

interface PlanFormProps {
  /** Existing plan when editing; undefined when creating. */
  plan?: PlanResponse;
  /** Whether the submit mutation is in flight. */
  isPending: boolean;
  /** Inline error/info message shown above the form. */
  message?: string | null;
  onSubmit: (payload: PlanCreateRequest) => void;
  onCancel: () => void;
}

function buildDefaults(plan?: PlanResponse): PlanFormValues {
  return {
    name: plan?.name ?? "",
    grade: (plan?.grade as PlanGrade | undefined) ?? "BRONZE",
    price: plan?.price ?? 0,
    periodMonths: plan?.periodMonths ?? 1,
    pointRate: plan?.pointRate ?? 5,
    benefit: plan?.benefit ?? "",
    active: plan?.active === "N" ? "N" : "Y",
  };
}

/**
 * PlanForm is the shared create/edit form for membership plans (RHF + Zod).
 * Submission is delegated to the parent via onSubmit; the parent owns the
 * mutation and success handling.
 */
export default function PlanForm({
  plan,
  isPending,
  message,
  onSubmit,
  onCancel,
}: PlanFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PlanFormValues>({
    resolver: zodResolver(planSchema),
    defaultValues: buildDefaults(plan),
  });

  const submit = (values: PlanFormValues) => {
    const payload: PlanCreateRequest = {
      name: values.name.trim(),
      grade: values.grade,
      price: values.price,
      periodMonths: values.periodMonths,
      pointRate: values.pointRate,
      benefit: values.benefit?.trim() || undefined,
      active: values.active,
    };
    onSubmit(payload);
  };

  return (
    <form onSubmit={handleSubmit(submit)} noValidate>
      {message && (
        <p className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {message}
        </p>
      )}

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        {/* Name */}
        <div className="sm:col-span-2">
          <label
            htmlFor="name"
            className="mb-1 block text-xs font-medium text-slate-500"
          >
            플랜명 *
          </label>
          <input id="name" type="text" className={FIELD_CLASS} {...register("name")} />
          {errors.name && (
            <p className="mt-1 text-xs text-red-600">{errors.name.message}</p>
          )}
        </div>

        {/* Grade */}
        <div>
          <label
            htmlFor="grade"
            className="mb-1 block text-xs font-medium text-slate-500"
          >
            등급 *
          </label>
          <select id="grade" className={FIELD_CLASS} {...register("grade")}>
            {GRADE_ORDER.map((grade) => (
              <option key={grade} value={PlanCreateRequestGrade[grade]}>
                {GRADE_LABEL[grade]}
              </option>
            ))}
          </select>
          {errors.grade && (
            <p className="mt-1 text-xs text-red-600">{errors.grade.message}</p>
          )}
        </div>

        {/* Active */}
        <div>
          <label
            htmlFor="active"
            className="mb-1 block text-xs font-medium text-slate-500"
          >
            상태
          </label>
          <select id="active" className={FIELD_CLASS} {...register("active")}>
            <option value="Y">활성</option>
            <option value="N">비활성</option>
          </select>
        </div>

        {/* Price */}
        <div>
          <label
            htmlFor="price"
            className="mb-1 block text-xs font-medium text-slate-500"
          >
            금액 (원) *
          </label>
          <input
            id="price"
            type="number"
            min={0}
            className={FIELD_CLASS}
            {...register("price")}
          />
          {errors.price && (
            <p className="mt-1 text-xs text-red-600">{errors.price.message}</p>
          )}
        </div>

        {/* Period months */}
        <div>
          <label
            htmlFor="periodMonths"
            className="mb-1 block text-xs font-medium text-slate-500"
          >
            결제 주기 (개월) *
          </label>
          <input
            id="periodMonths"
            type="number"
            min={1}
            className={FIELD_CLASS}
            {...register("periodMonths")}
          />
          {errors.periodMonths && (
            <p className="mt-1 text-xs text-red-600">
              {errors.periodMonths.message}
            </p>
          )}
        </div>

        {/* Point rate */}
        <div>
          <label
            htmlFor="pointRate"
            className="mb-1 block text-xs font-medium text-slate-500"
          >
            적립률 (%) *
          </label>
          <input
            id="pointRate"
            type="number"
            min={0}
            max={100}
            className={FIELD_CLASS}
            {...register("pointRate")}
          />
          {errors.pointRate && (
            <p className="mt-1 text-xs text-red-600">
              {errors.pointRate.message}
            </p>
          )}
        </div>

        {/* Benefit */}
        <div className="sm:col-span-2">
          <label
            htmlFor="benefit"
            className="mb-1 block text-xs font-medium text-slate-500"
          >
            혜택 설명
          </label>
          <textarea
            id="benefit"
            rows={2}
            className={FIELD_CLASS}
            {...register("benefit")}
          />
        </div>
      </div>

      <div className="mt-6 flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={isPending}
          className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:opacity-60"
        >
          취소
        </button>
        <button
          type="submit"
          disabled={isPending}
          className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
        >
          {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
          저장
        </button>
      </div>
    </form>
  );
}

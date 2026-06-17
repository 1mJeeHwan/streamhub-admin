"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";

import { useGrant } from "@/apis/query/point/point";
import type { PointGrantRequest } from "@/apis/query/streamHubAdminAPI.schemas";
import { FIELD_CLASS, grantSchema, type GrantFormValues } from "@/lib/point-form";
import { SUCCESS_CODE } from "@/types/api";

interface GrantPointFormProps {
  /** Called after a successful grant so the parent can refetch the ledger. */
  onSuccess: () => void;
  /** Called when the panel should close (취소). */
  onCancel: () => void;
}

/**
 * GrantPointForm is the manual point grant / deduction panel (RHF + Zod).
 * A negative delta deducts points; expireDays empty means 무기한.
 */
export default function GrantPointForm({
  onSuccess,
  onCancel,
}: GrantPointFormProps) {
  const grantMutation = useGrant();

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setError,
    formState: { errors },
  } = useForm<GrantFormValues>({
    resolver: zodResolver(grantSchema),
    defaultValues: {
      memberId: "",
      delta: "",
      reason: "",
      expireDays: "",
    },
  });

  // Live hint: a negative delta is a deduction (gnuboard "음수 가능").
  const deltaValue = Number(watch("delta"));
  const isDeduct = Number.isFinite(deltaValue) && deltaValue < 0;

  const onSubmit = (values: GrantFormValues) => {
    const payload: PointGrantRequest = {
      memberId: Number(values.memberId),
      delta: Number(values.delta),
      reason: values.reason.trim(),
      expireDays: values.expireDays ? Number(values.expireDays) : undefined,
    };

    grantMutation.mutate(
      { data: payload },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            reset();
            onSuccess();
          } else {
            setError("root", {
              message: response.resultMessage ?? "포인트 지급에 실패했습니다.",
            });
          }
        },
        onError: () =>
          setError("root", {
            message: "포인트 지급 중 오류가 발생했습니다.",
          }),
      },
    );
  };

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="mb-4 rounded-md border border-slate-200 bg-white p-6"
      noValidate
    >
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-base font-semibold text-slate-900">포인트 지급</h2>
        {isDeduct && (
          <span className="rounded-full bg-red-50 px-2.5 py-0.5 text-xs font-medium text-red-600">
            음수 입력 — 차감 처리됩니다
          </span>
        )}
      </div>

      {errors.root && (
        <p className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {errors.root.message}
        </p>
      )}

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        {/* Member ID */}
        <div>
          <label
            htmlFor="memberId"
            className="mb-1 block text-xs font-medium text-slate-500"
          >
            회원 ID *
          </label>
          <input
            id="memberId"
            type="number"
            min={1}
            className={FIELD_CLASS}
            {...register("memberId")}
          />
          {errors.memberId && (
            <p className="mt-1 text-xs text-red-600">
              {errors.memberId.message}
            </p>
          )}
        </div>

        {/* Delta */}
        <div>
          <label
            htmlFor="delta"
            className="mb-1 block text-xs font-medium text-slate-500"
          >
            포인트 * <span className="text-slate-400">(음수 = 차감)</span>
          </label>
          <input
            id="delta"
            type="number"
            className={FIELD_CLASS}
            {...register("delta")}
          />
          {errors.delta && (
            <p className="mt-1 text-xs text-red-600">{errors.delta.message}</p>
          )}
        </div>

        {/* Reason */}
        <div className="sm:col-span-2">
          <label
            htmlFor="reason"
            className="mb-1 block text-xs font-medium text-slate-500"
          >
            사유 *
          </label>
          <input
            id="reason"
            type="text"
            placeholder="예: 가입 축하 포인트"
            className={FIELD_CLASS}
            {...register("reason")}
          />
          {errors.reason && (
            <p className="mt-1 text-xs text-red-600">{errors.reason.message}</p>
          )}
        </div>

        {/* Expire days */}
        <div>
          <label
            htmlFor="expireDays"
            className="mb-1 block text-xs font-medium text-slate-500"
          >
            유효기간 (일) <span className="text-slate-400">(빈값 = 무기한)</span>
          </label>
          <input
            id="expireDays"
            type="number"
            min={1}
            className={FIELD_CLASS}
            {...register("expireDays")}
          />
          {errors.expireDays && (
            <p className="mt-1 text-xs text-red-600">
              {errors.expireDays.message}
            </p>
          )}
        </div>
      </div>

      <div className="mt-6 flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={grantMutation.isPending}
          className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
        >
          취소
        </button>
        <button
          type="submit"
          disabled={grantMutation.isPending}
          className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
        >
          {grantMutation.isPending && (
            <Loader2 className="h-4 w-4 animate-spin" />
          )}
          지급
        </button>
      </div>
    </form>
  );
}

"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Loader2, X } from "lucide-react";

import { useCouponCreate, useCouponUpdate } from "@/apis/query/coupon/coupon";
import {
  type CouponDto,
  type CouponDtoDiscountType,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { SUCCESS_CODE } from "@/types/api";

const FIELD_CLASS =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

interface CouponFormDialogProps {
  /** Coupon being edited, or null/undefined when creating a new one. */
  coupon?: CouponDto | null;
  onClose: () => void;
  /** Called after a successful create/update so the parent can refetch. */
  onSaved: () => void;
}

interface CouponFormValues {
  code: string;
  name: string;
  discountType: CouponDtoDiscountType;
  discountValue: string;
  minOrderAmount: string;
  maxDiscountAmount: string;
  roundUnit: string;
  usageLimit: string;
  startAt: string;
  endAt: string;
  useYn: string;
}

/** Converts an ISO/datetime string to the value expected by datetime-local inputs. */
const toLocalInput = (value?: string): string => {
  if (!value) {
    return "";
  }
  // Keep only "YYYY-MM-DDTHH:mm" so existing backend values map cleanly.
  return value.slice(0, 16);
};

const buildDefaults = (coupon?: CouponDto | null): CouponFormValues => ({
  code: coupon?.code ?? "",
  name: coupon?.name ?? "",
  discountType: coupon?.discountType ?? "AMOUNT",
  discountValue: coupon?.discountValue != null ? String(coupon.discountValue) : "",
  minOrderAmount:
    coupon?.minOrderAmount != null ? String(coupon.minOrderAmount) : "",
  maxDiscountAmount:
    coupon?.maxDiscountAmount != null ? String(coupon.maxDiscountAmount) : "",
  roundUnit: coupon?.roundUnit != null ? String(coupon.roundUnit) : "",
  usageLimit: coupon?.usageLimit != null ? String(coupon.usageLimit) : "",
  startAt: toLocalInput(coupon?.startAt),
  endAt: toLocalInput(coupon?.endAt),
  useYn: coupon?.useYn ?? "Y",
});

const toNumber = (value: string): number | undefined => {
  const trimmed = value.trim();
  if (trimmed === "") {
    return undefined;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
};

const buildPayload = (values: CouponFormValues): CouponDto => ({
  code: values.code.trim(),
  name: values.name.trim(),
  discountType: values.discountType,
  discountValue: toNumber(values.discountValue),
  minOrderAmount: toNumber(values.minOrderAmount),
  maxDiscountAmount: toNumber(values.maxDiscountAmount),
  roundUnit: toNumber(values.roundUnit),
  usageLimit: toNumber(values.usageLimit),
  startAt: values.startAt ? values.startAt : undefined,
  endAt: values.endAt ? values.endAt : undefined,
  useYn: values.useYn,
});

/**
 * CouponFormDialog is a modal create/edit form for a coupon. When `coupon` has
 * an id it issues an update; otherwise it creates a new coupon. The discount
 * type select toggles between fixed-amount and percentage discounts.
 */
export default function CouponFormDialog({
  coupon,
  onClose,
  onSaved,
}: CouponFormDialogProps) {
  const isEdit = coupon?.id != null;
  const [message, setMessage] = useState<string | null>(null);

  const createMutation = useCouponCreate();
  const updateMutation = useCouponUpdate();
  const isPending = createMutation.isPending || updateMutation.isPending;

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<CouponFormValues>({
    defaultValues: buildDefaults(coupon),
  });

  useEffect(() => {
    reset(buildDefaults(coupon));
  }, [coupon, reset]);

  const discountType = watch("discountType");
  const isPercent = discountType === "PERCENT";

  const handleResult = (resultCode?: string, resultMessage?: string) => {
    if (resultCode === SUCCESS_CODE) {
      onSaved();
    } else {
      setMessage(resultMessage ?? "저장에 실패했습니다.");
    }
  };

  const onSubmit = (values: CouponFormValues) => {
    setMessage(null);
    const payload = buildPayload(values);

    if (isEdit && coupon?.id != null) {
      updateMutation.mutate(
        { id: coupon.id, data: payload },
        {
          onSuccess: (response) =>
            handleResult(response.resultCode, response.resultMessage),
          onError: () => setMessage("저장 중 오류가 발생했습니다."),
        },
      );
    } else {
      createMutation.mutate(
        { data: payload },
        {
          onSuccess: (response) =>
            handleResult(response.resultCode, response.resultMessage),
          onError: () => setMessage("저장 중 오류가 발생했습니다."),
        },
      );
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="w-full max-w-lg rounded-md bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
          <h2 className="text-base font-semibold text-slate-900">
            {isEdit ? "쿠폰 수정" : "쿠폰 등록"}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
            aria-label="닫기"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="px-5 py-4" noValidate>
          {message && (
            <p className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
              {message}
            </p>
          )}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {/* Code */}
            <div>
              <label
                htmlFor="coupon-code"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                쿠폰코드 *
              </label>
              <input
                id="coupon-code"
                type="text"
                placeholder="예: WELCOME10"
                className={FIELD_CLASS}
                {...register("code", { required: "쿠폰코드를 입력하세요." })}
              />
              {errors.code && (
                <p className="mt-1 text-xs text-red-600">
                  {errors.code.message}
                </p>
              )}
            </div>

            {/* Name */}
            <div>
              <label
                htmlFor="coupon-name"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                쿠폰명 *
              </label>
              <input
                id="coupon-name"
                type="text"
                className={FIELD_CLASS}
                {...register("name", { required: "쿠폰명을 입력하세요." })}
              />
              {errors.name && (
                <p className="mt-1 text-xs text-red-600">
                  {errors.name.message}
                </p>
              )}
            </div>

            {/* Discount type */}
            <div>
              <label
                htmlFor="coupon-discount-type"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                할인유형 *
              </label>
              <select
                id="coupon-discount-type"
                className={FIELD_CLASS}
                {...register("discountType")}
              >
                <option value="AMOUNT">정액 (원)</option>
                <option value="PERCENT">정률 (%)</option>
              </select>
            </div>

            {/* Discount value */}
            <div>
              <label
                htmlFor="coupon-discount-value"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                {isPercent ? "할인율 (%)" : "할인금액 (원)"}
              </label>
              <input
                id="coupon-discount-value"
                type="number"
                min={0}
                className={FIELD_CLASS}
                {...register("discountValue")}
              />
            </div>

            {/* Min order amount */}
            <div>
              <label
                htmlFor="coupon-min-order"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                최소주문금액 (원)
              </label>
              <input
                id="coupon-min-order"
                type="number"
                min={0}
                className={FIELD_CLASS}
                {...register("minOrderAmount")}
              />
            </div>

            {/* Max discount amount (relevant for percent) */}
            <div>
              <label
                htmlFor="coupon-max-discount"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                최대할인금액 (원)
              </label>
              <input
                id="coupon-max-discount"
                type="number"
                min={0}
                placeholder={isPercent ? "정률 할인 한도" : "선택"}
                className={FIELD_CLASS}
                {...register("maxDiscountAmount")}
              />
            </div>

            {/* Round unit */}
            <div>
              <label
                htmlFor="coupon-round-unit"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                절사단위 (원)
              </label>
              <input
                id="coupon-round-unit"
                type="number"
                min={0}
                placeholder="예: 100"
                className={FIELD_CLASS}
                {...register("roundUnit")}
              />
            </div>

            {/* Usage limit */}
            <div>
              <label
                htmlFor="coupon-usage-limit"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                사용 가능 횟수
              </label>
              <input
                id="coupon-usage-limit"
                type="number"
                min={0}
                placeholder="비워두면 무제한"
                className={FIELD_CLASS}
                {...register("usageLimit")}
              />
            </div>

            {/* Use yn */}
            <div>
              <label
                htmlFor="coupon-use"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                사용여부
              </label>
              <select
                id="coupon-use"
                className={FIELD_CLASS}
                {...register("useYn")}
              >
                <option value="Y">사용</option>
                <option value="N">미사용</option>
              </select>
            </div>

            {/* Start at */}
            <div>
              <label
                htmlFor="coupon-start"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                시작일시
              </label>
              <input
                id="coupon-start"
                type="datetime-local"
                className={FIELD_CLASS}
                {...register("startAt")}
              />
            </div>

            {/* End at */}
            <div>
              <label
                htmlFor="coupon-end"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                종료일시
              </label>
              <input
                id="coupon-end"
                type="datetime-local"
                className={FIELD_CLASS}
                {...register("endAt")}
              />
            </div>
          </div>

          <div className="mt-5 flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
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
      </div>
    </div>
  );
}

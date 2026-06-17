"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import { list3 } from "@/apis/query/member/member";
import type { OnceDonationRequest } from "@/apis/query/streamHubAdminAPI.schemas";
import { FIELD_CLASS } from "@/lib/content-form";
import TestModeBadge from "@/components/donation/TestModeBadge";

const onceSchema = z.object({
  memberId: z
    .string()
    .min(1, "회원을 선택하세요.")
    .refine((value) => Number.isFinite(Number(value)), "회원을 선택하세요."),
  amount: z.coerce.number().int().positive("금액은 0보다 커야 합니다."),
});

type OnceFormValues = z.infer<typeof onceSchema>;

interface OnceDonationModalProps {
  isPending: boolean;
  message?: string | null;
  onSubmit: (payload: OnceDonationRequest) => void;
  onClose: () => void;
}

/**
 * OnceDonationModal collects a member + amount for a manual single donation.
 * All single donations are recorded in test mode (no real PG). Submission is
 * delegated to the parent via onSubmit.
 */
export default function OnceDonationModal({
  isPending,
  message,
  onSubmit,
  onClose,
}: OnceDonationModalProps) {
  // Load a generous page of members for the picker (demo dataset is small).
  const membersQuery = useQuery({
    queryKey: ["donation-member-options"],
    queryFn: ({ signal }) =>
      list3({ pageNumber: 0, pageSize: 200 }, signal),
  });
  const members = membersQuery.data?.resultObject?.contents ?? [];

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<OnceFormValues>({
    resolver: zodResolver(onceSchema),
    defaultValues: { memberId: "", amount: 10000 },
  });

  const submit = (values: OnceFormValues) => {
    onSubmit({ memberId: Number(values.memberId), amount: values.amount });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="w-full max-w-md rounded-md border border-slate-200 bg-white p-6 shadow-lg">
        <div className="mb-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold text-slate-900">단건 후원 등록</h2>
            <TestModeBadge />
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
            aria-label="닫기"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit(submit)} noValidate>
          {message && (
            <p className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
              {message}
            </p>
          )}

          <div className="grid grid-cols-1 gap-5">
            {/* Member */}
            <div>
              <label
                htmlFor="memberId"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                회원 *
              </label>
              <select
                id="memberId"
                className={FIELD_CLASS}
                disabled={membersQuery.isLoading}
                {...register("memberId")}
              >
                <option value="">
                  {membersQuery.isLoading ? "불러오는 중…" : "선택하세요"}
                </option>
                {members.map((member) => (
                  <option key={member.id} value={member.id}>
                    {member.name ?? `회원 ${member.id}`}
                    {member.email ? ` (${member.email})` : ""}
                  </option>
                ))}
              </select>
              {errors.memberId && (
                <p className="mt-1 text-xs text-red-600">
                  {errors.memberId.message}
                </p>
              )}
            </div>

            {/* Amount */}
            <div>
              <label
                htmlFor="amount"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                금액 (원) *
              </label>
              <input
                id="amount"
                type="number"
                min={0}
                step={1000}
                className={FIELD_CLASS}
                {...register("amount")}
              />
              {errors.amount && (
                <p className="mt-1 text-xs text-red-600">
                  {errors.amount.message}
                </p>
              )}
            </div>
          </div>

          <div className="mt-6 flex justify-end gap-2">
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
              등록
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

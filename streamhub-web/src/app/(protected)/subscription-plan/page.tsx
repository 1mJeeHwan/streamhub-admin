"use client";

import { useState } from "react";
import { useSession } from "next-auth/react";
import { Loader2, Plus, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import {
  list8,
  useCreate,
  useDelete,
  useUpdate,
} from "@/apis/query/subscription-plan/subscription-plan";
import type {
  PlanCreateRequest,
  PlanResponse,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { isSystem } from "@/lib/auth-utils";
import { formatNumber } from "@/lib/format";
import GradeBadge from "@/components/subscription/GradeBadge";
import TestModeBadge from "@/components/donation/TestModeBadge";
import PlanForm from "@/components/plan/PlanForm";
import { SUCCESS_CODE } from "@/types/api";

const PLAN_LIST_KEY = ["plan-list"];

type ModalState =
  | { mode: "closed" }
  | { mode: "create" }
  | { mode: "edit"; plan: PlanResponse };

export default function SubscriptionPlanPage() {
  const { data: session } = useSession();
  const canDelete = isSystem(session?.user?.role);

  const [modal, setModal] = useState<ModalState>({ mode: "closed" });
  const [message, setMessage] = useState<string | null>(null);

  const listQuery = useQuery({
    queryKey: PLAN_LIST_KEY,
    queryFn: ({ signal }) => list8(signal),
  });
  const plans = listQuery.data?.resultObject ?? [];

  const createMutation = useCreate();
  const updateMutation = useUpdate();
  const deleteMutation = useDelete();

  const closeModal = () => {
    setModal({ mode: "closed" });
    setMessage(null);
  };

  const handleSubmit = (payload: PlanCreateRequest) => {
    setMessage(null);

    if (modal.mode === "edit") {
      updateMutation.mutate(
        { id: modal.plan.id ?? 0, data: payload },
        {
          onSuccess: (response) => {
            if (response.resultCode === SUCCESS_CODE) {
              closeModal();
              listQuery.refetch();
            } else {
              setMessage(response.resultMessage ?? "저장에 실패했습니다.");
            }
          },
          onError: () => setMessage("저장 중 오류가 발생했습니다."),
        },
      );
      return;
    }

    createMutation.mutate(
      { data: payload },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            closeModal();
            listQuery.refetch();
          } else {
            setMessage(response.resultMessage ?? "등록에 실패했습니다.");
          }
        },
        onError: () => setMessage("등록 중 오류가 발생했습니다."),
      },
    );
  };

  const handleDelete = (plan: PlanResponse) => {
    if (!window.confirm(`'${plan.name}' 플랜을 삭제하시겠습니까?`)) {
      return;
    }
    deleteMutation.mutate(
      { id: plan.id ?? 0 },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            listQuery.refetch();
          } else {
            window.alert(response.resultMessage ?? "삭제에 실패했습니다.");
          }
        },
        onError: () => window.alert("삭제 중 오류가 발생했습니다."),
      },
    );
  };

  const isSubmitting = createMutation.isPending || updateMutation.isPending;

  return (
    <div>
      <div className="mb-4 flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-semibold text-slate-900">멤버십 플랜</h1>
            <TestModeBadge />
          </div>
          <p className="mt-1 text-sm text-slate-500">
            정기후원 멤버십 플랜을 등록 / 수정 / 삭제합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            setMessage(null);
            setModal({ mode: "create" });
          }}
          className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          <Plus className="h-4 w-4" />
          플랜 등록
        </button>
      </div>

      {listQuery.isLoading ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : listQuery.isError ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">플랜 목록을 불러오지 못했습니다.</p>
        </div>
      ) : plans.length === 0 ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-slate-500">등록된 플랜이 없습니다.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {plans.map((plan) => (
            <div
              key={plan.id}
              className="flex flex-col rounded-md border border-slate-200 bg-white p-5"
            >
              <div className="mb-3 flex items-center justify-between">
                <GradeBadge grade={plan.grade ?? undefined} />
                <span
                  className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                    plan.active === "N"
                      ? "bg-slate-200 text-slate-500"
                      : "bg-emerald-100 text-emerald-700"
                  }`}
                >
                  {plan.active === "N" ? "비활성" : "활성"}
                </span>
              </div>

              <h2 className="text-base font-semibold text-slate-900">
                {plan.name ?? "-"}
              </h2>
              <p className="mt-1 text-2xl font-bold text-slate-900">
                ₩{formatNumber(plan.price)}
                <span className="ml-1 text-sm font-normal text-slate-500">
                  / {plan.periodMonths ?? 1}개월
                </span>
              </p>

              <dl className="mt-3 space-y-1 text-sm text-slate-600">
                <div className="flex justify-between">
                  <dt>적립률</dt>
                  <dd className="font-medium text-slate-900">
                    {plan.pointRate ?? 0}%
                  </dd>
                </div>
                <div className="flex justify-between">
                  <dt>구독자</dt>
                  <dd className="font-medium text-slate-900">
                    {formatNumber(plan.activeSubscriptionCount)}명
                  </dd>
                </div>
              </dl>

              {plan.benefit && (
                <p className="mt-3 text-xs text-slate-500">{plan.benefit}</p>
              )}

              <div className="mt-4 flex gap-2 border-t border-slate-100 pt-4">
                <button
                  type="button"
                  onClick={() => {
                    setMessage(null);
                    setModal({ mode: "edit", plan });
                  }}
                  className="flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-100"
                >
                  수정
                </button>
                <button
                  type="button"
                  onClick={() => handleDelete(plan)}
                  disabled={!canDelete || deleteMutation.isPending}
                  title={canDelete ? undefined : "SYSTEM 관리자만 삭제할 수 있습니다."}
                  className="flex-1 rounded-md border border-red-300 px-3 py-1.5 text-sm font-medium text-red-600 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  삭제
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create / edit modal */}
      {modal.mode !== "closed" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-lg rounded-md border border-slate-200 bg-white p-6 shadow-lg">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-900">
                {modal.mode === "edit" ? "플랜 수정" : "플랜 등록"}
              </h2>
              <button
                type="button"
                onClick={closeModal}
                className="rounded-md p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
                aria-label="닫기"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <PlanForm
              plan={modal.mode === "edit" ? modal.plan : undefined}
              isPending={isSubmitting}
              message={message}
              onSubmit={handleSubmit}
              onCancel={closeModal}
            />
          </div>
        </div>
      )}
    </div>
  );
}

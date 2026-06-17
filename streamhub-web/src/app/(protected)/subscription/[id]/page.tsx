"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Loader2, Pause, Play, XCircle } from "lucide-react";

import {
  useChangeStatus,
  useDetail4,
} from "@/apis/query/subscription/subscription";
import {
  SubscriptionStatusRequestStatus,
  type SubscriptionDetail,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDate, formatDateTime, formatNumber } from "@/lib/format";
import GradeBadge from "@/components/subscription/GradeBadge";
import SubscriptionStatusBadge from "@/components/subscription/SubscriptionStatusBadge";
import TestModeBadge from "@/components/donation/TestModeBadge";
import { SUCCESS_CODE } from "@/types/api";

interface ReadonlyFieldProps {
  label: string;
  value: React.ReactNode;
}

function ReadonlyField({ label, value }: ReadonlyFieldProps) {
  return (
    <div>
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <div className="mt-1 text-sm text-slate-900">{value}</div>
    </div>
  );
}

export default function SubscriptionDetailPage() {
  const params = useParams<{ id: string }>();
  const subscriptionId = Number(params.id);

  const [message, setMessage] = useState<string | null>(null);

  const detailQuery = useDetail4(subscriptionId, {
    query: { enabled: Number.isFinite(subscriptionId) },
  });
  const changeStatusMutation = useChangeStatus();

  const detail: SubscriptionDetail | undefined =
    detailQuery.data?.resultObject;
  const status = detail?.status;

  const transition = (
    next: keyof typeof SubscriptionStatusRequestStatus,
    confirmText?: string,
  ) => {
    if (confirmText && !window.confirm(confirmText)) {
      return;
    }
    setMessage(null);
    changeStatusMutation.mutate(
      {
        id: subscriptionId,
        data: { status: SubscriptionStatusRequestStatus[next] },
      },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage("상태가 변경되었습니다.");
            detailQuery.refetch();
          } else {
            setMessage(response.resultMessage ?? "상태 변경에 실패했습니다.");
          }
        },
        onError: () => setMessage("상태 변경 중 오류가 발생했습니다."),
      },
    );
  };

  const isPending = changeStatusMutation.isPending;
  // Valid transitions: ACTIVE → PAUSED/CANCELED, PAUSED → ACTIVE/CANCELED,
  // CANCELED → (terminal). Buttons are disabled outside these.
  const canPause = status === "ACTIVE";
  const canResume = status === "PAUSED";
  const canCancel = status === "ACTIVE" || status === "PAUSED";

  return (
    <div className="mx-auto max-w-2xl">
      <Link
        href="/subscription"
        className="mb-4 inline-flex items-center gap-1 text-sm text-slate-600 transition hover:text-slate-900"
      >
        <ArrowLeft className="h-4 w-4" />
        목록으로
      </Link>

      <div className="mb-4 flex items-center gap-2">
        <h1 className="text-xl font-semibold text-slate-900">구독 상세</h1>
        <TestModeBadge />
      </div>

      {detailQuery.isPending ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : detailQuery.isError || !detail ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">
            구독 정보를 불러오지 못했습니다.
          </p>
        </div>
      ) : (
        <div className="rounded-md border border-slate-200 bg-white p-6">
          {message && (
            <p className="mb-4 rounded-md bg-blue-50 px-3 py-2 text-sm text-blue-700">
              {message}
            </p>
          )}

          {/* Header: member + status */}
          <div className="mb-6 flex items-start justify-between">
            <div>
              <p className="text-lg font-semibold text-slate-900">
                {detail.memberName ?? "-"}
              </p>
              <p className="mt-1 text-sm text-slate-500">
                {detail.planName ?? "-"}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <GradeBadge grade={detail.planGrade ?? undefined} />
              <SubscriptionStatusBadge status={detail.status ?? undefined} />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            <ReadonlyField
              label="플랜 가격"
              value={`₩${formatNumber(detail.planPrice)} / ${
                detail.planPeriodMonths ?? 1
              }개월`}
            />
            <ReadonlyField
              label="빌링키"
              value={
                <span className="font-mono">
                  {detail.billingKeyMasked ?? "-"}
                </span>
              }
            />
            <ReadonlyField
              label="현재 회차"
              value={detail.cycleNo != null ? `${detail.cycleNo}회` : "-"}
            />
            <ReadonlyField
              label="다음 청구일"
              value={formatDate(detail.nextBillingAt)}
            />
            <ReadonlyField label="시작일" value={formatDate(detail.startedAt)} />
            <ReadonlyField
              label="해지일"
              value={formatDate(detail.canceledAt)}
            />
          </div>

          <hr className="my-6 border-slate-200" />

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            <ReadonlyField
              label="등록일"
              value={formatDateTime(detail.createdAt)}
            />
            <ReadonlyField
              label="수정일"
              value={formatDateTime(detail.updatedAt)}
            />
          </div>

          {/* Status transition actions */}
          <hr className="my-6 border-slate-200" />
          <div>
            <p className="mb-3 text-xs font-medium text-slate-500">상태 전이</p>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => transition("PAUSED")}
                disabled={!canPause || isPending}
                className="flex items-center gap-1.5 rounded-md border border-amber-300 px-4 py-2 text-sm font-medium text-amber-700 transition hover:bg-amber-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Pause className="h-4 w-4" />
                일시정지
              </button>
              <button
                type="button"
                onClick={() => transition("ACTIVE")}
                disabled={!canResume || isPending}
                className="flex items-center gap-1.5 rounded-md border border-emerald-300 px-4 py-2 text-sm font-medium text-emerald-700 transition hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Play className="h-4 w-4" />
                재개
              </button>
              <button
                type="button"
                onClick={() =>
                  transition(
                    "CANCELED",
                    "이 구독을 해지하시겠습니까? 해지 후에는 되돌릴 수 없습니다.",
                  )
                }
                disabled={!canCancel || isPending}
                className="flex items-center gap-1.5 rounded-md border border-red-300 px-4 py-2 text-sm font-medium text-red-600 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <XCircle className="h-4 w-4" />
                해지
              </button>
              {isPending && (
                <Loader2 className="h-5 w-5 animate-spin self-center text-slate-400" />
              )}
            </div>
            {status === "CANCELED" && (
              <p className="mt-3 text-xs text-slate-500">
                해지된 구독은 더 이상 상태를 변경할 수 없습니다.
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

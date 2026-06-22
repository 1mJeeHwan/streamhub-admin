"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Loader2 } from "lucide-react";

import { useWorshipDetail, useWorshipStatus } from "@/apis/query/worship/worship";
import {
  type RegistrationFamilyDto,
  type WorshipRegistrationDetail,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDate, formatDateTime } from "@/lib/format";
import WorshipStatusBadge from "@/components/worship/WorshipStatusBadge";
import WorshipStatusStepper from "@/components/worship/WorshipStatusStepper";
import {
  BAPTISM_TYPE_LABEL,
  GENDER_LABEL,
  REGISTER_DEPT_LABEL,
  STATUS_LABEL,
  isDestructive,
  labelOf,
  type WorshipStatus,
} from "@/lib/worship-status";
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

function yesNo(value?: string): string {
  if (value == null || value === "") {
    return "-";
  }
  return value === "Y" || value === "1" ? "예" : "아니오";
}

export default function WorshipDetailPage() {
  const params = useParams<{ id: string }>();
  const worshipId = Number(params.id);

  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pendingTransition, setPendingTransition] =
    useState<WorshipStatus | null>(null);
  const [memo, setMemo] = useState("");

  const detailQuery = useWorshipDetail(worshipId, {
    query: { enabled: Number.isFinite(worshipId) },
  });
  const changeStatusMutation = useWorshipStatus();

  const detail: WorshipRegistrationDetail | undefined =
    detailQuery.data?.resultObject;
  const status = detail?.status as WorshipStatus | undefined;
  const families: RegistrationFamilyDto[] = detail?.families ?? [];

  const requestTransition = (next: WorshipStatus) => {
    setMessage(null);
    setError(null);
    if (isDestructive(next)) {
      // Destructive transitions (CANCELED) require explicit confirmation.
      setMemo("");
      setPendingTransition(next);
      return;
    }
    submitTransition(next, "");
  };

  const submitTransition = (next: WorshipStatus, memoValue: string) => {
    changeStatusMutation.mutate(
      {
        id: worshipId,
        data: { status: next, memo: memoValue.trim() || undefined },
      },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage(`${STATUS_LABEL[next]} 상태로 변경되었습니다.`);
            setPendingTransition(null);
            setMemo("");
            detailQuery.refetch();
          } else {
            setError(response.resultMessage ?? "상태 변경에 실패했습니다.");
          }
        },
        onError: () => setError("상태 변경 중 오류가 발생했습니다."),
      },
    );
  };

  return (
    <div className="mx-auto max-w-3xl">
      <Link
        href="/worship"
        className="mb-4 inline-flex items-center gap-1 text-sm text-slate-600 transition hover:text-slate-900"
      >
        <ArrowLeft className="h-4 w-4" />
        목록으로
      </Link>

      <div className="mb-4 flex items-center gap-3">
        <h1 className="text-xl font-semibold text-slate-900">신청 상세</h1>
        {detail?.regNo && (
          <span className="text-sm text-slate-500">{detail.regNo}</span>
        )}
        {status && <WorshipStatusBadge status={status} />}
      </div>

      {detailQuery.isPending ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : detailQuery.isError || !detail ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">
            신청 정보를 불러오지 못했습니다.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {message && (
            <p className="rounded-md bg-blue-50 px-3 py-2 text-sm text-blue-700">
              {message}
            </p>
          )}
          {error && (
            <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </p>
          )}

          {/* Status machine */}
          <section className="rounded-md border border-slate-200 bg-white p-6">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">
              신청 상태
            </h2>
            <WorshipStatusStepper
              status={(status ?? "RECEIVED") as WorshipStatus}
              pending={changeStatusMutation.isPending}
              onTransition={requestTransition}
            />
          </section>

          {/* Personal info */}
          <section className="rounded-md border border-slate-200 bg-white p-6">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">
              인적 사항
            </h2>
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
              <ReadonlyField label="접수번호" value={detail.regNo ?? "-"} />
              <ReadonlyField
                label="신청일시"
                value={formatDateTime(detail.createdAt)}
              />
              <ReadonlyField label="이름" value={detail.name ?? "-"} />
              <ReadonlyField
                label="성별"
                value={labelOf(GENDER_LABEL, detail.gender)}
              />
              <ReadonlyField
                label="생년월일"
                value={formatDate(detail.birthDate)}
              />
              <ReadonlyField
                label="등록부서"
                value={labelOf(REGISTER_DEPT_LABEL, detail.registerDept)}
              />
              <ReadonlyField label="연락처" value={detail.phone ?? "-"} />
              <ReadonlyField label="이메일" value={detail.email ?? "-"} />
              <ReadonlyField
                label="교회"
                value={
                  detail.churchId != null ? (
                    <Link
                      href={`/churches/${detail.churchId}`}
                      className="text-brand hover:underline"
                      title="교회 상세로 이동"
                    >
                      {detail.churchName ?? "-"}
                    </Link>
                  ) : (
                    (detail.churchName ?? "-")
                  )
                }
              />
              <ReadonlyField
                label="개인정보 동의"
                value={yesNo(detail.privacyAgreed)}
              />
            </div>
          </section>

          {/* Address */}
          <section className="rounded-md border border-slate-200 bg-white p-6">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">주소</h2>
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
              <ReadonlyField label="우편번호" value={detail.zipcode ?? "-"} />
              <div className="sm:col-span-2">
                <ReadonlyField label="기본주소" value={detail.addr1 ?? "-"} />
              </div>
              <div className="sm:col-span-2">
                <ReadonlyField label="상세주소" value={detail.addr2 ?? "-"} />
              </div>
            </div>
          </section>

          {/* Faith background */}
          <section className="rounded-md border border-slate-200 bg-white p-6">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">
              신앙 배경
            </h2>
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
              <ReadonlyField
                label="교회 경험"
                value={yesNo(detail.churchExperience)}
              />
              <ReadonlyField
                label="이전 교회"
                value={detail.prevChurch ?? "-"}
              />
              <ReadonlyField
                label="세례 구분"
                value={labelOf(BAPTISM_TYPE_LABEL, detail.baptismType)}
              />
              <ReadonlyField
                label="인도자"
                value={detail.leaderName ?? "-"}
              />
              <ReadonlyField
                label="인도자 연락처"
                value={detail.leaderPhone ?? "-"}
              />
              <div className="sm:col-span-2">
                <ReadonlyField label="메모" value={detail.memo ?? "-"} />
              </div>
            </div>
          </section>

          {/* Family */}
          <section className="rounded-md border border-slate-200 bg-white p-6">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">
              가족 사항
            </h2>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left text-xs font-medium text-slate-500">
                    <th className="py-2 pr-3">이름</th>
                    <th className="py-2 pr-3">관계</th>
                    <th className="py-2">생년월일</th>
                  </tr>
                </thead>
                <tbody>
                  {families.length === 0 ? (
                    <tr>
                      <td
                        colSpan={3}
                        className="py-4 text-center text-sm text-slate-400"
                      >
                        등록된 가족이 없습니다.
                      </td>
                    </tr>
                  ) : (
                    families.map((family, index) => (
                      <tr
                        key={index}
                        className="border-b border-slate-100 text-slate-900"
                      >
                        <td className="py-2.5 pr-3">{family.name ?? "-"}</td>
                        <td className="py-2.5 pr-3 text-slate-600">
                          {family.relation ?? "-"}
                        </td>
                        <td className="py-2.5">
                          {formatDate(family.birthDate)}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </div>
      )}

      {/* Destructive transition confirmation modal */}
      {pendingTransition && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-md rounded-md bg-white p-6 shadow-lg">
            <h3 className="text-base font-semibold text-slate-900">
              {STATUS_LABEL[pendingTransition]} 처리 확인
            </h3>
            <p className="mt-2 text-sm text-slate-600">
              이 신청을 <strong>{STATUS_LABEL[pendingTransition]}</strong>(으)로
              변경하면 되돌릴 수 없습니다. 계속하시겠습니까?
            </p>
            <div className="mt-4">
              <label
                htmlFor="memo"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                처리 메모 (선택)
              </label>
              <input
                id="memo"
                type="text"
                value={memo}
                onChange={(event) => setMemo(event.target.value)}
                placeholder="예: 신청자 요청 취소"
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
              />
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setPendingTransition(null);
                  setMemo("");
                }}
                disabled={changeStatusMutation.isPending}
                className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:opacity-60"
              >
                취소
              </button>
              <button
                type="button"
                onClick={() => submitTransition(pendingTransition, memo)}
                disabled={changeStatusMutation.isPending}
                className="flex items-center gap-1.5 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {changeStatusMutation.isPending && (
                  <Loader2 className="h-4 w-4 animate-spin" />
                )}
                {STATUS_LABEL[pendingTransition]} 처리
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

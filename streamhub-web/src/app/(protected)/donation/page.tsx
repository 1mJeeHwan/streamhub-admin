"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Loader2, Plus } from "lucide-react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { list5, useCreateOnce } from "@/apis/query/donation/donation";
import {
  DonationSearchRequestStatus,
  DonationSearchRequestType,
  type DonationListItem,
  type DonationSearchRequest,
  type OnceDonationRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import OnceDonationModal from "@/components/donation/OnceDonationModal";
import TestModeBadge from "@/components/donation/TestModeBadge";
import { SUCCESS_CODE } from "@/types/api";

const DonationGrid = dynamic(() => import("@/components/donation/DonationGrid"), {
  ssr: false,
  loading: () => (
    <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
      <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
    </div>
  ),
});

const PAGE_SIZE = 10;

type TypeFilter = "ALL" | DonationSearchRequestType;
type StatusFilter = "ALL" | DonationSearchRequestStatus;

const TYPE_OPTIONS: { value: TypeFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: DonationSearchRequestType.ONCE, label: "단건" },
  { value: DonationSearchRequestType.SUBSCRIPTION, label: "구독" },
];

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: DonationSearchRequestStatus.PAID, label: "결제완료" },
  { value: DonationSearchRequestStatus.CANCELED, label: "취소" },
  { value: DonationSearchRequestStatus.FAILED, label: "실패" },
];

/** Converts a date-input value (YYYY-MM-DD) to an ISO string, or undefined. */
function toIso(value: string, endOfDay: boolean): string | undefined {
  if (!value) {
    return undefined;
  }
  return endOfDay ? `${value}T23:59:59` : `${value}T00:00:00`;
}

export default function DonationPage() {
  // Committed search criteria.
  const [keyword, setKeyword] = useState("");
  const [type, setType] = useState<TypeFilter>("ALL");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [pageNumber, setPageNumber] = useState(1);

  // Draft inputs.
  const [keywordDraft, setKeywordDraft] = useState("");
  const [typeDraft, setTypeDraft] = useState<TypeFilter>("ALL");
  const [statusDraft, setStatusDraft] = useState<StatusFilter>("ALL");
  const [fromDraft, setFromDraft] = useState("");
  const [toDraft, setToDraft] = useState("");

  const [modalOpen, setModalOpen] = useState(false);
  const [modalMessage, setModalMessage] = useState<string | null>(null);

  const searchRequest = useMemo<DonationSearchRequest>(
    () => ({
      pageNumber: pageNumber - 1,
      pageSize: PAGE_SIZE,
      keyword: keyword.trim() || undefined,
      type: type === "ALL" ? undefined : type,
      status: status === "ALL" ? undefined : status,
      from: toIso(from, false),
      to: toIso(to, true),
    }),
    [pageNumber, keyword, type, status, from, to],
  );

  const listQuery = useQuery({
    queryKey: ["donation-list", searchRequest],
    queryFn: ({ signal }) => list5(searchRequest, signal),
    placeholderData: keepPreviousData,
  });

  const result = listQuery.data?.resultObject;
  const rows: DonationListItem[] = result?.contents ?? [];
  const totalCount = result?.totalCount ?? 0;
  const totalPage = result?.totalPage ?? 0;

  const createOnceMutation = useCreateOnce();

  const handleSearch = () => {
    setKeyword(keywordDraft);
    setType(typeDraft);
    setStatus(statusDraft);
    setFrom(fromDraft);
    setTo(toDraft);
    setPageNumber(1);
  };

  const goToPage = (next: number) => {
    if (next < 1 || (totalPage > 0 && next > totalPage)) {
      return;
    }
    setPageNumber(next);
  };

  const handleCreateOnce = (payload: OnceDonationRequest) => {
    setModalMessage(null);
    createOnceMutation.mutate(
      { data: payload },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setModalOpen(false);
            setPageNumber(1);
            listQuery.refetch();
          } else {
            setModalMessage(response.resultMessage ?? "등록에 실패했습니다.");
          }
        },
        onError: () => setModalMessage("등록 중 오류가 발생했습니다."),
      },
    );
  };

  return (
    <div>
      <div className="mb-4 flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-semibold text-slate-900">후원 내역</h1>
            <TestModeBadge />
          </div>
          <p className="mt-1 text-sm text-slate-500">
            단건 후원과 정기 구독 회차 결제 내역을 조회합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            setModalMessage(null);
            setModalOpen(true);
          }}
          className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          <Plus className="h-4 w-4" />
          단건 후원 등록
        </button>
      </div>

      {/* Search / filter bar */}
      <div className="mb-4 flex flex-wrap items-end gap-3 rounded-md border border-slate-200 bg-white p-4">
        <div className="flex flex-col">
          <label
            htmlFor="keyword"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            검색어
          </label>
          <input
            id="keyword"
            type="text"
            value={keywordDraft}
            onChange={(event) => setKeywordDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                handleSearch();
              }
            }}
            placeholder="회원명"
            className="w-48 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          />
        </div>

        <div className="flex flex-col">
          <label
            htmlFor="type"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            유형
          </label>
          <select
            id="type"
            value={typeDraft}
            onChange={(event) => setTypeDraft(event.target.value as TypeFilter)}
            className="w-28 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          >
            {TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col">
          <label
            htmlFor="status"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            상태
          </label>
          <select
            id="status"
            value={statusDraft}
            onChange={(event) =>
              setStatusDraft(event.target.value as StatusFilter)
            }
            className="w-28 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col">
          <label
            htmlFor="from"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            시작일
          </label>
          <input
            id="from"
            type="date"
            value={fromDraft}
            onChange={(event) => setFromDraft(event.target.value)}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          />
        </div>

        <div className="flex flex-col">
          <label
            htmlFor="to"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            종료일
          </label>
          <input
            id="to"
            type="date"
            value={toDraft}
            onChange={(event) => setToDraft(event.target.value)}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          />
        </div>

        <button
          type="button"
          onClick={handleSearch}
          className="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          검색
        </button>
      </div>

      {/* Summary */}
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <span className="text-sm text-slate-600">
          총 {totalCount.toLocaleString()}건
        </span>
      </div>

      {/* Results */}
      {listQuery.isLoading ? (
        <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : listQuery.isError ? (
        <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">목록을 불러오지 못했습니다.</p>
        </div>
      ) : (
        <DonationGrid rows={rows} />
      )}

      {/* Pagination */}
      {totalPage > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <button
            type="button"
            onClick={() => goToPage(pageNumber - 1)}
            disabled={pageNumber <= 1}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
          >
            이전
          </button>
          <span className="text-sm text-slate-600">
            {pageNumber} / {totalPage}
          </span>
          <button
            type="button"
            onClick={() => goToPage(pageNumber + 1)}
            disabled={pageNumber >= totalPage}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
          >
            다음
          </button>
        </div>
      )}

      {modalOpen && (
        <OnceDonationModal
          isPending={createOnceMutation.isPending}
          message={modalMessage}
          onSubmit={handleCreateOnce}
          onClose={() => setModalOpen(false)}
        />
      )}
    </div>
  );
}

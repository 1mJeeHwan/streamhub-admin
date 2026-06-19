"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Loader2 } from "lucide-react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { subscriptionList } from "@/apis/query/subscription/subscription";
import {
  SubscriptionSearchRequestStatus,
  type SubscriptionListItem,
  type SubscriptionSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import TestModeBadge from "@/components/donation/TestModeBadge";

const SubscriptionGrid = dynamic(
  () => import("@/components/subscription/SubscriptionGrid"),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
        <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
      </div>
    ),
  },
);

const PAGE_SIZE = 10;

type StatusFilter = "ALL" | SubscriptionSearchRequestStatus;

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: SubscriptionSearchRequestStatus.ACTIVE, label: "활성" },
  { value: SubscriptionSearchRequestStatus.PAUSED, label: "일시정지" },
  { value: SubscriptionSearchRequestStatus.CANCELED, label: "해지" },
];

export default function SubscriptionPage() {
  // Committed search criteria (applied on 검색 / page change).
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [pageNumber, setPageNumber] = useState(1);
  const [sort, setSort] = useState<{ by: string; dir: "asc" | "desc" } | null>(null);

  // Draft inputs (not yet applied to the query).
  const [keywordDraft, setKeywordDraft] = useState("");
  const [statusDraft, setStatusDraft] = useState<StatusFilter>("ALL");

  const searchRequest = useMemo<SubscriptionSearchRequest>(
    () => ({
      // UI pageNumber is 1-based; the backend expects 0-based.
      pageNumber: pageNumber - 1,
      pageSize: PAGE_SIZE,
      keyword: keyword.trim() || undefined,
      status: status === "ALL" ? undefined : status,
      // Server-side sort (cast until the Orval client is regenerated post-deploy; the backend
      // SubscriptionSearchRequest already accepts sortBy/sortDir and sends them in the POST body).
      ...(sort ? { sortBy: sort.by, sortDir: sort.dir } : {}),
    }) as SubscriptionSearchRequest,
    [pageNumber, keyword, status, sort],
  );

  // POST search modeled as a cached query so page/filter changes refetch and
  // prior results stay visible (no flicker).
  const listQuery = useQuery({
    queryKey: ["subscription-list", searchRequest],
    queryFn: ({ signal }) => subscriptionList(searchRequest, signal),
    placeholderData: keepPreviousData,
  });

  const result = listQuery.data?.resultObject;
  const rows: SubscriptionListItem[] = result?.contents ?? [];
  const totalCount = result?.totalCount ?? 0;
  const totalPage = result?.totalPage ?? 0;

  const handleSearch = () => {
    setKeyword(keywordDraft);
    setStatus(statusDraft);
    setPageNumber(1);
  };

  const goToPage = (next: number) => {
    if (next < 1 || (totalPage > 0 && next > totalPage)) {
      return;
    }
    setPageNumber(next);
  };

  // Sorting changes the whole result set, so jump back to the first page.
  const handleSortChange = (by: string | null, dir: "asc" | "desc" | null) => {
    setSort(by && dir ? { by, dir } : null);
    setPageNumber(1);
  };

  return (
    <div>
      <div className="mb-4">
        <div className="flex items-center gap-2">
          <h1 className="text-xl font-semibold text-slate-900">구독 현황</h1>
          <TestModeBadge />
        </div>
        <p className="mt-1 text-sm text-slate-500">
          멤버십 구독 현황을 조회하고 상세에서 상태를 전이합니다.
        </p>
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
            className="w-64 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          />
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
            className="w-32 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
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
        <SubscriptionGrid rows={rows} onSortChange={handleSortChange} />
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
    </div>
  );
}

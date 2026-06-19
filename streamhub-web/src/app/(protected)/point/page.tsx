"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Loader2, Plus } from "lucide-react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { pointList } from "@/apis/query/point/point";
import type {
  PointLedgerListItem,
  PointLedgerSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import GrantPointForm from "@/components/point/GrantPointForm";

const PointLedgerGrid = dynamic(
  () => import("@/components/point/PointLedgerGrid"),
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

export default function PointPage() {
  // Committed search criteria (applied on 검색 / page change).
  const [keyword, setKeyword] = useState("");
  const [pageNumber, setPageNumber] = useState(1);
  const [sort, setSort] = useState<{ by: string; dir: "asc" | "desc" } | null>(
    null,
  );

  // Draft input (not yet applied to the query).
  const [keywordDraft, setKeywordDraft] = useState("");

  // Manual-grant panel toggle + success banner.
  const [showGrant, setShowGrant] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const searchRequest = useMemo<PointLedgerSearchRequest>(
    () =>
      ({
        // UI pageNumber is 1-based (for display); the backend expects 0-based.
        pageNumber: pageNumber - 1,
        pageSize: PAGE_SIZE,
        keyword: keyword.trim() || undefined,
        // Server-side sort (cast until the Orval client is regenerated post-deploy; the backend
        // PointLedgerSearchRequest already accepts sortBy/sortDir and sends them in the POST body).
        ...(sort ? { sortBy: sort.by, sortDir: sort.dir } : {}),
      }) as PointLedgerSearchRequest,
    [pageNumber, keyword, sort],
  );

  // List is a POST search, but it's a read — model it as a cached query keyed by
  // the criteria so page/search changes refetch automatically and prior results
  // stay visible (no flicker).
  const listQuery = useQuery({
    queryKey: ["point-list", searchRequest],
    queryFn: ({ signal }) => pointList(searchRequest, signal),
    placeholderData: keepPreviousData,
  });

  const result = listQuery.data?.resultObject;
  const rows: PointLedgerListItem[] = result?.contents ?? [];
  const totalCount = result?.totalCount ?? 0;
  const totalPage = result?.totalPage ?? 0;

  const handleSearch = () => {
    setKeyword(keywordDraft);
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

  const handleGrantSuccess = () => {
    setMessage("포인트가 지급되었습니다.");
    setShowGrant(false);
    setPageNumber(1);
    listQuery.refetch();
  };

  return (
    <div>
      <div className="mb-4 flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">포인트 원장</h1>
          <p className="mt-1 text-sm text-slate-500">
            포인트 증감 내역을 조회하고 수동 지급 / 차감을 관리합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            setMessage(null);
            setShowGrant((prev) => !prev);
          }}
          className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          <Plus className="h-4 w-4" />
          포인트 지급
        </button>
      </div>

      {/* Success banner */}
      {message && (
        <p className="mb-4 rounded-md bg-blue-50 px-3 py-2 text-sm text-blue-700">
          {message}
        </p>
      )}

      {/* Manual grant panel */}
      {showGrant && (
        <GrantPointForm
          onSuccess={handleGrantSuccess}
          onCancel={() => setShowGrant(false)}
        />
      )}

      {/* Search bar */}
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
            placeholder="회원 이름 / 이메일 / 사유"
            className="w-72 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
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
        <PointLedgerGrid rows={rows} onSortChange={handleSortChange} />
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

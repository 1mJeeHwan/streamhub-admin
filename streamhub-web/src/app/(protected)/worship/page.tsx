"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Loader2 } from "lucide-react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { worshipList } from "@/apis/query/worship/worship";
import {
  WorshipSearchRequestStatus,
  type WorshipRegistrationListItem,
  type WorshipSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";

const WorshipGrid = dynamic(() => import("@/components/worship/WorshipGrid"), {
  ssr: false,
  loading: () => (
    <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
      <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
    </div>
  ),
});

const PAGE_SIZE = 10;

type StatusFilter = "ALL" | WorshipSearchRequestStatus;
type SearchField = "name" | "churchName";

const SEARCH_FIELD_OPTIONS: { value: SearchField; label: string }[] = [
  { value: "name", label: "이름" },
  { value: "churchName", label: "교회" },
];

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: WorshipSearchRequestStatus.RECEIVED, label: "접수" },
  { value: WorshipSearchRequestStatus.CONTACTED, label: "연락완료" },
  { value: WorshipSearchRequestStatus.COMPLETED, label: "등록완료" },
  { value: WorshipSearchRequestStatus.CANCELED, label: "취소" },
];

const FILTER_INPUT_CLASS =
  "rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

export default function WorshipPage() {
  // Committed search criteria (applied on 검색 / page change).
  const [searchField, setSearchField] = useState<SearchField>("name");
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [pageNumber, setPageNumber] = useState(1);
  const [sort, setSort] = useState<{ by: string; dir: "asc" | "desc" } | null>(
    null,
  );

  // Draft inputs (not yet applied to the query).
  const [searchFieldDraft, setSearchFieldDraft] = useState<SearchField>("name");
  const [keywordDraft, setKeywordDraft] = useState("");
  const [statusDraft, setStatusDraft] = useState<StatusFilter>("ALL");
  const [fromDateDraft, setFromDateDraft] = useState("");
  const [toDateDraft, setToDateDraft] = useState("");

  const searchRequest = useMemo<WorshipSearchRequest>(
    () => ({
      // UI pageNumber is 1-based (for display); the backend expects 0-based.
      pageNumber: pageNumber - 1,
      pageSize: PAGE_SIZE,
      searchField: keyword.trim() ? searchField : undefined,
      keyword: keyword.trim() || undefined,
      status: status === "ALL" ? undefined : status,
      fromDate: fromDate || undefined,
      toDate: toDate || undefined,
      // Server-side sort (cast until the Orval client is regenerated post-deploy; the backend
      // WorshipSearchRequest already accepts sortBy/sortDir and sends them in the POST body).
      ...(sort ? { sortBy: sort.by, sortDir: sort.dir } : {}),
    }) as WorshipSearchRequest,
    [pageNumber, searchField, keyword, status, fromDate, toDate, sort],
  );

  // List is a POST search, but it's a read — model it as a cached query keyed by
  // the criteria so page/filter changes refetch automatically and prior results
  // stay visible (no flicker).
  const listQuery = useQuery({
    queryKey: ["worship-list", searchRequest],
    queryFn: ({ signal }) => worshipList(searchRequest, signal),
    placeholderData: keepPreviousData,
  });

  const result = listQuery.data?.resultObject;
  const rows: WorshipRegistrationListItem[] = result?.contents ?? [];
  const totalCount = result?.totalCount ?? 0;
  const totalPage = result?.totalPage ?? 0;

  const handleSearch = () => {
    setSearchField(searchFieldDraft);
    setKeyword(keywordDraft);
    setStatus(statusDraft);
    setFromDate(fromDateDraft);
    setToDate(toDateDraft);
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
        <h1 className="text-xl font-semibold text-slate-900">
          예배 / 새가족 신청관리
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          새가족 등록 신청을 조회하고 상세에서 접수 / 연락 / 등록 상태를
          관리합니다.
        </p>
      </div>

      {/* Search / filter bar */}
      <div className="mb-4 flex flex-wrap items-end gap-3 rounded-md border border-slate-200 bg-white p-4">
        <div className="flex flex-col">
          <label
            htmlFor="searchField"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            검색 항목
          </label>
          <select
            id="searchField"
            value={searchFieldDraft}
            onChange={(event) =>
              setSearchFieldDraft(event.target.value as SearchField)
            }
            className={`w-28 ${FILTER_INPUT_CLASS}`}
          >
            {SEARCH_FIELD_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

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
            placeholder="이름 / 교회명"
            className={`w-56 ${FILTER_INPUT_CLASS}`}
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
            className={`w-28 ${FILTER_INPUT_CLASS}`}
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
            htmlFor="fromDate"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            신청일(시작)
          </label>
          <input
            id="fromDate"
            type="date"
            value={fromDateDraft}
            onChange={(event) => setFromDateDraft(event.target.value)}
            className={FILTER_INPUT_CLASS}
          />
        </div>

        <div className="flex flex-col">
          <label
            htmlFor="toDate"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            신청일(종료)
          </label>
          <input
            id="toDate"
            type="date"
            value={toDateDraft}
            onChange={(event) => setToDateDraft(event.target.value)}
            className={FILTER_INPUT_CLASS}
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
        <WorshipGrid rows={rows} onSortChange={handleSortChange} />
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

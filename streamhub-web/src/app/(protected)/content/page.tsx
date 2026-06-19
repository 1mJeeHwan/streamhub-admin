"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { Loader2, Plus } from "lucide-react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { contentList } from "@/apis/query/content/content";
import {
  ContentSearchRequestStatus,
  ContentSearchRequestType,
  type ContentListItem,
  type ContentSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";

const ContentGrid = dynamic(() => import("@/components/content/ContentGrid"), {
  ssr: false,
  loading: () => (
    <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
      <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
    </div>
  ),
});

const PAGE_SIZE = 10;

type TypeFilter = "ALL" | ContentSearchRequestType;
type StatusFilter = "ALL" | ContentSearchRequestStatus;

const TYPE_OPTIONS: { value: TypeFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: ContentSearchRequestType.VIDEO, label: "영상" },
  { value: ContentSearchRequestType.SOUND, label: "음원" },
];

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: ContentSearchRequestStatus.DRAFT, label: "임시" },
  { value: ContentSearchRequestStatus.PUBLISHED, label: "게시" },
];

export default function ContentPage() {
  // Committed search criteria (applied on 검색 / page change).
  const [keyword, setKeyword] = useState("");
  const [type, setType] = useState<TypeFilter>("ALL");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [pageNumber, setPageNumber] = useState(1);
  const [sort, setSort] = useState<{ by: string; dir: "asc" | "desc" } | null>(null);

  // Draft inputs (not yet applied to the query).
  const [keywordDraft, setKeywordDraft] = useState("");
  const [typeDraft, setTypeDraft] = useState<TypeFilter>("ALL");
  const [statusDraft, setStatusDraft] = useState<StatusFilter>("ALL");

  const searchRequest = useMemo<ContentSearchRequest>(
    () => ({
      // UI pageNumber is 1-based (for display); the backend expects 0-based.
      pageNumber: pageNumber - 1,
      pageSize: PAGE_SIZE,
      keyword: keyword.trim() || undefined,
      type: type === "ALL" ? undefined : type,
      status: status === "ALL" ? undefined : status,
      // Server-side sort (cast until the Orval client is regenerated post-deploy; the backend
      // ContentSearchRequest already accepts sortBy/sortDir and sends them in the POST body).
      ...(sort ? { sortBy: sort.by, sortDir: sort.dir } : {}),
    }) as ContentSearchRequest,
    [pageNumber, keyword, type, status, sort],
  );

  // List is a POST search, but it's a read — model it as a cached query keyed by the criteria
  // so page/filter changes refetch automatically and prior results stay visible (no flicker).
  const listQuery = useQuery({
    queryKey: ["content-list", searchRequest],
    queryFn: ({ signal }) => contentList(searchRequest, signal),
    placeholderData: keepPreviousData,
  });

  const result = listQuery.data?.resultObject;
  const rows: ContentListItem[] = result?.contents ?? [];
  const totalCount = result?.totalCount ?? 0;
  const totalPage = result?.totalPage ?? 0;

  const handleSearch = () => {
    setKeyword(keywordDraft);
    setType(typeDraft);
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
      <div className="mb-4 flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">콘텐츠관리</h1>
          <p className="mt-1 text-sm text-slate-500">
            콘텐츠 목록을 조회하고 등록 / 수정 / 삭제를 관리합니다.
          </p>
        </div>
        <Link
          href="/content/add"
          className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          <Plus className="h-4 w-4" />
          콘텐츠 등록
        </Link>
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
            placeholder="제목 / 해시태그"
            className="w-64 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
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
            className="w-32 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
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
        <ContentGrid rows={rows} onSortChange={handleSortChange} />
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

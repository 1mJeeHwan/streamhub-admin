"use client";

import { useCallback, useMemo, useRef, useState } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { Loader2, Plus, Save } from "lucide-react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { goodsList, useGoodsBulk, useGoodsCategories } from "@/apis/query/goods/goods";
import {
  GoodsSearchRequestStatus,
  type GoodsListItem,
  type GoodsSearchRequest,
  type Row,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { SUCCESS_CODE } from "@/types/api";

const GoodsGrid = dynamic(() => import("@/components/goods/GoodsGrid"), {
  ssr: false,
  loading: () => (
    <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
      <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
    </div>
  ),
});

const PAGE_SIZE = 10;

type StatusFilter = "ALL" | GoodsSearchRequestStatus;
type SoldOutFilter = "ALL" | "Y" | "N";

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: GoodsSearchRequestStatus.SELLING, label: "판매중" },
  { value: GoodsSearchRequestStatus.PAUSED, label: "판매중지" },
];

const SOLD_OUT_OPTIONS: { value: SoldOutFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "N", label: "정상" },
  { value: "Y", label: "품절" },
];

export default function GoodsPage() {
  // Committed search criteria (applied on 검색 / page change).
  const [keyword, setKeyword] = useState("");
  const [categoryId, setCategoryId] = useState<string>("ALL");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [soldOut, setSoldOut] = useState<SoldOutFilter>("ALL");
  const [pageNumber, setPageNumber] = useState(1);
  const [sort, setSort] = useState<{ by: string; dir: "asc" | "desc" } | null>(
    null,
  );

  // Draft inputs (not yet applied to the query).
  const [keywordDraft, setKeywordDraft] = useState("");
  const [categoryDraft, setCategoryDraft] = useState<string>("ALL");
  const [statusDraft, setStatusDraft] = useState<StatusFilter>("ALL");
  const [soldOutDraft, setSoldOutDraft] = useState<SoldOutFilter>("ALL");

  const [message, setMessage] = useState<string | null>(null);

  // Tracks ids of rows edited inline in the grid (the bulk-save working set).
  const dirtyIdsRef = useRef<Set<number>>(new Set());
  const [dirtyCount, setDirtyCount] = useState(0);

  const categoriesQuery = useGoodsCategories();
  const categories = categoriesQuery.data?.resultObject ?? [];

  const searchRequest = useMemo<GoodsSearchRequest>(
    () => ({
      // UI pageNumber is 1-based (for display); the backend expects 0-based.
      pageNumber: pageNumber - 1,
      pageSize: PAGE_SIZE,
      keyword: keyword.trim() || undefined,
      categoryId: categoryId === "ALL" ? undefined : Number(categoryId),
      status: status === "ALL" ? undefined : status,
      soldOut: soldOut === "ALL" ? undefined : soldOut,
      // Server-side sort (cast until the Orval client is regenerated post-deploy; the backend
      // GoodsSearchRequest already accepts sortBy/sortDir and sends them in the POST body).
      ...(sort ? { sortBy: sort.by, sortDir: sort.dir } : {}),
    }) as GoodsSearchRequest,
    [pageNumber, keyword, categoryId, status, soldOut, sort],
  );

  // List is a POST search, but it's a read — model it as a cached query keyed by
  // the criteria so page/filter changes refetch and prior results stay visible.
  const listQuery = useQuery({
    queryKey: ["goods-list", searchRequest],
    queryFn: ({ signal }) => goodsList(searchRequest, signal),
    placeholderData: keepPreviousData,
  });

  const bulkMutation = useGoodsBulk();

  const result = listQuery.data?.resultObject;
  const rows: GoodsListItem[] = result?.contents ?? [];
  const totalCount = result?.totalCount ?? 0;
  const totalPage = result?.totalPage ?? 0;

  const resetDirty = useCallback(() => {
    dirtyIdsRef.current = new Set();
    setDirtyCount(0);
  }, []);

  const handleRowEdited = useCallback((id: number) => {
    dirtyIdsRef.current.add(id);
    setDirtyCount(dirtyIdsRef.current.size);
  }, []);

  const handleSearch = () => {
    setKeyword(keywordDraft);
    setCategoryId(categoryDraft);
    setStatus(statusDraft);
    setSoldOut(soldOutDraft);
    setPageNumber(1);
    setMessage(null);
    resetDirty();
  };

  const goToPage = (next: number) => {
    if (next < 1 || (totalPage > 0 && next > totalPage)) {
      return;
    }
    setPageNumber(next);
    resetDirty();
  };

  // Sorting changes the whole result set, so jump back to the first page and drop any
  // pending inline edits (they would refer to rows no longer on the current page).
  const handleSortChange = (
    by: string | null,
    dir: "asc" | "desc" | null,
  ) => {
    setSort(by && dir ? { by, dir } : null);
    setPageNumber(1);
    setMessage(null);
    resetDirty();
  };

  const handleBulkSave = () => {
    setMessage(null);
    const dirtyIds = dirtyIdsRef.current;
    if (dirtyIds.size === 0) {
      return;
    }

    // Build rows from the current grid data, sending only edited rows.
    const bulkRows: Row[] = rows
      .filter((item) => item.id != null && dirtyIds.has(item.id))
      .map((item) => ({
        id: item.id as number,
        stock: item.stock,
        notiQty: item.notiQty,
        price: item.price,
        soldOut: item.soldOut,
        useYn: item.useYn,
      }));

    bulkMutation.mutate(
      { data: { rows: bulkRows } },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage(`${response.resultObject ?? bulkRows.length}건 수정됨`);
            resetDirty();
            listQuery.refetch();
          } else {
            setMessage(response.resultMessage ?? "일괄수정에 실패했습니다.");
          }
        },
        onError: () => setMessage("일괄수정 중 오류가 발생했습니다."),
      },
    );
  };

  return (
    <div>
      <div className="mb-4 flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">굿즈관리</h1>
          <p className="mt-1 text-sm text-slate-500">
            굿즈 상품을 조회하고 재고 / 가격을 인라인으로 일괄 수정합니다.
          </p>
        </div>
        <Link
          href="/goods/add"
          className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          <Plus className="h-4 w-4" />
          굿즈 등록
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
            placeholder="상품명 / 상품코드"
            className="w-64 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          />
        </div>

        <div className="flex flex-col">
          <label
            htmlFor="category"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            분류
          </label>
          <select
            id="category"
            value={categoryDraft}
            onChange={(event) => setCategoryDraft(event.target.value)}
            className="w-40 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          >
            <option value="ALL">전체</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {" ".repeat(((category.depth ?? 1) - 1) * 2)}
                {category.name ?? `분류 ${category.id}`}
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

        <div className="flex flex-col">
          <label
            htmlFor="soldOut"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            품절
          </label>
          <select
            id="soldOut"
            value={soldOutDraft}
            onChange={(event) =>
              setSoldOutDraft(event.target.value as SoldOutFilter)
            }
            className="w-32 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          >
            {SOLD_OUT_OPTIONS.map((option) => (
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

      {/* Summary + bulk action */}
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-600">
            총 {totalCount.toLocaleString()}건
          </span>
          {message && (
            <span className="rounded-md bg-blue-50 px-2.5 py-1 text-xs text-blue-700">
              {message}
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={handleBulkSave}
          disabled={dirtyCount === 0 || bulkMutation.isPending}
          className="flex items-center gap-1.5 rounded-md border border-brand px-4 py-2 text-sm font-medium text-brand transition hover:bg-brand/5 disabled:cursor-not-allowed disabled:border-slate-300 disabled:text-slate-400"
        >
          {bulkMutation.isPending ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Save className="h-4 w-4" />
          )}
          일괄수정{dirtyCount > 0 ? ` (${dirtyCount})` : ""}
        </button>
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
        <GoodsGrid
          rows={rows}
          onRowEdited={handleRowEdited}
          onSortChange={handleSortChange}
        />
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

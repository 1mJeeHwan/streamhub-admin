"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Loader2 } from "lucide-react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { list2 } from "@/apis/query/order/order";
import {
  OrderSearchRequestStatus,
  type OrderListItem,
  type OrderSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";

const OrderGrid = dynamic(() => import("@/components/order/OrderGrid"), {
  ssr: false,
  loading: () => (
    <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
      <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
    </div>
  ),
});

const PAGE_SIZE = 10;

type StatusFilter = "ALL" | OrderSearchRequestStatus;
type PayMethodFilter = "ALL" | "BANK" | "CARD";
type SearchField = "orderNo" | "orderedName" | "receiverName" | "trackingNo";

const SEARCH_FIELD_OPTIONS: { value: SearchField; label: string }[] = [
  { value: "orderNo", label: "주문번호" },
  { value: "orderedName", label: "주문자" },
  { value: "receiverName", label: "받는분" },
  { value: "trackingNo", label: "운송장" },
];

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: OrderSearchRequestStatus.PLACED, label: "주문" },
  { value: OrderSearchRequestStatus.PAID, label: "입금" },
  { value: OrderSearchRequestStatus.READY, label: "배송준비" },
  { value: OrderSearchRequestStatus.SHIPPING, label: "배송중" },
  { value: OrderSearchRequestStatus.DONE, label: "완료" },
  { value: OrderSearchRequestStatus.CANCEL, label: "취소" },
  { value: OrderSearchRequestStatus.RETURN, label: "반품" },
];

const PAY_METHOD_OPTIONS: { value: PayMethodFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "BANK", label: "무통장" },
  { value: "CARD", label: "카드" },
];

const FILTER_INPUT_CLASS =
  "rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

export default function OrderPage() {
  // Committed search criteria (applied on 검색 / page change).
  const [searchField, setSearchField] = useState<SearchField>("orderNo");
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [payMethod, setPayMethod] = useState<PayMethodFilter>("ALL");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [pageNumber, setPageNumber] = useState(1);

  // Draft inputs (not yet applied to the query).
  const [searchFieldDraft, setSearchFieldDraft] = useState<SearchField>("orderNo");
  const [keywordDraft, setKeywordDraft] = useState("");
  const [statusDraft, setStatusDraft] = useState<StatusFilter>("ALL");
  const [payMethodDraft, setPayMethodDraft] = useState<PayMethodFilter>("ALL");
  const [fromDateDraft, setFromDateDraft] = useState("");
  const [toDateDraft, setToDateDraft] = useState("");

  const searchRequest = useMemo<OrderSearchRequest>(
    () => ({
      // UI pageNumber is 1-based (for display); the backend expects 0-based.
      pageNumber: pageNumber - 1,
      pageSize: PAGE_SIZE,
      searchField: keyword.trim() ? searchField : undefined,
      keyword: keyword.trim() || undefined,
      status: status === "ALL" ? undefined : status,
      payMethod: payMethod === "ALL" ? undefined : payMethod,
      fromDate: fromDate || undefined,
      toDate: toDate || undefined,
    }),
    [pageNumber, searchField, keyword, status, payMethod, fromDate, toDate],
  );

  // List is a POST search, but it's a read — model it as a cached query keyed by
  // the criteria so page/filter changes refetch automatically and prior results
  // stay visible (no flicker).
  const listQuery = useQuery({
    queryKey: ["order-list", searchRequest],
    queryFn: ({ signal }) => list2(searchRequest, signal),
    placeholderData: keepPreviousData,
  });

  const result = listQuery.data?.resultObject;
  const rows: OrderListItem[] = result?.contents ?? [];
  const totalCount = result?.totalCount ?? 0;
  const totalPage = result?.totalPage ?? 0;

  const handleSearch = () => {
    setSearchField(searchFieldDraft);
    setKeyword(keywordDraft);
    setStatus(statusDraft);
    setPayMethod(payMethodDraft);
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

  return (
    <div>
      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">주문관리</h1>
        <p className="mt-1 text-sm text-slate-500">
          주문 목록을 조회하고 상세에서 상태 변경 / 운송장 입력을 관리합니다.
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
            className={`w-32 ${FILTER_INPUT_CLASS}`}
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
            placeholder="검색어 입력"
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
            htmlFor="payMethod"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            결제수단
          </label>
          <select
            id="payMethod"
            value={payMethodDraft}
            onChange={(event) =>
              setPayMethodDraft(event.target.value as PayMethodFilter)
            }
            className={`w-28 ${FILTER_INPUT_CLASS}`}
          >
            {PAY_METHOD_OPTIONS.map((option) => (
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
            주문일(시작)
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
            주문일(종료)
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
        <OrderGrid rows={rows} />
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

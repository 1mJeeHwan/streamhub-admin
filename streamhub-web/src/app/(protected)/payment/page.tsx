"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Loader2 } from "lucide-react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { paymentList, usePaymentRefundCreate } from "@/apis/query/payment/payment";
import {
  PaymentSearchRequestKind,
  type PaymentListItem,
  type PaymentSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { SUCCESS_CODE } from "@/types/api";

const PaymentGrid = dynamic(() => import("@/components/payment/PaymentGrid"), {
  ssr: false,
  loading: () => (
    <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
      <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
    </div>
  ),
});

const PAGE_SIZE = 10;

type KindFilter = "ALL" | PaymentSearchRequestKind;
type MethodFilter = "ALL" | "BANK" | "CARD";
type SearchField = "orderNo" | "memberName" | "txnId";

const SEARCH_FIELD_OPTIONS: { value: SearchField; label: string }[] = [
  { value: "orderNo", label: "주문번호" },
  { value: "memberName", label: "회원명" },
  { value: "txnId", label: "거래번호" },
];

const KIND_OPTIONS: { value: KindFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: PaymentSearchRequestKind.PAY, label: "입금" },
  { value: PaymentSearchRequestKind.REFUND, label: "환불" },
];

const METHOD_OPTIONS: { value: MethodFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "BANK", label: "무통장" },
  { value: "CARD", label: "카드" },
];

const FILTER_INPUT_CLASS =
  "rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

export default function PaymentPage() {
  // Committed search criteria (applied on 검색 / page change).
  const [searchField, setSearchField] = useState<SearchField>("orderNo");
  const [keyword, setKeyword] = useState("");
  const [kind, setKind] = useState<KindFilter>("ALL");
  const [method, setMethod] = useState<MethodFilter>("ALL");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [pageNumber, setPageNumber] = useState(1);

  // Draft inputs (not yet applied to the query).
  const [searchFieldDraft, setSearchFieldDraft] = useState<SearchField>("orderNo");
  const [keywordDraft, setKeywordDraft] = useState("");
  const [kindDraft, setKindDraft] = useState<KindFilter>("ALL");
  const [methodDraft, setMethodDraft] = useState<MethodFilter>("ALL");
  const [fromDateDraft, setFromDateDraft] = useState("");
  const [toDateDraft, setToDateDraft] = useState("");

  // Admin refund (destructive) — confirm dialog state.
  const [refundTarget, setRefundTarget] = useState<PaymentListItem | null>(null);
  const [refundReason, setRefundReason] = useState("");
  const [refundError, setRefundError] = useState<string | null>(null);
  const [refundMessage, setRefundMessage] = useState<string | null>(null);
  const refundMutation = usePaymentRefundCreate();

  const searchRequest = useMemo<PaymentSearchRequest>(
    () => ({
      // UI pageNumber is 1-based (for display); the backend expects 0-based.
      pageNumber: pageNumber - 1,
      pageSize: PAGE_SIZE,
      searchField: keyword.trim() ? searchField : undefined,
      keyword: keyword.trim() || undefined,
      kind: kind === "ALL" ? undefined : kind,
      method: method === "ALL" ? undefined : method,
      fromDate: fromDate || undefined,
      toDate: toDate || undefined,
    }),
    [pageNumber, searchField, keyword, kind, method, fromDate, toDate],
  );

  // List is a POST search but a read — model it as a cached query keyed by the
  // criteria so page/filter changes refetch automatically without flicker.
  const listQuery = useQuery({
    queryKey: ["payment-list", searchRequest],
    queryFn: ({ signal }) => paymentList(searchRequest, signal),
    placeholderData: keepPreviousData,
  });

  const result = listQuery.data?.resultObject;
  const rows: PaymentListItem[] = result?.contents ?? [];
  const totalCount = result?.totalCount ?? 0;
  const totalPage = result?.totalPage ?? 0;

  const handleSearch = () => {
    setSearchField(searchFieldDraft);
    setKeyword(keywordDraft);
    setKind(kindDraft);
    setMethod(methodDraft);
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

  const openRefund = (row: PaymentListItem) => {
    setRefundError(null);
    setRefundReason("");
    setRefundTarget(row);
  };

  const closeRefund = () => {
    if (refundMutation.isPending) {
      return;
    }
    setRefundTarget(null);
    setRefundReason("");
    setRefundError(null);
  };

  const confirmRefund = () => {
    if (refundTarget?.orderId == null) {
      return;
    }
    setRefundError(null);
    setRefundMessage(null);
    refundMutation.mutate(
      {
        // toStatus defaults to CANCEL on the backend; reason is optional.
        data: {
          orderId: refundTarget.orderId,
          reason: refundReason.trim() || undefined,
        },
      },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setRefundMessage(
              `${refundTarget.orderNo ?? "주문"} 환불이 처리되었습니다.`,
            );
            setRefundTarget(null);
            setRefundReason("");
            // Refund issues a REFUND receipt — refetch so the new row appears.
            listQuery.refetch();
          } else {
            setRefundError(response.resultMessage ?? "환불 처리에 실패했습니다.");
          }
        },
        onError: () => setRefundError("환불 처리 중 오류가 발생했습니다."),
      },
    );
  };

  return (
    <div>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">결제 내역</h1>
          <p className="mt-1 text-sm text-slate-500">
            주문 결제·환불 영수증을 조회합니다. 행을 클릭하면 해당 주문 상세로
            이동합니다.
          </p>
        </div>
        <span className="shrink-0 rounded-full bg-amber-100 px-3 py-1 text-xs font-medium text-amber-700">
          데모 데이터 · 테스트 모드 (실 PG 미연동)
        </span>
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
          <label htmlFor="kind" className="mb-1 text-xs font-medium text-slate-600">
            구분
          </label>
          <select
            id="kind"
            value={kindDraft}
            onChange={(event) => setKindDraft(event.target.value as KindFilter)}
            className={`w-28 ${FILTER_INPUT_CLASS}`}
          >
            {KIND_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col">
          <label
            htmlFor="method"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            결제수단
          </label>
          <select
            id="method"
            value={methodDraft}
            onChange={(event) =>
              setMethodDraft(event.target.value as MethodFilter)
            }
            className={`w-28 ${FILTER_INPUT_CLASS}`}
          >
            {METHOD_OPTIONS.map((option) => (
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
            결제일(시작)
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
            결제일(종료)
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

      {refundMessage && (
        <p className="mb-3 rounded-md bg-blue-50 px-3 py-2 text-sm text-blue-700">
          {refundMessage}
        </p>
      )}

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
        <PaymentGrid rows={rows} onRefund={openRefund} />
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

      {/* Destructive refund confirmation modal */}
      {refundTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-md rounded-md bg-white p-6 shadow-lg">
            <h3 className="text-base font-semibold text-slate-900">환불 처리 확인</h3>
            <p className="mt-2 text-sm text-slate-600">
              주문 <strong>{refundTarget.orderNo ?? "-"}</strong>
              {refundTarget.amount != null && (
                <>
                  {" "}(<strong>{Number(refundTarget.amount).toLocaleString()}원</strong>)
                </>
              )}
              을(를) 환불하면 주문이 취소(CANCEL)로 전이되고 재고 복원과 함께
              환불(REFUND) 영수증이 발급됩니다. 계속하시겠습니까?
            </p>

            <p className="mt-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
              Mock PG에서는 실제 환불 호출 없이 내부 원장만 되돌립니다.
            </p>

            <div className="mt-4">
              <label
                htmlFor="refundReason"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                환불 사유 (선택)
              </label>
              <input
                id="refundReason"
                type="text"
                value={refundReason}
                onChange={(event) => setRefundReason(event.target.value)}
                placeholder="예: 고객 요청 환불"
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
              />
            </div>

            {refundError && (
              <p className="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                {refundError}
              </p>
            )}

            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                onClick={closeRefund}
                disabled={refundMutation.isPending}
                className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:opacity-60"
              >
                취소
              </button>
              <button
                type="button"
                onClick={confirmRefund}
                disabled={refundMutation.isPending}
                className="flex items-center gap-1.5 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {refundMutation.isPending && (
                  <Loader2 className="h-4 w-4 animate-spin" />
                )}
                환불 처리
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Loader2, Send } from "lucide-react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { smsList } from "@/apis/query/sms/sms";
import {
  SmsSearchRequestKind,
  type SmsListItem,
  type SmsSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import SmsSendModal from "@/components/sms/SmsSendModal";

const SmsGrid = dynamic(() => import("@/components/sms/SmsGrid"), {
  ssr: false,
  loading: () => (
    <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
      <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
    </div>
  ),
});

const PAGE_SIZE = 10;

type KindFilter = "ALL" | SmsSearchRequestKind;

const KIND_OPTIONS: { value: KindFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: SmsSearchRequestKind.CUSTOM, label: "커스텀" },
  { value: SmsSearchRequestKind.ORDER_PAID, label: "주문결제" },
  { value: SmsSearchRequestKind.ORDER_SHIPPING, label: "배송" },
  { value: SmsSearchRequestKind.DONATION_ONCE, label: "후원" },
];

export default function SmsPage() {
  // Committed search criteria (applied on 검색 / page change).
  const [keyword, setKeyword] = useState("");
  const [kind, setKind] = useState<KindFilter>("ALL");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [pageNumber, setPageNumber] = useState(1);
  const [sort, setSort] = useState<{ by: string; dir: "asc" | "desc" } | null>(null);

  // Draft inputs (not yet applied to the query).
  const [keywordDraft, setKeywordDraft] = useState("");
  const [kindDraft, setKindDraft] = useState<KindFilter>("ALL");
  const [fromDraft, setFromDraft] = useState("");
  const [toDraft, setToDraft] = useState("");

  const [sendOpen, setSendOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const searchRequest = useMemo<SmsSearchRequest>(
    () => ({
      // UI pageNumber is 1-based (for display); the backend expects 0-based.
      pageNumber: pageNumber - 1,
      pageSize: PAGE_SIZE,
      keyword: keyword.trim() || undefined,
      kind: kind === "ALL" ? undefined : kind,
      from: from || undefined,
      to: to || undefined,
      // Server-side sort (cast until the Orval client is regenerated post-deploy; the backend
      // SmsSearchRequest already accepts sortBy/sortDir and sends them in the POST body).
      ...(sort ? { sortBy: sort.by, sortDir: sort.dir } : {}),
    }) as SmsSearchRequest,
    [pageNumber, keyword, kind, from, to, sort],
  );

  // List is a POST search, but it's a read — model it as a cached query keyed by
  // the criteria so page/filter changes refetch and prior results stay visible.
  const listQuery = useQuery({
    queryKey: ["sms-list", searchRequest],
    queryFn: ({ signal }) => smsList(searchRequest, signal),
    placeholderData: keepPreviousData,
  });

  const result = listQuery.data?.resultObject;
  const rows: SmsListItem[] = result?.contents ?? [];
  const totalCount = result?.totalCount ?? 0;
  const totalPage = result?.totalPage ?? 0;

  const handleSearch = () => {
    setKeyword(keywordDraft);
    setKind(kindDraft);
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

  // Sorting changes the whole result set, so jump back to the first page.
  const handleSortChange = (by: string | null, dir: "asc" | "desc" | null) => {
    setSort(by && dir ? { by, dir } : null);
    setPageNumber(1);
  };

  const handleSent = () => {
    setMessage("발송 내역에 기록되었습니다. (데모/테스트 발송)");
    setPageNumber(1);
    listQuery.refetch();
  };

  return (
    <div>
      <div className="mb-4 flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">문자 발송 관리</h1>
          <p className="mt-1 text-sm text-slate-500">
            발송 내역을 조회하고 커스텀 문자를 직접 발송합니다. 모든 발송은
            실제로 전송되지 않고 내역만 기록됩니다(데모/테스트).
          </p>
        </div>
        <button
          type="button"
          onClick={() => setSendOpen(true)}
          className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          <Send className="h-4 w-4" />
          커스텀 발송
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
            placeholder="수신번호 / 내용 / 회원명"
            className="w-64 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          />
        </div>

        <div className="flex flex-col">
          <label
            htmlFor="kind"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            종류
          </label>
          <select
            id="kind"
            value={kindDraft}
            onChange={(event) => setKindDraft(event.target.value as KindFilter)}
            className="w-36 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
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
            className="w-40 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          />
        </div>

        <div className="flex flex-col">
          <label htmlFor="to" className="mb-1 text-xs font-medium text-slate-600">
            종료일
          </label>
          <input
            id="to"
            type="date"
            value={toDraft}
            onChange={(event) => setToDraft(event.target.value)}
            className="w-40 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
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
        {message && (
          <span className="rounded-md bg-blue-50 px-2.5 py-1 text-xs text-blue-700">
            {message}
          </span>
        )}
      </div>

      {/* Results */}
      {listQuery.isLoading ? (
        <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : listQuery.isError ? (
        <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">발송 내역을 불러오지 못했습니다.</p>
        </div>
      ) : (
        <SmsGrid rows={rows} onSortChange={handleSortChange} />
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

      {sendOpen && (
        <SmsSendModal onClose={() => setSendOpen(false)} onSent={handleSent} />
      )}
    </div>
  );
}

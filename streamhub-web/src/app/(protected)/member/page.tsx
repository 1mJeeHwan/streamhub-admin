"use client";

import { useEffect, useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Loader2 } from "lucide-react";

import { useList3, useApprove, useDeny } from "@/apis/query/member/member";
import {
  MemberSearchRequestUserStatus,
  type MemberListItem,
  type MemberSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { SUCCESS_CODE } from "@/types/api";

const MemberGrid = dynamic(() => import("@/components/member/MemberGrid"), {
  ssr: false,
  loading: () => (
    <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
      <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
    </div>
  ),
});

const PAGE_SIZE = 10;

type StatusFilter = "ALL" | MemberSearchRequestUserStatus;

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: MemberSearchRequestUserStatus.PENDING, label: "대기" },
  { value: MemberSearchRequestUserStatus.CONFIRMED, label: "승인" },
  { value: MemberSearchRequestUserStatus.INACTIVE, label: "비활성" },
];

export default function MemberPage() {
  // Committed search criteria (applied on 검색 / page change).
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [pageNumber, setPageNumber] = useState(1);

  // Draft inputs (not yet applied to the query).
  const [keywordDraft, setKeywordDraft] = useState("");
  const [statusDraft, setStatusDraft] = useState<StatusFilter>("ALL");

  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [message, setMessage] = useState<string | null>(null);

  const listMutation = useList3();
  const approveMutation = useApprove();
  const denyMutation = useDeny();

  const { mutate: fetchList } = listMutation;

  const searchRequest = useMemo<MemberSearchRequest>(
    () => ({
      // UI pageNumber is 1-based (for display); the backend expects 0-based.
      pageNumber: pageNumber - 1,
      pageSize: PAGE_SIZE,
      keyword: keyword.trim() || undefined,
      userStatus: status === "ALL" ? undefined : status,
    }),
    [pageNumber, keyword, status],
  );

  useEffect(() => {
    fetchList({ data: searchRequest });
  }, [fetchList, searchRequest]);

  const result = listMutation.data?.resultObject;
  const rows: MemberListItem[] = result?.contents ?? [];
  const totalCount = result?.totalCount ?? 0;
  const totalPage = result?.totalPage ?? 0;

  const isBulkPending = approveMutation.isPending || denyMutation.isPending;

  const handleSearch = () => {
    setMessage(null);
    setSelectedIds([]);
    setKeyword(keywordDraft);
    setStatus(statusDraft);
    setPageNumber(1);
  };

  const goToPage = (next: number) => {
    if (next < 1 || (totalPage > 0 && next > totalPage)) {
      return;
    }
    setSelectedIds([]);
    setPageNumber(next);
  };

  const refetch = () => {
    fetchList({ data: searchRequest });
  };

  const handleApprove = () => {
    if (selectedIds.length === 0) {
      return;
    }
    setMessage(null);
    approveMutation.mutate(
      { data: { idList: selectedIds } },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage(`${response.resultObject ?? selectedIds.length}건 승인됨`);
            setSelectedIds([]);
            refetch();
          } else {
            setMessage(response.resultMessage ?? "승인에 실패했습니다.");
          }
        },
        onError: () => setMessage("승인 처리 중 오류가 발생했습니다."),
      },
    );
  };

  const handleDeny = () => {
    if (selectedIds.length === 0) {
      return;
    }
    setMessage(null);
    denyMutation.mutate(
      { data: { idList: selectedIds } },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage(`${response.resultObject ?? selectedIds.length}건 거부됨`);
            setSelectedIds([]);
            refetch();
          } else {
            setMessage(response.resultMessage ?? "거부에 실패했습니다.");
          }
        },
        onError: () => setMessage("거부 처리 중 오류가 발생했습니다."),
      },
    );
  };

  return (
    <div>
      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">회원관리</h1>
        <p className="mt-1 text-sm text-slate-500">
          회원 목록을 조회하고 가입 승인 / 거부를 관리합니다.
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
            placeholder="이름 / 이메일 / 전화"
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

      {/* Bulk actions */}
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <span className="text-sm text-slate-600">
          총 {totalCount.toLocaleString()}명
          {selectedIds.length > 0 && (
            <span className="ml-2 text-brand">
              ({selectedIds.length}명 선택)
            </span>
          )}
        </span>
        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            onClick={handleApprove}
            disabled={selectedIds.length === 0 || isBulkPending}
            className="flex items-center gap-1.5 rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {approveMutation.isPending && (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            )}
            일괄 승인
          </button>
          <button
            type="button"
            onClick={handleDeny}
            disabled={selectedIds.length === 0 || isBulkPending}
            className="flex items-center gap-1.5 rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {denyMutation.isPending && (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            )}
            일괄 거부
          </button>
        </div>
      </div>

      {message && (
        <p className="mb-3 rounded-md bg-blue-50 px-3 py-2 text-sm text-blue-700">
          {message}
        </p>
      )}

      {/* Results */}
      {listMutation.isPending ? (
        <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : listMutation.isError ? (
        <div className="flex h-[560px] items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">
            목록을 불러오지 못했습니다.
          </p>
        </div>
      ) : (
        <MemberGrid rows={rows} onSelectionChanged={setSelectedIds} />
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

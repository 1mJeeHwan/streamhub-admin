"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2 } from "lucide-react";

import { useList2 } from "@/apis/query/action-log/action-log";
import type {
  ActionLogItem,
  ActionLogSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDateTime } from "@/lib/format";

const PAGE_SIZE = 15;

const ACTION_OPTIONS: { value: string; label: string }[] = [
  { value: "", label: "전체" },
  { value: "LOGIN", label: "로그인" },
  { value: "MEMBER_APPROVE", label: "회원 승인" },
  { value: "MEMBER_DENY", label: "회원 거부" },
  { value: "MEMBER_UPDATE", label: "회원 수정" },
  { value: "CONTENT_CREATE", label: "콘텐츠 등록" },
  { value: "CONTENT_UPDATE", label: "콘텐츠 수정" },
  { value: "CONTENT_DELETE", label: "콘텐츠 삭제" },
];

const ACTION_LABELS: Record<string, string> = Object.fromEntries(
  ACTION_OPTIONS.filter((o) => o.value).map((o) => [o.value, o.label]),
);

function actionColor(action?: string): string {
  if (!action) return "bg-slate-100 text-slate-600";
  if (action.endsWith("DELETE") || action.endsWith("DENY"))
    return "bg-red-100 text-red-700";
  if (action.endsWith("CREATE") || action.endsWith("APPROVE"))
    return "bg-emerald-100 text-emerald-700";
  if (action === "LOGIN") return "bg-blue-100 text-blue-700";
  return "bg-amber-100 text-amber-700";
}

export default function ActionLogPage() {
  const [action, setAction] = useState("");
  const [keyword, setKeyword] = useState("");
  const [actionDraft, setActionDraft] = useState("");
  const [keywordDraft, setKeywordDraft] = useState("");
  const [pageNumber, setPageNumber] = useState(1);

  const listMutation = useList2();
  const { mutate: fetchList } = listMutation;

  const searchRequest = useMemo<ActionLogSearchRequest>(
    () => ({
      pageNumber: pageNumber - 1, // UI 1-based → backend 0-based
      pageSize: PAGE_SIZE,
      action: action || undefined,
      keyword: keyword.trim() || undefined,
    }),
    [pageNumber, action, keyword],
  );

  useEffect(() => {
    fetchList({ data: searchRequest });
  }, [fetchList, searchRequest]);

  const result = listMutation.data?.resultObject;
  const rows: ActionLogItem[] = result?.contents ?? [];
  const totalCount = result?.totalCount ?? 0;
  const totalPage = result?.totalPage ?? 0;

  const handleSearch = () => {
    setAction(actionDraft);
    setKeyword(keywordDraft);
    setPageNumber(1);
  };

  const goToPage = (next: number) => {
    if (next < 1 || (totalPage > 0 && next > totalPage)) return;
    setPageNumber(next);
  };

  return (
    <div>
      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">감사 로그</h1>
        <p className="mt-1 text-sm text-slate-500">
          관리자 활동 기록입니다. 액션은 SQS를 거쳐 비동기로 적재됩니다.
        </p>
      </div>

      {/* Search / filter */}
      <div className="mb-4 flex flex-wrap items-end gap-3 rounded-md border border-slate-200 bg-white p-4">
        <div className="flex flex-col">
          <label htmlFor="action" className="mb-1 text-xs font-medium text-slate-600">
            액션
          </label>
          <select
            id="action"
            value={actionDraft}
            onChange={(e) => setActionDraft(e.target.value)}
            className="w-36 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          >
            {ACTION_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col">
          <label htmlFor="keyword" className="mb-1 text-xs font-medium text-slate-600">
            검색어
          </label>
          <input
            id="keyword"
            type="text"
            value={keywordDraft}
            onChange={(e) => setKeywordDraft(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
            placeholder="관리자 / 상세 / 대상"
            className="w-64 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
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

      <p className="mb-3 text-sm text-slate-600">총 {totalCount.toLocaleString()}건</p>

      {/* Table */}
      <div className="overflow-hidden rounded-md border border-slate-200 bg-white">
        <table className="w-full text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-600">
            <tr>
              <th className="px-4 py-3 font-medium">시각</th>
              <th className="px-4 py-3 font-medium">관리자</th>
              <th className="px-4 py-3 font-medium">액션</th>
              <th className="px-4 py-3 font-medium">대상</th>
              <th className="px-4 py-3 font-medium">상세</th>
            </tr>
          </thead>
          <tbody>
            {listMutation.isPending ? (
              <tr>
                <td colSpan={5} className="px-4 py-16 text-center">
                  <Loader2 className="mx-auto h-5 w-5 animate-spin text-slate-400" />
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-16 text-center text-slate-400">
                  기록이 없습니다.
                </td>
              </tr>
            ) : (
              rows.map((r) => (
                <tr key={r.id} className="border-b border-slate-100 last:border-0">
                  <td className="whitespace-nowrap px-4 py-3 text-slate-500">
                    {formatDateTime(r.createdAt)}
                  </td>
                  <td className="px-4 py-3 text-slate-900">{r.adminName ?? "-"}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${actionColor(
                        r.action,
                      )}`}
                    >
                      {ACTION_LABELS[r.action ?? ""] ?? r.action ?? "-"}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-500">
                    {r.targetType ?? "-"}
                    {r.targetId ? ` #${r.targetId}` : ""}
                  </td>
                  <td className="px-4 py-3 text-slate-700">{r.detail ?? "-"}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

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

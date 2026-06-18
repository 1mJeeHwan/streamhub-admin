"use client";

import { useState } from "react";
import { Loader2, Pencil, Plus, Trash2 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import { couponList, useCouponDelete } from "@/apis/query/coupon/coupon";
import {
  type CouponDto,
  type CouponSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import CouponFormDialog from "@/components/coupons/CouponFormDialog";
import { SUCCESS_CODE } from "@/types/api";

/** Formats a number as a Korean-style thousands-separated currency string. */
const formatWon = (value?: number): string =>
  value != null ? `${value.toLocaleString()}원` : "-";

/** Renders the discount column text based on AMOUNT vs PERCENT type. */
const formatDiscount = (coupon: CouponDto): string => {
  if (coupon.discountType === "PERCENT") {
    const rate = coupon.discountValue != null ? `${coupon.discountValue}%` : "-";
    if (coupon.maxDiscountAmount != null) {
      return `${rate} (최대 ${coupon.maxDiscountAmount.toLocaleString()}원)`;
    }
    return rate;
  }
  return formatWon(coupon.discountValue);
};

/** Renders usage as "used/limit" with an infinity symbol when there is no limit. */
const formatUsage = (coupon: CouponDto): string => {
  const used = coupon.usedCount ?? 0;
  const limit = coupon.usageLimit != null ? coupon.usageLimit.toLocaleString() : "∞";
  return `${used.toLocaleString()}/${limit}`;
};

/** Returns a short date label ("YYYY-MM-DD") from an ISO/datetime string. */
const formatDate = (value?: string): string =>
  value ? value.slice(0, 10) : "-";

/** True when the coupon's end date is in the past. */
const isExpired = (endAt?: string): boolean => {
  if (!endAt) {
    return false;
  }
  const end = new Date(endAt);
  if (Number.isNaN(end.getTime())) {
    return false;
  }
  return end.getTime() < Date.now();
};

export default function CouponsPage() {
  const [keyword, setKeyword] = useState("");
  const [useYn, setUseYn] = useState("");
  // Committed search params drive the query; the inputs above are draft state.
  const [search, setSearch] = useState<CouponSearchRequest>({});
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<CouponDto | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const listQuery = useQuery({
    queryKey: ["coupon-list", search],
    queryFn: ({ signal }) => couponList(search, signal),
  });

  const deleteMutation = useCouponDelete();

  const coupons: CouponDto[] = listQuery.data?.resultObject ?? [];

  const runSearch = () => {
    setMessage(null);
    setSearch({
      keyword: keyword.trim() ? keyword.trim() : undefined,
      useYn: useYn ? useYn : undefined,
    });
  };

  const openCreate = () => {
    setEditing(null);
    setMessage(null);
    setDialogOpen(true);
  };

  const openEdit = (coupon: CouponDto) => {
    setEditing(coupon);
    setMessage(null);
    setDialogOpen(true);
  };

  const handleSaved = () => {
    setDialogOpen(false);
    setEditing(null);
    setMessage("저장되었습니다.");
    listQuery.refetch();
  };

  const handleDelete = (coupon: CouponDto) => {
    if (coupon.id == null) {
      return;
    }
    if (!window.confirm(`'${coupon.name ?? "쿠폰"}'을(를) 삭제하시겠습니까?`)) {
      return;
    }
    setMessage(null);
    deleteMutation.mutate(
      { id: coupon.id },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage("삭제되었습니다.");
            listQuery.refetch();
          } else {
            setMessage(response.resultMessage ?? "삭제에 실패했습니다.");
          }
        },
        onError: () => setMessage("삭제 중 오류가 발생했습니다."),
      },
    );
  };

  return (
    <div>
      <div className="mb-4 flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">쿠폰 관리</h1>
          <p className="mt-1 text-sm text-slate-500">
            할인 쿠폰을 등록하고 사용 기간과 할인 조건을 관리합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={openCreate}
          className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          <Plus className="h-4 w-4" />
          쿠폰 등록
        </button>
      </div>

      {/* Search bar */}
      <div className="mb-4 flex flex-wrap items-end gap-3 rounded-md border border-slate-200 bg-white p-4">
        <div className="flex flex-col">
          <label
            htmlFor="coupon-keyword"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            검색어
          </label>
          <input
            id="coupon-keyword"
            type="text"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                runSearch();
              }
            }}
            placeholder="쿠폰코드 / 쿠폰명"
            className="w-64 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          />
        </div>
        <div className="flex flex-col">
          <label
            htmlFor="coupon-use-filter"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            사용여부
          </label>
          <select
            id="coupon-use-filter"
            value={useYn}
            onChange={(event) => setUseYn(event.target.value)}
            className="w-32 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          >
            <option value="">전체</option>
            <option value="Y">사용</option>
            <option value="N">미사용</option>
          </select>
        </div>
        <button
          type="button"
          onClick={runSearch}
          className="rounded-md bg-slate-800 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-700"
        >
          검색
        </button>
      </div>

      {/* Summary */}
      <div className="mb-3 flex items-center gap-3">
        <span className="text-sm text-slate-600">
          총 {coupons.length.toLocaleString()}건
        </span>
        {message && (
          <span className="rounded-md bg-blue-50 px-2.5 py-1 text-xs text-blue-700">
            {message}
          </span>
        )}
      </div>

      {/* List */}
      {listQuery.isLoading ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : listQuery.isError ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">쿠폰 목록을 불러오지 못했습니다.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-md border border-slate-200 bg-white">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
              <tr>
                <th className="px-4 py-3">쿠폰코드</th>
                <th className="px-4 py-3">쿠폰명</th>
                <th className="px-4 py-3">할인</th>
                <th className="px-4 py-3">최소주문금액</th>
                <th className="px-4 py-3">사용횟수</th>
                <th className="px-4 py-3">기간</th>
                <th className="px-4 py-3">사용</th>
                <th className="px-4 py-3 text-right">관리</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {coupons.length === 0 ? (
                <tr>
                  <td
                    colSpan={8}
                    className="px-4 py-10 text-center text-slate-400"
                  >
                    조회된 쿠폰이 없습니다.
                  </td>
                </tr>
              ) : (
                coupons.map((coupon) => {
                  const expired = isExpired(coupon.endAt);
                  return (
                    <tr key={coupon.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3 font-mono text-xs font-medium text-slate-900">
                        {coupon.code ?? "-"}
                      </td>
                      <td className="px-4 py-3 font-medium text-slate-900">
                        {coupon.name ?? "-"}
                      </td>
                      <td className="px-4 py-3 text-slate-700">
                        {formatDiscount(coupon)}
                      </td>
                      <td className="px-4 py-3 text-slate-700">
                        {formatWon(coupon.minOrderAmount)}
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-700">
                        {formatUsage(coupon)}
                      </td>
                      <td className="px-4 py-3 text-slate-700">
                        <span className={expired ? "text-red-600" : ""}>
                          {formatDate(coupon.startAt)} ~{" "}
                          {formatDate(coupon.endAt)}
                          {expired && (
                            <span className="ml-1 text-xs font-medium text-red-600">
                              (만료)
                            </span>
                          )}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                            coupon.useYn === "N"
                              ? "bg-slate-200 text-slate-600"
                              : "bg-emerald-100 text-emerald-700"
                          }`}
                        >
                          {coupon.useYn === "N" ? "미사용" : "사용"}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            type="button"
                            onClick={() => openEdit(coupon)}
                            className="rounded p-1.5 text-slate-500 transition hover:bg-slate-100 hover:text-brand"
                            aria-label="수정"
                          >
                            <Pencil className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDelete(coupon)}
                            disabled={deleteMutation.isPending}
                            className="rounded p-1.5 text-slate-500 transition hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
                            aria-label="삭제"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      )}

      {dialogOpen && (
        <CouponFormDialog
          coupon={editing}
          onClose={() => setDialogOpen(false)}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
}

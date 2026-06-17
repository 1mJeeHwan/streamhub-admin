"use client";

import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Loader2 } from "lucide-react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { calendar } from "@/apis/query/donation/donation";
import { formatNumber } from "@/lib/format";
import BillingCalendar from "@/components/billing-calendar/BillingCalendar";
import TestModeBadge from "@/components/donation/TestModeBadge";

const MONTH_LABELS = [
  "1월",
  "2월",
  "3월",
  "4월",
  "5월",
  "6월",
  "7월",
  "8월",
  "9월",
  "10월",
  "11월",
  "12월",
];

export default function BillingCalendarPage() {
  const now = new Date();
  // year/month are 1-based for display and for the backend request.
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);

  const calendarQuery = useQuery({
    queryKey: ["billing-calendar", year, month],
    queryFn: ({ signal }) => calendar({ year, month }, signal),
    placeholderData: keepPreviousData,
  });

  const items = useMemo(
    () => calendarQuery.data?.resultObject ?? [],
    [calendarQuery.data],
  );

  const totals = useMemo(() => {
    let count = 0;
    let amount = 0;
    for (const item of items) {
      count += item.count ?? 0;
      amount += item.amount ?? 0;
    }
    return { count, amount };
  }, [items]);

  const goToMonth = (delta: number) => {
    // month is 1-based; normalize into 1..12 with year carry.
    const zeroBased = month - 1 + delta;
    const nextYear = year + Math.floor(zeroBased / 12);
    const nextMonth = ((zeroBased % 12) + 12) % 12;
    setYear(nextYear);
    setMonth(nextMonth + 1);
  };

  return (
    <div>
      <div className="mb-4 flex items-center gap-2">
        <h1 className="text-xl font-semibold text-slate-900">결제일정</h1>
        <TestModeBadge />
      </div>
      <p className="mb-4 text-sm text-slate-500">
        활성 구독의 월별 정기청구 예정 일정입니다.{" "}
        <span className="font-medium text-slate-600">
          데모 데이터 · 테스트 모드
        </span>
        로 실제 결제는 발생하지 않습니다.
      </p>

      {/* Month navigation + summary */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-md border border-slate-200 bg-white p-4">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => goToMonth(-1)}
            className="rounded-md border border-slate-300 p-1.5 text-slate-600 transition hover:bg-slate-100"
            aria-label="이전 달"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <span className="min-w-[120px] text-center text-base font-semibold text-slate-900">
            {year}년 {MONTH_LABELS[month - 1]}
          </span>
          <button
            type="button"
            onClick={() => goToMonth(1)}
            className="rounded-md border border-slate-300 p-1.5 text-slate-600 transition hover:bg-slate-100"
            aria-label="다음 달"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>

        <div className="flex items-center gap-6">
          <div className="text-sm">
            <span className="text-slate-500">총 청구 예정 </span>
            <span className="font-semibold text-slate-900">
              {formatNumber(totals.count)}건
            </span>
          </div>
          <div className="text-sm">
            <span className="text-slate-500">예상 금액 </span>
            <span className="font-semibold text-brand">
              ₩{formatNumber(totals.amount)}
            </span>
          </div>
        </div>
      </div>

      {/* Calendar grid */}
      {calendarQuery.isLoading ? (
        <div className="flex h-[480px] items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : calendarQuery.isError ? (
        <div className="flex h-[480px] items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">
            결제일정을 불러오지 못했습니다.
          </p>
        </div>
      ) : (
        <BillingCalendar year={year} month={month} items={items} />
      )}
    </div>
  );
}

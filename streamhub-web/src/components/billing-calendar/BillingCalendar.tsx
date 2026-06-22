import { useEffect, useMemo, useState } from "react";

import type { BillingCalendarItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatNumber } from "@/lib/format";

interface BillingCalendarProps {
  year: number;
  /** 1-based month (1 = January). */
  month: number;
  items: BillingCalendarItem[];
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** Builds the YYYY-MM-DD key the backend returns for a given day. */
function dateKey(year: number, month: number, day: number): string {
  const mm = String(month).padStart(2, "0");
  const dd = String(day).padStart(2, "0");
  return `${year}-${mm}-${dd}`;
}

/** Renders a YYYY-MM-DD key as a human-readable Korean date with weekday. */
function formatSelectedDate(key: string): string {
  const [y, m, d] = key.split("-").map(Number);
  const weekday = WEEKDAYS[new Date(y, m - 1, d).getDay()];
  return `${y}년 ${m}월 ${d}일 (${weekday})`;
}

/**
 * BillingCalendar renders a month grid (7-column CSS grid) where each day cell
 * shows the scheduled billing count and total amount for that date. Pure div
 * grid — no charting dependency — matching the lightweight action-log table
 * style. Days with scheduled billing are clickable: selecting one highlights the
 * cell and reveals a drilldown strip with that date's totals. The calendar data
 * is a per-day aggregate (count/amount only — no per-subscription identifiers),
 * so the drilldown surfaces the day's figures rather than linking to records.
 * All figures are test-mode demo data.
 */
export default function BillingCalendar({
  year,
  month,
  items,
}: BillingCalendarProps) {
  // Currently selected day cell (YYYY-MM-DD), or null when nothing is selected.
  const [selectedDate, setSelectedDate] = useState<string | null>(null);

  // Index items by date for O(1) cell lookup.
  const byDate = useMemo(() => {
    const map = new Map<string, BillingCalendarItem>();
    for (const item of items) {
      if (item.date) {
        map.set(item.date, item);
      }
    }
    return map;
  }, [items]);

  // A new month's data invalidates any prior selection.
  useEffect(() => {
    setSelectedDate(null);
  }, [year, month]);

  const selectedItem = selectedDate ? byDate.get(selectedDate) : undefined;

  // Leading blanks (weekday of the 1st) + day cells for the whole month.
  const cells = useMemo(() => {
    const firstWeekday = new Date(year, month - 1, 1).getDay();
    const daysInMonth = new Date(year, month, 0).getDate();
    const result: (number | null)[] = [];
    for (let i = 0; i < firstWeekday; i += 1) {
      result.push(null);
    }
    for (let day = 1; day <= daysInMonth; day += 1) {
      result.push(day);
    }
    return result;
  }, [year, month]);

  return (
    <div className="overflow-hidden rounded-md border border-slate-200 bg-white">
      {/* Weekday header */}
      <div className="grid grid-cols-7 border-b border-slate-200 bg-slate-50">
        {WEEKDAYS.map((label, index) => (
          <div
            key={label}
            className={`py-2 text-center text-xs font-medium ${
              index === 0
                ? "text-red-500"
                : index === 6
                  ? "text-blue-500"
                  : "text-slate-600"
            }`}
          >
            {label}
          </div>
        ))}
      </div>

      {/* Day grid */}
      <div className="grid grid-cols-7">
        {cells.map((day, index) => {
          if (day == null) {
            return (
              <div
                key={`blank-${index}`}
                className="min-h-[88px] border-b border-r border-slate-100 bg-slate-50/40"
              />
            );
          }

          const key = dateKey(year, month, day);
          const item = byDate.get(key);
          const weekday = index % 7;
          const hasBilling = item != null && (item.count ?? 0) > 0;
          const isSelected = selectedDate === key;

          const dayNumber = (
            <span
              className={`text-xs font-medium ${
                weekday === 0
                  ? "text-red-500"
                  : weekday === 6
                    ? "text-blue-500"
                    : "text-slate-500"
              }`}
            >
              {day}
            </span>
          );

          const billingBadge = hasBilling && (
            <div className="mt-1.5 rounded-md bg-brand/10 px-1.5 py-1 text-[11px] leading-tight text-brand">
              <div className="font-semibold">{item.count}건</div>
              <div>₩{formatNumber(item.amount)}</div>
            </div>
          );

          // Only billing days are interactive; empty days stay static.
          if (!hasBilling) {
            return (
              <div
                key={day}
                className="min-h-[88px] border-b border-r border-slate-100 p-2"
              >
                {dayNumber}
              </div>
            );
          }

          return (
            <button
              key={day}
              type="button"
              onClick={() => setSelectedDate(isSelected ? null : key)}
              aria-pressed={isSelected}
              className={`min-h-[88px] cursor-pointer border-b border-r border-slate-100 p-2 text-left transition hover:bg-brand/5 ${
                isSelected ? "bg-brand/10 ring-1 ring-inset ring-brand" : ""
              }`}
            >
              {dayNumber}
              {billingBadge}
            </button>
          );
        })}
      </div>

      {/* Drilldown strip for the selected day. The calendar feed is a per-day
          aggregate (no per-subscription rows), so this shows the day's totals. */}
      {selectedDate && selectedItem && (selectedItem.count ?? 0) > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 bg-slate-50 px-4 py-3">
          <div className="flex flex-wrap items-center gap-6 text-sm">
            <span className="font-medium text-slate-900">
              {formatSelectedDate(selectedDate)}
            </span>
            <span className="text-slate-600">
              청구 예정{" "}
              <span className="font-semibold text-slate-900">
                {formatNumber(selectedItem.count)}건
              </span>
            </span>
            <span className="text-slate-600">
              예상 금액{" "}
              <span className="font-semibold text-brand">
                ₩{formatNumber(selectedItem.amount)}
              </span>
            </span>
          </div>
          <button
            type="button"
            onClick={() => setSelectedDate(null)}
            className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-600 transition hover:bg-white"
          >
            선택 해제
          </button>
        </div>
      )}
    </div>
  );
}

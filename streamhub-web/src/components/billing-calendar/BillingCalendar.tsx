import { useMemo } from "react";

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

/**
 * BillingCalendar renders a month grid (7-column CSS grid) where each day cell
 * shows the scheduled billing count and total amount for that date. Pure div
 * grid — no charting dependency — matching the lightweight action-log table
 * style. All figures are test-mode demo data.
 */
export default function BillingCalendar({
  year,
  month,
  items,
}: BillingCalendarProps) {
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

          const item = byDate.get(dateKey(year, month, day));
          const weekday = index % 7;

          return (
            <div
              key={day}
              className="min-h-[88px] border-b border-r border-slate-100 p-2"
            >
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
              {item && (item.count ?? 0) > 0 && (
                <div className="mt-1.5 rounded-md bg-brand/10 px-1.5 py-1 text-[11px] leading-tight text-brand">
                  <div className="font-semibold">{item.count}건</div>
                  <div>₩{formatNumber(item.amount)}</div>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

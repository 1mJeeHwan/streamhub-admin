"use client";

import { useEffect, useRef, useState } from "react";
import { ArrowDown, ArrowUp, Minus, type LucideIcon } from "lucide-react";
import type { ApexOptions } from "apexcharts";

import type { KpiDelta } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatNumber } from "@/lib/format";

import ApexChart from "./ApexChart";

const COUNT_UP_DURATION_MS = 800;

interface KpiCardProps {
  label: string;
  icon: LucideIcon;
  iconClassName: string;
  /** "₩" 같은 접두 단위 (선택). */
  unitPrefix?: string;
  /** "건" 같은 접미 단위 (선택). */
  unitSuffix?: string;
  /** 스파크라인/▲▼ 색 (브랜드 팔레트). */
  sparkColor?: string;
  data?: KpiDelta;
  isLoading?: boolean;
  isError?: boolean;
}

/**
 * useCountUp animates a number from 0 to `target` over ~800ms using
 * requestAnimationFrame with an ease-out curve, so each KPI card "ticks up"
 * on mount without pulling in an extra animation dependency.
 */
function useCountUp(target: number, enabled: boolean): number {
  const [value, setValue] = useState(0);
  const frameRef = useRef<number | null>(null);

  useEffect(() => {
    if (!enabled) {
      setValue(0);
      return;
    }

    const start = performance.now();
    const animate = (now: number) => {
      const progress = Math.min((now - start) / COUNT_UP_DURATION_MS, 1);
      // easeOutCubic for a snappy finish.
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(target * eased));
      if (progress < 1) {
        frameRef.current = requestAnimationFrame(animate);
      }
    };

    frameRef.current = requestAnimationFrame(animate);
    return () => {
      if (frameRef.current !== null) {
        cancelAnimationFrame(frameRef.current);
      }
    };
  }, [target, enabled]);

  return value;
}

/**
 * KpiCard is a single headline metric: an animated count-up value, a
 * period-over-period ▲▼ delta, and a sparkline of the recent trend. It mirrors
 * the SummaryCards card shell (rounded border, white bg) but adds the delta and
 * spark visuals for the operations control-room strip.
 */
export default function KpiCard({
  label,
  icon: Icon,
  iconClassName,
  unitPrefix,
  unitSuffix,
  sparkColor = "#2563eb",
  data,
  isLoading = false,
  isError = false,
}: KpiCardProps) {
  const current = data?.current ?? 0;
  const deltaPct = data?.deltaPct ?? 0;
  const spark = data?.spark ?? [];

  const animated = useCountUp(current, !isLoading && !isError);

  const deltaUp = deltaPct > 0;
  const deltaDown = deltaPct < 0;
  const DeltaIcon = deltaUp ? ArrowUp : deltaDown ? ArrowDown : Minus;
  const deltaClassName = deltaUp
    ? "text-emerald-600"
    : deltaDown
      ? "text-red-600"
      : "text-slate-400";

  const sparkOptions: ApexOptions = {
    chart: { type: "line", sparkline: { enabled: true }, fontFamily: "inherit" },
    stroke: { curve: "smooth", width: 2 },
    colors: [sparkColor],
    tooltip: { enabled: false },
  };
  const sparkSeries = [{ name: label, data: spark }];

  return (
    <div className="flex flex-col justify-between rounded-md border border-slate-200 bg-white p-5">
      <div className="flex items-start justify-between">
        <p className="text-sm text-slate-500">{label}</p>
        <span
          className={`flex h-9 w-9 items-center justify-center rounded-md ${iconClassName}`}
        >
          <Icon className="h-4 w-4" />
        </span>
      </div>

      <div className="mt-3 flex items-end justify-between gap-2">
        <div className="min-w-0">
          <div className="truncate text-2xl font-semibold text-slate-900">
            {isLoading ? (
              <span className="inline-block h-7 w-16 animate-pulse rounded bg-slate-100" />
            ) : isError ? (
              <span className="text-base text-red-600">-</span>
            ) : (
              <>
                {unitPrefix}
                {formatNumber(animated)}
                {unitSuffix ? (
                  <span className="ml-0.5 text-base font-medium text-slate-400">
                    {unitSuffix}
                  </span>
                ) : null}
              </>
            )}
          </div>
          {!isLoading && !isError ? (
            <div
              className={`mt-1 flex items-center gap-0.5 text-xs font-medium ${deltaClassName}`}
            >
              <DeltaIcon className="h-3.5 w-3.5" />
              <span>
                {Math.abs(deltaPct).toFixed(1)}%
                <span className="ml-1 font-normal text-slate-400">
                  전기간 대비
                </span>
              </span>
            </div>
          ) : null}
        </div>

        {!isLoading && !isError && spark.length > 1 ? (
          <div className="h-9 w-20 shrink-0">
            <ApexChart
              options={sparkOptions}
              series={sparkSeries}
              type="line"
              height={36}
            />
          </div>
        ) : null}
      </div>
    </div>
  );
}

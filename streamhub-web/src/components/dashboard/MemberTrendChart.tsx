"use client";

import type { ApexOptions } from "apexcharts";

import { useMemberTrend } from "@/apis/query/statistics/statistics";

import ApexChart from "./ApexChart";
import ChartCard from "./ChartCard";

const TREND_DAYS = 30;

/**
 * MemberTrendChart renders an area chart of daily new-member counts over the
 * last 30 days using the member-trend statistics endpoint.
 */
export default function MemberTrendChart() {
  const { data, isPending, isError } = useMemberTrend({ days: TREND_DAYS });

  const points = data?.resultObject ?? [];
  const categories = points.map((point) => point.date ?? "");
  const counts = points.map((point) => point.count ?? 0);

  const options: ApexOptions = {
    chart: {
      type: "area",
      toolbar: { show: false },
      fontFamily: "inherit",
    },
    colors: ["#2563eb"],
    dataLabels: { enabled: false },
    stroke: { curve: "smooth", width: 2 },
    fill: {
      type: "gradient",
      gradient: { shadeIntensity: 1, opacityFrom: 0.4, opacityTo: 0.05 },
    },
    grid: { borderColor: "#e2e8f0", strokeDashArray: 4 },
    xaxis: {
      categories,
      type: "category",
      tickAmount: 6,
      labels: { style: { colors: "#64748b", fontSize: "12px" } },
      axisBorder: { color: "#e2e8f0" },
      axisTicks: { color: "#e2e8f0" },
    },
    yaxis: {
      labels: { style: { colors: "#64748b", fontSize: "12px" } },
    },
    tooltip: { x: { format: "yyyy-MM-dd" } },
  };

  const series = [{ name: "신규 가입", data: counts }];

  return (
    <ChartCard
      title="일별 가입 추이 (최근 30일)"
      isLoading={isPending}
      isError={isError}
      isEmpty={points.length === 0}
    >
      <ApexChart options={options} series={series} type="area" height="100%" />
    </ChartCard>
  );
}

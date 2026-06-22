"use client";

import type { ApexOptions } from "apexcharts";

import { useDashboardTimeseries } from "@/apis/query/dashboard/dashboard";
import { formatNumber } from "@/lib/format";

import ApexChart from "./ApexChart";
import ChartCard from "./ChartCard";

const TREND_DAYS = 90;

// 굿즈매출 / 정기후원 / 단건후원 스택. 브랜드 블루 셰이드로 구분.
const SERIES_COLORS = ["#2563eb", "#60a5fa", "#bfdbfe"];

interface RevenueTrendChartProps {
  className?: string;
  /**
   * Invoked when a bar is clicked (drill-down to the donation/revenue domain).
   * The donation list has no date filter, so no date is passed.
   */
  onSelect?: () => void;
}

/**
 * RevenueTrendChart renders the 90-day stacked donation/revenue trend
 * (goods revenue + recurring + one-off donations) from `/v1/dashboard/timeseries`.
 * The backend fills empty days with 0 so the x-axis stays continuous.
 */
export default function RevenueTrendChart({
  className,
  onSelect,
}: RevenueTrendChartProps) {
  const { data, isPending, isError } = useDashboardTimeseries({ days: TREND_DAYS });

  const trend = data?.resultObject;
  const categories = trend?.categories ?? [];
  const goodsRevenue = trend?.goodsRevenue ?? [];
  const recurringDonation = trend?.recurringDonation ?? [];
  const onceDonation = trend?.onceDonation ?? [];

  const isEmpty = categories.length === 0;

  const options: ApexOptions = {
    chart: {
      type: "bar",
      stacked: true,
      toolbar: { show: false },
      fontFamily: "inherit",
      events: {
        dataPointSelection: () => {
          onSelect?.();
        },
      },
    },
    colors: SERIES_COLORS,
    plotOptions: {
      bar: { columnWidth: "70%", borderRadius: 2 },
    },
    dataLabels: { enabled: false },
    grid: { borderColor: "#e2e8f0", strokeDashArray: 4 },
    legend: {
      position: "top",
      horizontalAlign: "right",
      fontSize: "12px",
      labels: { colors: "#64748b" },
    },
    xaxis: {
      categories,
      type: "category",
      tickAmount: 8,
      labels: {
        rotate: 0,
        hideOverlappingLabels: true,
        style: { colors: "#64748b", fontSize: "11px" },
        formatter: (value: string) => (value ? value.slice(5) : value),
      },
      axisTicks: { show: false },
    },
    yaxis: {
      labels: {
        style: { colors: "#64748b", fontSize: "12px" },
        formatter: (value: number) => formatNumber(Math.round(value)),
      },
    },
    tooltip: {
      shared: true,
      intersect: false,
      y: { formatter: (value: number) => `₩${formatNumber(value)}` },
    },
  };

  const series = [
    { name: "굿즈매출", data: goodsRevenue },
    { name: "정기후원", data: recurringDonation },
    { name: "단건후원", data: onceDonation },
  ];

  return (
    <ChartCard
      title="후원·매출 추이 (최근 90일)"
      className={className}
      isLoading={isPending}
      isError={isError}
      isEmpty={isEmpty}
    >
      <ApexChart options={options} series={series} type="bar" height="100%" />
    </ChartCard>
  );
}

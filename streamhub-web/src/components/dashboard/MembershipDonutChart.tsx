"use client";

import type { ApexOptions } from "apexcharts";

import { useDashboardSummary } from "@/apis/query/dashboard/dashboard";
import { formatNumber } from "@/lib/format";

import ApexChart from "./ApexChart";
import ChartCard from "./ChartCard";

// 멤버십/구독 구성 도넛. 브랜드 블루 셰이드.
const SLICE_COLORS = ["#2563eb", "#10b981", "#0ea5e9"];

// 슬라이스 인덱스 → 드릴다운 도메인 경로. slices/labels 배열 순서와 일치해야 한다.
const SLICE_HREFS = ["/subscription", "/subscription", "/order"];

interface MembershipDonutChartProps {
  /** Invoked with the destination path when a slice is clicked. */
  onSelect?: (href: string) => void;
}

/**
 * MembershipDonutChart renders the membership/subscription composition as a
 * donut. The generated dashboard client exposes only summary/timeseries/feed,
 * so this derives its slices from the `/v1/dashboard/summary` KPI values
 * already on screen (active subscribers, new subscriptions, open orders) rather
 * than a dedicated membership endpoint.
 */
export default function MembershipDonutChart({
  onSelect,
}: MembershipDonutChartProps) {
  const { data, isPending, isError } = useDashboardSummary();
  const summary = data?.resultObject;

  const slices = [
    { label: "활성 구독자", value: summary?.activeSubscribers?.current ?? 0 },
    { label: "신규 구독", value: summary?.newSubscriptions?.current ?? 0 },
    { label: "진행 중 주문", value: summary?.openOrders?.current ?? 0 },
  ];

  const labels = slices.map((slice) => slice.label);
  const series = slices.map((slice) => slice.value);
  const isEmpty = series.every((value) => value === 0);

  const options: ApexOptions = {
    chart: {
      type: "donut",
      fontFamily: "inherit",
      events: {
        dataPointSelection: (
          _event: unknown,
          _chartContext: unknown,
          config: { dataPointIndex: number },
        ) => {
          const href = SLICE_HREFS[config.dataPointIndex];
          if (href != null) {
            onSelect?.(href);
          }
        },
      },
    },
    labels,
    colors: SLICE_COLORS,
    legend: {
      position: "bottom",
      fontSize: "12px",
      labels: { colors: "#64748b" },
    },
    dataLabels: {
      enabled: true,
      formatter: (percent: number) => `${Number(percent).toFixed(1)}%`,
    },
    stroke: { width: 2, colors: ["#ffffff"] },
    plotOptions: {
      pie: { donut: { size: "62%" } },
    },
    tooltip: {
      y: { formatter: (value: number) => `${formatNumber(value)}` },
    },
  };

  return (
    <ChartCard
      title="멤버십 구성"
      isLoading={isPending}
      isError={isError}
      isEmpty={isEmpty}
    >
      <ApexChart options={options} series={series} type="donut" height="100%" />
    </ChartCard>
  );
}

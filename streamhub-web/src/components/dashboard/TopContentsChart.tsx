"use client";

import type { ApexOptions } from "apexcharts";

import { useTopContents } from "@/apis/query/statistics/statistics";
import { formatNumber } from "@/lib/format";

import ApexChart from "./ApexChart";
import ChartCard from "./ChartCard";

const TOP_LIMIT = 5;

/**
 * TopContentsChart renders a horizontal bar chart of the top viewed contents
 * using the top-contents statistics endpoint.
 */
export default function TopContentsChart() {
  const { data, isPending, isError } = useTopContents({ limit: TOP_LIMIT });

  const items = data?.resultObject ?? [];
  const categories = items.map((item) => item.title ?? "-");
  const viewCounts = items.map((item) => item.viewCount ?? 0);

  const options: ApexOptions = {
    chart: {
      type: "bar",
      toolbar: { show: false },
      fontFamily: "inherit",
    },
    colors: ["#2563eb"],
    plotOptions: {
      bar: { horizontal: true, borderRadius: 4, barHeight: "60%" },
    },
    dataLabels: {
      enabled: true,
      formatter: (value: number) => formatNumber(value),
      style: { colors: ["#1e293b"], fontSize: "12px" },
      offsetX: 28,
    },
    grid: { borderColor: "#e2e8f0", strokeDashArray: 4 },
    xaxis: {
      categories,
      labels: {
        style: { colors: "#64748b", fontSize: "12px" },
        formatter: (value: string) => formatNumber(Number(value)),
      },
    },
    yaxis: {
      labels: { style: { colors: "#64748b", fontSize: "12px" } },
    },
    tooltip: {
      y: { formatter: (value: number) => `${formatNumber(value)} 회` },
    },
  };

  const series = [{ name: "조회수", data: viewCounts }];

  return (
    <ChartCard
      title="조회수 Top 5"
      isLoading={isPending}
      isError={isError}
      isEmpty={items.length === 0}
    >
      <ApexChart options={options} series={series} type="bar" height="100%" />
    </ChartCard>
  );
}

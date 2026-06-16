"use client";

import type { ApexOptions } from "apexcharts";

import { useWatchByChannel } from "@/apis/query/statistics/statistics";
import { secondsToHours } from "@/lib/format";

import ApexChart from "./ApexChart";
import ChartCard from "./ChartCard";

// Brand-blue palette shades used to differentiate channel slices.
const CHANNEL_COLORS = [
  "#2563eb",
  "#3b82f6",
  "#60a5fa",
  "#93c5fd",
  "#1d4ed8",
  "#1e40af",
  "#bfdbfe",
];

/**
 * WatchByChannelChart renders a donut chart of total watch time per channel,
 * converting the endpoint's seconds into hours for display.
 */
export default function WatchByChannelChart() {
  const { data, isPending, isError } = useWatchByChannel();

  const items = data?.resultObject ?? [];
  const labels = items.map((item) => item.channelName ?? "-");
  const hours = items.map((item) => secondsToHours(item.totalSeconds));

  const options: ApexOptions = {
    chart: { type: "donut", fontFamily: "inherit" },
    labels,
    colors: CHANNEL_COLORS,
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
      y: { formatter: (value: number) => `${value.toFixed(1)} 시간` },
    },
  };

  return (
    <ChartCard
      title="채널별 시청시간 (시간)"
      isLoading={isPending}
      isError={isError}
      isEmpty={items.length === 0}
    >
      <ApexChart options={options} series={hours} type="donut" height="100%" />
    </ChartCard>
  );
}

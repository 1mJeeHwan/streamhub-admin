"use client";

import type { ApexOptions } from "apexcharts";

import { useStatisticsWatchByChannel } from "@/apis/query/statistics/statistics";
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

interface WatchByChannelChartProps {
  /**
   * Invoked when a channel slice is clicked (drill-down to the content domain).
   * The watch endpoint exposes no channel id, so the channel name is passed for
   * the caller to use if/when a channel filter exists; otherwise it is ignored.
   */
  onSelect?: (channelName: string) => void;
}

/**
 * WatchByChannelChart renders a donut chart of total watch time per channel,
 * converting the endpoint's seconds into hours for display.
 */
export default function WatchByChannelChart({
  onSelect,
}: WatchByChannelChartProps) {
  const { data, isPending, isError } = useStatisticsWatchByChannel();

  const items = data?.resultObject ?? [];
  const labels = items.map((item) => item.channelName ?? "-");
  const hours = items.map((item) => secondsToHours(item.totalSeconds));

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
          const item = items[config.dataPointIndex];
          if (item?.channelName != null) {
            onSelect?.(item.channelName);
          }
        },
      },
    },
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

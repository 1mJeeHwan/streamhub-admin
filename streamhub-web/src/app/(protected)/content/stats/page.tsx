"use client";

import type { ApexOptions } from "apexcharts";
import Link from "next/link";
import { Eye, FilmIcon, Loader2, UserPlus, Users } from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import {
  statisticsSummary,
  statisticsTopContents,
  statisticsWatchByChannel,
} from "@/apis/query/statistics/statistics";
import type {
  ChannelWatchItem,
  SummaryResponse,
  TopContentItem,
} from "@/apis/query/streamHubAdminAPI.schemas";
import ApexChart from "@/components/dashboard/ApexChart";
import { formatNumber, secondsToHours } from "@/lib/format";

const TOP_LIMIT = 10;

/**
 * ContentStatsPage is the read-only content analytics screen: four KPI summary
 * cards from the summary endpoint, a top-N viewed contents table, and a
 * channel-by-channel watch-time bar chart (ApexChart, SSR-safe). Each block
 * owns its own loading / error / empty state.
 */
export default function ContentStatsPage() {
  const summaryQuery = useQuery({
    queryKey: ["content-stats-summary"],
    queryFn: ({ signal }) => statisticsSummary(signal),
  });

  const topContentsQuery = useQuery({
    queryKey: ["content-stats-top-contents", TOP_LIMIT],
    queryFn: ({ signal }) => statisticsTopContents({ limit: TOP_LIMIT }, signal),
  });

  const watchByChannelQuery = useQuery({
    queryKey: ["content-stats-watch-by-channel"],
    queryFn: ({ signal }) => statisticsWatchByChannel(signal),
  });

  const summary: SummaryResponse = summaryQuery.data?.resultObject ?? {};
  const topContents: TopContentItem[] = topContentsQuery.data?.resultObject ?? [];
  const channels: ChannelWatchItem[] =
    watchByChannelQuery.data?.resultObject ?? [];

  const kpis = [
    {
      label: "총 회원",
      value: summary.totalMembers,
      icon: Users,
      accent: "bg-blue-50 text-blue-600",
      href: "/member",
    },
    {
      label: "신규 회원 (7일)",
      value: summary.newMembers7d,
      icon: UserPlus,
      accent: "bg-emerald-50 text-emerald-600",
      href: "/member",
    },
    {
      label: "총 조회수",
      value: summary.totalViews,
      icon: Eye,
      accent: "bg-violet-50 text-violet-600",
      href: "/analytics",
    },
    {
      label: "총 콘텐츠",
      value: summary.totalContents,
      icon: FilmIcon,
      accent: "bg-amber-50 text-amber-600",
      href: "/content",
    },
  ];

  const channelNames = channels.map((channel) => channel.channelName ?? "-");
  const channelHours = channels.map((channel) =>
    secondsToHours(channel.totalSeconds),
  );

  const chartOptions: ApexOptions = {
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
      formatter: (value: number) => `${formatNumber(value)}h`,
      style: { colors: ["#1e293b"], fontSize: "12px" },
      offsetX: 26,
    },
    grid: { borderColor: "#e2e8f0", strokeDashArray: 4 },
    xaxis: {
      categories: channelNames,
      labels: {
        style: { colors: "#64748b", fontSize: "12px" },
        formatter: (value: string) => `${formatNumber(Number(value))}h`,
      },
    },
    yaxis: {
      labels: { style: { colors: "#64748b", fontSize: "12px" } },
    },
    tooltip: {
      y: { formatter: (value: number) => `${formatNumber(value)} 시간` },
    },
  };

  const chartSeries = [{ name: "시청시간", data: channelHours }];

  return (
    <div>
      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">콘텐츠 통계</h1>
        <p className="mt-1 text-sm text-slate-500">
          요약 지표, 조회수 상위 콘텐츠, 채널별 시청시간을 확인합니다.
        </p>
      </div>

      {/* KPI summary cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {kpis.map((kpi) => {
          const Icon = kpi.icon;
          return (
            <Link
              key={kpi.label}
              href={kpi.href}
              className="rounded-md border border-slate-200 bg-white p-4 transition hover:border-brand hover:shadow-sm focus:outline-none focus:ring-1 focus:ring-brand"
            >
              <div className="flex items-center justify-between">
                <span className="text-sm text-slate-500">{kpi.label}</span>
                <span
                  className={`flex h-8 w-8 items-center justify-center rounded-md ${kpi.accent}`}
                >
                  <Icon className="h-4 w-4" />
                </span>
              </div>
              <div className="mt-3 text-2xl font-semibold text-slate-900">
                {summaryQuery.isLoading ? (
                  <Loader2 className="h-5 w-5 animate-spin text-slate-300" />
                ) : summaryQuery.isError ? (
                  <span className="text-sm font-normal text-red-600">
                    불러오기 실패
                  </span>
                ) : (
                  formatNumber(kpi.value)
                )}
              </div>
            </Link>
          );
        })}
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        {/* Top contents table */}
        <div className="rounded-md border border-slate-200 bg-white">
          <div className="border-b border-slate-200 px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-900">
              조회수 Top {TOP_LIMIT}
            </h2>
          </div>
          {topContentsQuery.isLoading ? (
            <div className="flex h-64 items-center justify-center">
              <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
            </div>
          ) : topContentsQuery.isError ? (
            <div className="flex h-64 items-center justify-center">
              <p className="text-sm text-red-600">
                조회수 순위를 불러오지 못했습니다.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                  <tr>
                    <th className="w-12 px-4 py-3">#</th>
                    <th className="px-4 py-3">콘텐츠</th>
                    <th className="px-4 py-3">채널</th>
                    <th className="px-4 py-3 text-right">조회수</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {topContents.length === 0 ? (
                    <tr>
                      <td
                        colSpan={4}
                        className="px-4 py-10 text-center text-slate-400"
                      >
                        조회된 콘텐츠가 없습니다.
                      </td>
                    </tr>
                  ) : (
                    topContents.map((item, index) => (
                      <tr
                        key={item.id ?? `${item.title}-${index}`}
                        className="hover:bg-slate-50"
                      >
                        <td className="px-4 py-3 text-slate-400">
                          {index + 1}
                        </td>
                        <td className="px-4 py-3 font-medium text-slate-900">
                          {item.id != null ? (
                            <Link
                              href={`/content/${item.id}`}
                              className="text-brand hover:underline"
                              title="콘텐츠 상세로 이동"
                            >
                              {item.title ?? "-"}
                            </Link>
                          ) : (
                            (item.title ?? "-")
                          )}
                        </td>
                        <td className="px-4 py-3 text-slate-700">
                          {item.channelName ?? "-"}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums text-slate-700">
                          {formatNumber(item.viewCount)}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Channel watch-time bar chart */}
        <div className="rounded-md border border-slate-200 bg-white">
          <div className="border-b border-slate-200 px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-900">
              채널별 시청시간
            </h2>
          </div>
          <div className="h-72 p-4">
            {watchByChannelQuery.isLoading ? (
              <div className="flex h-full items-center justify-center">
                <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
              </div>
            ) : watchByChannelQuery.isError ? (
              <div className="flex h-full items-center justify-center">
                <p className="text-sm text-red-600">
                  채널별 시청시간을 불러오지 못했습니다.
                </p>
              </div>
            ) : channels.length === 0 ? (
              <div className="flex h-full items-center justify-center">
                <p className="text-sm text-slate-400">데이터가 없습니다.</p>
              </div>
            ) : (
              <ApexChart
                options={chartOptions}
                series={chartSeries}
                type="bar"
                height="100%"
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

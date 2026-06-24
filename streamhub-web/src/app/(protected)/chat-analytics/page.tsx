"use client";

import type { ApexOptions } from "apexcharts";
import { useQuery } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";

import { chatStats } from "@/apis/chat-stats";
import ApexChart from "@/components/dashboard/ApexChart";
import ChartCard from "@/components/dashboard/ChartCard";
import { formatNumber } from "@/lib/format";

/** ChatIntent enum → Korean label for the charts. */
const INTENT_LABELS: Record<string, string> = {
  PRODUCT_INQUIRY: "상품 문의",
  ORDER_LOOKUP: "주문 조회",
  FAQ: "자주 묻는 질문",
  FEATURE_GUIDE: "기능 안내",
  FALLBACK: "미해결(폴백)",
};

const DONUT_COLORS = ["#2563eb", "#10b981", "#0ea5e9", "#a855f7", "#f43f5e"];

export default function ChatAnalyticsPage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["chat-stats"],
    queryFn: ({ signal }) => chatStats(signal),
  });
  const stats = data?.resultObject;

  const kpis = [
    { label: "총 세션", value: stats?.totalSessions ?? 0, suffix: "" },
    { label: "사용자 질문", value: stats?.userQuestions ?? 0, suffix: "" },
    { label: "폴백률", value: stats?.fallbackRate ?? 0, suffix: "%" },
    { label: "미답변 대기", value: stats?.openUnanswered ?? 0, suffix: "" },
  ];

  const intentLabels = (stats?.intents ?? []).map((i) => INTENT_LABELS[i.label] ?? i.label);
  const intentSeries = (stats?.intents ?? []).map((i) => i.count);
  const intentEmpty = intentSeries.every((v) => v === 0);

  const providerLabels = (stats?.providers ?? []).map((p) => p.label);
  const providerSeries = (stats?.providers ?? []).map((p) => p.count);
  const providerEmpty = providerSeries.every((v) => v === 0);

  const daily = stats?.daily ?? [];
  const dailyEmpty = daily.every((d) => d.count === 0);

  const donutOptions = (labels: string[]): ApexOptions => ({
    chart: { type: "donut", fontFamily: "inherit" },
    labels,
    colors: DONUT_COLORS,
    legend: { position: "bottom", fontSize: "12px", labels: { colors: "#64748b" } },
    dataLabels: { enabled: true, formatter: (p: number) => `${Number(p).toFixed(0)}%` },
    stroke: { width: 2, colors: ["#ffffff"] },
    plotOptions: { pie: { donut: { size: "62%" } } },
  });

  const barOptions: ApexOptions = {
    chart: { type: "bar", fontFamily: "inherit", toolbar: { show: false } },
    colors: ["#2563eb"],
    plotOptions: { bar: { borderRadius: 4, columnWidth: "55%" } },
    dataLabels: { enabled: false },
    xaxis: {
      categories: daily.map((d) => d.date.slice(5)), // MM-DD
      labels: { style: { colors: "#94a3b8", fontSize: "11px" } },
    },
    yaxis: { labels: { style: { colors: "#94a3b8" } }, forceNiceScale: true },
    grid: { borderColor: "#f1f5f9" },
    tooltip: { y: { formatter: (v: number) => `${formatNumber(v)} 세션` } },
  };

  return (
    <div>
      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">챗봇 분석</h1>
        <p className="mt-1 text-sm text-slate-500">
          상담 챗봇의 세션·질문 인텐트 분포·폴백률·일별 추이를 한눈에 봅니다.
        </p>
      </div>

      {isPending ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : isError ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">통계를 불러오지 못했습니다.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {/* KPI cards */}
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {kpis.map((kpi) => (
              <div key={kpi.label} className="rounded-md border border-slate-200 bg-white p-5">
                <p className="text-sm text-slate-500">{kpi.label}</p>
                <p className="mt-1 text-2xl font-bold text-slate-900">
                  {kpi.suffix === "%" ? kpi.value.toFixed(1) : formatNumber(kpi.value)}
                  <span className="ml-0.5 text-base font-normal text-slate-400">{kpi.suffix}</span>
                </p>
              </div>
            ))}
          </div>

          {/* Charts */}
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <ChartCard title="질문 인텐트 분포" isEmpty={intentEmpty}>
              <ApexChart
                options={donutOptions(intentLabels)}
                series={intentSeries}
                type="donut"
                height="100%"
              />
            </ChartCard>
            <ChartCard title="제공자 구성 (RULE / LLM)" isEmpty={providerEmpty}>
              <ApexChart
                options={donutOptions(providerLabels)}
                series={providerSeries}
                type="donut"
                height="100%"
              />
            </ChartCard>
          </div>

          <ChartCard title="일별 세션 추이 (최근 14일)" isEmpty={dailyEmpty}>
            <ApexChart
              options={barOptions}
              series={[{ name: "세션", data: daily.map((d) => d.count) }]}
              type="bar"
              height="100%"
            />
          </ChartCard>
        </div>
      )}
    </div>
  );
}

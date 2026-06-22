"use client";

import { useMemo } from "react";
import Link from "next/link";
import { Loader2 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import {
  analyticsBreakdown,
  analyticsContentPerformance,
  analyticsOverview,
  analyticsTimeseries,
} from "@/apis/query/analytics/analytics";
import {
  type AnalyticsBreakdownDto,
  type AnalyticsOverviewDto,
  type ContentStatDto,
  ContentStatDtoContentType,
  type CountItemDto,
  type TimeseriesPointDto,
} from "@/apis/query/streamHubAdminAPI.schemas";
import AnalyticsDailyTrendChart from "@/components/analytics/AnalyticsDailyTrendChart";
import AnalyticsDeviceDonutChart from "@/components/analytics/AnalyticsDeviceDonutChart";
import { formatDateTime, formatNumber } from "@/lib/format";

/**
 * formatDwell converts an average dwell time in milliseconds into a readable
 * Korean duration: under a minute shows "12.3초", a minute or more shows
 * "2분 5초". Returns "-" for missing or invalid input.
 */
function formatDwell(ms?: number | null): string {
  if (ms == null || ms < 0 || Number.isNaN(ms)) {
    return "-";
  }
  const totalSeconds = ms / 1000;
  if (totalSeconds < 60) {
    return `${(Math.round(totalSeconds * 10) / 10).toLocaleString("ko-KR")}초`;
  }
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = Math.round(totalSeconds % 60);
  return `${minutes}분 ${seconds}초`;
}

// 콘텐츠 유형 → 한글 라벨 + 배지 색상.
const CONTENT_TYPE_STYLES: Record<
  ContentStatDtoContentType,
  { label: string; className: string }
> = {
  [ContentStatDtoContentType.VIDEO]: {
    label: "동영상",
    className: "bg-blue-100 text-blue-700",
  },
  [ContentStatDtoContentType.ALBUM]: {
    label: "앨범",
    className: "bg-violet-100 text-violet-700",
  },
  [ContentStatDtoContentType.POST]: {
    label: "게시글",
    className: "bg-emerald-100 text-emerald-700",
  },
  [ContentStatDtoContentType.PAGE]: {
    label: "페이지",
    className: "bg-slate-200 text-slate-600",
  },
};

function ContentTypeBadge({ type }: { type?: ContentStatDtoContentType }) {
  const style = type ? CONTENT_TYPE_STYLES[type] : undefined;
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
        style?.className ?? "bg-slate-100 text-slate-500"
      }`}
    >
      {style?.label ?? type ?? "기타"}
    </span>
  );
}

interface KpiCardProps {
  label: string;
  value: string;
  hint?: string;
  isLoading: boolean;
}

function KpiCard({ label, value, hint, isLoading }: KpiCardProps) {
  return (
    <div className="rounded-md border border-slate-200 bg-white p-5">
      <p className="text-sm text-slate-500">{label}</p>
      {isLoading ? (
        <div className="mt-2 flex h-9 items-center">
          <Loader2 className="h-5 w-5 animate-spin text-slate-300" />
        </div>
      ) : (
        <p className="mt-1 text-3xl font-semibold text-slate-900">{value}</p>
      )}
      {hint && <p className="mt-1 text-xs text-slate-400">{hint}</p>}
    </div>
  );
}

interface ContentTableProps {
  title: string;
  helpText: string;
  rows: ContentStatDto[];
  isLoading: boolean;
  isError: boolean;
}

/**
 * ContentTable renders one side of the popular / underperforming comparison:
 * type badge · title · views · avg dwell. Read-only — the business view of
 * which content earns its keep and which lags.
 */
function ContentTable({
  title,
  helpText,
  rows,
  isLoading,
  isError,
}: ContentTableProps) {
  return (
    <div className="rounded-md border border-slate-200 bg-white">
      <div className="border-b border-slate-200 px-5 py-4">
        <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
        <p className="mt-0.5 text-xs text-slate-400">{helpText}</p>
      </div>
      {isLoading ? (
        <div className="flex h-64 items-center justify-center">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : isError ? (
        <div className="flex h-64 items-center justify-center">
          <p className="text-sm text-red-600">불러오지 못했습니다.</p>
        </div>
      ) : rows.length === 0 ? (
        <div className="flex h-64 items-center justify-center">
          <p className="text-sm text-slate-400">데이터가 없습니다.</p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
              <tr>
                <th className="px-4 py-3">유형</th>
                <th className="px-4 py-3">제목</th>
                <th className="px-4 py-3 text-right">조회수</th>
                <th className="px-4 py-3 text-right">평균 체류</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((row) => (
                <tr
                  key={`${row.contentType}-${row.targetId}`}
                  className="hover:bg-slate-50"
                >
                  <td className="px-4 py-3">
                    <ContentTypeBadge type={row.contentType} />
                  </td>
                  <td className="max-w-[240px] truncate px-4 py-3 font-medium text-slate-900">
                    {row.contentType === ContentStatDtoContentType.VIDEO &&
                    row.targetId != null ? (
                      <Link
                        href={`/content/${row.targetId}`}
                        className="text-brand hover:underline"
                        title="콘텐츠 상세로 이동"
                      >
                        {row.title ?? "-"}
                      </Link>
                    ) : (
                      (row.title ?? "-")
                    )}
                  </td>
                  <td className="px-4 py-3 text-right text-slate-700">
                    {formatNumber(row.views)}
                  </td>
                  <td className="px-4 py-3 text-right text-slate-500">
                    {formatDwell(row.avgDwellMs)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

interface CountListProps {
  title: string;
  hint?: string;
  items: CountItemDto[];
  isLoading: boolean;
  isError: boolean;
}

/**
 * CountList renders a ranked label/count list (used for referrers and paths).
 * Each row shows a proportional bar so the relative weight is readable at a
 * glance. Labels are shown as-is (e.g. "google" / "naver" / "direct").
 */
function CountList({ title, hint, items, isLoading, isError }: CountListProps) {
  const max = items.reduce((acc, item) => Math.max(acc, item.count ?? 0), 0);

  return (
    <div className="rounded-md border border-slate-200 bg-white">
      <div className="border-b border-slate-200 px-5 py-4">
        <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
        {hint && <p className="mt-0.5 text-xs text-slate-400">{hint}</p>}
      </div>
      <div className="p-5">
        {isLoading ? (
          <div className="flex h-40 items-center justify-center">
            <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
          </div>
        ) : isError ? (
          <div className="flex h-40 items-center justify-center">
            <p className="text-sm text-red-600">불러오지 못했습니다.</p>
          </div>
        ) : items.length === 0 ? (
          <div className="flex h-40 items-center justify-center">
            <p className="text-sm text-slate-400">데이터가 없습니다.</p>
          </div>
        ) : (
          <ul className="space-y-3">
            {items.map((item, index) => {
              const count = item.count ?? 0;
              const width = max > 0 ? Math.max((count / max) * 100, 2) : 0;
              return (
                <li key={`${item.label}-${index}`}>
                  <div className="mb-1 flex items-center justify-between text-sm">
                    <span className="truncate font-medium text-slate-700">
                      {item.label ?? "-"}
                    </span>
                    <span className="ml-2 shrink-0 text-slate-500">
                      {formatNumber(count)}
                    </span>
                  </div>
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
                    <div
                      className="h-full rounded-full bg-brand"
                      style={{ width: `${width}%` }}
                    />
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

/**
 * AnalyticsPage is the "콘텐츠 활동 분석" screen. It joins four read-only GET
 * endpoints — overview KPIs, the 30-day timeseries, content performance
 * (sorted by views desc), and the referrer/device/path breakdown — into a
 * business-facing view of which content earns its keep and where traffic comes
 * from. Data is seeded plus live events accumulated from the public site.
 */
export default function AnalyticsPage() {
  const overviewQuery = useQuery({
    queryKey: ["analytics-overview"],
    queryFn: ({ signal }) => analyticsOverview(signal),
  });

  const timeseriesQuery = useQuery({
    queryKey: ["analytics-timeseries"],
    queryFn: ({ signal }) => analyticsTimeseries(signal),
  });

  const contentQuery = useQuery({
    queryKey: ["analytics-content-performance"],
    queryFn: ({ signal }) => analyticsContentPerformance(signal),
  });

  const breakdownQuery = useQuery({
    queryKey: ["analytics-breakdown"],
    queryFn: ({ signal }) => analyticsBreakdown(signal),
  });

  const overview: AnalyticsOverviewDto | undefined =
    overviewQuery.data?.resultObject;
  const points: TimeseriesPointDto[] =
    timeseriesQuery.data?.resultObject ?? [];
  const breakdown: AnalyticsBreakdownDto | undefined =
    breakdownQuery.data?.resultObject;

  // 콘텐츠 성과 목록은 조회수 내림차순 정렬 — 앞 10개 = 인기, 뒤 10개 = 저조.
  const { popular, underperforming } = useMemo(() => {
    const stats: ContentStatDto[] = contentQuery.data?.resultObject ?? [];
    const top = stats.slice(0, 10);
    const bottom = stats.length > 10 ? stats.slice(-10).reverse() : [];
    return { popular: top, underperforming: bottom };
  }, [contentQuery.data]);

  const byDevice = breakdown?.byDevice;
  const topReferrers: CountItemDto[] = breakdown?.topReferrers ?? [];
  const topPaths: CountItemDto[] = breakdown?.topPaths ?? [];

  return (
    <div>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">
            콘텐츠 활동 분석
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            돈 버는 입장에서 어떤 콘텐츠가 인기·저조한지, 트래픽이 어디서
            들어오는지 한눈에 봅니다.
          </p>
        </div>
        <span className="shrink-0 rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-500">
          데모 데이터 · 실시간 이벤트 누적
        </span>
      </div>

      {/* 1. 개요 KPI 카드 */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
        <KpiCard
          label="총 이벤트"
          value={formatNumber(overview?.totalEvents)}
          isLoading={overviewQuery.isLoading}
        />
        <KpiCard
          label="세션"
          value={formatNumber(overview?.totalSessions)}
          isLoading={overviewQuery.isLoading}
        />
        <KpiCard
          label="순 방문자"
          value={formatNumber(overview?.uniqueVisitors)}
          isLoading={overviewQuery.isLoading}
        />
        <KpiCard
          label="페이지뷰"
          value={formatNumber(overview?.pageViews)}
          isLoading={overviewQuery.isLoading}
        />
        <KpiCard
          label="콘텐츠뷰"
          value={formatNumber(overview?.contentViews)}
          isLoading={overviewQuery.isLoading}
        />
        <KpiCard
          label="평균 체류시간"
          value={formatDwell(overview?.avgDwellMs)}
          hint="세션당 평균"
          isLoading={overviewQuery.isLoading}
        />
      </div>

      {overviewQuery.isError && (
        <p className="mt-3 text-sm text-red-600">개요를 불러오지 못했습니다.</p>
      )}

      {/* 2. 일별 추이 차트 */}
      <div className="mt-4">
        <AnalyticsDailyTrendChart
          points={points}
          isLoading={timeseriesQuery.isLoading}
          isError={timeseriesQuery.isError}
        />
      </div>

      {/* 3. 인기 / 저조 콘텐츠 */}
      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <ContentTable
          title="인기 콘텐츠 TOP 10"
          helpText="조회수 기준 상위 — 가장 잘 팔리는 콘텐츠"
          rows={popular}
          isLoading={contentQuery.isLoading}
          isError={contentQuery.isError}
        />
        <ContentTable
          title="저조 콘텐츠 BOTTOM 10"
          helpText="조회수 기준 하위 — 개선·정리 검토 대상"
          rows={underperforming}
          isLoading={contentQuery.isLoading}
          isError={contentQuery.isError}
        />
      </div>

      {/* 4. 유입·기기 분석 */}
      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-3">
        <AnalyticsDeviceDonutChart
          byDevice={byDevice}
          isLoading={breakdownQuery.isLoading}
          isError={breakdownQuery.isError}
        />
        <CountList
          title="유입 경로"
          hint="어디서 들어왔는지 (referrer)"
          items={topReferrers}
          isLoading={breakdownQuery.isLoading}
          isError={breakdownQuery.isError}
        />
        <CountList
          title="인기 경로"
          hint="어떤 페이지를 봤는지 (path)"
          items={topPaths}
          isLoading={breakdownQuery.isLoading}
          isError={breakdownQuery.isError}
        />
      </div>

      {overview && (
        <p className="mt-4 text-xs text-slate-400">
          {formatDateTime(new Date().toISOString())} 기준 · 시드 데이터에 공개
          사이트의 실시간 이벤트가 누적됩니다.
        </p>
      )}
    </div>
  );
}

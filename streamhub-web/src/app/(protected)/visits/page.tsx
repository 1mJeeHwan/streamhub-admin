"use client";

import Link from "next/link";
import { Loader2 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import {
  visitDailyCreate,
  visitList,
  visitSummary,
} from "@/apis/query/visit/visit";
import {
  type DailyCountDto,
  type PathCountDto,
  type VisitLogDto,
  type VisitSummaryDto,
} from "@/apis/query/streamHubAdminAPI.schemas";
import VisitDailyTrendChart from "@/components/visits/VisitDailyTrendChart";
import VisitDeviceBreakdownChart from "@/components/visits/VisitDeviceBreakdownChart";
import { formatDateTime, formatNumber } from "@/lib/format";

// 기기 코드 → 한글 라벨.
const DEVICE_LABELS: Record<string, string> = {
  PC: "PC",
  MOBILE: "모바일",
  TABLET: "태블릿",
};

interface SummaryCardProps {
  label: string;
  value?: number;
  hint?: string;
  isLoading: boolean;
}

function SummaryCard({ label, value, hint, isLoading }: SummaryCardProps) {
  return (
    <div className="rounded-md border border-slate-200 bg-white p-5">
      <p className="text-sm text-slate-500">{label}</p>
      {isLoading ? (
        <div className="mt-2 flex h-9 items-center">
          <Loader2 className="h-5 w-5 animate-spin text-slate-300" />
        </div>
      ) : (
        <p className="mt-1 text-3xl font-semibold text-slate-900">
          {formatNumber(value)}
        </p>
      )}
      {hint && <p className="mt-1 text-xs text-slate-400">{hint}</p>}
    </div>
  );
}

/**
 * VisitsPage is the "접속 통계" screen: summary cards (total / today / unique IP),
 * a daily-trend area chart, a device breakdown donut, the top-paths table, and a
 * recent-visits table. All read-only — summary via GET, daily + recent list via
 * POST with a (currently empty) search body that lets the backend apply defaults.
 */
export default function VisitsPage() {
  const summaryQuery = useQuery({
    queryKey: ["visit-summary"],
    queryFn: ({ signal }) => visitSummary(signal),
  });

  const dailyQuery = useQuery({
    queryKey: ["visit-daily"],
    queryFn: ({ signal }) => visitDailyCreate({}, signal),
  });

  const listQuery = useQuery({
    queryKey: ["visit-list"],
    queryFn: ({ signal }) => visitList({}, signal),
  });

  const summary: VisitSummaryDto | undefined = summaryQuery.data?.resultObject;
  const daily: DailyCountDto[] = dailyQuery.data?.resultObject ?? [];
  const topPaths: PathCountDto[] = summary?.topPaths ?? [];
  const visits: VisitLogDto[] = listQuery.data?.resultObject ?? [];

  return (
    <div>
      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">접속 통계</h1>
        <p className="mt-1 text-sm text-slate-500">
          방문 추이와 기기·경로별 분포, 최근 접속 로그를 확인합니다.
        </p>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <SummaryCard
          label="총 방문"
          value={summary?.totalVisits}
          isLoading={summaryQuery.isLoading}
        />
        <SummaryCard
          label="오늘 방문"
          value={summary?.todayVisits}
          isLoading={summaryQuery.isLoading}
        />
        <SummaryCard
          label="순 IP (추정)"
          value={summary?.uniqueIpApprox}
          hint="마스킹 기준 추정치"
          isLoading={summaryQuery.isLoading}
        />
      </div>

      {summaryQuery.isError && (
        <p className="mt-3 text-sm text-red-600">
          요약 통계를 불러오지 못했습니다.
        </p>
      )}

      {/* Daily trend + device breakdown */}
      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-3">
        <VisitDailyTrendChart
          className="lg:col-span-2"
          daily={daily}
          isLoading={dailyQuery.isLoading}
          isError={dailyQuery.isError}
        />
        <VisitDeviceBreakdownChart
          breakdown={summary?.deviceBreakdown}
          isLoading={summaryQuery.isLoading}
          isError={summaryQuery.isError}
        />
      </div>

      {/* Top paths */}
      <div className="mt-4 rounded-md border border-slate-200 bg-white">
        <div className="border-b border-slate-200 px-5 py-4">
          <h2 className="text-sm font-semibold text-slate-900">상위 경로</h2>
        </div>
        {summaryQuery.isLoading ? (
          <div className="flex h-40 items-center justify-center">
            <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
          </div>
        ) : summaryQuery.isError ? (
          <div className="flex h-40 items-center justify-center">
            <p className="text-sm text-red-600">불러오지 못했습니다.</p>
          </div>
        ) : topPaths.length === 0 ? (
          <div className="flex h-40 items-center justify-center">
            <p className="text-sm text-slate-400">데이터가 없습니다.</p>
          </div>
        ) : (
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
              <tr>
                <th className="px-5 py-3 w-12">#</th>
                <th className="px-5 py-3">경로</th>
                <th className="px-5 py-3 text-right">접속 수</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {topPaths.map((row, index) => (
                <tr key={`${row.path}-${index}`} className="hover:bg-slate-50">
                  <td className="px-5 py-3 text-slate-400">{index + 1}</td>
                  <td className="px-5 py-3 font-medium text-slate-900">
                    {row.path ?? "-"}
                  </td>
                  <td className="px-5 py-3 text-right text-slate-700">
                    {formatNumber(row.count)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Recent visits */}
      <div className="mt-4 rounded-md border border-slate-200 bg-white">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <h2 className="text-sm font-semibold text-slate-900">최근 접속 로그</h2>
          <span className="text-xs text-slate-500">
            {listQuery.isLoading
              ? ""
              : `총 ${visits.length.toLocaleString()}건`}
          </span>
        </div>
        {listQuery.isLoading ? (
          <div className="flex h-64 items-center justify-center">
            <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
          </div>
        ) : listQuery.isError ? (
          <div className="flex h-64 items-center justify-center">
            <p className="text-sm text-red-600">접속 로그를 불러오지 못했습니다.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                <tr>
                  <th className="px-5 py-3">방문시각</th>
                  <th className="px-5 py-3">IP(마스킹)</th>
                  <th className="px-5 py-3">브라우저</th>
                  <th className="px-5 py-3">OS</th>
                  <th className="px-5 py-3">기기</th>
                  <th className="px-5 py-3">경로</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {visits.length === 0 ? (
                  <tr>
                    <td
                      colSpan={6}
                      className="px-5 py-10 text-center text-slate-400"
                    >
                      조회된 접속 로그가 없습니다.
                    </td>
                  </tr>
                ) : (
                  visits.map((visit) => (
                    <tr key={visit.id} className="hover:bg-slate-50">
                      <td className="px-5 py-3 whitespace-nowrap text-slate-700">
                        {formatDateTime(visit.visitedAt)}
                      </td>
                      <td className="px-5 py-3 font-mono text-xs text-slate-600">
                        {visit.memberId != null ? (
                          <Link
                            href={`/member/${visit.memberId}`}
                            className="text-brand hover:underline"
                            title="회원 상세로 이동"
                          >
                            {visit.ipMasked ?? "-"}
                          </Link>
                        ) : (
                          (visit.ipMasked ?? "-")
                        )}
                      </td>
                      <td className="px-5 py-3 text-slate-700">
                        {visit.browser ?? "-"}
                      </td>
                      <td className="px-5 py-3 text-slate-700">
                        {visit.os ?? "-"}
                      </td>
                      <td className="px-5 py-3 text-slate-700">
                        {visit.deviceType
                          ? (DEVICE_LABELS[visit.deviceType] ?? visit.deviceType)
                          : "-"}
                      </td>
                      <td className="px-5 py-3 text-slate-700">
                        {visit.path ?? "-"}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

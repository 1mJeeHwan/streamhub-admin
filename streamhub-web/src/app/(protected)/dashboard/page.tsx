"use client";

import MemberTrendChart from "@/components/dashboard/MemberTrendChart";
import SummaryCards from "@/components/dashboard/SummaryCards";
import TopContentsChart from "@/components/dashboard/TopContentsChart";
import WatchByChannelChart from "@/components/dashboard/WatchByChannelChart";

/**
 * DashboardPage is the operations overview: four summary metric cards on top,
 * followed by the daily sign-up trend and per-channel / top-content charts.
 */
export default function DashboardPage() {
  return (
    <div>
      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">대시보드</h1>
        <p className="mt-1 text-sm text-slate-500">
          회원, 콘텐츠, 시청 현황을 한눈에 확인합니다.
        </p>
      </div>

      <SummaryCards />

      <div className="mt-4 grid grid-cols-1 gap-4">
        <MemberTrendChart />
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <TopContentsChart />
          <WatchByChannelChart />
        </div>
      </div>
    </div>
  );
}

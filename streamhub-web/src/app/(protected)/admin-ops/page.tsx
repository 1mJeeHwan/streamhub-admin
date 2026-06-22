"use client";

import { useRouter } from "next/navigation";

import ActivityFeed from "@/components/dashboard/ActivityFeed";
import KpiStrip from "@/components/dashboard/KpiStrip";
import MembershipDonutChart from "@/components/dashboard/MembershipDonutChart";
import RevenueTrendChart from "@/components/dashboard/RevenueTrendChart";
import TopContentsChart from "@/components/dashboard/TopContentsChart";
import TodoQueue from "@/components/dashboard/TodoQueue";

/**
 * AdminOpsPage is the integrated operations control room: a six-card KPI strip
 * on top, then the 90-day revenue/donation trend beside the real-time activity
 * feed, a top-content + membership composition row, and finally the deep-linked
 * to-do queue. Read-only — the to-do cards link into the existing management
 * screens for action.
 */
export default function AdminOpsPage() {
  const router = useRouter();

  return (
    <div>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">
            통합 운영 대시보드
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            운영 중인 관제실을 한눈에 확인합니다.
          </p>
        </div>
        <span className="shrink-0 rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-500">
          데모 데이터 · 테스트 모드
        </span>
      </div>

      <KpiStrip />

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-3">
        <RevenueTrendChart
          className="lg:col-span-2"
          onSelect={() => router.push("/donation")}
        />
        <ActivityFeed />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <TopContentsChart onSelect={(id) => router.push(`/content/${id}`)} />
        <MembershipDonutChart onSelect={(href) => router.push(href)} />
      </div>

      <div className="mt-4">
        <TodoQueue />
      </div>
    </div>
  );
}

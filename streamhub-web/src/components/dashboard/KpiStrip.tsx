"use client";

import {
  Banknote,
  HelpCircle,
  PackageOpen,
  PackageX,
  UserPlus,
  Users,
  type LucideIcon,
} from "lucide-react";

import { useSummary1 } from "@/apis/query/dashboard/dashboard";
import type {
  DashboardSummaryResponse,
  KpiDelta,
} from "@/apis/query/streamHubAdminAPI.schemas";

import KpiCard from "./KpiCard";

interface KpiConfig {
  key: keyof DashboardSummaryResponse;
  label: string;
  icon: LucideIcon;
  iconClassName: string;
  sparkColor: string;
  unitPrefix?: string;
  unitSuffix?: string;
}

// 설계문서 도메인 언어를 유지한 KPI 6종. D1에서는 콘텐츠·시청·회원 파생값으로 채운다.
const KPIS: KpiConfig[] = [
  {
    key: "todayRevenue",
    label: "오늘 후원·매출",
    icon: Banknote,
    iconClassName: "bg-blue-50 text-brand",
    sparkColor: "#2563eb",
    unitPrefix: "₩",
  },
  {
    key: "newSubscriptions",
    label: "신규 구독",
    icon: UserPlus,
    iconClassName: "bg-emerald-50 text-emerald-600",
    sparkColor: "#10b981",
    unitSuffix: "명",
  },
  {
    key: "activeSubscribers",
    label: "활성 구독자",
    icon: Users,
    iconClassName: "bg-indigo-50 text-indigo-600",
    sparkColor: "#6366f1",
    unitSuffix: "명",
  },
  {
    key: "openOrders",
    label: "진행 중 주문",
    icon: PackageOpen,
    iconClassName: "bg-sky-50 text-sky-600",
    sparkColor: "#0ea5e9",
    unitSuffix: "건",
  },
  {
    key: "unansweredInquiry",
    label: "미답변 문의",
    icon: HelpCircle,
    iconClassName: "bg-amber-50 text-amber-600",
    sparkColor: "#f59e0b",
    unitSuffix: "건",
  },
  {
    key: "lowStock",
    label: "재고 경고",
    icon: PackageX,
    iconClassName: "bg-red-50 text-red-600",
    sparkColor: "#ef4444",
    unitSuffix: "종",
  },
];

/**
 * KpiStrip renders the six headline KPI cards at the top of the operations
 * dashboard from the `/v1/dashboard/summary` aggregate. Each card animates a
 * count-up, a period-over-period delta, and a sparkline.
 */
export default function KpiStrip() {
  const { data, isPending, isError } = useSummary1();
  const summary = data?.resultObject;

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-6">
      {KPIS.map((kpi) => (
        <KpiCard
          key={kpi.key}
          label={kpi.label}
          icon={kpi.icon}
          iconClassName={kpi.iconClassName}
          sparkColor={kpi.sparkColor}
          unitPrefix={kpi.unitPrefix}
          unitSuffix={kpi.unitSuffix}
          data={summary?.[kpi.key] as KpiDelta | undefined}
          isLoading={isPending}
          isError={isError}
        />
      ))}
    </div>
  );
}

"use client";

import {
  Eye,
  Film,
  Loader2,
  UserPlus,
  Users,
  type LucideIcon,
} from "lucide-react";

import { useSummary } from "@/apis/query/statistics/statistics";
import { formatNumber } from "@/lib/format";

interface CardConfig {
  key: "totalMembers" | "newMembers7d" | "totalViews" | "totalContents";
  label: string;
  icon: LucideIcon;
  iconClassName: string;
}

const CARDS: CardConfig[] = [
  {
    key: "totalMembers",
    label: "총 회원수",
    icon: Users,
    iconClassName: "bg-blue-50 text-brand",
  },
  {
    key: "newMembers7d",
    label: "신규 회원 (7일)",
    icon: UserPlus,
    iconClassName: "bg-emerald-50 text-emerald-600",
  },
  {
    key: "totalViews",
    label: "총 조회수",
    icon: Eye,
    iconClassName: "bg-amber-50 text-amber-600",
  },
  {
    key: "totalContents",
    label: "총 콘텐츠",
    icon: Film,
    iconClassName: "bg-indigo-50 text-indigo-600",
  },
];

/**
 * SummaryCards renders the four headline metric cards (members, new members,
 * views, contents) from the summary statistics endpoint.
 */
export default function SummaryCards() {
  const { data, isPending, isError } = useSummary();

  const summary = data?.resultObject;

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {CARDS.map((card) => {
        const Icon = card.icon;
        const value = summary?.[card.key];

        return (
          <div
            key={card.key}
            className="flex items-center justify-between rounded-md border border-slate-200 bg-white p-5"
          >
            <div>
              <p className="text-sm text-slate-500">{card.label}</p>
              <div className="mt-2 text-2xl font-semibold text-slate-900">
                {isPending ? (
                  <Loader2 className="h-6 w-6 animate-spin text-slate-300" />
                ) : isError ? (
                  <span className="text-base text-red-600">-</span>
                ) : (
                  formatNumber(value)
                )}
              </div>
            </div>
            <span
              className={`flex h-11 w-11 items-center justify-center rounded-md ${card.iconClassName}`}
            >
              <Icon className="h-5 w-5" />
            </span>
          </div>
        );
      })}
    </div>
  );
}

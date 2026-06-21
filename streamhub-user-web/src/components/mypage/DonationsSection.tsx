"use client";

import { HeartHandshake } from "lucide-react";
import clsx from "clsx";
import { useMyDonations, type MyDonationItem } from "@/lib/me";
import { formatDate } from "@/lib/format";
import { SectionShell } from "./SectionShell";

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: "진행중",
  PAUSED: "일시중지",
  CANCELED: "해지",
  CANCELLED: "해지",
};

function statusClass(status: string): string {
  if (status === "ACTIVE") return "bg-primary/15 text-primary";
  if (status === "PAUSED") return "bg-secondary/15 text-secondary";
  return "bg-card text-inactive";
}

/** Cycle label, e.g. MONTHLY → "매월", EVERY_3_MONTHS → "3개월마다". */
function cycleLabel(cycle: string | null): string {
  if (!cycle) return "";
  if (cycle === "MONTHLY") return "매월";
  const m = cycle.match(/EVERY_(\d+)_MONTHS/);
  return m ? `${m[1]}개월마다` : cycle;
}

function DonationRow({ d }: { d: MyDonationItem }) {
  return (
    <li className="px-4 py-3.5">
      <div className="flex items-center justify-between gap-3">
        <p className="ellipsis-1 text-sm font-bold text-active">{d.name || "정기후원"}</p>
        <span className={clsx("shrink-0 rounded-full px-2 py-0.5 text-[11px] font-bold", statusClass(d.status))}>
          {STATUS_LABELS[d.status] ?? d.status}
        </span>
      </div>
      <div className="mt-1 flex items-center justify-between gap-3">
        <span className="text-sm font-bold text-primary">
          {d.amount.toLocaleString()}원 {cycleLabel(d.cycle)}
        </span>
        {d.nextBillingAt && d.status === "ACTIVE" && (
          <span className="text-[11px] text-inactive">다음 결제 {formatDate(d.nextBillingAt)}</span>
        )}
      </div>
    </li>
  );
}

/** The member's recurring donations / memberships. */
export function DonationsSection({ token }: { token: string }) {
  const { data, isLoading, isError } = useMyDonations(token);
  const donations = data ?? [];

  return (
    <SectionShell
      icon={HeartHandshake}
      title="정기후원·구독"
      count={donations.length > 0 ? `${donations.length}건` : undefined}
      isLoading={isLoading}
      isError={isError}
      isEmpty={donations.length === 0}
      errorMessage="후원 현황을 불러오지 못했습니다."
      emptyIcon={HeartHandshake}
      emptyMessage="진행 중인 정기후원이 없습니다."
    >
      <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
        {donations.map((d) => (
          <DonationRow key={d.id} d={d} />
        ))}
      </ul>
    </SectionShell>
  );
}

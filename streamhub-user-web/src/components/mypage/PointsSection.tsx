"use client";

import { useState } from "react";
import { Coins } from "lucide-react";
import clsx from "clsx";
import { useMyPoints } from "@/lib/me";
import { formatDate } from "@/lib/format";
import { SectionShell } from "./SectionShell";
import { Pagination } from "@/components/Pagination";

const PAGE_SIZE = 8;

/** Member point balance + paged ledger (earn = primary, spend = point/red). */
export function PointsSection({ token }: { token: string }) {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useMyPoints(token, page, PAGE_SIZE);
  const ledger = data?.ledger.contents ?? [];

  return (
    <SectionShell
      icon={Coins}
      title="포인트"
      count={data ? `${data.balance.toLocaleString()}P` : undefined}
      isLoading={isLoading}
      isError={isError}
      isEmpty={false}
      errorMessage="포인트를 불러오지 못했습니다."
      emptyIcon={Coins}
      emptyMessage="포인트 내역이 없습니다."
    >
      <div className="overflow-hidden rounded-card border border-border/70 bg-surface">
        <div className="flex items-center justify-between border-b border-border/60 px-4 py-3.5">
          <span className="text-sm text-inactive">보유 포인트</span>
          <span className="text-lg font-extrabold text-primary">
            {(data?.balance ?? 0).toLocaleString()} P
          </span>
        </div>
        {ledger.length === 0 ? (
          <p className="px-4 py-7 text-center text-sm text-inactive">포인트 적립·사용 내역이 없습니다.</p>
        ) : (
          <ul className="divide-y divide-border/60">
            {ledger.map((row) => (
              <li key={row.id} className="flex items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <p className="ellipsis-1 text-sm text-active">{row.memo || row.type}</p>
                  <p className="mt-0.5 text-[11px] text-inactive">{formatDate(row.createdAt)}</p>
                </div>
                <span
                  className={clsx(
                    "shrink-0 text-sm font-bold tabular-nums",
                    row.amount >= 0 ? "text-primary" : "text-point",
                  )}
                >
                  {row.amount >= 0 ? "+" : ""}
                  {row.amount.toLocaleString()} P
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
      <Pagination pageNumber={page} totalPage={data?.ledger.totalPage ?? 1} onChange={setPage} />
    </SectionShell>
  );
}

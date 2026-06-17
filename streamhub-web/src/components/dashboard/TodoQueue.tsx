"use client";

import Link from "next/link";
import { ArrowRight, Loader2, type LucideIcon } from "lucide-react";
import { HelpCircle, PackageOpen, PackageX } from "lucide-react";

import { useSummary1 } from "@/apis/query/dashboard/dashboard";
import { formatNumber } from "@/lib/format";

interface TodoConfig {
  key: "openOrders" | "unansweredInquiry" | "lowStock";
  label: string;
  description: string;
  href: string;
  icon: LucideIcon;
  unitSuffix: string;
}

// 미처리 항목을 관리화면 필터로 딥링크. 카운트 0이면 회색, >0이면 amber 강조.
const TODOS: TodoConfig[] = [
  {
    key: "openOrders",
    label: "미처리 주문",
    description: "진행 중인 주문을 확인하세요.",
    href: "/content?status=DRAFT",
    icon: PackageOpen,
    unitSuffix: "건",
  },
  {
    key: "unansweredInquiry",
    label: "미답변 문의",
    description: "대기 중인 문의에 답변하세요.",
    href: "/member?status=PENDING",
    icon: HelpCircle,
    unitSuffix: "건",
  },
  {
    key: "lowStock",
    label: "재고 경고",
    description: "재고가 부족한 상품을 점검하세요.",
    href: "/content?lowStock=1",
    icon: PackageX,
    unitSuffix: "종",
  },
];

/**
 * TodoQueue surfaces the unprocessed operations work (open orders, unanswered
 * inquiries, low-stock warnings) as deep-linked cards into the relevant
 * management screens. Counts are read from the `/v1/dashboard/summary` aggregate.
 * Non-zero cards are highlighted amber ("operations blemish"); zero cards stay
 * muted.
 */
export default function TodoQueue() {
  const { data, isPending, isError } = useSummary1();
  const summary = data?.resultObject;

  return (
    <section className="rounded-md border border-slate-200 bg-white p-5">
      <h2 className="mb-4 text-sm font-semibold text-slate-900">할 일 큐</h2>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {TODOS.map((todo) => {
          const Icon = todo.icon;
          const count = summary?.[todo.key]?.current ?? 0;
          const isActive = !isPending && !isError && count > 0;

          return (
            <Link
              key={todo.key}
              href={todo.href}
              className={`group flex flex-col justify-between rounded-md border p-4 transition ${
                isActive
                  ? "border-amber-200 bg-amber-50 hover:bg-amber-100"
                  : "border-slate-200 bg-white hover:bg-slate-50"
              }`}
            >
              <div className="flex items-start justify-between">
                <div>
                  <p
                    className={`text-sm font-medium ${
                      isActive ? "text-amber-800" : "text-slate-600"
                    }`}
                  >
                    {todo.label}
                  </p>
                  <div
                    className={`mt-1 text-2xl font-semibold ${
                      isActive ? "text-amber-700" : "text-slate-400"
                    }`}
                  >
                    {isPending ? (
                      <Loader2 className="h-6 w-6 animate-spin text-slate-300" />
                    ) : isError ? (
                      <span className="text-base text-red-600">-</span>
                    ) : (
                      <>
                        {formatNumber(count)}
                        <span className="ml-0.5 text-base font-medium text-slate-400">
                          {todo.unitSuffix}
                        </span>
                      </>
                    )}
                  </div>
                </div>
                <span
                  className={`flex h-9 w-9 items-center justify-center rounded-md ${
                    isActive
                      ? "bg-amber-100 text-amber-600"
                      : "bg-slate-100 text-slate-400"
                  }`}
                >
                  <Icon className="h-4 w-4" />
                </span>
              </div>

              <div
                className={`mt-4 flex items-center gap-1 text-xs font-medium ${
                  isActive ? "text-amber-700" : "text-slate-400"
                }`}
              >
                처리하러 가기
                <ArrowRight className="h-3.5 w-3.5 transition group-hover:translate-x-0.5" />
              </div>
            </Link>
          );
        })}
      </div>
    </section>
  );
}

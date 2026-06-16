"use client";

import { Loader2 } from "lucide-react";

interface ChartCardProps {
  title: string;
  isLoading?: boolean;
  isError?: boolean;
  isEmpty?: boolean;
  className?: string;
  children: React.ReactNode;
}

/**
 * ChartCard is the shared container for dashboard charts. It renders a titled
 * white card and swaps the body for loading / error / empty states so each
 * chart component only has to describe its own visualization.
 */
export default function ChartCard({
  title,
  isLoading = false,
  isError = false,
  isEmpty = false,
  className,
  children,
}: ChartCardProps) {
  return (
    <section
      className={`rounded-md border border-slate-200 bg-white p-5 ${
        className ?? ""
      }`}
    >
      <h2 className="mb-4 text-sm font-semibold text-slate-900">{title}</h2>
      <div className="h-[320px]">
        {isLoading ? (
          <div className="flex h-full items-center justify-center">
            <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
          </div>
        ) : isError ? (
          <div className="flex h-full items-center justify-center">
            <p className="text-sm text-red-600">데이터를 불러오지 못했습니다.</p>
          </div>
        ) : isEmpty ? (
          <div className="flex h-full items-center justify-center">
            <p className="text-sm text-slate-400">표시할 데이터가 없습니다.</p>
          </div>
        ) : (
          children
        )}
      </div>
    </section>
  );
}

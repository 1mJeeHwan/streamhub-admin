"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import {
  AllCommunityModule,
  ModuleRegistry,
  themeQuartz,
  type ColDef,
  type GridReadyEvent,
  type ICellRendererParams,
  type RowClickedEvent,
  type SortChangedEvent,
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";

import type { SubscriptionListItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDate } from "@/lib/format";
import SubscriptionStatusBadge from "@/components/subscription/SubscriptionStatusBadge";
import GradeBadge from "@/components/subscription/GradeBadge";

// AG Grid v33 requires explicit module registration at module scope.
ModuleRegistry.registerModules([AllCommunityModule]);

const gridTheme = themeQuartz.withParams({
  accentColor: "#2563eb",
  borderColor: "#e2e8f0",
  headerBackgroundColor: "#f8fafc",
  headerTextColor: "#334155",
  fontFamily: "inherit",
  fontSize: 13,
  rowHeight: 48,
  headerHeight: 44,
});

interface SubscriptionGridProps {
  rows: SubscriptionListItem[];
  /** Server-side sort callback: the column field + direction (null when sorting is cleared). */
  onSortChange?: (sortBy: string | null, sortDir: "asc" | "desc" | null) => void;
}

/**
 * SubscriptionGrid renders the subscription result set with AG Grid (v33).
 * Clicking a row (or the 상세 button) navigates to the subscription detail.
 */
export default function SubscriptionGrid({ rows, onSortChange }: SubscriptionGridProps) {
  const router = useRouter();

  const columnDefs = useMemo<ColDef<SubscriptionListItem>[]>(
    () => [
      { field: "memberName", headerName: "회원", minWidth: 120, flex: 1 },
      { field: "planName", headerName: "플랜", minWidth: 120, flex: 1 },
      {
        field: "planGrade",
        headerName: "등급",
        minWidth: 100,
        cellRenderer: (params: ICellRendererParams<SubscriptionListItem>) => (
          <GradeBadge grade={params.value ?? undefined} />
        ),
      },
      {
        field: "status",
        headerName: "상태",
        minWidth: 100,
        cellRenderer: (params: ICellRendererParams<SubscriptionListItem>) => (
          <SubscriptionStatusBadge status={params.value ?? undefined} />
        ),
      },
      {
        field: "cycleNo",
        headerName: "회차",
        minWidth: 80,
        valueFormatter: (params) =>
          params.value != null ? `${params.value}회` : "-",
      },
      {
        field: "nextBillingAt",
        headerName: "다음 청구",
        minWidth: 120,
        valueFormatter: (params) => formatDate(params.value),
      },
      {
        field: "startedAt",
        headerName: "시작일",
        minWidth: 120,
        valueFormatter: (params) => formatDate(params.value),
      },
      {
        headerName: "상세",
        minWidth: 80,
        maxWidth: 90,
        sortable: false,
        filter: false,
        cellRenderer: (params: ICellRendererParams<SubscriptionListItem>) => {
          const id = params.data?.id;
          if (id == null) {
            return null;
          }
          return (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                router.push(`/subscription/${id}`);
              }}
              className="text-sm font-medium text-brand hover:underline"
            >
              상세
            </button>
          );
        },
      },
    ],
    [router],
  );

  const defaultColDef = useMemo<ColDef<SubscriptionListItem>>(
    () => ({ sortable: true, resizable: true, suppressMovable: true }),
    [],
  );

  const handleGridReady = (event: GridReadyEvent) => {
    event.api.sizeColumnsToFit();
  };

  // Server-side sort: report the active sort column so the page refetches the whole result set
  // sorted (not just the visible page). Single-column sort.
  const handleSortChanged = (event: SortChangedEvent) => {
    const sorted = event.api.getColumnState().find((col) => col.sort);
    onSortChange?.((sorted?.colId as string) ?? null, (sorted?.sort as "asc" | "desc") ?? null);
  };

  const handleRowClicked = (event: RowClickedEvent<SubscriptionListItem>) => {
    const id = event.data?.id;
    if (id != null) {
      router.push(`/subscription/${id}`);
    }
  };

  return (
    <div className="h-[560px] w-full">
      <AgGridReact<SubscriptionListItem>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        suppressCellFocus
        onGridReady={handleGridReady}
        onSortChanged={handleSortChanged}
        onRowClicked={handleRowClicked}
        overlayNoRowsTemplate="조회된 구독이 없습니다."
      />
    </div>
  );
}

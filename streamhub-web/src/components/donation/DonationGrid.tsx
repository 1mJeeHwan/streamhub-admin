"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import {
  AllCommunityModule,
  ModuleRegistry,
  themeQuartz,
  type ColDef,
  type ICellRendererParams,
  type RowClickedEvent,
  type SortChangedEvent,
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";

import type { DonationListItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDateTime, formatNumber } from "@/lib/format";
import DonationStatusBadge from "@/components/donation/DonationStatusBadge";
import DonationTypeBadge from "@/components/donation/DonationTypeBadge";

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

interface DonationGridProps {
  rows: DonationListItem[];
  /** Server-side sort callback: the column field + direction (null when sorting is cleared). */
  onSortChange?: (sortBy: string | null, sortDir: "asc" | "desc" | null) => void;
}

/**
 * DonationGrid renders the donation history result set with AG Grid (v33).
 * Every paid row is in test mode (no real PG), surfaced via the 결제구분 column.
 * A row click drills into the related subscription (recurring) or member (one-off).
 */
export default function DonationGrid({ rows, onSortChange }: DonationGridProps) {
  const router = useRouter();

  const handleRowClicked = (event: RowClickedEvent<DonationListItem>) => {
    const row = event.data;
    if (row?.subscriptionId != null) {
      router.push(`/subscription/${row.subscriptionId}`);
    } else if (row?.memberId != null) {
      router.push(`/member/${row.memberId}`);
    }
  };

  const columnDefs = useMemo<ColDef<DonationListItem>[]>(
    () => [
      { field: "memberName", headerName: "회원", minWidth: 120, flex: 1 },
      {
        field: "planName",
        headerName: "플랜",
        minWidth: 110,
        flex: 1,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "type",
        headerName: "유형",
        minWidth: 90,
        cellRenderer: (params: ICellRendererParams<DonationListItem>) => (
          <DonationTypeBadge type={params.value ?? undefined} />
        ),
      },
      {
        field: "amount",
        headerName: "금액",
        minWidth: 110,
        valueFormatter: (params) => `${formatNumber(params.value)}원`,
      },
      {
        field: "cycleNo",
        headerName: "회차",
        minWidth: 80,
        valueFormatter: (params) =>
          params.value != null ? `${params.value}회` : "-",
      },
      {
        field: "status",
        headerName: "상태",
        minWidth: 100,
        cellRenderer: (params: ICellRendererParams<DonationListItem>) => (
          <DonationStatusBadge status={params.value ?? undefined} />
        ),
      },
      {
        field: "pointAwarded",
        headerName: "적립",
        minWidth: 90,
        valueFormatter: (params) => `${formatNumber(params.value)}P`,
      },
      {
        field: "paidAt",
        headerName: "결제일시",
        minWidth: 150,
        valueFormatter: (params) => formatDateTime(params.value),
      },
      {
        field: "testMode",
        headerName: "결제구분",
        minWidth: 90,
        sortable: false,
        filter: false,
        cellRenderer: (params: ICellRendererParams<DonationListItem>) =>
          params.value === "Y" ? (
            <span className="inline-flex items-center rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
              테스트
            </span>
          ) : (
            <span className="text-xs text-slate-400">실결제</span>
          ),
      },
    ],
    [],
  );

  const defaultColDef = useMemo<ColDef<DonationListItem>>(
    () => ({ sortable: true, resizable: true, suppressMovable: true }),
    [],
  );

  // Server-side sort: report the active sort column so the page refetches the whole result set
  // sorted (not just the visible page). Single-column sort.
  const handleSortChanged = (event: SortChangedEvent) => {
    const sorted = event.api.getColumnState().find((col) => col.sort);
    onSortChange?.((sorted?.colId as string) ?? null, (sorted?.sort as "asc" | "desc") ?? null);
  };

  return (
    <div className="h-[560px] w-full">
      <AgGridReact<DonationListItem>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        suppressCellFocus
        rowClass="cursor-pointer"
        onRowClicked={handleRowClicked}
        onGridReady={(event) => event.api.sizeColumnsToFit()}
        onSortChanged={handleSortChanged}
        overlayNoRowsTemplate="조회된 후원 내역이 없습니다."
      />
    </div>
  );
}

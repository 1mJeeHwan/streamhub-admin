"use client";

import { useMemo } from "react";
import {
  AllCommunityModule,
  ModuleRegistry,
  themeQuartz,
  type ColDef,
  type GridReadyEvent,
  type ICellRendererParams,
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";

import type { PointLedgerListItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDate, formatDateTime } from "@/lib/format";
import {
  LedgerStatusBadge,
  SourceTypeBadge,
} from "@/components/point/PointBadges";

// AG Grid v33 requires explicit module registration. Registering the full
// community bundle once at module scope covers every feature used here.
ModuleRegistry.registerModules([AllCommunityModule]);

// v33 Theming API: a Tailwind-friendly Quartz theme tuned to the slate palette.
const gridTheme = themeQuartz.withParams({
  accentColor: "#2563eb",
  borderColor: "#e2e8f0",
  headerBackgroundColor: "#f8fafc",
  headerTextColor: "#334155",
  fontFamily: "inherit",
  fontSize: 13,
  rowHeight: 56,
  headerHeight: 44,
});

interface PointLedgerGridProps {
  rows: PointLedgerListItem[];
}

/** Renders a signed point delta with thousands separators (+/-). */
function formatDelta(value?: number | null): string {
  if (value == null || Number.isNaN(value)) {
    return "-";
  }
  return value > 0
    ? `+${value.toLocaleString()}`
    : value.toLocaleString();
}

/**
 * PointLedgerGrid renders the point-ledger result set with AG Grid
 * (community, v33). Ledger rows are read-only — no navigation on click.
 */
export default function PointLedgerGrid({ rows }: PointLedgerGridProps) {
  const columnDefs = useMemo<ColDef<PointLedgerListItem>[]>(
    () => [
      {
        field: "createdAt",
        headerName: "일시",
        minWidth: 150,
        valueFormatter: (params) => formatDateTime(params.value),
      },
      {
        field: "memberName",
        headerName: "회원",
        minWidth: 110,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "memberEmail",
        headerName: "이메일",
        minWidth: 160,
        flex: 1.2,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "reason",
        headerName: "사유",
        minWidth: 160,
        flex: 1.4,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "delta",
        headerName: "증감",
        minWidth: 110,
        type: "rightAligned",
        valueFormatter: (params) => formatDelta(params.value),
        cellClass: (params) => {
          const value = params.value as number | null | undefined;
          if (value == null) {
            return "";
          }
          return value > 0
            ? "text-emerald-600 font-medium"
            : "text-red-600 font-medium";
        },
      },
      {
        field: "balanceAfter",
        headerName: "잔액",
        minWidth: 110,
        type: "rightAligned",
        valueFormatter: (params) =>
          params.value != null ? Number(params.value).toLocaleString() : "0",
      },
      {
        field: "sourceType",
        headerName: "출처",
        minWidth: 90,
        cellRenderer: (params: ICellRendererParams<PointLedgerListItem>) => (
          <SourceTypeBadge value={params.value ?? undefined} />
        ),
      },
      {
        field: "status",
        headerName: "상태",
        minWidth: 90,
        cellRenderer: (params: ICellRendererParams<PointLedgerListItem>) => (
          <LedgerStatusBadge value={params.value ?? undefined} />
        ),
      },
      {
        field: "expireAt",
        headerName: "만료일",
        minWidth: 110,
        valueFormatter: (params) =>
          params.value ? formatDate(params.value) : "무기한",
      },
    ],
    [],
  );

  const defaultColDef = useMemo<ColDef<PointLedgerListItem>>(
    () => ({ sortable: true, resizable: true, suppressMovable: true }),
    [],
  );

  const handleGridReady = (event: GridReadyEvent) => {
    event.api.sizeColumnsToFit();
  };

  return (
    <div className="h-[560px] w-full">
      <AgGridReact<PointLedgerListItem>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        suppressCellFocus
        onGridReady={handleGridReady}
        overlayNoRowsTemplate="포인트 내역이 없습니다."
      />
    </div>
  );
}

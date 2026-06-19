"use client";

import { useMemo } from "react";
import {
  AllCommunityModule,
  ModuleRegistry,
  themeQuartz,
  type ColDef,
  type GridReadyEvent,
  type ICellRendererParams,
  type SortChangedEvent,
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";

import type { SmsListItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDateTime } from "@/lib/format";
import {
  SmsChannelBadge,
  SmsKindBadge,
  SmsStatusBadge,
  SmsTestModeBadge,
} from "@/components/sms/SmsBadges";

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
  rowHeight: 52,
  headerHeight: 44,
});

interface SmsGridProps {
  rows: SmsListItem[];
  /** Server-side sort callback: the column field + direction (null when sorting is cleared). */
  onSortChange?: (sortBy: string | null, sortDir: "asc" | "desc" | null) => void;
}

/**
 * SmsGrid renders the SMS send-history result set with AG Grid (community, v33).
 * Every column is read-only — send history is an immutable audit log. The kind,
 * channel and status columns render colored badges; testMode rows are flagged
 * with a 데모/테스트 발송 pill.
 */
export default function SmsGrid({ rows, onSortChange }: SmsGridProps) {
  const columnDefs = useMemo<ColDef<SmsListItem>[]>(
    () => [
      {
        field: "sentAt",
        headerName: "발송일시",
        minWidth: 150,
        valueFormatter: (params) => formatDateTime(params.value),
      },
      {
        field: "kind",
        headerName: "종류",
        minWidth: 100,
        cellRenderer: (params: ICellRendererParams<SmsListItem>) => (
          <SmsKindBadge value={params.value ?? undefined} />
        ),
      },
      {
        field: "channel",
        headerName: "채널",
        minWidth: 80,
        cellRenderer: (params: ICellRendererParams<SmsListItem>) => (
          <SmsChannelBadge value={params.value ?? undefined} />
        ),
      },
      {
        field: "toNumber",
        headerName: "수신번호",
        minWidth: 130,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "memberName",
        headerName: "회원",
        minWidth: 100,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "content",
        headerName: "내용",
        minWidth: 220,
        flex: 1.6,
        tooltipField: "content",
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "status",
        headerName: "상태",
        minWidth: 100,
        cellRenderer: (params: ICellRendererParams<SmsListItem>) => (
          <SmsStatusBadge value={params.value ?? undefined} />
        ),
      },
      {
        field: "testMode",
        headerName: "모드",
        minWidth: 130,
        sortable: false,
        cellRenderer: (params: ICellRendererParams<SmsListItem>) => (
          <SmsTestModeBadge value={params.value ?? undefined} />
        ),
      },
      {
        field: "sender",
        headerName: "발신번호",
        minWidth: 120,
        valueFormatter: (params) => params.value ?? "-",
      },
    ],
    [],
  );

  const defaultColDef = useMemo<ColDef<SmsListItem>>(
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

  return (
    <div className="h-[560px] w-full">
      <AgGridReact<SmsListItem>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        onGridReady={handleGridReady}
        onSortChanged={handleSortChanged}
        getRowId={(params) => String(params.data.id)}
        overlayNoRowsTemplate="발송 내역이 없습니다."
      />
    </div>
  );
}

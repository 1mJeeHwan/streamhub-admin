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

import type { WorshipRegistrationListItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDateTime } from "@/lib/format";
import {
  GENDER_LABEL,
  REGISTER_DEPT_LABEL,
  labelOf,
} from "@/lib/worship-status";
import WorshipStatusBadge from "@/components/worship/WorshipStatusBadge";

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

interface WorshipGridProps {
  rows: WorshipRegistrationListItem[];
  /** Server-side sort callback: the column field + direction (null when sorting is cleared). */
  onSortChange?: (sortBy: string | null, sortDir: "asc" | "desc" | null) => void;
}

/**
 * WorshipGrid renders the worship/new-family registration result set with
 * AG Grid (community, v33). It is read-only — clicking a row (or the 상세
 * button) navigates to the detail page where status transitions happen.
 */
export default function WorshipGrid({ rows, onSortChange }: WorshipGridProps) {
  const router = useRouter();

  const columnDefs = useMemo<ColDef<WorshipRegistrationListItem>[]>(
    () => [
      { field: "regNo", headerName: "접수번호", minWidth: 130, flex: 1 },
      {
        field: "name",
        headerName: "이름",
        minWidth: 100,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "gender",
        headerName: "성별",
        minWidth: 70,
        maxWidth: 80,
        valueFormatter: (params) => labelOf(GENDER_LABEL, params.value),
      },
      {
        field: "churchName",
        headerName: "교회",
        minWidth: 130,
        flex: 1,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "registerDept",
        headerName: "등록부서",
        minWidth: 100,
        valueFormatter: (params) => labelOf(REGISTER_DEPT_LABEL, params.value),
      },
      {
        field: "phone",
        headerName: "연락처",
        minWidth: 120,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "familyCount",
        headerName: "가족수",
        minWidth: 80,
        maxWidth: 90,
        valueFormatter: (params) =>
          params.value != null ? Number(params.value).toLocaleString() : "0",
        cellClass: "text-right",
      },
      {
        field: "status",
        headerName: "상태",
        minWidth: 100,
        cellRenderer: (
          params: ICellRendererParams<WorshipRegistrationListItem>,
        ) => <WorshipStatusBadge status={params.value ?? undefined} />,
      },
      {
        field: "createdAt",
        headerName: "신청일시",
        minWidth: 140,
        valueFormatter: (params) => formatDateTime(params.value),
      },
      {
        headerName: "상세",
        minWidth: 80,
        maxWidth: 90,
        sortable: false,
        filter: false,
        cellRenderer: (
          params: ICellRendererParams<WorshipRegistrationListItem>,
        ) => {
          const id = params.data?.id;
          if (id == null) {
            return null;
          }
          return (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                router.push(`/worship/${id}`);
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

  const defaultColDef = useMemo<ColDef<WorshipRegistrationListItem>>(
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
    onSortChange?.(
      (sorted?.colId as string) ?? null,
      (sorted?.sort as "asc" | "desc") ?? null,
    );
  };

  const handleRowClicked = (
    event: RowClickedEvent<WorshipRegistrationListItem>,
  ) => {
    const id = event.data?.id;
    if (id != null) {
      router.push(`/worship/${id}`);
    }
  };

  return (
    <div className="h-[560px] w-full">
      <AgGridReact<WorshipRegistrationListItem>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        suppressCellFocus
        onGridReady={handleGridReady}
        onSortChanged={handleSortChanged}
        onRowClicked={handleRowClicked}
        overlayNoRowsTemplate="조회된 신청이 없습니다."
      />
    </div>
  );
}

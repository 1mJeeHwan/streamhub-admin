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
  type SelectionChangedEvent,
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";

import type { MemberListItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDate } from "@/lib/format";
import StatusBadge from "@/components/member/StatusBadge";

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
  rowHeight: 48,
  headerHeight: 44,
});

interface MemberGridProps {
  rows: MemberListItem[];
}

/**
 * MemberGrid renders the member result set with AG Grid (community, v33).
 * Selection state is read by the parent via the grid's API on selection change;
 * this component reports selected ids through `onSelectionChanged`.
 */
export default function MemberGrid({
  rows,
  onSelectionChanged,
}: MemberGridProps & {
  onSelectionChanged: (selectedIds: number[]) => void;
}) {
  const router = useRouter();

  const columnDefs = useMemo<ColDef<MemberListItem>[]>(
    () => [
      { field: "name", headerName: "이름", minWidth: 120, flex: 1 },
      { field: "email", headerName: "이메일", minWidth: 200, flex: 1.4 },
      {
        field: "phone",
        headerName: "전화",
        minWidth: 130,
        flex: 1,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "churchName",
        headerName: "교회",
        minWidth: 140,
        flex: 1,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "regionName",
        headerName: "지역",
        minWidth: 110,
        flex: 1,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "userStatus",
        headerName: "상태",
        minWidth: 100,
        cellRenderer: (params: ICellRendererParams<MemberListItem>) => (
          <StatusBadge status={params.value} />
        ),
      },
      {
        field: "liveYn",
        headerName: "라이브",
        minWidth: 90,
        valueFormatter: (params) => (params.value === "Y" ? "가능" : "불가"),
      },
      {
        field: "createdAt",
        headerName: "가입일",
        minWidth: 120,
        valueFormatter: (params) => formatDate(params.value),
      },
      {
        headerName: "상세",
        minWidth: 90,
        sortable: false,
        filter: false,
        cellRenderer: (params: ICellRendererParams<MemberListItem>) => {
          const id = params.data?.id;
          if (id == null) {
            return null;
          }
          return (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                router.push(`/member/${id}`);
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

  const defaultColDef = useMemo<ColDef<MemberListItem>>(
    () => ({ sortable: true, resizable: true, suppressMovable: true }),
    [],
  );

  const handleGridReady = (event: GridReadyEvent) => {
    event.api.sizeColumnsToFit();
  };

  const handleSelectionChanged = (event: SelectionChangedEvent) => {
    const ids = event.api
      .getSelectedRows()
      .map((row: MemberListItem) => row.id)
      .filter((id): id is number => id != null);
    onSelectionChanged(ids);
  };

  const handleRowClicked = (event: RowClickedEvent<MemberListItem>) => {
    const id = event.data?.id;
    if (id != null) {
      router.push(`/member/${id}`);
    }
  };

  return (
    <div className="h-[560px] w-full">
      <AgGridReact<MemberListItem>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        rowSelection={{ mode: "multiRow" }}
        suppressCellFocus
        onGridReady={handleGridReady}
        onSelectionChanged={handleSelectionChanged}
        onRowClicked={handleRowClicked}
        overlayNoRowsTemplate="조회된 회원이 없습니다."
      />
    </div>
  );
}

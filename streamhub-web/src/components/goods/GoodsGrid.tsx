"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import {
  AllCommunityModule,
  ModuleRegistry,
  themeQuartz,
  type CellValueChangedEvent,
  type ColDef,
  type GridReadyEvent,
  type ICellRendererParams,
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";

import type { GoodsListItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { GoodsStatusBadge } from "@/components/goods/GoodsStatusBadge";

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

interface GoodsGridProps {
  rows: GoodsListItem[];
  /** Called with a row id whenever one of its editable cells changes. */
  onRowEdited: (id: number) => void;
}

function ThumbnailCell({ url }: { url?: string }) {
  if (!url) {
    return (
      <div className="flex h-9 w-9 items-center justify-center rounded bg-slate-100 text-[10px] text-slate-400">
        없음
      </div>
    );
  }
  return (
    // Plain <img>: thumbnails come from arbitrary storage origins (thumbnailUrl
    // is an absolute URL) and next/image would require per-host config.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={url}
      alt="썸네일"
      className="h-9 w-9 rounded object-cover"
      loading="lazy"
    />
  );
}

const moneyFormatter = (params: { value?: number | null }) =>
  params.value != null ? Number(params.value).toLocaleString() : "-";

const countFormatter = (params: { value?: number | null }) =>
  params.value != null ? Number(params.value).toLocaleString() : "0";

/**
 * GoodsGrid renders the goods result set with AG Grid (community, v33).
 * Several columns (price/stock/notiQty/soldOut/useYn) are inline-editable; each
 * edit reports the row id up via onRowEdited so the parent can batch a bulk save.
 */
export default function GoodsGrid({ rows, onRowEdited }: GoodsGridProps) {
  const router = useRouter();

  const columnDefs = useMemo<ColDef<GoodsListItem>[]>(
    () => [
      {
        headerName: "",
        field: "thumbnailUrl",
        minWidth: 60,
        maxWidth: 64,
        sortable: false,
        filter: false,
        editable: false,
        headerCheckboxSelection: false,
        cellRenderer: (params: ICellRendererParams<GoodsListItem>) => (
          <ThumbnailCell url={params.value ?? undefined} />
        ),
      },
      { field: "code", headerName: "상품코드", minWidth: 110, editable: false },
      {
        field: "name",
        headerName: "상품명",
        minWidth: 180,
        flex: 1.4,
        editable: false,
      },
      {
        field: "categoryName",
        headerName: "분류",
        minWidth: 110,
        editable: false,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "price",
        headerName: "판매가",
        minWidth: 110,
        editable: true,
        cellEditor: "agNumberCellEditor",
        cellEditorParams: { min: 0 },
        valueFormatter: moneyFormatter,
        cellClass: "text-right",
      },
      {
        field: "listPrice",
        headerName: "시중가",
        minWidth: 100,
        editable: false,
        valueFormatter: moneyFormatter,
        cellClass: "text-right",
      },
      {
        field: "stock",
        headerName: "재고",
        minWidth: 90,
        editable: true,
        cellEditor: "agNumberCellEditor",
        cellEditorParams: { min: 0 },
        valueFormatter: countFormatter,
        cellClass: "text-right",
      },
      {
        field: "notiQty",
        headerName: "통보수량",
        minWidth: 100,
        editable: true,
        cellEditor: "agNumberCellEditor",
        cellEditorParams: { min: 0 },
        valueFormatter: countFormatter,
        cellClass: "text-right",
      },
      {
        field: "soldOut",
        headerName: "품절",
        minWidth: 80,
        editable: true,
        cellEditor: "agSelectCellEditor",
        cellEditorParams: { values: ["Y", "N"] },
      },
      {
        field: "useYn",
        headerName: "판매",
        minWidth: 80,
        editable: true,
        cellEditor: "agSelectCellEditor",
        cellEditorParams: { values: ["Y", "N"] },
      },
      {
        field: "status",
        headerName: "상태",
        minWidth: 100,
        editable: false,
        cellRenderer: (params: ICellRendererParams<GoodsListItem>) => (
          <GoodsStatusBadge value={params.value ?? undefined} />
        ),
      },
      {
        field: "saleCount",
        headerName: "판매수",
        minWidth: 90,
        editable: false,
        valueFormatter: countFormatter,
        cellClass: "text-right",
      },
      {
        field: "viewCount",
        headerName: "조회",
        minWidth: 90,
        editable: false,
        valueFormatter: countFormatter,
        cellClass: "text-right",
      },
      {
        headerName: "상세",
        minWidth: 70,
        maxWidth: 80,
        sortable: false,
        filter: false,
        editable: false,
        cellRenderer: (params: ICellRendererParams<GoodsListItem>) => {
          const id = params.data?.id;
          if (id == null) {
            return null;
          }
          return (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                router.push(`/goods/${id}`);
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

  const defaultColDef = useMemo<ColDef<GoodsListItem>>(
    () => ({ sortable: true, resizable: true, suppressMovable: true }),
    [],
  );

  const handleGridReady = (event: GridReadyEvent) => {
    event.api.sizeColumnsToFit();
  };

  const handleCellValueChanged = (
    event: CellValueChangedEvent<GoodsListItem>,
  ) => {
    const id = event.data?.id;
    if (id != null) {
      onRowEdited(id);
    }
  };

  return (
    <div className="h-[560px] w-full">
      <AgGridReact<GoodsListItem>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        stopEditingWhenCellsLoseFocus
        onGridReady={handleGridReady}
        onCellValueChanged={handleCellValueChanged}
        getRowId={(params) => String(params.data.id)}
        overlayNoRowsTemplate="조회된 굿즈가 없습니다."
      />
    </div>
  );
}

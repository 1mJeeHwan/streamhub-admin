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
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";

import type { OrderListItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDateTime } from "@/lib/format";
import OrderStatusBadge from "@/components/order/OrderStatusBadge";

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

const PAY_METHOD_LABEL: Record<string, string> = {
  BANK: "무통장",
  CARD: "카드",
};

interface OrderGridProps {
  rows: OrderListItem[];
}

/**
 * OrderGrid renders the order result set with AG Grid (community, v33).
 * It is read-only — clicking a row (or the 상세 button) navigates to the
 * order detail page where status transitions happen.
 */
export default function OrderGrid({ rows }: OrderGridProps) {
  const router = useRouter();

  const columnDefs = useMemo<ColDef<OrderListItem>[]>(
    () => [
      { field: "orderNo", headerName: "주문번호", minWidth: 140, flex: 1 },
      {
        field: "orderedName",
        headerName: "주문자",
        minWidth: 100,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "receiverName",
        headerName: "받는분",
        minWidth: 100,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "itemCount",
        headerName: "상품수",
        minWidth: 80,
        maxWidth: 90,
        valueFormatter: (params) =>
          params.value != null ? Number(params.value).toLocaleString() : "0",
      },
      {
        field: "total",
        headerName: "주문합계",
        minWidth: 110,
        valueFormatter: (params) =>
          params.value != null ? Number(params.value).toLocaleString() : "0",
      },
      {
        field: "status",
        headerName: "상태",
        minWidth: 100,
        cellRenderer: (params: ICellRendererParams<OrderListItem>) => (
          <OrderStatusBadge status={params.value ?? undefined} />
        ),
      },
      {
        field: "payMethod",
        headerName: "결제수단",
        minWidth: 90,
        valueFormatter: (params) =>
          params.value ? (PAY_METHOD_LABEL[params.value] ?? params.value) : "-",
      },
      {
        field: "trackingNo",
        headerName: "운송장",
        minWidth: 120,
        flex: 1,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "orderedAt",
        headerName: "주문일시",
        minWidth: 140,
        valueFormatter: (params) => formatDateTime(params.value),
      },
      {
        headerName: "상세",
        minWidth: 80,
        maxWidth: 90,
        sortable: false,
        filter: false,
        cellRenderer: (params: ICellRendererParams<OrderListItem>) => {
          const id = params.data?.id;
          if (id == null) {
            return null;
          }
          return (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                router.push(`/order/${id}`);
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

  const defaultColDef = useMemo<ColDef<OrderListItem>>(
    () => ({ sortable: true, resizable: true, suppressMovable: true }),
    [],
  );

  const handleGridReady = (event: GridReadyEvent) => {
    event.api.sizeColumnsToFit();
  };

  const handleRowClicked = (event: RowClickedEvent<OrderListItem>) => {
    const id = event.data?.id;
    if (id != null) {
      router.push(`/order/${id}`);
    }
  };

  return (
    <div className="h-[560px] w-full">
      <AgGridReact<OrderListItem>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        suppressCellFocus
        onGridReady={handleGridReady}
        onRowClicked={handleRowClicked}
        overlayNoRowsTemplate="조회된 주문이 없습니다."
      />
    </div>
  );
}

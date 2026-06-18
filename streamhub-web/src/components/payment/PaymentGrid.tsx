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

import {
  PaymentListItemKind,
  PaymentListItemPayStatus,
  type PaymentListItem,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDateTime } from "@/lib/format";
import {
  KIND_META,
  PAY_METHOD_LABEL,
  PAY_STATUS_META,
} from "@/lib/payment-status";

/**
 * isRefundable decides whether the 환불 action shows for a receipt row.
 * Only a PAY receipt whose order is in the approved (refundable) state qualifies;
 * REFUND rows and not-yet-approved payments are excluded. The server re-validates
 * and any rejection message is surfaced in the confirm dialog.
 */
export function isRefundable(row: PaymentListItem): boolean {
  return (
    row.kind === PaymentListItemKind.PAY &&
    row.payStatus === PaymentListItemPayStatus.APPROVED &&
    row.orderId != null
  );
}

// AG Grid v33 requires explicit module registration (community bundle once).
ModuleRegistry.registerModules([AllCommunityModule]);

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

interface PaymentGridProps {
  rows: PaymentListItem[];
  /** Invoked when the 환불 action of an eligible PAY receipt is clicked. */
  onRefund: (row: PaymentListItem) => void;
}

/**
 * PaymentGrid renders the payment-history result set with AG Grid (community, v33).
 * Each row is a payment/refund receipt; clicking a row (or the 주문 button) opens
 * the related order detail, where refunds/transitions are performed.
 */
export default function PaymentGrid({ rows, onRefund }: PaymentGridProps) {
  const router = useRouter();

  const columnDefs = useMemo<ColDef<PaymentListItem>[]>(
    () => [
      {
        field: "createdAt",
        headerName: "일시",
        minWidth: 150,
        valueFormatter: (params) => formatDateTime(params.value),
      },
      {
        field: "kind",
        headerName: "구분",
        minWidth: 80,
        maxWidth: 90,
        cellRenderer: (params: ICellRendererParams<PaymentListItem>) => {
          const meta = KIND_META[params.value as PaymentListItemKind];
          if (!meta) {
            return "-";
          }
          return (
            <span
              className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${meta.className}`}
            >
              {meta.label}
            </span>
          );
        },
      },
      { field: "orderNo", headerName: "주문번호", minWidth: 140, flex: 1 },
      {
        field: "memberName",
        headerName: "회원",
        minWidth: 100,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "amount",
        headerName: "금액",
        minWidth: 110,
        type: "rightAligned",
        valueFormatter: (params) =>
          params.value != null
            ? `${Number(params.value).toLocaleString()}원`
            : "-",
      },
      {
        field: "method",
        headerName: "수단",
        minWidth: 80,
        valueFormatter: (params) =>
          params.value ? (PAY_METHOD_LABEL[params.value] ?? params.value) : "-",
      },
      {
        field: "provider",
        headerName: "PG",
        minWidth: 80,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "txnId",
        headerName: "거래번호",
        minWidth: 180,
        flex: 1,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "payStatus",
        headerName: "결제상태",
        minWidth: 100,
        cellRenderer: (params: ICellRendererParams<PaymentListItem>) => {
          const meta = PAY_STATUS_META[params.value as PaymentListItemPayStatus];
          if (!meta) {
            return "-";
          }
          return (
            <span
              className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${meta.className}`}
            >
              {meta.label}
            </span>
          );
        },
      },
      {
        headerName: "환불",
        minWidth: 80,
        maxWidth: 90,
        sortable: false,
        filter: false,
        cellRenderer: (params: ICellRendererParams<PaymentListItem>) => {
          const row = params.data;
          if (!row || !isRefundable(row)) {
            return null;
          }
          return (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                onRefund(row);
              }}
              className="text-sm font-medium text-rose-600 hover:underline"
            >
              환불
            </button>
          );
        },
      },
      {
        headerName: "주문",
        minWidth: 80,
        maxWidth: 90,
        sortable: false,
        filter: false,
        cellRenderer: (params: ICellRendererParams<PaymentListItem>) => {
          const orderId = params.data?.orderId;
          if (orderId == null) {
            return null;
          }
          return (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                router.push(`/order/${orderId}`);
              }}
              className="text-sm font-medium text-brand hover:underline"
            >
              주문
            </button>
          );
        },
      },
    ],
    [router, onRefund],
  );

  const defaultColDef = useMemo<ColDef<PaymentListItem>>(
    () => ({ sortable: true, resizable: true, suppressMovable: true }),
    [],
  );

  const handleGridReady = (event: GridReadyEvent) => {
    event.api.sizeColumnsToFit();
  };

  const handleRowClicked = (event: RowClickedEvent<PaymentListItem>) => {
    const orderId = event.data?.orderId;
    if (orderId != null) {
      router.push(`/order/${orderId}`);
    }
  };

  return (
    <div className="h-[560px] w-full">
      <AgGridReact<PaymentListItem>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        suppressCellFocus
        onGridReady={handleGridReady}
        onRowClicked={handleRowClicked}
        overlayNoRowsTemplate="조회된 결제 내역이 없습니다."
      />
    </div>
  );
}

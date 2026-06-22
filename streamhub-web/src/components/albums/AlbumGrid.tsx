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
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";

import type { AlbumListItem } from "@/apis/query/streamHubAdminAPI.schemas";
import {
  AlbumGenreBadge,
  AlbumStatusBadge,
} from "@/components/albums/AlbumStatusBadge";

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

interface AlbumGridProps {
  rows: AlbumListItem[];
}

function CoverCell({ url }: { url?: string }) {
  if (!url) {
    return (
      <div className="flex h-9 w-9 items-center justify-center rounded bg-slate-100 text-[10px] text-slate-400">
        없음
      </div>
    );
  }
  return (
    // Plain <img>: covers come from arbitrary storage origins (coverUrl is an
    // absolute URL) and next/image would require per-host config.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={url}
      alt="커버"
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
 * AlbumGrid renders the album result set with AG Grid (community, v33).
 * Rows are read-only; the rightmost column links to the album detail page.
 */
export default function AlbumGrid({ rows }: AlbumGridProps) {
  const router = useRouter();

  const columnDefs = useMemo<ColDef<AlbumListItem>[]>(
    () => [
      {
        headerName: "",
        field: "coverUrl",
        minWidth: 60,
        maxWidth: 64,
        sortable: false,
        filter: false,
        cellRenderer: (params: ICellRendererParams<AlbumListItem>) => (
          <CoverCell url={params.value ?? undefined} />
        ),
      },
      {
        field: "title",
        headerName: "앨범명",
        minWidth: 180,
        flex: 1.4,
        cellRenderer: (params: ICellRendererParams<AlbumListItem>) => {
          const id = params.data?.id;
          const title = params.value ?? "-";
          if (id == null) {
            return title;
          }
          return (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                router.push(`/albums/${id}`);
              }}
              className="font-medium text-brand hover:underline"
            >
              {title}
            </button>
          );
        },
      },
      { field: "artist", headerName: "아티스트", minWidth: 140, flex: 1 },
      {
        field: "genre",
        headerName: "장르",
        minWidth: 100,
        cellRenderer: (params: ICellRendererParams<AlbumListItem>) => (
          <AlbumGenreBadge value={params.value ?? undefined} />
        ),
      },
      {
        field: "trackCount",
        headerName: "트랙수",
        minWidth: 90,
        valueFormatter: countFormatter,
        cellClass: "text-right",
      },
      {
        field: "price",
        headerName: "가격",
        minWidth: 110,
        valueFormatter: moneyFormatter,
        cellClass: "text-right",
      },
      {
        field: "releaseDate",
        headerName: "발매일",
        minWidth: 120,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "status",
        headerName: "상태",
        minWidth: 100,
        cellRenderer: (params: ICellRendererParams<AlbumListItem>) => (
          <AlbumStatusBadge value={params.value ?? undefined} />
        ),
      },
      {
        headerName: "상세",
        minWidth: 70,
        maxWidth: 80,
        sortable: false,
        filter: false,
        cellRenderer: (params: ICellRendererParams<AlbumListItem>) => {
          const id = params.data?.id;
          if (id == null) {
            return null;
          }
          return (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                router.push(`/albums/${id}`);
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

  const defaultColDef = useMemo<ColDef<AlbumListItem>>(
    () => ({ sortable: true, resizable: true, suppressMovable: true }),
    [],
  );

  const handleGridReady = (event: GridReadyEvent) => {
    event.api.sizeColumnsToFit();
  };

  return (
    <div className="h-[560px] w-full">
      <AgGridReact<AlbumListItem>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        onGridReady={handleGridReady}
        getRowId={(params) => String(params.data.id)}
        overlayNoRowsTemplate="조회된 앨범이 없습니다."
      />
    </div>
  );
}

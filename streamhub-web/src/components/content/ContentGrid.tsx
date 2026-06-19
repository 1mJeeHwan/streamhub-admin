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

import type { ContentListItem } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDate, formatDuration } from "@/lib/format";
import {
  ContentStatusBadge,
  TypeBadge,
} from "@/components/content/ContentBadges";
import HashtagChips from "@/components/content/HashtagChips";

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

interface ContentGridProps {
  rows: ContentListItem[];
  /** Server-side sort callback: the column field + direction (null when sorting is cleared). */
  onSortChange?: (sortBy: string | null, sortDir: "asc" | "desc" | null) => void;
}

function ThumbnailCell({ url }: { url?: string }) {
  if (!url) {
    return (
      <div className="flex h-9 w-16 items-center justify-center rounded bg-slate-100 text-[10px] text-slate-400">
        없음
      </div>
    );
  }
  return (
    // Using a plain <img>: thumbnails come from arbitrary storage origins and
    // next/image would require per-host config.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={url}
      alt="썸네일"
      className="h-9 w-16 rounded object-cover"
      loading="lazy"
    />
  );
}

/**
 * ContentGrid renders the content result set with AG Grid (community, v33).
 * Clicking a row (or the 상세 button) navigates to the content detail page.
 */
export default function ContentGrid({ rows, onSortChange }: ContentGridProps) {
  const router = useRouter();

  const columnDefs = useMemo<ColDef<ContentListItem>[]>(
    () => [
      {
        headerName: "썸네일",
        field: "thumbnailUrl",
        minWidth: 90,
        maxWidth: 100,
        sortable: false,
        filter: false,
        cellRenderer: (params: ICellRendererParams<ContentListItem>) => (
          <ThumbnailCell url={params.value ?? undefined} />
        ),
      },
      { field: "title", headerName: "제목", minWidth: 180, flex: 1.6 },
      {
        field: "type",
        headerName: "유형",
        minWidth: 90,
        cellRenderer: (params: ICellRendererParams<ContentListItem>) => (
          <TypeBadge value={params.value ?? undefined} />
        ),
      },
      {
        field: "status",
        headerName: "상태",
        minWidth: 90,
        cellRenderer: (params: ICellRendererParams<ContentListItem>) => (
          <ContentStatusBadge value={params.value ?? undefined} />
        ),
      },
      {
        field: "channelName",
        headerName: "채널",
        minWidth: 120,
        flex: 1,
        valueFormatter: (params) => params.value ?? "-",
      },
      {
        field: "hashtags",
        headerName: "해시태그",
        minWidth: 160,
        flex: 1.2,
        sortable: false,
        cellRenderer: (params: ICellRendererParams<ContentListItem>) => (
          <HashtagChips tags={params.value ?? undefined} />
        ),
      },
      {
        field: "viewCount",
        headerName: "조회수",
        minWidth: 90,
        valueFormatter: (params) =>
          params.value != null ? Number(params.value).toLocaleString() : "0",
      },
      {
        field: "durationSec",
        headerName: "길이",
        minWidth: 80,
        valueFormatter: (params) => formatDuration(params.value),
      },
      {
        field: "createdAt",
        headerName: "등록일",
        minWidth: 120,
        valueFormatter: (params) => formatDate(params.value),
      },
      {
        headerName: "상세",
        minWidth: 80,
        maxWidth: 90,
        sortable: false,
        filter: false,
        cellRenderer: (params: ICellRendererParams<ContentListItem>) => {
          const id = params.data?.id;
          if (id == null) {
            return null;
          }
          return (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                router.push(`/content/${id}`);
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

  const defaultColDef = useMemo<ColDef<ContentListItem>>(
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

  const handleRowClicked = (event: RowClickedEvent<ContentListItem>) => {
    const id = event.data?.id;
    if (id != null) {
      router.push(`/content/${id}`);
    }
  };

  return (
    <div className="h-[560px] w-full">
      <AgGridReact<ContentListItem>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        suppressCellFocus
        onGridReady={handleGridReady}
        onSortChanged={handleSortChanged}
        onRowClicked={handleRowClicked}
        overlayNoRowsTemplate="조회된 콘텐츠가 없습니다."
      />
    </div>
  );
}

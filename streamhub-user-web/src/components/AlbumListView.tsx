"use client";

import { useEffect, useState } from "react";
import { Disc3 } from "lucide-react";
import clsx from "clsx";
import { GENRE_LABELS, GENRE_ORDER, useAlbums, type AlbumGenre } from "@/lib/albums";
import { useUrlSearch } from "@/lib/useUrlSearch";
import { AlbumGrid } from "./AlbumGrid";
import { SearchBar } from "./SearchBar";
import { Pagination } from "./Pagination";
import { CardSkeletonGrid, EmptyState, ErrorState } from "./States";

const PAGE_SIZE = 12;

/** Album list page: genre filter chips + URL-synced keyword search + cover grid + pagination. */
export function AlbumListView() {
  const { keyword, setKeyword, debounced } = useUrlSearch();
  const [genre, setGenre] = useState<AlbumGenre | undefined>(undefined);
  const [page, setPage] = useState(0);

  // A new search or genre change always restarts at the first page.
  useEffect(() => {
    setPage(0);
  }, [debounced, genre]);

  const params = { genre, keyword: debounced || undefined, page, size: PAGE_SIZE };
  const { data, isLoading, isError, error, isPlaceholderData, refetch } = useAlbums(params);

  return (
    <section className="animate-fade-up pt-4">
      <div className="flex items-center gap-2 px-5">
        <Disc3 className="h-6 w-6 text-primary" />
        <h1 className="text-2xl font-bold tracking-tight">음반</h1>
      </div>
      <p className="mt-1 px-5 text-sm text-inactive">찬양 음반을 둘러보고 30초 미리듣기로 들어보세요.</p>

      <div className="px-5 pb-2 pt-4">
        <SearchBar value={keyword} onChange={setKeyword} placeholder="음반·아티스트 검색" />
      </div>

      <div className="hrow px-5 pb-3 pt-1">
        <button className="pill" data-active={genre === undefined} onClick={() => setGenre(undefined)}>
          전체
        </button>
        {GENRE_ORDER.map((g) => (
          <button key={g} className="pill" data-active={genre === g} onClick={() => setGenre(g)}>
            {GENRE_LABELS[g]}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="px-5">
          <CardSkeletonGrid square count={PAGE_SIZE} />
        </div>
      ) : isError ? (
        <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />
      ) : !data || data.contents.length === 0 ? (
        <EmptyState message={debounced ? `'${debounced}' 검색 결과가 없습니다.` : "음반이 없습니다."} />
      ) : (
        <div className={clsx("transition-opacity", isPlaceholderData && "opacity-60")}>
          <p className="px-5 pb-3 text-sm text-inactive">
            <span className="font-bold text-active">{data.totalCount}</span>장의 음반
          </p>
          <AlbumGrid items={data.contents} />
          <Pagination pageNumber={page} totalPage={data.totalPage} onChange={setPage} />
        </div>
      )}
    </section>
  );
}

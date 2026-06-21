"use client";

import { useState } from "react";
import Link from "next/link";
import { Disc, Headphones } from "lucide-react";
import { useMyAlbums } from "@/lib/me";
import { formatDate } from "@/lib/format";
import { SectionShell } from "./SectionShell";
import { CoverThumb } from "./CoverThumb";
import { Pagination } from "@/components/Pagination";

const PAGE_SIZE = 8;

/** Albums from the member's PAID orders, paged, each linking to the album detail (전체듣기). */
export function PurchasedAlbumsSection({ token }: { token: string }) {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useMyAlbums(token, page, PAGE_SIZE);
  const albums = data?.contents ?? [];

  return (
    <SectionShell
      icon={Disc}
      title="구매한 음반"
      count={data && data.totalCount > 0 ? `${data.totalCount}장` : undefined}
      isLoading={isLoading}
      isError={isError}
      isEmpty={albums.length === 0}
      errorMessage="구매한 음반을 불러오지 못했습니다."
      emptyIcon={Disc}
      emptyMessage="아직 구매한 음반이 없습니다."
    >
      <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
        {albums.map((album) => (
          <li key={album.albumId} className="flex items-center gap-3 px-4 py-3">
            <CoverThumb src={album.coverUrl} alt={album.title} />
            <div className="min-w-0 flex-1">
              <p className="ellipsis-1 text-sm font-bold text-active">{album.title}</p>
              <p className="ellipsis-1 text-xs text-inactive">{album.artist}</p>
              <p className="mt-0.5 text-[11px] text-inactive">{formatDate(album.purchasedAt)} 구매</p>
            </div>
            <Link
              href={`/albums/${album.albumId}`}
              className="flex shrink-0 items-center gap-1 rounded-full border border-border px-2.5 py-1.5 text-[11px] font-semibold text-primary active:bg-card"
            >
              <Headphones className="h-3.5 w-3.5" />
              전체듣기
            </Link>
          </li>
        ))}
      </ul>
      <Pagination pageNumber={page} totalPage={data?.totalPage ?? 1} onChange={setPage} />
    </SectionShell>
  );
}

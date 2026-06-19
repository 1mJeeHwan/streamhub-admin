"use client";

import { useEffect, useMemo, useState } from "react";
import { useBanners, useContents } from "@/lib/queries";
import { useUrlSearch } from "@/lib/useUrlSearch";
import type { BannerItem, ContentType } from "@/lib/types";
import { ContentGrid } from "./ContentGrid";
import { SearchBar } from "./SearchBar";
import { Pagination } from "./Pagination";
import { AdBanner, type AdSlide } from "./AdBanner";
import { CardSkeletonGrid, EmptyState, ErrorState } from "./States";

const PAGE_SIZE = 12;

/** Gradient cycle for image-less promo banners (managed in admin, rendered as text promos). */
const BANNER_GRADIENTS = [
  "bg-gradient-to-br from-primary to-secondary",
  "bg-gradient-to-br from-secondary to-point",
];

/** Map an admin banner row to an AdBanner slide (gradient by order; falls back to the tab route). */
function toSlide(banner: BannerItem, index: number, fallbackHref: string): AdSlide {
  return {
    title: banner.title,
    subtitle: banner.subtitle ?? "",
    href: banner.linkUrl || fallbackHref,
    gradient: BANNER_GRADIENTS[index % BANNER_GRADIENTS.length],
    imageUrl: banner.imageUrl,
  };
}

/** Shared list page for video (VIDEO) and music (SOUND): URL-synced search + grid + pagination. */
export function MediaListView({
  type,
  title,
  searchPlaceholder,
}: {
  type: ContentType;
  title: string;
  searchPlaceholder: string;
}) {
  const { keyword, setKeyword, debounced } = useUrlSearch();
  const [page, setPage] = useState(0);

  const fallbackHref = type === "SOUND" ? "/music" : "/video";
  const bannerQuery = useBanners(type);
  const slides = useMemo(
    () => (bannerQuery.data ?? []).map((b, i) => toSlide(b, i, fallbackHref)),
    [bannerQuery.data, fallbackHref],
  );

  // A new search always restarts at the first page.
  useEffect(() => {
    setPage(0);
  }, [debounced]);

  const params = { type, keyword: debounced || undefined, pageNumber: page, pageSize: PAGE_SIZE };
  const { data, isLoading, isError, error, isPlaceholderData, refetch } = useContents(params);

  return (
    <section className="animate-fade-up pt-4">
      <h1 className="px-20px text-24px font-bold leading-30px tracking-tight">{title}</h1>
      {slides.length > 0 && (
        <div className="pt-4">
          <AdBanner slides={slides} />
        </div>
      )}
      <div className="px-5 pb-2 pt-4">
        <SearchBar value={keyword} onChange={setKeyword} placeholder={searchPlaceholder} />
      </div>

      {isLoading ? (
        <div className="px-5">
          <CardSkeletonGrid square={type === "SOUND"} count={PAGE_SIZE} />
        </div>
      ) : isError ? (
        <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />
      ) : !data || data.contents.length === 0 ? (
        <EmptyState message={debounced ? `'${debounced}' 검색 결과가 없습니다.` : "콘텐츠가 없습니다."} />
      ) : (
        <div className={isPlaceholderData ? "opacity-60 transition-opacity" : "transition-opacity"}>
          {debounced && (
            <p className="px-5 pb-3 text-sm text-inactive">
              <span className="font-bold text-active">{data.totalCount}</span>건의 결과
            </p>
          )}
          <ContentGrid items={data.contents} />
          <Pagination pageNumber={page} totalPage={data.totalPage} onChange={setPage} />
        </div>
      )}
    </section>
  );
}

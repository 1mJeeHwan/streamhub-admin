"use client";

import { useEffect, useState } from "react";
import { ChevronLeft } from "lucide-react";
import { useChannels, useContents } from "@/lib/queries";
import { useAlbums, GENRE_LABELS, type AlbumGenre, type AlbumSortBy } from "@/lib/albums";
import { usePlaylists } from "@/lib/playlists";
import { useUrlSearch } from "@/lib/useUrlSearch";
import type { ContentSortBy, PublicChannel } from "@/lib/api";
import type { BannerTarget, ContentType } from "@/lib/types";
import { ContentContainer } from "./ContentContainer";
import { BannerHero } from "./BannerHero";
import { ItemCarousel } from "./ItemCarousel";
import { ContentCard } from "./ContentCard";
import { AlbumCard } from "./AlbumCard";
import { PlaylistCard } from "./PlaylistCard";
import { ChannelCard } from "./ChannelCard";
import { ContentGrid } from "./ContentGrid";
import { SearchBar } from "./SearchBar";
import { Pagination } from "./Pagination";
import { CardSkeletonGrid, EmptyState, ErrorState } from "./States";

const PAGE_SIZE = 12;
const ROW_SIZE = 12;

/** Album genres surfaced as their own carousels on the music page (in display order). */
const MUSIC_GENRE_ROWS: AlbumGenre[] = ["WORSHIP", "CCM", "GOSPEL", "HYMN"];

/** A row of contents (SOUND/VIDEO) for one sort, rendered as a horizontal carousel. */
function ContentRow({
  title,
  type,
  sortBy,
}: {
  title: string;
  type: ContentType;
  sortBy: ContentSortBy;
}) {
  const { data, isLoading } = useContents({
    type,
    sortBy,
    sortDir: "desc",
    pageNumber: 0,
    pageSize: ROW_SIZE,
  });
  const items = data?.contents ?? [];

  if (isLoading) {
    return (
      <ContentContainer title={title}>
        <ItemCarousel>
          {Array.from({ length: 4 }).map((_, i) => (
            <ItemCarousel.ItemWrapper key={i} width={152}>
              <div className={`skeleton ${type === "SOUND" ? "aspect-square" : "aspect-video"} rounded-card`} />
              <div className="skeleton mt-10px h-4 w-4/5 rounded" />
            </ItemCarousel.ItemWrapper>
          ))}
        </ItemCarousel>
      </ContentContainer>
    );
  }
  if (items.length === 0) return null;

  return (
    <ContentContainer title={title}>
      <ItemCarousel>
        {items.map((item) => (
          <ItemCarousel.ItemWrapper key={item.id}>
            <ContentCard item={item} size="md" />
          </ItemCarousel.ItemWrapper>
        ))}
      </ItemCarousel>
    </ContentContainer>
  );
}

/** Curated playlists carousel for the music tab. */
function PlaylistRow() {
  const { data, isLoading } = usePlaylists();
  const items = data ?? [];

  if (isLoading) {
    return (
      <ContentContainer title="플레이리스트">
        <ItemCarousel>
          {Array.from({ length: 4 }).map((_, i) => (
            <ItemCarousel.ItemWrapper key={i} width={152}>
              <div className="skeleton aspect-square rounded-card" />
              <div className="skeleton mt-10px h-4 w-4/5 rounded" />
            </ItemCarousel.ItemWrapper>
          ))}
        </ItemCarousel>
      </ContentContainer>
    );
  }
  if (items.length === 0) return null;

  return (
    <ContentContainer title="플레이리스트">
      <ItemCarousel>
        {items.map((playlist) => (
          <ItemCarousel.ItemWrapper key={playlist.id}>
            <PlaylistCard item={playlist} size="md" />
          </ItemCarousel.ItemWrapper>
        ))}
      </ItemCarousel>
    </ContentContainer>
  );
}

/** A row of albums for one sort (인기/최신), linking to the sorted full list via 더보기. */
function AlbumSortRow({
  title,
  sortBy,
}: {
  title: string;
  sortBy: AlbumSortBy;
}) {
  const { data, isLoading } = useAlbums({ sortBy, sortDir: "desc", pageNumber: 0, pageSize: ROW_SIZE });
  const items = data?.contents ?? [];
  const moreHref = `/albums?sort=${sortBy}`;

  if (isLoading) {
    return (
      <ContentContainer title={title} moreHref={moreHref}>
        <ItemCarousel>
          {Array.from({ length: 4 }).map((_, i) => (
            <ItemCarousel.ItemWrapper key={i} width={152}>
              <div className="skeleton aspect-square rounded-card" />
              <div className="skeleton mt-10px h-4 w-4/5 rounded" />
            </ItemCarousel.ItemWrapper>
          ))}
        </ItemCarousel>
      </ContentContainer>
    );
  }
  if (items.length === 0) return null;

  return (
    <ContentContainer title={title} moreHref={moreHref}>
      <ItemCarousel>
        {items.map((album) => (
          <ItemCarousel.ItemWrapper key={album.id}>
            <AlbumCard item={album} size="md" />
          </ItemCarousel.ItemWrapper>
        ))}
      </ItemCarousel>
    </ContentContainer>
  );
}

/** A row of albums for one genre, linking to the full filtered list via 더보기. */
function AlbumGenreRow({ genre }: { genre: AlbumGenre }) {
  const { data, isLoading } = useAlbums({ genre, pageNumber: 0, pageSize: ROW_SIZE });
  const items = data?.contents ?? [];

  if (isLoading) {
    return (
      <ContentContainer title={GENRE_LABELS[genre]} moreHref={`/albums?genre=${genre}`}>
        <ItemCarousel>
          {Array.from({ length: 4 }).map((_, i) => (
            <ItemCarousel.ItemWrapper key={i} width={152}>
              <div className="skeleton aspect-square rounded-card" />
              <div className="skeleton mt-10px h-4 w-4/5 rounded" />
            </ItemCarousel.ItemWrapper>
          ))}
        </ItemCarousel>
      </ContentContainer>
    );
  }
  if (items.length === 0) return null;

  return (
    <ContentContainer title={GENRE_LABELS[genre]} moreHref={`/albums?genre=${genre}`}>
      <ItemCarousel>
        {items.map((album) => (
          <ItemCarousel.ItemWrapper key={album.id}>
            <AlbumCard item={album} size="md" />
          </ItemCarousel.ItemWrapper>
        ))}
      </ItemCarousel>
    </ContentContainer>
  );
}

/** Channel-browse carousel: tap a channel tile to filter the list to that channel. */
function ChannelDirectoryRow({
  type,
  selectedId,
  onSelect,
}: {
  type: ContentType;
  selectedId: number | null;
  onSelect: (channel: PublicChannel) => void;
}) {
  const { data, isLoading } = useChannels({ type, limit: 12 });
  const channels = data ?? [];

  if (isLoading) {
    return (
      <ContentContainer title="채널">
        <ItemCarousel>
          {Array.from({ length: 5 }).map((_, i) => (
            <ItemCarousel.ItemWrapper key={i} width={132}>
              <div className="skeleton aspect-square rounded-card" />
              <div className="skeleton mt-10px h-4 w-4/5 rounded" />
            </ItemCarousel.ItemWrapper>
          ))}
        </ItemCarousel>
      </ContentContainer>
    );
  }
  if (channels.length === 0) return null;

  return (
    <ContentContainer title="채널">
      <ItemCarousel>
        {channels.map((channel) => (
          <ItemCarousel.ItemWrapper key={channel.id}>
            <ChannelCard
              channel={channel}
              active={channel.id === selectedId}
              onSelect={() => onSelect(channel)}
            />
          </ItemCarousel.ItemWrapper>
        ))}
      </ItemCarousel>
    </ContentContainer>
  );
}

/** Paginated grid of one channel's content, with a header to return to all channels. */
function ChannelContentList({
  type,
  channel,
  onReset,
}: {
  type: ContentType;
  channel: PublicChannel;
  onReset: () => void;
}) {
  const [page, setPage] = useState(0);
  useEffect(() => {
    setPage(0);
  }, [channel.id]);

  const { data, isLoading, isError, error, isPlaceholderData, refetch } = useContents({
    type,
    channelId: channel.id,
    pageNumber: page,
    pageSize: PAGE_SIZE,
  });

  return (
    <div className="pt-2">
      <button
        type="button"
        onClick={onReset}
        className="mx-5 mb-1 inline-flex items-center gap-1 text-13px font-medium text-inactive hover:text-active"
      >
        <ChevronLeft className="h-4 w-4" />
        전체 채널
      </button>
      <h2 className="px-5 pb-2 text-20px font-bold leading-tight text-active">{channel.name}</h2>

      {isLoading ? (
        <div className="px-5 pt-1">
          <CardSkeletonGrid square={type === "SOUND"} count={PAGE_SIZE} />
        </div>
      ) : isError ? (
        <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />
      ) : !data || data.contents.length === 0 ? (
        <EmptyState message="이 채널의 콘텐츠가 없습니다." />
      ) : (
        <div className={isPlaceholderData ? "opacity-60 transition-opacity" : "transition-opacity"}>
          <p className="px-5 pb-3 text-sm text-inactive">
            <span className="font-bold text-active">{data.totalCount}</span>개의 콘텐츠
          </p>
          <ContentGrid items={data.contents} />
          <Pagination pageNumber={page} totalPage={data.totalPage} onChange={setPage} />
        </div>
      )}
    </div>
  );
}

/** Flat, paginated search results (shown only while a keyword is active). */
function SearchResults({ type, keyword }: { type: ContentType; keyword: string }) {
  const [page, setPage] = useState(0);
  useEffect(() => {
    setPage(0);
  }, [keyword]);

  const { data, isLoading, isError, error, isPlaceholderData, refetch } = useContents({
    type,
    keyword,
    pageNumber: page,
    pageSize: PAGE_SIZE,
  });

  if (isLoading) {
    return (
      <div className="px-5 pt-3">
        <CardSkeletonGrid square={type === "SOUND"} count={PAGE_SIZE} />
      </div>
    );
  }
  if (isError) {
    return <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />;
  }
  if (!data || data.contents.length === 0) {
    return <EmptyState message={`'${keyword}' 검색 결과가 없습니다.`} />;
  }
  return (
    <div className={isPlaceholderData ? "opacity-60 transition-opacity" : "transition-opacity"}>
      <p className="px-5 pb-3 pt-1 text-sm text-inactive">
        <span className="font-bold text-active">{data.totalCount}</span>건의 결과
      </p>
      <ContentGrid items={data.contents} />
      <Pagination pageNumber={page} totalPage={data.totalPage} onChange={setPage} />
    </div>
  );
}

/**
 * Sectioned media browse for music (SOUND) and video (VIDEO). With no search keyword it shows
 * discovery sections (베스트/최신 + 음악은 장르별 앨범 캐러셀, 영상은 채널 캐러셀); typing a keyword
 * switches to a flat grid, and selecting a channel filters to that channel. Sections reuse the
 * ng-front-ported ContentContainer/ItemCarousel pattern.
 */
export function MediaBrowse({
  type,
  title,
  searchPlaceholder,
  showAlbumGenres = false,
  showChannels = false,
  bannerTarget,
}: {
  type: ContentType;
  title: string;
  searchPlaceholder: string;
  showAlbumGenres?: boolean;
  showChannels?: boolean;
  bannerTarget?: BannerTarget;
}) {
  const { keyword, setKeyword, debounced } = useUrlSearch();
  const [channel, setChannel] = useState<PublicChannel | null>(null);

  return (
    <section className="animate-fade-up">
      {bannerTarget && <BannerHero target={bannerTarget} />}
      <h1 className="px-20px pt-4 text-24px font-bold leading-30px tracking-tight">{title}</h1>
      <div className="px-5 pb-1 pt-4">
        <SearchBar value={keyword} onChange={setKeyword} placeholder={searchPlaceholder} />
      </div>

      {debounced ? (
        <SearchResults type={type} keyword={debounced} />
      ) : channel ? (
        <ChannelContentList type={type} channel={channel} onReset={() => setChannel(null)} />
      ) : (
        <>
          <ContentRow title="베스트" type={type} sortBy="viewCount" />
          <ContentRow title="최신" type={type} sortBy="createdAt" />
          {showChannels && (
            <ChannelDirectoryRow type={type} selectedId={null} onSelect={setChannel} />
          )}
          {showAlbumGenres && (
            <>
              <PlaylistRow />
              <AlbumSortRow title="인기 앨범" sortBy="viewCount" />
              <AlbumSortRow title="최신 앨범" sortBy="releaseDate" />
              {MUSIC_GENRE_ROWS.map((genre) => (
                <AlbumGenreRow key={genre} genre={genre} />
              ))}
            </>
          )}
        </>
      )}
    </section>
  );
}

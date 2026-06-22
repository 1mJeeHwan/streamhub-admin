"use client";

import Link from "next/link";
import { Coins, Ticket, ChevronRight } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { useAlbums } from "@/lib/albums";
import { useGoods } from "@/lib/goods";
import { useCampaigns } from "@/lib/campaigns";
import { useMyPoints, useMyCoupons } from "@/lib/me";
import { ContentContainer } from "./ContentContainer";
import { ItemCarousel } from "./ItemCarousel";
import { AlbumCard } from "./AlbumCard";
import { GoodsCard } from "./GoodsCard";
import { CampaignCard } from "./CampaignCard";

const ROW = 10;

/** Reusable square-card skeleton row while a store section loads. */
function CarouselSkeleton({ wide = false }: { wide?: boolean }) {
  return (
    <ItemCarousel>
      {Array.from({ length: 4 }).map((_, i) => (
        <ItemCarousel.ItemWrapper key={i} width={wide ? 260 : 152}>
          <div className={`skeleton ${wide ? "aspect-video" : "aspect-square"} rounded-card`} />
          <div className="skeleton mt-10px h-4 w-4/5 rounded" />
        </ItemCarousel.ItemWrapper>
      ))}
    </ItemCarousel>
  );
}

/**
 * Wallet summary at the top of the store: the member's point balance + unused coupon count, a
 * one-tap path from "shopping" to "spend". Token-gated — logged-out members see a login nudge.
 */
function WalletStrip() {
  const { token } = useAuth();
  const { data: points } = useMyPoints(token, 0, 1);
  const { data: coupons } = useMyCoupons(token);

  if (!token) {
    return (
      <Link
        href="/login"
        className="flex items-center justify-between rounded-card bg-card px-4 py-3 text-13px font-medium text-active"
      >
        로그인하고 포인트·쿠폰을 확인하세요
        <ChevronRight className="h-4 w-4 text-inactive" />
      </Link>
    );
  }

  const balance = points?.balance ?? 0;
  const unused = coupons?.filter((c) => !c.used).length ?? 0;

  return (
    <div className="grid grid-cols-2 gap-2.5">
      <Link href="/mypage" className="flex items-center gap-2.5 rounded-card bg-card px-4 py-3">
        <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-primary/15 text-primary">
          <Coins className="h-5 w-5" />
        </span>
        <span className="min-w-0">
          <span className="block text-11px font-medium text-inactive">내 포인트</span>
          <span className="block text-15px font-bold text-active">{balance.toLocaleString()}P</span>
        </span>
      </Link>
      <Link href="/mypage" className="flex items-center gap-2.5 rounded-card bg-card px-4 py-3">
        <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-primary/15 text-primary">
          <Ticket className="h-5 w-5" />
        </span>
        <span className="min-w-0">
          <span className="block text-11px font-medium text-inactive">쿠폰</span>
          <span className="block text-15px font-bold text-active">{unused}장</span>
        </span>
      </Link>
    </div>
  );
}

/** Featured events/promotions row (16:9 banners). */
function CampaignStoreRow() {
  const { data, isLoading } = useCampaigns({ pageNumber: 0, pageSize: ROW });
  const items = data?.contents ?? [];
  if (isLoading) {
    return (
      <ContentContainer title="이벤트" moreHref="/campaigns">
        <CarouselSkeleton wide />
      </ContentContainer>
    );
  }
  if (items.length === 0) return null;
  return (
    <ContentContainer title="이벤트" moreHref="/campaigns">
      <ItemCarousel>
        {items.map((item) => (
          <ItemCarousel.ItemWrapper key={item.id} width={260}>
            <CampaignCard item={item} />
          </ItemCarousel.ItemWrapper>
        ))}
      </ItemCarousel>
    </ContentContainer>
  );
}

/** Popular albums row (by play count), 더보기 into the full album catalog. */
function AlbumStoreRow() {
  const { data, isLoading } = useAlbums({
    sortBy: "viewCount",
    sortDir: "desc",
    pageNumber: 0,
    pageSize: ROW,
  });
  const items = data?.contents ?? [];
  if (isLoading) {
    return (
      <ContentContainer title="인기 음반" moreHref="/albums">
        <CarouselSkeleton />
      </ContentContainer>
    );
  }
  if (items.length === 0) return null;
  return (
    <ContentContainer title="인기 음반" moreHref="/albums">
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

/** Goods row, 더보기 into the full goods shop. */
function GoodsStoreRow() {
  const { data, isLoading } = useGoods({ pageNumber: 0, pageSize: ROW });
  const items = data?.contents ?? [];
  if (isLoading) {
    return (
      <ContentContainer title="굿즈샵" moreHref="/goods">
        <CarouselSkeleton />
      </ContentContainer>
    );
  }
  if (items.length === 0) return null;
  return (
    <ContentContainer title="굿즈샵" moreHref="/goods">
      <ItemCarousel>
        {items.map((item) => (
          <ItemCarousel.ItemWrapper key={item.id} width={152}>
            <GoodsCard item={item} />
          </ItemCarousel.ItemWrapper>
        ))}
      </ItemCarousel>
    </ContentContainer>
  );
}

/**
 * Store hub: a single shopping destination consolidating events, albums, and goods — previously
 * scattered as separate app-bar icons. A wallet strip surfaces the member's points/coupons, and
 * each section drills into its existing full catalog page via 더보기.
 */
export function StoreHub() {
  return (
    <section className="animate-fade-up pb-4 pt-4">
      <h1 className="px-20px text-24px font-bold leading-30px tracking-tight">스토어</h1>
      <div className="px-5 pt-4">
        <WalletStrip />
      </div>
      <CampaignStoreRow />
      <AlbumStoreRow />
      <GoodsStoreRow />
    </section>
  );
}

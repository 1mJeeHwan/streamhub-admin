"use client";

import { useState } from "react";
import Link from "next/link";
import { Disc3, Info, MapPin, ShoppingCart } from "lucide-react";
import { useAlbum, GENRE_LABELS } from "@/lib/albums";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { BackLink } from "@/components/BackLink";
import { DemoBadge } from "@/components/DemoBadge";
import { TrackRow } from "@/components/TrackRow";
import { EmptyState, ErrorState } from "@/components/States";

export default function AlbumDetailPage({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  const { data, isLoading, isError, error, refetch } = useAlbum(id);
  const [coverFailed, setCoverFailed] = useState(false);
  const [added, setAdded] = useState(false);

  return (
    <div className="animate-fade-up">
      <div className="px-5 pt-4">
        <BackLink href="/albums" label="음반 목록" />
      </div>

      {isLoading ? (
        <div className="px-5 pt-4">
          <div className="skeleton mx-auto aspect-square w-full max-w-[280px] rounded-card" />
          <div className="skeleton mx-auto mt-5 h-6 w-2/5 rounded" />
          <div className="skeleton mx-auto mt-3 h-4 w-1/4 rounded" />
        </div>
      ) : isError ? (
        <div className="pt-3">
          {(error as ApiError)?.status === 404 ? (
            <EmptyState message="음반을 찾을 수 없습니다." />
          ) : (
            <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />
          )}
        </div>
      ) : data ? (
        <article className="pt-3">
          {/* Cover + meta */}
          <div className="px-5">
            <div className="mx-auto aspect-square w-full max-w-[280px] overflow-hidden rounded-card bg-gradient-to-br from-primary/25 via-surface to-bg">
              {data.coverUrl && !coverFailed ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={data.coverUrl}
                  alt={data.title}
                  onError={() => setCoverFailed(true)}
                  className="h-full w-full object-cover"
                />
              ) : (
                <div className="grid h-full w-full place-items-center">
                  <Disc3 className="h-14 w-14 text-inactive" />
                </div>
              )}
            </div>

            <div className="mt-5 text-center">
              <span className="rounded-full bg-primary/10 px-2.5 py-0.5 text-[11px] font-bold text-primary">
                {GENRE_LABELS[data.genre]}
              </span>
              <h1 className="mt-2 text-xl font-bold text-active">{data.title}</h1>
              <p className="mt-1 text-sm font-medium text-inactive">{data.artist}</p>
              <p className="mt-1 text-xs text-inactive">
                {data.label && <span>{data.label} · </span>}
                {data.releaseDate && <span>{formatDate(data.releaseDate)} 발매 · </span>}
                <span>{data.trackCount}곡</span>
              </p>
              {data.price != null && (
                <p className="mt-3 text-lg font-bold text-primary">{data.price.toLocaleString()}원</p>
              )}
            </div>

            {data.description && (
              <p className="mt-4 whitespace-pre-line text-sm leading-relaxed text-inactive">
                {data.description}
              </p>
            )}

            {/* Purchase (demo) */}
            <div className="mt-5 flex gap-2">
              <button
                onClick={() => setAdded(true)}
                className="flex flex-1 items-center justify-center gap-2 rounded-xl border border-border py-3 text-sm font-bold text-active active:bg-card"
              >
                <ShoppingCart className="h-4 w-4" />
                {added ? "담김 (데모)" : "장바구니"}
              </button>
              <button
                onClick={() => setAdded(true)}
                className="btn-primary flex-1 py-3 text-sm"
              >
                구매하기
              </button>
            </div>
            <p className="mt-2 flex items-center gap-1.5 text-[11px] text-inactive">
              <Info className="h-3.5 w-3.5 shrink-0" />
              구매·결제는 데모 플로우입니다(실 결제 미연동).
            </p>
          </div>

          {/* Track list */}
          <div className="mt-7 px-5">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-bold text-active">트랙 {data.tracks.length}곡</h2>
              <DemoBadge />
            </div>
            <p className="mt-1.5 flex items-center gap-1.5 text-[11px] text-inactive">
              <Info className="h-3.5 w-3.5 shrink-0" />
              미리듣기는 샘플 음원(SoundHelix)으로 재생되는 데모입니다.
            </p>
            <div className="mt-2 divide-y divide-border/40">
              {data.tracks.map((track) => (
                <TrackRow
                  key={track.id}
                  track={track}
                  albumId={data.id}
                  albumTitle={data.title}
                  artist={data.artist}
                  coverUrl={data.coverUrl}
                />
              ))}
            </div>
          </div>

          {/* Store CTA */}
          <div className="mt-7 px-5">
            <Link
              href="/stores"
              className="flex items-center justify-center gap-2 rounded-xl border border-border py-3 text-sm font-medium text-active active:bg-card"
            >
              <MapPin className="h-4 w-4 text-primary" />이 음반 판매 매장 보기
            </Link>
          </div>
        </article>
      ) : null}
    </div>
  );
}

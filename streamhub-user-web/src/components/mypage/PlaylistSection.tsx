"use client";

import Link from "next/link";
import { Heart, ListMusic, Loader2, X } from "lucide-react";
import { useMyFavorites, useRemoveFavorite, type FavoriteItem } from "@/lib/me";
import { SectionShell } from "./SectionShell";
import { CoverThumb } from "./CoverThumb";

/** Favorited tracks (찜) with per-row un-favorite and a link to each track's album. */
export function PlaylistSection({ token }: { token: string }) {
  const { data, isLoading, isError } = useMyFavorites(token);
  const favorites = data ?? [];

  return (
    <SectionShell
      icon={ListMusic}
      title="내 재생목록 (찜)"
      isLoading={isLoading}
      isError={isError}
      isEmpty={favorites.length === 0}
      errorMessage="재생목록을 불러오지 못했습니다."
      emptyIcon={Heart}
      emptyMessage="찜한 곡이 아직 없습니다."
    >
      <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
        {favorites.map((favorite) => (
          <FavoriteRow key={favorite.trackId} favorite={favorite} token={token} />
        ))}
      </ul>
    </SectionShell>
  );
}

function FavoriteRow({ favorite, token }: { favorite: FavoriteItem; token: string }) {
  const remove = useRemoveFavorite(token);

  return (
    <li className="flex items-center gap-3 px-4 py-3">
      <Link href={`/albums/${favorite.albumId}`} className="flex min-w-0 flex-1 items-center gap-3">
        <CoverThumb src={favorite.coverUrl} alt={favorite.albumTitle} size={44} />
        <div className="min-w-0 flex-1">
          <p className="ellipsis-1 text-sm font-bold text-active">{favorite.trackTitle}</p>
          <p className="ellipsis-1 text-xs text-inactive">
            {favorite.artist} · {favorite.albumTitle}
          </p>
        </div>
      </Link>
      <button
        type="button"
        onClick={() => remove.mutate(favorite.trackId)}
        disabled={remove.isPending}
        aria-label="찜 해제"
        className="grid h-8 w-8 shrink-0 place-items-center rounded-full border border-border text-inactive active:bg-card disabled:opacity-50"
      >
        {remove.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <X className="h-4 w-4" />}
      </button>
    </li>
  );
}

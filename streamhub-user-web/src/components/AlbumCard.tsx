"use client";

import { useState, type CSSProperties } from "react";
import Link from "next/link";
import { Disc3 } from "lucide-react";
import { GENRE_LABELS, type AlbumListItem } from "@/lib/albums";
import { CARD_SIZES, type CardSize } from "./ContentCard";

/** Album cover card: square artwork + title + artist + genre/price line. */
export function AlbumCard({ item, size }: { item: AlbumListItem; size?: CardSize }) {
  const [failed, setFailed] = useState(false);
  const showImage = item.coverUrl && !failed;
  const style: CSSProperties | undefined = size ? { width: CARD_SIZES[size] } : undefined;

  return (
    <Link href={`/albums/${item.id}`} style={style} className={size ? "block" : "block w-full"}>
      <div className="thumb aspect-square">
        {showImage ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={item.coverUrl ?? ""}
            alt={item.title}
            loading="lazy"
            onError={() => setFailed(true)}
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="absolute inset-0 grid place-items-center bg-gradient-to-br from-card to-surface">
            <Disc3 className="h-8 w-8 text-inactive" />
          </div>
        )}
        <span className="absolute left-1.5 top-1.5 rounded bg-bg/70 px-1.5 py-0.5 text-[10px] font-bold text-primary backdrop-blur">
          {GENRE_LABELS[item.genre]}
        </span>
      </div>
      <div className="mt-10px">
        <p className="ellipsis-1 text-16px font-bold leading-20px text-active">{item.title}</p>
        <p className="ellipsis-1 mt-0.5 text-12px font-medium leading-20px text-inactive">{item.artist}</p>
        {item.price != null && (
          <p className="mt-1 text-[13px] font-bold text-primary">{item.price.toLocaleString()}원</p>
        )}
      </div>
    </Link>
  );
}

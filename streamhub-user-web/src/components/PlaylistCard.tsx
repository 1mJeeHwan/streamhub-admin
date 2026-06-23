"use client";

import { useState, type CSSProperties } from "react";
import Link from "next/link";
import { ListMusic } from "lucide-react";
import type { PlaylistSummary } from "@/lib/playlists";
import { CARD_SIZES, type CardSize } from "./ContentCard";

/** Playlist tile: square cover + title + track count. Links to the playlist's listen page. */
export function PlaylistCard({ item, size }: { item: PlaylistSummary; size?: CardSize }) {
  const [failed, setFailed] = useState(false);
  const showImage = item.coverUrl && !failed;
  const style: CSSProperties | undefined = size ? { width: CARD_SIZES[size] } : undefined;

  return (
    <Link href={`/playlists/${item.id}`} style={style} className={size ? "block" : "block w-full"}>
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
          <div className="absolute inset-0 grid place-items-center bg-gradient-to-br from-primary/30 via-card to-surface">
            <ListMusic className="h-8 w-8 text-inactive" />
          </div>
        )}
        <span className="absolute bottom-1.5 right-1.5 rounded bg-bg/70 px-1.5 py-0.5 text-[10px] font-bold text-active backdrop-blur">
          {item.trackCount}곡
        </span>
      </div>
      <div className="mt-10px">
        <p className="ellipsis-1 text-16px font-bold leading-20px text-active">{item.title}</p>
        {item.description && (
          <p className="ellipsis-1 mt-0.5 text-12px font-medium leading-20px text-inactive">
            {item.description}
          </p>
        )}
      </div>
    </Link>
  );
}

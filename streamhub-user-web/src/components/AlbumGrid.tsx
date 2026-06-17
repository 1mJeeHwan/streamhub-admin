import { AlbumCard } from "./AlbumCard";
import type { AlbumListItem } from "@/lib/albums";

/** Two-column mobile grid of album cover cards. */
export function AlbumGrid({ items }: { items: AlbumListItem[] }) {
  return (
    <div className="grid grid-cols-2 gap-x-3 gap-y-5 px-5">
      {items.map((item) => (
        <AlbumCard key={item.id} item={item} />
      ))}
    </div>
  );
}

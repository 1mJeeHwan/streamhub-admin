import Link from "next/link";
import { Thumbnail } from "./Thumbnail";
import type { ContentListItem } from "@/lib/types";

function isNew(createdAt: string): boolean {
  const t = new Date(createdAt).getTime();
  if (Number.isNaN(t)) return false;
  return Date.now() - t < 14 * 24 * 60 * 60 * 1000;
}

/** production-app content card: rounded-6 thumbnail + bold title + inactive subtitle. */
export function ContentCard({ item }: { item: ContentListItem }) {
  const isMusic = item.type === "SOUND";
  const href = `${isMusic ? "/music" : "/video"}/${item.id}`;

  return (
    <Link href={href} className="block w-full">
      <div className="relative">
        <Thumbnail
          url={item.thumbnailUrl}
          type={item.type}
          title={item.title}
          ratio={isMusic ? "square" : "video"}
        />
        {isNew(item.createdAt) && (
          <span className="absolute right-1.5 top-1.5 rounded bg-primary px-1.5 py-0.5 text-[10px] font-bold text-bg">
            NEW
          </span>
        )}
      </div>
      <div className="mt-2.5">
        <p className="ellipsis-2 text-[15px] font-bold leading-snug text-active">{item.title}</p>
        {item.channelName && (
          <p className="ellipsis-1 mt-0.5 text-xs font-medium text-inactive">{item.channelName}</p>
        )}
      </div>
    </Link>
  );
}

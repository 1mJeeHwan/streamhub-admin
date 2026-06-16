import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { formatDate } from "@/lib/format";
import type { PostListItem } from "@/lib/types";

/** Notice/announcement row in production-app tone: surface card, title + excerpt + date. */
export function PostCard({ post }: { post: PostListItem }) {
  return (
    <Link
      href={`/posts/${post.id}`}
      className="flex items-center gap-3 rounded-card border border-border/70 bg-surface p-4 active:bg-card"
    >
      <div className="min-w-0 flex-1">
        <p className="ellipsis-1 text-[15px] font-bold text-active">{post.title}</p>
        {post.excerpt && <p className="ellipsis-1 mt-1 text-xs text-inactive">{post.excerpt}</p>}
        <p className="mt-1.5 text-[11px] text-inactive">{formatDate(post.createdAt)}</p>
      </div>
      <ChevronRight className="h-4 w-4 shrink-0 text-inactive" />
    </Link>
  );
}

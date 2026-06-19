import Link from "next/link";
import { ChevronRight } from "lucide-react";

/** Section title with optional result count and a "전체 보기" link. */
export function SectionHeader({
  title,
  count,
  moreHref,
}: {
  title: string;
  count?: number;
  moreHref?: string;
}) {
  return (
    <div className="flex items-center justify-between px-5 pb-3 pt-1">
      <h2 className="text-24px font-bold leading-30px text-active">
        {title}
        {count != null && <span className="ml-2 align-middle text-base font-medium text-inactive">{count}</span>}
      </h2>
      {moreHref && (
        <Link href={moreHref} className="flex shrink-0 items-center gap-0.5 text-xs font-medium text-primary">
          전체 보기
          <ChevronRight className="h-3.5 w-3.5" />
        </Link>
      )}
    </div>
  );
}

import Link from "next/link";

/** production-app section wrapper: bold 24px title + cyan "더보기" link, content below. */
export function ContentContainer({
  title,
  moreHref,
  children,
}: {
  title: string;
  moreHref?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="mt-7">
      <div className="flex items-center justify-between px-5 py-3">
        <h2 className="ellipsis-1 text-[22px] font-bold leading-tight text-active">{title}</h2>
        {moreHref && (
          <Link href={moreHref} className="shrink-0 text-xs font-medium text-primary">
            더보기
          </Link>
        )}
      </div>
      {children}
    </section>
  );
}

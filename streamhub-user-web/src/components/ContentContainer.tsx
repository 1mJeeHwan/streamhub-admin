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
    <section className="mt-30px">
      <div className="flex items-center justify-between px-20px py-14px">
        <h2 className="ellipsis-1 text-24px font-bold leading-30px text-active">{title}</h2>
        {moreHref && (
          <Link href={moreHref} className="shrink-0 text-12px font-medium text-primary">
            더보기
          </Link>
        )}
      </div>
      {children}
    </section>
  );
}

"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import clsx from "clsx";
import { ChevronRight } from "lucide-react";
import { useBanners } from "@/lib/queries";
import { safeHref } from "@/lib/url";
import type { BannerItem, BannerTarget } from "@/lib/types";

/** Bottom-scrim gradient for legible title text over a photo (mirrors the home Hero). */
const SCRIM =
  "linear-gradient(180deg, rgba(0,0,0,0.25) 0%, rgba(0,0,0,0) 28%, rgba(0,0,0,0) 52%, rgba(0,0,0,0.78) 100%)";

/** Fallback gradients for image-less (text-only) banners, cycled by position. */
const GRADIENTS = [
  "bg-gradient-to-br from-primary/70 via-surface to-bg",
  "bg-gradient-to-br from-secondary/60 via-surface to-bg",
  "bg-gradient-to-br from-violet-600/60 via-surface to-bg",
  "bg-gradient-to-br from-emerald-500/55 via-surface to-bg",
];

const AUTOPLAY_MS = 4500;

/**
 * Large ad-style promo banner for a content tab — the same image+title+subtitle treatment as the
 * home Hero, one notch shorter (16:9). Driven by the admin-managed banners for {@code target}
 * (image when set, gradient otherwise), with autoplay + expanding dots. Renders nothing when there
 * are no banners, so a tab without promos stays clean.
 */
export function BannerHero({ target }: { target: BannerTarget }) {
  const { data } = useBanners(target);
  const banners: BannerItem[] = (data ?? []).slice(0, 5);
  const ref = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);

  function onScroll() {
    const el = ref.current;
    if (!el) return;
    const i = Math.round(el.scrollLeft / el.clientWidth);
    if (i !== active) setActive(i);
  }

  function seek(i: number) {
    ref.current?.scrollTo({ left: i * (ref.current?.clientWidth ?? 0), behavior: "smooth" });
  }

  useEffect(() => {
    if (banners.length <= 1) return;
    const id = setInterval(() => {
      const el = ref.current;
      if (!el) return;
      const next = (Math.round(el.scrollLeft / el.clientWidth) + 1) % banners.length;
      el.scrollTo({ left: next * el.clientWidth, behavior: "smooth" });
    }, AUTOPLAY_MS);
    return () => clearInterval(id);
  }, [banners.length]);

  if (banners.length === 0) {
    return null;
  }

  return (
    <div className="relative">
      <div
        ref={ref}
        onScroll={onScroll}
        className="flex snap-x snap-mandatory overflow-x-auto [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        {banners.map((banner, i) => (
          <Link
            key={banner.id}
            href={safeHref(banner.linkUrl)}
            aria-label={banner.title}
            className="relative aspect-[16/9] w-full shrink-0 snap-start overflow-hidden text-left"
          >
            {banner.imageUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={banner.imageUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              <div className={clsx("h-full w-full", GRADIENTS[i % GRADIENTS.length])} />
            )}
            <div className="absolute inset-0" style={{ background: SCRIM }} />
            <div className="absolute inset-x-0 bottom-0 px-20px py-14px">
              <h2
                className="ellipsis-2 text-22px font-bold leading-28px text-white"
                style={{ textShadow: "0px 1px 3px rgba(0,0,0,0.7)" }}
              >
                {banner.title}
              </h2>
              {banner.subtitle && (
                <p
                  className="ellipsis-2 mt-1 text-13px leading-18px text-white/90"
                  style={{ textShadow: "0px 1px 3px rgba(0,0,0,0.7)" }}
                >
                  {banner.subtitle}
                </p>
              )}
              <span className="mt-2 inline-flex w-fit items-center gap-0.5 rounded-full bg-white/20 px-2.5 py-1 text-11px font-bold text-white backdrop-blur">
                바로가기
                <ChevronRight className="h-3 w-3" />
              </span>
            </div>
          </Link>
        ))}
      </div>

      {banners.length > 1 && (
        <div className="absolute inset-x-0 bottom-[14px] flex justify-center gap-2">
          {banners.map((_, i) => (
            <button
              key={i}
              onClick={() => seek(i)}
              aria-label={`${i + 1}번째 배너`}
              className={clsx(
                "h-[6px] rounded-full border border-white/80 transition-all",
                i === active ? "w-16px bg-white opacity-100" : "w-[6px] bg-white/70 opacity-60",
              )}
            />
          ))}
        </div>
      )}
    </div>
  );
}

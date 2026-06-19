"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ChevronRight } from "lucide-react";
import clsx from "clsx";

export type AdSlide = {
  title: string;
  subtitle: string;
  href: string;
  /** Tailwind gradient classes for the slide background (used when imageUrl is absent). */
  gradient: string;
  /** Optional background image; when set, it replaces the gradient (with a dark scrim). */
  imageUrl?: string | null;
  cta?: string;
};

const AUTOPLAY_MS = 4500;

/**
 * Top promo banner, ported from ng-front's AdCarousel: full-width rounded banner strip,
 * horizontal scroll-snap, autoplay, expanding dots. Slides are designed gradient promos
 * (no banner image data exists yet — swap to <img> here when ad assets are provided).
 */
export function AdBanner({ slides }: { slides: AdSlide[] }) {
  const ref = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);

  function onScroll() {
    const el = ref.current;
    if (!el) return;
    const i = Math.round(el.scrollLeft / el.clientWidth);
    if (i !== active) setActive(i);
  }

  useEffect(() => {
    if (slides.length <= 1) return;
    const id = setInterval(() => {
      const el = ref.current;
      if (!el) return;
      const next = (Math.round(el.scrollLeft / el.clientWidth) + 1) % slides.length;
      el.scrollTo({ left: next * el.clientWidth, behavior: "smooth" });
    }, AUTOPLAY_MS);
    return () => clearInterval(id);
  }, [slides.length]);

  if (slides.length === 0) return null;

  return (
    <div className="relative px-20px">
      <div
        ref={ref}
        onScroll={onScroll}
        className="flex snap-x snap-mandatory overflow-x-auto [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        {slides.map((s) => (
          <Link
            key={s.title}
            href={s.href}
            className={clsx(
              "relative flex aspect-[7/2] w-full shrink-0 snap-start flex-col justify-center overflow-hidden rounded-card px-20px text-white",
              !s.imageUrl && s.gradient,
            )}
          >
            {s.imageUrl && (
              <>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={s.imageUrl} alt="" className="absolute inset-0 h-full w-full object-cover" />
                <span className="absolute inset-0 bg-black/45" />
              </>
            )}
            <p className="relative text-16px font-bold leading-20px drop-shadow">{s.title}</p>
            <p className="relative mt-1 text-12px leading-20px text-white/85 drop-shadow">{s.subtitle}</p>
            <span className="relative mt-2 inline-flex w-fit items-center gap-0.5 rounded-full bg-white/20 px-2 py-0.5 text-11px font-bold backdrop-blur">
              {s.cta ?? "바로가기"}
              <ChevronRight className="h-3 w-3" />
            </span>
          </Link>
        ))}
      </div>

      {slides.length > 1 && (
        <div className="mt-2 flex justify-center gap-2">
          {slides.map((_, i) => (
            <span
              key={i}
              className={clsx(
                "h-[6px] rounded-full transition-all",
                i === active ? "w-16px bg-primary" : "w-[6px] bg-inactive/50",
              )}
            />
          ))}
        </div>
      )}
    </div>
  );
}

"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import clsx from "clsx";
import type { ContentListItem } from "@/lib/types";

/** ng-front MainSlide gradient: subtle top vignette + strong bottom scrim for the title. */
const SCRIM =
  "linear-gradient(180deg, rgba(0,0,0,0.2) 0%, rgba(0,0,0,0) 21.82%, rgba(0,0,0,0) 68.01%, rgba(0,0,0,0.72) 100%)";

const AUTOPLAY_MS = 4000;

/**
 * Home hero, ported from ng-front's MainSlide: tall 8:7 banner, autoplay, bottom-scrim
 * title (22px) + subtitle (11px), expanding dots. Scroll-snap row (no carousel lib).
 */
export function Hero({ items }: { items: ContentListItem[] }) {
  const ref = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);
  const router = useRouter();
  const slides = items.slice(0, 5);

  function onScroll() {
    const el = ref.current;
    if (!el) return;
    const i = Math.round(el.scrollLeft / el.clientWidth);
    if (i !== active) setActive(i);
  }

  function seek(i: number) {
    const el = ref.current;
    if (!el) return;
    el.scrollTo({ left: i * el.clientWidth, behavior: "smooth" });
  }

  // Autoplay: advance one slide every 4s, wrapping back to the first.
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
    <div className="relative">
      <div
        ref={ref}
        onScroll={onScroll}
        className="flex snap-x snap-mandatory overflow-x-auto [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        {slides.map((item, i) => (
          <button
            key={item.id}
            onClick={() => router.push(`/video/${item.id}`)}
            className="relative aspect-[8/7] w-full shrink-0 snap-start overflow-hidden text-left"
            aria-label={item.title}
          >
            {item.thumbnailUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={item.thumbnailUrl}
                alt=""
                className="h-full w-full object-cover object-top"
              />
            ) : (
              <div
                className={clsx(
                  "h-full w-full",
                  i % 2 === 0
                    ? "bg-gradient-to-br from-primary/40 via-surface to-bg"
                    : "bg-gradient-to-br from-secondary/40 via-surface to-bg",
                )}
              />
            )}
            <div className="absolute inset-0" style={{ background: SCRIM }} />
            <div className="absolute inset-x-0 bottom-0 px-20px py-10px">
              <h2
                className="ellipsis-2 text-22px font-bold leading-30px text-white"
                style={{ textShadow: "0px 1px 2px #000000" }}
              >
                {item.title}
              </h2>
              {item.channelName && (
                <p
                  className="ellipsis-2 mt-1 text-11px leading-[13.8px] -tracking-[0.24px] text-white/90"
                  style={{ textShadow: "0px 1px 2px #000000" }}
                >
                  {item.channelName}
                </p>
              )}
            </div>
          </button>
        ))}
      </div>

      <div className="absolute inset-x-0 bottom-20px flex justify-center gap-2">
        {slides.map((_, i) => (
          <button
            key={i}
            onClick={() => seek(i)}
            aria-label={`${i + 1}번째 배너`}
            className={clsx(
              "h-[6px] rounded-full border border-white transition-all",
              i === active ? "w-16px bg-white opacity-100" : "w-[6px] bg-white opacity-50",
            )}
          />
        ))}
      </div>
    </div>
  );
}

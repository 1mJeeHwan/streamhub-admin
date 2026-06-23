"use client";

import { useBanners } from "@/lib/queries";
import { safeHref } from "@/lib/url";
import { AdBanner, type AdSlide } from "./AdBanner";
import type { BannerItem, BannerTarget } from "@/lib/types";

/** Fallback gradients for image-less (text promo) banners, cycled by position. */
const GRADIENTS = [
  "bg-gradient-to-br from-primary/85 to-indigo-500",
  "bg-gradient-to-br from-rose-500 to-orange-400",
  "bg-gradient-to-br from-emerald-500 to-teal-500",
  "bg-gradient-to-br from-violet-600 to-fuchsia-500",
];

function toSlide(banner: BannerItem, index: number): AdSlide {
  return {
    title: banner.title,
    subtitle: banner.subtitle ?? "",
    href: safeHref(banner.linkUrl),
    gradient: GRADIENTS[index % GRADIENTS.length],
    imageUrl: banner.imageUrl,
  };
}

/**
 * Admin-managed promo banner strip at the top of a content tab. Pulls the active banners for the
 * given target ({@code VIDEO}/{@code SOUND}/{@code ALL}); renders nothing when there are none, so a
 * tab with no banners stays clean.
 */
export function TabBanner({ target }: { target: BannerTarget }) {
  const { data } = useBanners(target);
  const banners = data ?? [];
  if (banners.length === 0) {
    return null;
  }
  return (
    <div className="pt-4">
      <AdBanner slides={banners.map(toSlide)} />
    </div>
  );
}

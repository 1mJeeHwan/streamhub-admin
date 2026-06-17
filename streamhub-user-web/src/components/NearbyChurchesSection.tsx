"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ChevronRight, MapPin, Navigation } from "lucide-react";
import { useNearbyChurches } from "@/lib/churches";
import { getCurrentPosition, type Coords } from "@/lib/geolocation";
import { denominationLabel } from "@/lib/churchTypes";

/**
 * Compact "near me" widget: geolocation → top 3 nearby churches. Designed to be
 * dropped into the home / mypage screens without disturbing existing layout.
 */
export function NearbyChurchesSection() {
  const [coords, setCoords] = useState<Coords | null>(null);

  useEffect(() => {
    let active = true;
    getCurrentPosition().then((r) => active && setCoords(r.coords));
    return () => {
      active = false;
    };
  }, []);

  const { data, isLoading } = useNearbyChurches(
    { lat: coords?.lat, lng: coords?.lng, radiusKm: 5, pageSize: 3 },
    coords != null,
  );
  const churches = (data?.contents ?? []).slice(0, 3);

  // Hide entirely if nothing nearby (and not loading) — keeps the screen clean.
  if (coords && !isLoading && churches.length === 0) return null;

  return (
    <section className="px-5 pt-6">
      <div className="flex items-center justify-between pb-3">
        <h2 className="flex items-center gap-1.5 text-[22px] font-bold leading-tight text-active">
          <MapPin className="h-5 w-5 text-primary" />내 주변 교회
        </h2>
        <Link href="/churches" className="flex shrink-0 items-center gap-0.5 text-xs font-medium text-primary">
          전체 보기
          <ChevronRight className="h-3.5 w-3.5" />
        </Link>
      </div>

      {isLoading || coords == null ? (
        <div className="space-y-2.5">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="skeleton h-16 rounded-card" />
          ))}
        </div>
      ) : (
        <div className="space-y-2.5">
          {churches.map((c) => (
            <Link
              key={c.id}
              href={`/churches/${c.id}`}
              className="flex items-center gap-3 rounded-card border border-border/70 bg-surface p-3 active:bg-card"
            >
              <div className="thumb grid h-12 w-12 shrink-0 place-items-center rounded-lg text-noimg">
                {c.thumbnailUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={c.thumbnailUrl} alt="" className="h-full w-full object-cover" loading="lazy" />
                ) : (
                  <Navigation className="h-4 w-4" />
                )}
              </div>
              <div className="min-w-0 flex-1">
                <p className="ellipsis-1 text-sm font-bold text-active">{c.name}</p>
                <p className="text-[11px] text-inactive">{denominationLabel(c.denomination)}</p>
              </div>
              {c.distanceKm != null && (
                <span className="shrink-0 rounded-full bg-primary/15 px-2 py-0.5 text-[11px] font-bold text-primary">
                  {c.distanceKm < 1 ? `${Math.round(c.distanceKm * 1000)}m` : `${c.distanceKm.toFixed(1)}km`}
                </span>
              )}
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}

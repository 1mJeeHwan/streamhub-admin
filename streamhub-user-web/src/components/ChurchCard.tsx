"use client";

import Link from "next/link";
import { MapPin, Navigation, Phone } from "lucide-react";
import clsx from "clsx";
import type { ChurchNearbyItem } from "@/lib/churchTypes";
import { denominationLabel } from "@/lib/churchTypes";

function distanceText(km: number | null): string | null {
  if (km == null) return null;
  if (km < 1) return `${Math.round(km * 1000)}m`;
  return `${km.toFixed(1)}km`;
}

/**
 * Distance-sorted church row. Whole card links to the detail page; hovering/focusing
 * calls `onHover` so the parent can highlight the matching map marker.
 */
export function ChurchCard({
  church,
  active,
  onHover,
}: {
  church: ChurchNearbyItem;
  active?: boolean;
  onHover?: (id: number | undefined) => void;
}) {
  const dist = distanceText(church.distanceKm);
  return (
    <Link
      href={`/churches/${church.id}`}
      onMouseEnter={() => onHover?.(church.id)}
      onMouseLeave={() => onHover?.(undefined)}
      onFocus={() => onHover?.(church.id)}
      className={clsx(
        "flex gap-3 rounded-card border bg-surface p-3 transition-colors",
        active ? "border-primary" : "border-border/70 hover:border-border",
      )}
    >
      <div className="thumb h-16 w-16 shrink-0 rounded-lg">
        {church.thumbnailUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={church.thumbnailUrl} alt="" className="h-full w-full object-cover" loading="lazy" />
        ) : (
          <div className="grid h-full w-full place-items-center text-noimg">
            <Navigation className="h-5 w-5" />
          </div>
        )}
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <h3 className="ellipsis-1 text-sm font-bold text-active">{church.name}</h3>
          {dist && (
            <span className="shrink-0 rounded-full bg-primary/15 px-2 py-0.5 text-[11px] font-bold text-primary">
              {dist}
            </span>
          )}
        </div>
        <p className="mt-0.5 text-[11px] text-inactive">{denominationLabel(church.denomination)}</p>
        {church.address && (
          <p className="mt-1 flex items-start gap-1 text-[11px] text-inactive">
            <MapPin className="mt-0.5 h-3 w-3 shrink-0" />
            <span className="ellipsis-1">{church.address}</span>
          </p>
        )}
        {church.phone && (
          <p className="mt-0.5 flex items-center gap-1 text-[11px] text-inactive">
            <Phone className="h-3 w-3 shrink-0" />
            {church.phone}
          </p>
        )}
      </div>
    </Link>
  );
}

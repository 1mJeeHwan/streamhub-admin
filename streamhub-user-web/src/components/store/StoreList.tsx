"use client";

import { Clock, MapPin, Phone } from "lucide-react";
import clsx from "clsx";
import type { StoreDto } from "@/lib/albums";

/** Distance-sorted store list. Tapping a row highlights its map marker; phone is a tel: CTA. */
export function StoreList({
  stores,
  selectedId,
  onSelect,
}: {
  stores: StoreDto[];
  selectedId?: number;
  onSelect?: (id: number) => void;
}) {
  return (
    <ul className="space-y-2.5 px-5">
      {stores.map((s) => {
        const active = s.id === selectedId;
        return (
          <li key={s.id}>
            <button
              onClick={() => onSelect?.(s.id)}
              className={clsx(
                "w-full rounded-card border bg-surface p-4 text-left transition-colors",
                active ? "border-primary" : "border-border active:bg-card",
              )}
            >
              <div className="flex items-start justify-between gap-2">
                <p className="font-bold text-active">{s.name}</p>
                {s.distanceKm != null && (
                  <span className="shrink-0 rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-bold text-primary">
                    {s.distanceKm.toFixed(1)}km
                  </span>
                )}
              </div>
              {s.address && (
                <p className="mt-1.5 flex items-start gap-1.5 text-xs text-inactive">
                  <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                  {s.address}
                </p>
              )}
              {s.openHours && (
                <p className="mt-1 flex items-center gap-1.5 text-xs text-inactive">
                  <Clock className="h-3.5 w-3.5 shrink-0" />
                  {s.openHours}
                </p>
              )}
              {s.phone && (
                <a
                  href={`tel:${s.phone}`}
                  onClick={(e) => e.stopPropagation()}
                  className="mt-2.5 inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-active active:bg-card"
                >
                  <Phone className="h-3.5 w-3.5 text-primary" />
                  {s.phone}
                </a>
              )}
            </button>
          </li>
        );
      })}
    </ul>
  );
}

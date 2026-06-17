"use client";

import { useMemo } from "react";
import MapProvider, { type MapMarker } from "../map/MapProvider";
import type { Coords } from "@/lib/geolocation";
import type { StoreDto } from "@/lib/albums";

/**
 * Store map: adapts StoreDto[] onto the shared key-less MapProvider (Leaflet + OSM, no API key).
 * Marker click ↔ list selection share `selectedId`. Reuses the same map stack as the church finder.
 */
export function StoreMap({
  stores,
  center,
  selectedId,
  onSelect,
}: {
  stores: StoreDto[];
  center: Coords;
  selectedId?: number;
  onSelect?: (id: number) => void;
}) {
  const markers = useMemo<MapMarker[]>(
    () =>
      stores
        .filter((s) => s.lat != null && s.lng != null)
        .map((s) => ({ id: s.id, lat: s.lat as number, lng: s.lng as number, label: s.name })),
    [stores],
  );

  return (
    <div className="h-64 px-5">
      <MapProvider center={center} markers={markers} selectedId={selectedId} onSelect={onSelect} />
    </div>
  );
}

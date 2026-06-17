"use client";

// Single point where the map implementation is chosen. Today: Leaflet + OpenStreetMap
// (no API key). To swap to Kakao Maps later, implement KakaoMap.tsx with the SAME
// MapViewProps contract and change only the import below — callers stay untouched.

import dynamic from "next/dynamic";

export interface MapMarker {
  id: number;
  lat: number;
  lng: number;
  label: string;
}

export interface MapViewProps {
  center: { lat: number; lng: number };
  markers: MapMarker[];
  selectedId?: number;
  onSelect?: (id: number) => void;
  /** Tailwind height utility, e.g. "h-64". */
  heightClass?: string;
}

// Leaflet touches `window`, so it must be client-only (no SSR) — same as the admin
// app's AG Grid dynamic import.
const LeafletMap = dynamic(() => import("./LeafletMap"), {
  ssr: false,
  loading: () => <div className="skeleton h-full w-full rounded-card" />,
});

export default function MapProvider(props: MapViewProps) {
  return <LeafletMap {...props} />;
}

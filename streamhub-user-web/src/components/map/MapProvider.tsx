"use client";

// Single point where the map implementation is chosen. Today: Kakao Maps (needs a
// JavaScript app key in NEXT_PUBLIC_KAKAO_MAP_KEY and the domain registered in the
// Kakao Developers console). LeafletMap.tsx is kept as a no-key fallback — both
// implement the same MapViewProps contract, so switching is a one-line import change.

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

// The Kakao SDK touches `window`, so the map must be client-only (no SSR) — same as
// the admin app's AG Grid dynamic import.
const KakaoMap = dynamic(() => import("./KakaoMap"), {
  ssr: false,
  loading: () => <div className="skeleton h-full w-full rounded-card" />,
});

export default function MapProvider(props: MapViewProps) {
  return <KakaoMap {...props} />;
}

"use client";

// Kakao Maps implementation of the MapProvider contract (MapViewProps). Drop-in
// replacement for LeafletMap: same props, same teal/pink SVG pins, same
// click → onSelect behaviour. The Kakao Maps JS SDK is loaded once from
// dapi.kakao.com with autoload=false; it needs a JavaScript app key in
// NEXT_PUBLIC_KAKAO_MAP_KEY and the serving domain registered in the Kakao
// Developers console (플랫폼 → Web). Missing key / blocked domain degrades
// gracefully to an empty container (no crash), like the Leaflet CDN fallback.

import { useEffect, useRef } from "react";
import type { MapViewProps } from "./MapProvider";

// Minimal shape of the Kakao Maps runtime we touch (global `window.kakao`).
interface KakaoLatLng {
  getLat: () => number;
  getLng: () => number;
}
interface KakaoMapInstance {
  setBounds: (bounds: unknown, ...padding: number[]) => void;
  setCenter: (latlng: KakaoLatLng) => void;
  setLevel: (level: number) => void;
  relayout: () => void;
}
interface KakaoBounds {
  extend: (latlng: KakaoLatLng) => void;
}
interface KakaoOverlay {
  setMap: (map: KakaoMapInstance | null) => void;
}
interface KakaoNamespace {
  maps: {
    load: (cb: () => void) => void;
    Map: new (el: HTMLElement, opts: { center: KakaoLatLng; level: number }) => KakaoMapInstance;
    LatLng: new (lat: number, lng: number) => KakaoLatLng;
    LatLngBounds: new () => KakaoBounds;
    CustomOverlay: new (opts: {
      position: KakaoLatLng;
      content: HTMLElement;
      yAnchor?: number;
      xAnchor?: number;
    }) => KakaoOverlay;
  };
}

declare global {
  interface Window {
    kakao?: KakaoNamespace;
  }
}

const SDK_ID = "kakao-maps-sdk";
let loaderPromise: Promise<KakaoNamespace> | null = null;

/** Inject the Kakao Maps SDK once (autoload=false); resolve with `window.kakao`. */
function loadKakao(appKey: string): Promise<KakaoNamespace> {
  if (typeof window === "undefined") return Promise.reject(new Error("no window"));
  if (window.kakao?.maps) return Promise.resolve(window.kakao);
  if (loaderPromise) return loaderPromise;

  const src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false`;
  loaderPromise = new Promise<KakaoNamespace>((resolve, reject) => {
    const done = () => {
      if (window.kakao) resolve(window.kakao);
      else reject(new Error("kakao sdk missing after load"));
    };
    const existing = document.getElementById(SDK_ID) as HTMLScriptElement | null;
    if (existing) {
      existing.addEventListener("load", done);
      existing.addEventListener("error", () => reject(new Error("kakao sdk load failed")));
      return;
    }
    const script = document.createElement("script");
    script.id = SDK_ID;
    script.src = src;
    script.async = true;
    script.onload = done;
    script.onerror = () => reject(new Error("kakao sdk load failed"));
    document.head.appendChild(script);
  });
  return loaderPromise;
}

/** Teal default / pink selected SVG pin, bottom-anchored by the CustomOverlay. */
function pinElement(selected: boolean, onClick: () => void): HTMLElement {
  const fill = selected ? "#FF1B58" : "#40C1DF";
  const size = selected ? 30 : 24;
  const el = document.createElement("div");
  el.style.cursor = "pointer";
  el.innerHTML = `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="${fill}" stroke="#15171D" stroke-width="1.2" style="filter:drop-shadow(0 1px 2px rgba(0,0,0,.5))">
    <path d="M12 2C8.1 2 5 5.1 5 9c0 5.2 7 13 7 13s7-7.8 7-13c0-3.9-3.1-7-7-7z"/>
    <circle cx="12" cy="9" r="2.6" fill="#15171D"/>
  </svg>`;
  el.addEventListener("click", onClick);
  return el;
}

export default function KakaoMap({
  center,
  markers,
  selectedId,
  onSelect,
  heightClass = "h-full",
}: MapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  // onSelect identity can change each render; read the latest via a ref so marker
  // rebuilds are driven only by the marker set, not by the parent re-rendering.
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;

  useEffect(() => {
    const appKey = process.env.NEXT_PUBLIC_KAKAO_MAP_KEY;
    if (!appKey) {
      // eslint-disable-next-line no-console
      console.error("Missing NEXT_PUBLIC_KAKAO_MAP_KEY — map disabled");
      return;
    }

    let cancelled = false;
    const overlays: KakaoOverlay[] = [];

    loadKakao(appKey)
      .then((kakao) => {
        kakao.maps.load(() => {
          if (cancelled || !containerRef.current) return;
          const map = new kakao.maps.Map(containerRef.current, {
            center: new kakao.maps.LatLng(center.lat, center.lng),
            level: 5,
          });

          const bounds = new kakao.maps.LatLngBounds();
          for (const m of markers) {
            const position = new kakao.maps.LatLng(m.lat, m.lng);
            const content = pinElement(m.id === selectedId, () => onSelectRef.current?.(m.id));
            const overlay = new kakao.maps.CustomOverlay({ position, content, yAnchor: 1, xAnchor: 0.5 });
            overlay.setMap(map);
            overlays.push(overlay);
            bounds.extend(position);
          }
          if (markers.length > 1) {
            map.setBounds(bounds, 36, 36, 36, 36);
          } else if (markers.length === 1) {
            map.setCenter(new kakao.maps.LatLng(markers[0].lat, markers[0].lng));
            map.setLevel(4);
          }
          // Container may be sized after the dynamic import; nudge a relayout.
          setTimeout(() => map.relayout(), 0);
        });
      })
      .catch(() => {
        /* missing key / blocked domain / SDK unreachable — degrade to empty container */
      });

    return () => {
      cancelled = true;
      for (const o of overlays) o.setMap(null);
    };
    // Rebuild when the marker set or center changes. selectedId-only changes are
    // intentionally NOT in deps (parity with LeafletMap; highlight is set at build).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [center.lat, center.lng, markers.map((m) => `${m.id}:${m.lat}:${m.lng}`).join("|")]);

  return (
    <div
      ref={containerRef}
      role="application"
      aria-label="지도"
      className={`${heightClass} w-full overflow-hidden rounded-card`}
    />
  );
}

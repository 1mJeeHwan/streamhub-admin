"use client";

// Default MapProvider implementation: Leaflet + OpenStreetMap tiles (no API key,
// no account). Leaflet's JS+CSS are loaded from unpkg CDN on first mount so the
// feature works without an `npm install` step. The package.json also declares
// `leaflet` as a dependency so a future build can bundle it instead of using the CDN.
//
// To swap to Kakao Maps later: add KakaoMap.tsx implementing the same MapViewProps
// and re-point MapProvider — this file needs no changes.

import { useEffect, useRef } from "react";
import type { MapViewProps } from "./MapProvider";

const LEAFLET_VERSION = "1.9.4";
const CSS_URL = `https://unpkg.com/leaflet@${LEAFLET_VERSION}/dist/leaflet.css`;
const JS_URL = `https://unpkg.com/leaflet@${LEAFLET_VERSION}/dist/leaflet.js`;

// Minimal shape of the Leaflet runtime we touch (CDN global `L`).
interface LeafletStatic {
  map: (el: HTMLElement, opts?: Record<string, unknown>) => LeafletMapInstance;
  tileLayer: (url: string, opts?: Record<string, unknown>) => { addTo: (m: LeafletMapInstance) => void };
  marker: (latlng: [number, number], opts?: Record<string, unknown>) => LeafletMarker;
  divIcon: (opts: Record<string, unknown>) => unknown;
  latLngBounds: (latlngs: [number, number][]) => unknown;
}
interface LeafletMapInstance {
  setView: (latlng: [number, number], zoom: number) => LeafletMapInstance;
  fitBounds: (bounds: unknown, opts?: Record<string, unknown>) => void;
  remove: () => void;
  invalidateSize: () => void;
}
interface LeafletMarker {
  addTo: (m: LeafletMapInstance) => LeafletMarker;
  on: (event: string, fn: () => void) => LeafletMarker;
}

declare global {
  interface Window {
    L?: LeafletStatic;
  }
}

let loaderPromise: Promise<LeafletStatic> | null = null;

/** Inject Leaflet CSS + JS from the CDN once; resolve with the global `L`. */
function loadLeaflet(): Promise<LeafletStatic> {
  if (typeof window === "undefined") return Promise.reject(new Error("no window"));
  if (window.L) return Promise.resolve(window.L);
  if (loaderPromise) return loaderPromise;

  loaderPromise = new Promise<LeafletStatic>((resolve, reject) => {
    if (!document.querySelector(`link[href="${CSS_URL}"]`)) {
      const link = document.createElement("link");
      link.rel = "stylesheet";
      link.href = CSS_URL;
      document.head.appendChild(link);
    }
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${JS_URL}"]`);
    if (existing) {
      existing.addEventListener("load", () => resolve(window.L as LeafletStatic));
      existing.addEventListener("error", () => reject(new Error("leaflet load failed")));
      return;
    }
    const script = document.createElement("script");
    script.src = JS_URL;
    script.async = true;
    script.onload = () => resolve(window.L as LeafletStatic);
    script.onerror = () => reject(new Error("leaflet load failed"));
    document.body.appendChild(script);
  });
  return loaderPromise;
}

function markerHtml(selected: boolean): string {
  const fill = selected ? "#FF1B58" : "#40C1DF";
  const size = selected ? 30 : 24;
  return `<div style="transform:translate(-50%,-100%);">
    <svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="${fill}" stroke="#15171D" stroke-width="1.2"
      style="filter:drop-shadow(0 1px 2px rgba(0,0,0,.5))">
      <path d="M12 2C8.1 2 5 5.1 5 9c0 5.2 7 13 7 13s7-7.8 7-13c0-3.9-3.1-7-7-7z"/>
      <circle cx="12" cy="9" r="2.6" fill="#15171D"/>
    </svg>
  </div>`;
}

export default function LeafletMap({
  center,
  markers,
  selectedId,
  onSelect,
  heightClass = "h-full",
}: MapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<LeafletMapInstance | null>(null);
  // onSelect can change identity each render; read the latest via a ref to avoid
  // tearing down/rebuilding the whole map (and its markers) on every parent render.
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;

  useEffect(() => {
    let cancelled = false;
    loadLeaflet()
      .then((L) => {
        if (cancelled || !containerRef.current || mapRef.current) return;
        const map = L.map(containerRef.current, { zoomControl: true, attributionControl: true }).setView(
          [center.lat, center.lng],
          13,
        );
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
          maxZoom: 19,
          attribution: "&copy; OpenStreetMap",
        }).addTo(map);

        const pts: [number, number][] = [];
        for (const m of markers) {
          const selected = m.id === selectedId;
          const icon = L.divIcon({
            html: markerHtml(selected),
            className: "",
            iconSize: [0, 0],
          });
          const mk = L.marker([m.lat, m.lng], { icon, title: m.label }).addTo(map);
          mk.on("click", () => onSelectRef.current?.(m.id));
          pts.push([m.lat, m.lng]);
        }
        if (pts.length > 1) {
          map.fitBounds(L.latLngBounds(pts), { padding: [36, 36] });
        } else if (pts.length === 1) {
          map.setView(pts[0], 15);
        }
        mapRef.current = map;
        // The container may have been sized after init (dynamic import); nudge a relayout.
        setTimeout(() => map.invalidateSize(), 0);
      })
      .catch(() => {
        /* CDN unreachable — the empty container degrades gracefully. */
      });

    return () => {
      cancelled = true;
      mapRef.current?.remove();
      mapRef.current = null;
    };
    // Rebuild when the marker set or center changes. selectedId-only changes are
    // intentionally NOT in deps (highlight is cosmetic; full rebuild is wasteful).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [center.lat, center.lng, markers.map((m) => `${m.id}:${m.lat}:${m.lng}`).join("|")]);

  return <div ref={containerRef} className={`${heightClass} w-full overflow-hidden rounded-card`} />;
}

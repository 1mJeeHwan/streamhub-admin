"use client";

// Kakao Maps implementation of the MapProvider contract (MapViewProps). Beyond a
// plain marker map it adds: an info bubble on marker hover/click, and a "search
// this area" button that re-searches the *currently visible* map area (radius
// derived from the viewport, not a fixed value). The map is created once and only
// its markers are refreshed, so a re-search keeps the zoom/center the user set
// instead of snapping back to fit results.
//
// Needs a Kakao JavaScript app key in NEXT_PUBLIC_KAKAO_MAP_KEY and the serving
// domain registered in the Kakao Developers console; missing key / blocked domain
// degrades gracefully to an empty container (no crash).

import { useEffect, useRef, useState } from "react";
import { RotateCw } from "lucide-react";
import type { MapMarker, MapViewProps } from "./MapProvider";

interface KakaoLatLng {
  getLat: () => number;
  getLng: () => number;
}
interface KakaoBounds {
  extend: (latlng: KakaoLatLng) => void;
  getSouthWest: () => KakaoLatLng;
  getNorthEast: () => KakaoLatLng;
}
interface KakaoMapInstance {
  setBounds: (bounds: KakaoBounds, ...padding: number[]) => void;
  setCenter: (latlng: KakaoLatLng) => void;
  setLevel: (level: number) => void;
  getCenter: () => KakaoLatLng;
  getBounds: () => KakaoBounds;
  relayout: () => void;
}
interface KakaoOverlay {
  setMap: (map: KakaoMapInstance | null) => void;
  setPosition: (latlng: KakaoLatLng) => void;
  setContent: (content: HTMLElement | string) => void;
}
interface KakaoNamespace {
  maps: {
    load: (cb: () => void) => void;
    Map: new (el: HTMLElement, opts: { center: KakaoLatLng; level: number }) => KakaoMapInstance;
    LatLng: new (lat: number, lng: number) => KakaoLatLng;
    LatLngBounds: new () => KakaoBounds;
    CustomOverlay: new (opts: {
      position?: KakaoLatLng;
      content?: HTMLElement | string;
      yAnchor?: number;
      xAnchor?: number;
      zIndex?: number;
      clickable?: boolean;
    }) => KakaoOverlay;
    event: { addListener: (target: unknown, type: string, handler: () => void) => void };
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
function pinElement(selected: boolean): HTMLElement {
  const fill = selected ? "#FF1B58" : "#40C1DF";
  const size = selected ? 32 : 26;
  const el = document.createElement("div");
  el.style.cursor = "pointer";
  el.innerHTML = `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="${fill}" stroke="#15171D" stroke-width="1.2" style="filter:drop-shadow(0 2px 3px rgba(0,0,0,.55))">
    <path d="M12 2C8.1 2 5 5.1 5 9c0 5.2 7 13 7 13s7-7.8 7-13c0-3.9-3.1-7-7-7z"/>
    <circle cx="12" cy="9" r="2.6" fill="#15171D"/>
  </svg>`;
  return el;
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c] as string,
  );
}

/** Dark info-bubble shown above a marker on hover/click. */
function bubbleElement(m: MapMarker): HTMLElement {
  const wrap = document.createElement("div");
  const dist = m.distanceText
    ? `<span style="flex:none;border-radius:999px;background:rgba(64,193,223,.18);color:#40C1DF;font-size:11px;font-weight:700;padding:1px 7px;">${escapeHtml(m.distanceText)}</span>`
    : "";
  const subtitle = m.subtitle
    ? `<div style="font-size:11px;color:#9AA0AA;margin-top:3px;">${escapeHtml(m.subtitle)}</div>`
    : "";
  const address = m.address
    ? `<div style="font-size:11px;color:#9AA0AA;margin-top:2px;line-height:1.35;">${escapeHtml(m.address)}</div>`
    : "";
  wrap.innerHTML = `<div style="margin-bottom:34px;">
    <div style="position:relative;min-width:150px;max-width:240px;background:#1B1E26;border:1px solid #2A2E38;border-radius:12px;padding:9px 12px;box-shadow:0 8px 24px rgba(0,0,0,.5);">
      <div style="display:flex;align-items:center;gap:6px;">
        <strong style="font-size:13px;color:#E6E8EC;line-height:1.3;">${escapeHtml(m.label)}</strong>
        ${dist}
      </div>
      ${subtitle}
      ${address}
      <div style="position:absolute;left:50%;bottom:-6px;width:11px;height:11px;background:#1B1E26;border-right:1px solid #2A2E38;border-bottom:1px solid #2A2E38;transform:translateX(-50%) rotate(45deg);"></div>
    </div>
  </div>`;
  return wrap;
}

/** Great-circle distance in km (for deriving the search radius from the viewport). */
function haversineKm(aLat: number, aLng: number, bLat: number, bLng: number): number {
  const R = 6371;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(bLat - aLat);
  const dLng = toRad(bLng - aLng);
  const s =
    Math.sin(dLat / 2) ** 2 + Math.cos(toRad(aLat)) * Math.cos(toRad(bLat)) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
}

export default function KakaoMap({
  center,
  markers,
  selectedId,
  onSelect,
  onSearchHere,
  heightClass = "h-full",
}: MapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;
  const onSearchHereRef = useRef(onSearchHere);
  onSearchHereRef.current = onSearchHere;
  const markersRef = useRef(markers);
  markersRef.current = markers;
  const selectedIdRef = useRef(selectedId);
  selectedIdRef.current = selectedId;
  const initialCenterRef = useRef(center); // only used to place the map on first create

  const mapRef = useRef<KakaoMapInstance | null>(null);
  const overlaysRef = useRef<KakaoOverlay[]>([]);
  const bubbleRef = useRef<KakaoOverlay | null>(null);
  const pinnedRef = useRef<number | undefined>(undefined);
  const baselineRef = useRef<{ lat: number; lng: number } | null>(null);
  const didFitRef = useRef(false);
  const [movedCenter, setMovedCenter] = useState<{ lat: number; lng: number } | null>(null);

  // Rebuild the marker overlays (+ info bubble) for the current marker set. Fits the
  // viewport only on the very first render; later refreshes keep the user's view.
  const renderMarkers = (kakao: KakaoNamespace, map: KakaoMapInstance) => {
    for (const o of overlaysRef.current) o.setMap(null);
    overlaysRef.current = [];
    pinnedRef.current = undefined;
    bubbleRef.current?.setMap(null);

    const bubble =
      bubbleRef.current ?? new kakao.maps.CustomOverlay({ yAnchor: 1, xAnchor: 0.5, zIndex: 10 });
    bubbleRef.current = bubble;
    const byId = new Map<number, { pos: KakaoLatLng; data: MapMarker }>();
    const show = (id: number) => {
      const hit = byId.get(id);
      if (!hit) return;
      bubble.setContent(bubbleElement(hit.data));
      bubble.setPosition(hit.pos);
      bubble.setMap(map);
    };
    const settle = () => (pinnedRef.current != null ? show(pinnedRef.current) : bubble.setMap(null));

    const list = markersRef.current;
    const bounds = new kakao.maps.LatLngBounds();
    for (const m of list) {
      const position = new kakao.maps.LatLng(m.lat, m.lng);
      byId.set(m.id, { pos: position, data: m });
      const content = pinElement(m.id === selectedIdRef.current);
      content.addEventListener("mouseover", () => show(m.id));
      content.addEventListener("mouseout", settle);
      content.addEventListener("click", () => {
        pinnedRef.current = m.id;
        onSelectRef.current?.(m.id);
        show(m.id);
      });
      const overlay = new kakao.maps.CustomOverlay({
        position,
        content,
        yAnchor: 1,
        xAnchor: 0.5,
        clickable: true,
      });
      overlay.setMap(map);
      overlaysRef.current.push(overlay);
      bounds.extend(position);
    }

    if (!didFitRef.current && list.length) {
      if (list.length > 1) {
        map.setBounds(bounds, 40, 40, 40, 40);
      } else {
        map.setCenter(new kakao.maps.LatLng(list[0].lat, list[0].lng));
        map.setLevel(4);
      }
      didFitRef.current = true;
      const c = map.getCenter();
      baselineRef.current = { lat: c.getLat(), lng: c.getLng() };
    }
  };

  // Create the map once, wire up map-level listeners, render the first markers.
  useEffect(() => {
    const appKey = process.env.NEXT_PUBLIC_KAKAO_MAP_KEY;
    if (!appKey) {
      // eslint-disable-next-line no-console
      console.error("Missing NEXT_PUBLIC_KAKAO_MAP_KEY — map disabled");
      return;
    }
    let cancelled = false;

    loadKakao(appKey)
      .then((kakao) => {
        kakao.maps.load(() => {
          if (cancelled || !containerRef.current || mapRef.current) return;
          const map = new kakao.maps.Map(containerRef.current, {
            center: new kakao.maps.LatLng(initialCenterRef.current.lat, initialCenterRef.current.lng),
            level: 3,
          });
          mapRef.current = map;

          kakao.maps.event.addListener(map, "click", () => {
            pinnedRef.current = undefined;
            bubbleRef.current?.setMap(null);
          });
          // The first idle (post initial fit) sets the baseline; panning past a small
          // threshold from it reveals the "search this area" button.
          kakao.maps.event.addListener(map, "idle", () => {
            if (cancelled) return;
            const c = map.getCenter();
            const here = { lat: c.getLat(), lng: c.getLng() };
            if (!baselineRef.current) {
              baselineRef.current = here;
              return;
            }
            const moved =
              Math.abs(here.lat - baselineRef.current.lat) > 5e-4 ||
              Math.abs(here.lng - baselineRef.current.lng) > 5e-4;
            setMovedCenter(moved ? here : null);
          });

          renderMarkers(kakao, map);
          setTimeout(() => map.relayout(), 0);
        });
      })
      .catch(() => {
        /* missing key / blocked domain / SDK unreachable — degrade to empty container */
      });

    return () => {
      cancelled = true;
      for (const o of overlaysRef.current) o.setMap(null);
      overlaysRef.current = [];
      bubbleRef.current?.setMap(null);
      bubbleRef.current = null;
      mapRef.current = null;
      baselineRef.current = null;
      didFitRef.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Refresh markers in place when the set changes (keeps the user's pan/zoom).
  useEffect(() => {
    const kakao = window.kakao;
    if (kakao?.maps && mapRef.current) renderMarkers(kakao, mapRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [markers.map((m) => `${m.id}:${m.lat}:${m.lng}`).join("|")]);

  const onSearchClick = () => {
    const map = mapRef.current;
    if (!map || !movedCenter) return;
    const ne = map.getBounds().getNorthEast();
    // Radius = centre → viewport corner, so the search covers what the user sees.
    const radiusKm = Math.min(20, Math.max(0.1, haversineKm(movedCenter.lat, movedCenter.lng, ne.getLat(), ne.getLng())));
    baselineRef.current = movedCenter; // searched here — hide the button until the next pan
    onSearchHereRef.current?.({ lat: movedCenter.lat, lng: movedCenter.lng, radiusKm });
    setMovedCenter(null);
  };

  return (
    <div className={`relative ${heightClass} w-full`}>
      <div
        ref={containerRef}
        role="application"
        aria-label="지도"
        className="h-full w-full overflow-hidden rounded-card"
      />
      {movedCenter && onSearchHere && (
        <button
          type="button"
          onClick={onSearchClick}
          className="absolute left-1/2 top-3 z-10 flex -translate-x-1/2 items-center gap-1.5 rounded-full border border-border/60 bg-bg/90 px-3.5 py-2 text-xs font-bold text-active shadow-lg backdrop-blur-md transition-colors hover:bg-surface"
        >
          <RotateCw className="h-3.5 w-3.5 text-primary" />이 지역에서 재검색
        </button>
      )}
    </div>
  );
}

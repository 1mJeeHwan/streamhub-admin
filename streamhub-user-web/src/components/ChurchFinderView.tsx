"use client";

import { useEffect, useMemo, useState } from "react";
import { MapPin } from "lucide-react";
import MapProvider, { type MapMarker } from "@/components/map/MapProvider";
import { ChurchCard } from "@/components/ChurchCard";
import { SearchBar } from "@/components/SearchBar";
import { DemoBadge } from "@/components/DemoBadge";
import { CardSkeletonGrid, EmptyState, ErrorState } from "@/components/States";
import { useNearbyChurches } from "@/lib/churches";
import { getCurrentPosition, type Coords } from "@/lib/geolocation";
import { useDebounce } from "@/lib/useDebounce";
import { DENOMINATION_LABELS } from "@/lib/churchTypes";

const RADIUS_OPTIONS = [3, 5, 10, 20];

/** Client-side church finder: geolocation → distance search, with map + filters. */
export function ChurchFinderView() {
  const [coords, setCoords] = useState<Coords | null>(null);
  const [usingFallback, setUsingFallback] = useState(false);
  const [denomination, setDenomination] = useState("");
  const [radiusKm, setRadiusKm] = useState(5);
  const [keywordDraft, setKeywordDraft] = useState("");
  const keyword = useDebounce(keywordDraft, 350);
  const [hoveredId, setHoveredId] = useState<number | undefined>();

  // Resolve location once on mount; never blocks the UI (fallback is built in).
  useEffect(() => {
    let active = true;
    getCurrentPosition().then((r) => {
      if (!active) return;
      setCoords(r.coords);
      setUsingFallback(!r.granted);
    });
    return () => {
      active = false;
    };
  }, []);

  const params = useMemo(
    () => ({
      lat: coords?.lat,
      lng: coords?.lng,
      radiusKm,
      denomination: denomination || undefined,
      keyword: keyword || undefined,
      pageSize: 50,
    }),
    [coords, radiusKm, denomination, keyword],
  );

  const { data, isLoading, isError, error, refetch } = useNearbyChurches(params, coords != null);
  const churches = data?.contents ?? [];

  const markers: MapMarker[] = useMemo(
    () =>
      churches
        .filter((c) => c.latitude != null && c.longitude != null)
        .map((c) => ({ id: c.id, lat: c.latitude as number, lng: c.longitude as number, label: c.name })),
    [churches],
  );

  return (
    <div className="px-5 pb-4 pt-4">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-[22px] font-bold leading-tight">내 주변 교회</h1>
        <DemoBadge label="데모 데이터 · 실제 교회 정보 아님" />
      </div>
      {usingFallback && (
        <p className="mt-1.5 flex items-center gap-1 text-[11px] text-inactive">
          <MapPin className="h-3 w-3" />
          위치 사용이 거부되어 서울시청 기준으로 표시합니다.
        </p>
      )}

      {/* Filters */}
      <div className="mt-4 space-y-2.5">
        <SearchBar value={keywordDraft} onChange={setKeywordDraft} placeholder="교회명·지역 검색" />
        <div className="flex gap-2">
          <select
            value={denomination}
            onChange={(e) => setDenomination(e.target.value)}
            aria-label="교단 선택"
            className="input !pl-3 flex-1"
          >
            <option value="">전체 교단</option>
            {Object.entries(DENOMINATION_LABELS).map(([code, label]) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </select>
          <select
            value={radiusKm}
            onChange={(e) => setRadiusKm(Number(e.target.value))}
            aria-label="반경 선택"
            className="input !pl-3 w-28"
          >
            {RADIUS_OPTIONS.map((r) => (
              <option key={r} value={r}>
                반경 {r}km
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Map */}
      <div className="mt-4 h-56">
        {coords && (
          <MapProvider
            center={coords}
            markers={markers}
            selectedId={hoveredId}
            onSelect={setHoveredId}
            heightClass="h-56"
          />
        )}
      </div>

      {/* List */}
      <div className="mt-5">
        {isLoading || coords == null ? (
          <CardSkeletonGrid count={4} square />
        ) : isError ? (
          <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />
        ) : churches.length === 0 ? (
          <EmptyState message="조건에 맞는 교회가 없습니다. 반경을 넓혀보세요." />
        ) : (
          <>
            <p className="mb-2 text-xs text-inactive">{churches.length}곳 · 가까운 순</p>
            <div className="space-y-2.5">
              {churches.map((c) => (
                <ChurchCard key={c.id} church={c} active={c.id === hoveredId} onHover={setHoveredId} />
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

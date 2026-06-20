"use client";

import { useEffect, useMemo, useState } from "react";
import { MapPin, Move } from "lucide-react";
import MapProvider, { type MapMarker } from "@/components/map/MapProvider";
import { ChurchCard } from "@/components/ChurchCard";
import { SearchBar } from "@/components/SearchBar";
import { DemoBadge } from "@/components/DemoBadge";
import { CardSkeletonGrid, EmptyState, ErrorState } from "@/components/States";
import { useNearbyChurches } from "@/lib/churches";
import { getCurrentPosition, type Coords } from "@/lib/geolocation";
import { useDebounce } from "@/lib/useDebounce";
import { denominationLabel } from "@/lib/churchTypes";
import { formatDistance } from "@/lib/format";

// Initial radius (500m). After the user pans/zooms and taps "search this area",
// the radius follows the visible map extent instead of staying fixed.
const INITIAL_RADIUS_KM = 0.5;

/** Client-side church finder: geolocation → 500m search, map-driven re-search. */
export function ChurchFinderView() {
  const [usingFallback, setUsingFallback] = useState(false);
  // Where + how wide we search: starts at the resolved location / 500m, then both
  // follow the map when the user taps "search this area".
  const [searchCenter, setSearchCenter] = useState<Coords | null>(null);
  const [searchRadius, setSearchRadius] = useState(INITIAL_RADIUS_KM);
  const [keywordDraft, setKeywordDraft] = useState("");
  const keyword = useDebounce(keywordDraft, 350);
  const [hoveredId, setHoveredId] = useState<number | undefined>();

  // Resolve location once on mount; never blocks the UI (fallback is built in).
  useEffect(() => {
    let active = true;
    getCurrentPosition().then((r) => {
      if (!active) return;
      setSearchCenter(r.coords);
      setUsingFallback(!r.granted);
    });
    return () => {
      active = false;
    };
  }, []);

  const params = useMemo(
    () => ({
      lat: searchCenter?.lat,
      lng: searchCenter?.lng,
      radiusKm: searchRadius,
      keyword: keyword || undefined,
      pageSize: 50,
    }),
    [searchCenter, searchRadius, keyword],
  );

  const { data, isLoading, isError, error, refetch } = useNearbyChurches(params, searchCenter != null);
  const churches = data?.contents ?? [];

  const markers: MapMarker[] = useMemo(
    () =>
      churches
        .filter((c) => c.latitude != null && c.longitude != null)
        .map((c) => ({
          id: c.id,
          lat: c.latitude as number,
          lng: c.longitude as number,
          label: c.name,
          subtitle: c.dataSource === "KAKAO_POI" ? "카카오 지도에서 보기" : denominationLabel(c.denomination) || undefined,
          address: c.address ?? undefined,
          distanceText: formatDistance(c.distanceKm) || undefined,
        })),
    [churches],
  );

  // Show the demo badge only when the result set actually contains seed data — real Kakao
  // results (KAKAO / KAKAO_POI) drop it.
  const hasDemoData = churches.some((c) => c.dataSource === "SEED");

  return (
    <div className="px-5 pb-4 pt-4">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-24px font-bold leading-30px">내 주변 교회</h1>
        {hasDemoData && <DemoBadge label="데모 데이터 · 실제 교회 정보 아님" />}
      </div>
      {usingFallback && (
        <p className="mt-1.5 flex items-center gap-1 text-[11px] text-inactive">
          <MapPin className="h-3 w-3" />
          위치 사용이 거부되어 서울시청 기준으로 표시합니다.
        </p>
      )}

      {/* Keyword search */}
      <div className="mt-4">
        <SearchBar value={keywordDraft} onChange={setKeywordDraft} placeholder="교회명·지역 검색" />
      </div>

      {/* Map — primary control: pan + "search this area" */}
      <div className="mt-3">
        {searchCenter ? (
          <MapProvider
            center={searchCenter}
            markers={markers}
            selectedId={hoveredId}
            onSelect={setHoveredId}
            onSearchHere={({ lat, lng, radiusKm }) => {
              setSearchCenter({ lat, lng });
              setSearchRadius(radiusKm);
            }}
            heightClass="h-72"
          />
        ) : (
          <div className="skeleton h-72 w-full rounded-card" aria-busy="true" aria-label="지도 불러오는 중" />
        )}
        <p className="mt-1.5 flex items-center gap-1 text-[11px] text-inactive">
          <Move className="h-3 w-3 shrink-0" />
          지도를 움직여 다른 지역을 검색할 수 있어요 · 마커를 누르면 상세 정보가 보여요
        </p>
      </div>

      {/* List */}
      <div className="mt-5">
        {isLoading || searchCenter == null ? (
          <div aria-busy="true" aria-label="교회 목록 불러오는 중">
            <CardSkeletonGrid count={4} square />
          </div>
        ) : isError ? (
          <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />
        ) : churches.length === 0 ? (
          <EmptyState message="이 주변에 표시할 교회가 없습니다. 지도를 옮겨 다시 검색해보세요." />
        ) : (
          <>
            <p className="mb-2 text-xs text-inactive">
              {churches.length}곳 · 반경 {formatDistance(searchRadius)} · 가까운 순
            </p>
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

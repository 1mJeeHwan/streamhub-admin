"use client";

import { useEffect, useState } from "react";
import { Info, MapPin } from "lucide-react";
import { useStores } from "@/lib/albums";
import { getCurrentPosition, SEOUL_CITY_HALL, type Coords } from "@/lib/geolocation";
import { StoreMap } from "@/components/store/StoreMap";
import { StoreList } from "@/components/store/StoreList";
import { EmptyState, ErrorState } from "@/components/States";

/** Offline store finder: geolocation → distance-sorted list + map (key-less Leaflet/OSM). */
export default function StoresPage() {
  const [coords, setCoords] = useState<Coords | null>(null);
  const [usingFallback, setUsingFallback] = useState(false);
  const [selectedId, setSelectedId] = useState<number | undefined>(undefined);

  // Resolve location once on mount; never blocks (fallback is built in).
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

  const { data, isLoading, isError, error, refetch } = useStores({
    lat: coords?.lat,
    lng: coords?.lng,
  });

  const stores = data ?? [];
  const center = coords ?? SEOUL_CITY_HALL;

  return (
    <section className="animate-fade-up pt-4">
      <div className="flex items-center gap-2 px-5">
        <MapPin className="h-6 w-6 text-primary" />
        <h1 className="text-2xl font-bold tracking-tight">매장찾기</h1>
      </div>
      <p className="mt-1 px-5 text-sm text-inactive">가까운 직영 매장을 지도와 목록에서 확인하세요.</p>

      <p className="mx-5 mt-3 flex items-start gap-1.5 rounded-lg border border-border bg-surface px-3 py-2 text-[11px] text-inactive">
        <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" />
        데모용 가상 매장 정보입니다(실제 상호·연락처·좌표 아님).
        {usingFallback && " 위치 권한이 없어 서울시청 기준으로 정렬합니다."}
      </p>

      {coords && (
        <div className="mt-4">
          <StoreMap
            stores={stores}
            center={center}
            selectedId={selectedId}
            onSelect={setSelectedId}
          />
        </div>
      )}

      <div className="mt-4">
        {isLoading || coords == null ? (
          <div className="space-y-2.5 px-5">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="skeleton h-24 w-full rounded-card" />
            ))}
          </div>
        ) : isError ? (
          <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />
        ) : stores.length === 0 ? (
          <EmptyState message="등록된 매장이 없습니다." />
        ) : (
          <StoreList stores={stores} selectedId={selectedId} onSelect={setSelectedId} />
        )}
      </div>
    </section>
  );
}

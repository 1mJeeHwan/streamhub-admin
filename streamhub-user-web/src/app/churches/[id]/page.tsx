"use client";

import Link from "next/link";
import { CalendarClock, ExternalLink, Globe, Navigation, Phone, UserRound } from "lucide-react";
import MapProvider from "@/components/map/MapProvider";
import { BackLink } from "@/components/BackLink";
import { DemoBadge } from "@/components/DemoBadge";
import { EmptyState, ErrorState } from "@/components/States";
import { useChurchDetail } from "@/lib/churches";
import { denominationLabel, facilityLabels, WORSHIP_KIND_LABELS } from "@/lib/churchTypes";

function DetailSkeleton() {
  return (
    <div className="animate-fade-up px-5 pt-4">
      <div className="skeleton h-44 w-full rounded-card" />
      <div className="skeleton mt-4 h-7 w-2/3 rounded" />
      <div className="skeleton mt-2 h-4 w-1/3 rounded" />
    </div>
  );
}

export default function ChurchDetailPage({ params }: { params: { id: string } }) {
  const churchId = Number(params.id);
  const { data: church, isLoading, isError, error, refetch } = useChurchDetail(churchId);

  if (isLoading) return <DetailSkeleton />;
  if (isError)
    return (
      <div className="pt-6">
        <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />
      </div>
    );
  if (!church) return <div className="pt-6"><EmptyState message="교회를 찾을 수 없습니다." /></div>;

  const facilities = facilityLabels(church.facilities);
  const hasGeo = church.latitude != null && church.longitude != null;
  // Key-free directions deep links (no SDK).
  const kakaoTo = hasGeo
    ? `https://map.kakao.com/link/to/${encodeURIComponent(church.name)},${church.latitude},${church.longitude}`
    : null;
  const googleTo = hasGeo
    ? `https://www.google.com/maps/dir/?api=1&destination=${church.latitude},${church.longitude}`
    : null;

  return (
    <article className="animate-fade-up px-5 pb-6 pt-4">
      <BackLink href="/churches" label="교회찾기" />

      {/* Hero image */}
      <div className="thumb mt-3 aspect-[16/9] w-full rounded-card">
        {church.thumbnailUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={church.thumbnailUrl} alt={church.name} className="h-full w-full object-cover" />
        ) : (
          <div className="grid h-full w-full place-items-center text-noimg">
            <Navigation className="h-8 w-8" />
          </div>
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <h1 className="text-2xl font-bold leading-tight">{church.name}</h1>
        {church.demoData && <DemoBadge label="데모 데이터 · 실제 교회 정보 아님" />}
      </div>
      <p className="mt-1 text-sm text-inactive">
        {denominationLabel(church.denomination)}
        {church.regionName ? ` · ${church.regionName}` : ""}
      </p>

      {church.introduction && (
        <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-active/90">{church.introduction}</p>
      )}

      {/* Quick info */}
      <div className="mt-4 divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
        {church.address && (
          <div className="flex items-start gap-3 px-4 py-3">
            <Navigation className="mt-0.5 h-4 w-4 shrink-0 text-inactive" />
            <span className="text-sm text-active">
              {church.address}
              {church.addressDetail ? ` ${church.addressDetail}` : ""}
            </span>
          </div>
        )}
        {church.phone && (
          <a href={`tel:${church.phone}`} className="flex items-center gap-3 px-4 py-3 active:bg-card">
            <Phone className="h-4 w-4 shrink-0 text-inactive" />
            <span className="text-sm font-medium text-primary">{church.phone}</span>
          </a>
        )}
        {church.pastorName && (
          <div className="flex items-center gap-3 px-4 py-3">
            <UserRound className="h-4 w-4 shrink-0 text-inactive" />
            <span className="text-sm text-active">{church.pastorName}</span>
          </div>
        )}
        {church.homepageUrl && (
          <a
            href={church.homepageUrl}
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-3 px-4 py-3 active:bg-card"
          >
            <Globe className="h-4 w-4 shrink-0 text-inactive" />
            <span className="ellipsis-1 text-sm text-primary">{church.homepageUrl}</span>
          </a>
        )}
      </div>

      {/* Facilities */}
      {facilities.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-1.5">
          {facilities.map((f) => (
            <span key={f} className="pill !h-7 !text-xs text-active" data-active="false">
              {f}
            </span>
          ))}
        </div>
      )}

      {/* Worship times */}
      {church.worshipTimes.length > 0 && (
        <section className="mt-6">
          <h2 className="flex items-center gap-1.5 text-base font-bold">
            <CalendarClock className="h-4 w-4 text-primary" />
            예배 안내
          </h2>
          <div className="mt-2 overflow-hidden rounded-card border border-border/70">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-surface text-left text-xs text-inactive">
                  <th className="px-3 py-2 font-medium">예배</th>
                  <th className="px-3 py-2 font-medium">요일</th>
                  <th className="px-3 py-2 font-medium">시간</th>
                  <th className="px-3 py-2 font-medium">장소</th>
                </tr>
              </thead>
              <tbody>
                {church.worshipTimes.map((w, i) => (
                  <tr key={i} className="border-t border-border/50">
                    <td className="px-3 py-2 font-medium text-active">
                      {WORSHIP_KIND_LABELS[w.kind] ?? w.kind}
                    </td>
                    <td className="px-3 py-2 text-inactive">{w.dayLabel}</td>
                    <td className="px-3 py-2 text-active">{w.startTime}</td>
                    <td className="px-3 py-2 text-inactive">{w.place || "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* Map + directions */}
      {hasGeo && (
        <section className="mt-6">
          <div className="h-52">
            <MapProvider
              center={{ lat: church.latitude as number, lng: church.longitude as number }}
              markers={[
                {
                  id: church.id,
                  lat: church.latitude as number,
                  lng: church.longitude as number,
                  label: church.name,
                },
              ]}
              heightClass="h-52"
            />
          </div>
          <div className="mt-3 flex gap-2">
            {kakaoTo && (
              <a href={kakaoTo} target="_blank" rel="noreferrer" className="btn-primary flex-1 !py-2.5 text-sm">
                <ExternalLink className="h-4 w-4" />
                카카오맵 길찾기
              </a>
            )}
            {googleTo && (
              <a
                href={googleTo}
                target="_blank"
                rel="noreferrer"
                className="flex flex-1 items-center justify-center gap-2 rounded-xl border border-border py-2.5 text-sm font-medium text-active active:bg-card"
              >
                <ExternalLink className="h-4 w-4" />
                구글맵
              </a>
            )}
          </div>
        </section>
      )}

      {/* CTA: worship / new-family registration */}
      <Link
        href={`/churches/${church.id}/register`}
        className="btn-primary mt-6 w-full !py-3.5 text-base"
      >
        예배·새가족 신청
      </Link>
    </article>
  );
}

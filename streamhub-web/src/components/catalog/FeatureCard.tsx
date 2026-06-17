import Image from "next/image";
import Link from "next/link";
import { ArrowUpRight, Code2 } from "lucide-react";

import {
  domainMeta,
  type FeatureCard as FeatureCardData,
} from "@/lib/features.catalog";
import CatalogStatusBadge from "@/components/catalog/CatalogStatusBadge";

interface FeatureCardProps {
  feature: FeatureCardData;
}

const GITHUB_REPO_BASE =
  "https://github.com/imjihwan/streamhub-admin/blob/main/";

/**
 * FeatureCard renders one admin screen as a gallery tile: thumbnail with a
 * hover zoom, domain icon + title + honesty badge, summary, highlight chips,
 * and an action row. Only `live` cards navigate; `mock`/`wip` show a disabled
 * 열기 button (tooltip) so viewers never hit a 404.
 */
export default function FeatureCard({ feature }: FeatureCardProps) {
  const meta = domainMeta(feature.domain);
  const DomainIcon = meta.icon;
  const isLive = feature.status === "live";

  return (
    <div className="group flex flex-col overflow-hidden rounded-md border border-slate-200 bg-white transition hover:border-brand hover:shadow-md">
      {/* Thumbnail */}
      <div className="relative aspect-video overflow-hidden bg-slate-100">
        <Image
          src={feature.thumb}
          alt={`${feature.title} 미리보기`}
          fill
          sizes="(max-width: 640px) 100vw, (max-width: 1280px) 50vw, 25vw"
          className="object-cover transition duration-300 group-hover:scale-105"
        />
      </div>

      {/* Body */}
      <div className="flex flex-1 flex-col gap-2 p-4">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-1.5">
            <DomainIcon className="h-4 w-4 shrink-0 text-slate-400" />
            <h3 className="text-sm font-semibold text-slate-900">
              {feature.title}
            </h3>
          </div>
          <CatalogStatusBadge status={feature.status} />
        </div>

        <p className="text-xs text-slate-500">{feature.summary}</p>

        {feature.highlights.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {feature.highlights.map((highlight) => (
              <span
                key={highlight}
                className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-600"
              >
                {highlight}
              </span>
            ))}
          </div>
        )}

        {feature.gnuboard && (
          <p className="text-[11px] text-slate-400">출처 · {feature.gnuboard}</p>
        )}

        {/* Actions */}
        <div className="mt-auto flex items-center gap-2 pt-2">
          {isLive ? (
            <Link
              href={feature.href}
              className="inline-flex items-center gap-1 rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white transition hover:bg-brand-dark"
            >
              열기
              <ArrowUpRight className="h-3.5 w-3.5" />
            </Link>
          ) : (
            <button
              type="button"
              disabled
              title="목업 — 준비 중인 화면"
              className="inline-flex cursor-not-allowed items-center gap-1 rounded-md bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-400 opacity-60"
            >
              열기
            </button>
          )}

          {feature.repoPath && (
            <a
              href={`${GITHUB_REPO_BASE}${feature.repoPath}`}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-100"
            >
              <Code2 className="h-3.5 w-3.5" />
              코드 보기
            </a>
          )}
        </div>
      </div>
    </div>
  );
}

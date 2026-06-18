"use client";

import { useMemo, useState } from "react";

import {
  FEATURES,
  LIVE_COUNT,
  DEMO_COUNT,
  EXTERNAL_COUNT,
  type Domain,
  type FeatureStatus,
} from "@/lib/features.catalog";
import FilterChips from "@/components/catalog/FilterChips";
import FeatureCard from "@/components/catalog/FeatureCard";

export default function CatalogPage() {
  const [domain, setDomain] = useState<Domain | "ALL">("ALL");
  const [status, setStatus] = useState<FeatureStatus | "ALL">("ALL");
  const [keyword, setKeyword] = useState("");

  const visible = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return FEATURES.filter(
      (feature) =>
        (domain === "ALL" || feature.domain === domain) &&
        (status === "ALL" || feature.status === status) &&
        (kw === "" ||
          feature.title.toLowerCase().includes(kw) ||
          feature.summary.toLowerCase().includes(kw) ||
          feature.highlights.some((highlight) =>
            highlight.toLowerCase().includes(kw),
          )),
    );
  }, [domain, status, keyword]);

  return (
    <div>
      {/* Header */}
      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">기능 카탈로그</h1>
        <p className="mt-1 text-sm text-slate-500">
          7개 운영 도메인에 걸친 관리 화면을 한 곳에서 둘러봅니다.
        </p>
        <p className="mt-2 inline-flex items-center gap-1.5 rounded-md bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-700">
          🟡 데모 데이터 · 일부 화면은 목업입니다 (✅ 표시만 실제 동작)
        </p>
      </div>

      {/* Filters + search */}
      <div className="mb-4 space-y-4 rounded-md border border-slate-200 bg-white p-4">
        <FilterChips
          selectedDomain={domain}
          selectedStatus={status}
          onDomainChange={setDomain}
          onStatusChange={setStatus}
        />
        <div className="flex flex-col">
          <label
            htmlFor="catalog-keyword"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            검색어
          </label>
          <input
            id="catalog-keyword"
            type="text"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="화면명 / 설명 / 기술 태그"
            className="w-72 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          />
        </div>
      </div>

      {/* Count */}
      <p className="mb-3 text-sm text-slate-600">
        총 {FEATURES.length}개 · ✅ 실동작 {LIVE_COUNT} · 🟡 데모 {DEMO_COUNT} · 🔌 외부연동 대기{" "}
        {EXTERNAL_COUNT}
        {visible.length !== FEATURES.length && ` · 현재 필터 ${visible.length}개`}
      </p>

      {/* Grid */}
      {visible.length === 0 ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-slate-400">조건에 맞는 화면이 없습니다.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {visible.map((feature) => (
            <FeatureCard key={feature.id} feature={feature} />
          ))}
        </div>
      )}
    </div>
  );
}

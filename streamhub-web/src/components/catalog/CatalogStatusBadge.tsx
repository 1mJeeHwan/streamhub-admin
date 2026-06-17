import { STATUS_META, type FeatureStatus } from "@/lib/features.catalog";

interface CatalogStatusBadgeProps {
  status?: string;
}

const FALLBACK_CLASS = "bg-slate-200 text-slate-600";

/**
 * CatalogStatusBadge renders an honesty pill (✅ 실동작 / 🟡 목업 / 🔧 진행중)
 * for a feature card. Mirrors member/StatusBadge: unknown values fall back to a
 * neutral badge so a typo never crashes the grid.
 */
export default function CatalogStatusBadge({ status }: CatalogStatusBadgeProps) {
  const meta = status ? STATUS_META[status as FeatureStatus] : undefined;

  const label = meta?.label ?? status ?? "-";
  const emoji = meta?.emoji ?? "";
  const className = meta?.className ?? FALLBACK_CLASS;

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${className}`}
    >
      {emoji && <span aria-hidden="true">{emoji}</span>}
      {label}
    </span>
  );
}

import {
  DOMAIN_META,
  STATUS_META,
  type Domain,
  type FeatureStatus,
} from "@/lib/features.catalog";

interface FilterChipsProps {
  selectedDomain: Domain | "ALL";
  selectedStatus: FeatureStatus | "ALL";
  onDomainChange: (domain: Domain | "ALL") => void;
  onStatusChange: (status: FeatureStatus | "ALL") => void;
}

const STATUS_ORDER: FeatureStatus[] = ["live", "demo", "external"];

function chipClass(active: boolean): string {
  return `inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-medium transition ${
    active
      ? "bg-brand text-white"
      : "border border-slate-200 bg-white text-slate-600 hover:bg-slate-100"
  }`;
}

/**
 * FilterChips renders two chip rows — domain (7 + 전체) and honesty status
 * (live/mock/wip + 전체). Active chips reuse the Sidebar's `bg-brand text-white`
 * convention. Selection state is fully controlled by the parent page.
 */
export default function FilterChips({
  selectedDomain,
  selectedStatus,
  onDomainChange,
  onStatusChange,
}: FilterChipsProps) {
  return (
    <div className="space-y-3">
      {/* Domain row */}
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => onDomainChange("ALL")}
          className={chipClass(selectedDomain === "ALL")}
        >
          전체 도메인
        </button>
        {DOMAIN_META.map((meta) => {
          const Icon = meta.icon;
          return (
            <button
              key={meta.key}
              type="button"
              onClick={() => onDomainChange(meta.key)}
              className={chipClass(selectedDomain === meta.key)}
            >
              <Icon className="h-4 w-4" />
              {meta.label}
            </button>
          );
        })}
      </div>

      {/* Status row */}
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => onStatusChange("ALL")}
          className={chipClass(selectedStatus === "ALL")}
        >
          전체 상태
        </button>
        {STATUS_ORDER.map((key) => {
          const meta = STATUS_META[key];
          return (
            <button
              key={key}
              type="button"
              onClick={() => onStatusChange(key)}
              className={chipClass(selectedStatus === key)}
            >
              <span aria-hidden="true">{meta.emoji}</span>
              {meta.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

type SourceType = "MANUAL" | "EXPIRE" | "DONATION";
type LedgerStatus = "ACTIVE" | "EXPIRED";

const SOURCE_CONFIG: Record<
  SourceType,
  { label: string; className: string }
> = {
  MANUAL: {
    label: "수동",
    className: "bg-indigo-100 text-indigo-700",
  },
  EXPIRE: {
    label: "만료",
    className: "bg-slate-200 text-slate-600",
  },
  DONATION: {
    label: "후원",
    className: "bg-violet-100 text-violet-700",
  },
};

const STATUS_CONFIG: Record<
  LedgerStatus,
  { label: string; className: string }
> = {
  ACTIVE: {
    label: "유효",
    className: "bg-emerald-100 text-emerald-700",
  },
  EXPIRED: {
    label: "만료",
    className: "bg-slate-200 text-slate-600",
  },
};

const BADGE_BASE =
  "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium";
const NEUTRAL = "bg-slate-200 text-slate-600";

interface BadgeProps {
  value?: string;
}

/**
 * SourceTypeBadge renders a colored pill for a ledger source (수동 / 만료 / 후원).
 * Unknown values fall back to a neutral badge showing the raw value.
 */
export function SourceTypeBadge({ value }: BadgeProps) {
  const config = value ? SOURCE_CONFIG[value as SourceType] : undefined;
  const label = config?.label ?? value ?? "-";
  const className = config?.className ?? NEUTRAL;

  return <span className={`${BADGE_BASE} ${className}`}>{label}</span>;
}

/**
 * LedgerStatusBadge renders a colored pill for a ledger status (유효 / 만료).
 * Unknown values fall back to a neutral badge showing the raw value.
 */
export function LedgerStatusBadge({ value }: BadgeProps) {
  const config = value ? STATUS_CONFIG[value as LedgerStatus] : undefined;
  const label = config?.label ?? value ?? "-";
  const className = config?.className ?? NEUTRAL;

  return <span className={`${BADGE_BASE} ${className}`}>{label}</span>;
}

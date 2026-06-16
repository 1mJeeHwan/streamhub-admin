type ContentType = "VIDEO" | "SOUND";
type ContentStatus = "DRAFT" | "PUBLISHED";

const TYPE_CONFIG: Record<ContentType, { label: string; className: string }> = {
  VIDEO: {
    label: "영상",
    className: "bg-indigo-100 text-indigo-700",
  },
  SOUND: {
    label: "음원",
    className: "bg-sky-100 text-sky-700",
  },
};

const STATUS_CONFIG: Record<
  ContentStatus,
  { label: string; className: string }
> = {
  PUBLISHED: {
    label: "게시",
    className: "bg-emerald-100 text-emerald-700",
  },
  DRAFT: {
    label: "임시",
    className: "bg-amber-100 text-amber-700",
  },
};

const BADGE_BASE =
  "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium";
const NEUTRAL = "bg-slate-200 text-slate-600";

interface BadgeProps {
  value?: string;
}

/**
 * TypeBadge renders a colored pill for a content type (영상 / 음원). Unknown
 * values fall back to a neutral badge showing the raw value.
 */
export function TypeBadge({ value }: BadgeProps) {
  const config = value ? TYPE_CONFIG[value as ContentType] : undefined;
  const label = config?.label ?? value ?? "-";
  const className = config?.className ?? NEUTRAL;

  return <span className={`${BADGE_BASE} ${className}`}>{label}</span>;
}

/**
 * ContentStatusBadge renders a colored pill for a content status (게시 / 임시).
 * Unknown values fall back to a neutral badge showing the raw value.
 */
export function ContentStatusBadge({ value }: BadgeProps) {
  const config = value ? STATUS_CONFIG[value as ContentStatus] : undefined;
  const label = config?.label ?? value ?? "-";
  const className = config?.className ?? NEUTRAL;

  return <span className={`${BADGE_BASE} ${className}`}>{label}</span>;
}

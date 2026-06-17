type GoodsStatus = "SELLING" | "PAUSED";

const STATUS_CONFIG: Record<GoodsStatus, { label: string; className: string }> =
  {
    SELLING: {
      label: "판매중",
      className: "bg-emerald-100 text-emerald-700",
    },
    PAUSED: {
      label: "판매중지",
      className: "bg-amber-100 text-amber-700",
    },
  };

const BADGE_BASE =
  "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium";
const NEUTRAL = "bg-slate-200 text-slate-600";

interface GoodsStatusBadgeProps {
  value?: string;
}

/**
 * GoodsStatusBadge renders a colored pill for a goods status (판매중 / 판매중지).
 * Unknown values fall back to a neutral badge showing the raw value.
 */
export function GoodsStatusBadge({ value }: GoodsStatusBadgeProps) {
  const config = value ? STATUS_CONFIG[value as GoodsStatus] : undefined;
  const label = config?.label ?? value ?? "-";
  const className = config?.className ?? NEUTRAL;

  return <span className={`${BADGE_BASE} ${className}`}>{label}</span>;
}

interface SoldOutBadgeProps {
  value?: string;
}

/**
 * SoldOutBadge renders a red 품절 pill when value is "Y"; otherwise 판매 (slate).
 */
export function SoldOutBadge({ value }: SoldOutBadgeProps) {
  const isSoldOut = value === "Y";
  return (
    <span
      className={`${BADGE_BASE} ${
        isSoldOut ? "bg-red-100 text-red-700" : "bg-slate-100 text-slate-600"
      }`}
    >
      {isSoldOut ? "품절" : "정상"}
    </span>
  );
}

import {
  DONATION_TYPE_COLOR,
  DONATION_TYPE_LABEL,
  type DonationType,
} from "@/lib/subscription-status";

interface DonationTypeBadgeProps {
  type?: string;
}

/**
 * DonationTypeBadge renders a colored pill for a donation type
 * (단건 / 구독). Unknown values fall back to a neutral badge.
 */
export default function DonationTypeBadge({ type }: DonationTypeBadgeProps) {
  const key = type as DonationType | undefined;
  const label = (key && DONATION_TYPE_LABEL[key]) ?? type ?? "-";
  const className =
    (key && DONATION_TYPE_COLOR[key]) ?? "bg-slate-200 text-slate-600";

  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${className}`}
    >
      {label}
    </span>
  );
}

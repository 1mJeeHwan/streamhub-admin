import {
  DONATION_STATUS_COLOR,
  DONATION_STATUS_LABEL,
  type DonationStatus,
} from "@/lib/subscription-status";

interface DonationStatusBadgeProps {
  status?: string;
}

/**
 * DonationStatusBadge renders a colored pill for a donation payment status
 * (결제완료 / 취소 / 실패). Unknown values fall back to a neutral badge.
 */
export default function DonationStatusBadge({
  status,
}: DonationStatusBadgeProps) {
  const key = status as DonationStatus | undefined;
  const label = (key && DONATION_STATUS_LABEL[key]) ?? status ?? "-";
  const className =
    (key && DONATION_STATUS_COLOR[key]) ?? "bg-slate-200 text-slate-600";

  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${className}`}
    >
      {label}
    </span>
  );
}

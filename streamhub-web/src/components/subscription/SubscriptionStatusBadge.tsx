import {
  SUBSCRIPTION_STATUS_COLOR,
  SUBSCRIPTION_STATUS_LABEL,
  type SubscriptionStatus,
} from "@/lib/subscription-status";

interface SubscriptionStatusBadgeProps {
  status?: string;
}

/**
 * SubscriptionStatusBadge renders a colored pill for one of the three
 * subscription lifecycle statuses (활성 / 일시정지 / 해지). Unknown values
 * fall back to a neutral badge showing the raw string.
 */
export default function SubscriptionStatusBadge({
  status,
}: SubscriptionStatusBadgeProps) {
  const key = status as SubscriptionStatus | undefined;
  const label = (key && SUBSCRIPTION_STATUS_LABEL[key]) ?? status ?? "-";
  const className =
    (key && SUBSCRIPTION_STATUS_COLOR[key]) ?? "bg-slate-200 text-slate-600";

  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${className}`}
    >
      {label}
    </span>
  );
}

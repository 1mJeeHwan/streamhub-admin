import { STATUS_COLOR, STATUS_LABEL, type OrderStatus } from "@/lib/order-status";

interface OrderStatusBadgeProps {
  status?: string;
}

/**
 * OrderStatusBadge renders a colored pill for one of the 7 order statuses.
 * Unknown values fall back to a neutral badge showing the raw string.
 */
export default function OrderStatusBadge({ status }: OrderStatusBadgeProps) {
  const key = status as OrderStatus | undefined;
  const label = (key && STATUS_LABEL[key]) ?? status ?? "-";
  const className =
    (key && STATUS_COLOR[key]) ?? "bg-slate-200 text-slate-600";

  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${className}`}
    >
      {label}
    </span>
  );
}

type MemberStatus = "PENDING" | "CONFIRMED" | "INACTIVE";

const STATUS_CONFIG: Record<
  MemberStatus,
  { label: string; className: string }
> = {
  PENDING: {
    label: "대기",
    className: "bg-amber-100 text-amber-700",
  },
  CONFIRMED: {
    label: "승인",
    className: "bg-emerald-100 text-emerald-700",
  },
  INACTIVE: {
    label: "비활성",
    className: "bg-slate-200 text-slate-600",
  },
};

interface StatusBadgeProps {
  status?: string;
}

/**
 * StatusBadge renders a colored pill for a member's userStatus. Unknown values
 * fall back to a neutral badge showing the raw status string.
 */
export default function StatusBadge({ status }: StatusBadgeProps) {
  const config = status
    ? STATUS_CONFIG[status as MemberStatus]
    : undefined;

  const label = config?.label ?? status ?? "-";
  const className = config?.className ?? "bg-slate-200 text-slate-600";

  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${className}`}
    >
      {label}
    </span>
  );
}

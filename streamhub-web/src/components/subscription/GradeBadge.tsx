import {
  GRADE_COLOR,
  GRADE_LABEL,
  type PlanGrade,
} from "@/lib/subscription-status";

interface GradeBadgeProps {
  grade?: string;
}

/**
 * GradeBadge renders a colored pill for a membership plan grade
 * (브론즈 / 실버 / 골드 / 후원천사). Unknown values fall back to a neutral
 * badge showing the raw string.
 */
export default function GradeBadge({ grade }: GradeBadgeProps) {
  const key = grade as PlanGrade | undefined;
  const label = (key && GRADE_LABEL[key]) ?? grade ?? "-";
  const className = (key && GRADE_COLOR[key]) ?? "bg-slate-200 text-slate-600";

  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${className}`}
    >
      {label}
    </span>
  );
}

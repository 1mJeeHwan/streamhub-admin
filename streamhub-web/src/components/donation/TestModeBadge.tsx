import { FlaskConical } from "lucide-react";

/**
 * TestModeBadge marks the donation/subscription domain as running in
 * payment "test mode" — no real PG is wired and billing keys are masked
 * demo values. Required by the spec (§9, §11) to prevent payment confusion.
 */
export default function TestModeBadge() {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-700">
      <FlaskConical className="h-3 w-3" />
      테스트 모드
    </span>
  );
}

/** Shared label/color maps for subscription, plan grade, and donation domains. */

/** Subscription lifecycle statuses (ACTIVE / PAUSED / CANCELED). */
export type SubscriptionStatus = "ACTIVE" | "PAUSED" | "CANCELED";

export const SUBSCRIPTION_STATUS_LABEL: Record<SubscriptionStatus, string> = {
  ACTIVE: "활성",
  PAUSED: "일시정지",
  CANCELED: "해지",
};

export const SUBSCRIPTION_STATUS_COLOR: Record<SubscriptionStatus, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-700",
  PAUSED: "bg-amber-100 text-amber-700",
  CANCELED: "bg-slate-200 text-slate-600",
};

/** Membership plan grades (shared by plan / subscription). */
export type PlanGrade = "BRONZE" | "SILVER" | "GOLD" | "ANGEL";

export const GRADE_LABEL: Record<PlanGrade, string> = {
  BRONZE: "브론즈",
  SILVER: "실버",
  GOLD: "골드",
  ANGEL: "후원천사",
};

export const GRADE_COLOR: Record<PlanGrade, string> = {
  BRONZE: "bg-orange-100 text-orange-700",
  SILVER: "bg-slate-200 text-slate-700",
  GOLD: "bg-yellow-100 text-yellow-700",
  ANGEL: "bg-fuchsia-100 text-fuchsia-700",
};

/** Donation payment statuses (PAID / CANCELED / FAILED). */
export type DonationStatus = "PAID" | "CANCELED" | "FAILED";

export const DONATION_STATUS_LABEL: Record<DonationStatus, string> = {
  PAID: "결제완료",
  CANCELED: "취소",
  FAILED: "실패",
};

export const DONATION_STATUS_COLOR: Record<DonationStatus, string> = {
  PAID: "bg-emerald-100 text-emerald-700",
  CANCELED: "bg-slate-200 text-slate-600",
  FAILED: "bg-red-100 text-red-700",
};

/** Donation types (단건 / 구독 회차). */
export type DonationType = "ONCE" | "SUBSCRIPTION";

export const DONATION_TYPE_LABEL: Record<DonationType, string> = {
  ONCE: "단건",
  SUBSCRIPTION: "구독",
};

export const DONATION_TYPE_COLOR: Record<DonationType, string> = {
  ONCE: "bg-sky-100 text-sky-700",
  SUBSCRIPTION: "bg-indigo-100 text-indigo-700",
};

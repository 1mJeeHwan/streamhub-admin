/**
 * Order status presentation + transition rules for the order admin screens.
 *
 * The authoritative state machine lives in the backend
 * (`OrderService.TRANSITIONS`). `ALLOWED_TRANSITIONS` here is a UX-only mirror
 * used to render the right transition buttons — any illegal transition the
 * frontend might still send is rejected by the backend with INVALID_PARAMETER.
 * Keep both copies in sync (spec §4.2 / §7).
 */

/** Canonical order status keys (matches backend OrderStatus enum). */
export type OrderStatus =
  | "PLACED"
  | "PAID"
  | "READY"
  | "SHIPPING"
  | "DONE"
  | "CANCEL"
  | "RETURN";

/** Korean labels for each status. */
export const STATUS_LABEL: Record<OrderStatus, string> = {
  PLACED: "주문",
  PAID: "입금",
  READY: "배송준비",
  SHIPPING: "배송중",
  DONE: "완료",
  CANCEL: "취소",
  RETURN: "반품",
};

/** Tailwind badge classes for each status. */
export const STATUS_COLOR: Record<OrderStatus, string> = {
  PLACED: "bg-slate-100 text-slate-700",
  PAID: "bg-blue-100 text-blue-700",
  READY: "bg-amber-100 text-amber-700",
  SHIPPING: "bg-indigo-100 text-indigo-700",
  DONE: "bg-emerald-100 text-emerald-700",
  CANCEL: "bg-red-100 text-red-700",
  RETURN: "bg-red-100 text-red-700",
};

/** Allowed forward/branch transitions per status (UX guard only). */
export const ALLOWED_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  PLACED: ["PAID", "CANCEL"],
  PAID: ["READY", "CANCEL"],
  READY: ["SHIPPING", "CANCEL"],
  SHIPPING: ["DONE", "RETURN"],
  DONE: ["RETURN"],
  CANCEL: [],
  RETURN: [],
};

/**
 * Ordered "happy path" for the stepper visualization. Branch states
 * (CANCEL/RETURN) are not part of the linear flow and are shown separately.
 */
export const STATUS_FLOW: OrderStatus[] = [
  "PLACED",
  "PAID",
  "READY",
  "SHIPPING",
  "DONE",
];

/** Destructive transitions that require an explicit confirmation modal. */
export const DESTRUCTIVE_TRANSITIONS: OrderStatus[] = ["CANCEL", "RETURN"];

/** True when the transition is destructive (CANCEL/RETURN) and needs a modal. */
export function isDestructive(status: OrderStatus): boolean {
  return DESTRUCTIVE_TRANSITIONS.includes(status);
}

/** Statuses in which a tracking number can be entered. */
export function isTrackingEnabled(status?: OrderStatus): boolean {
  return status === "READY" || status === "SHIPPING";
}

"use client";

import { Check } from "lucide-react";

import {
  ALLOWED_TRANSITIONS,
  STATUS_FLOW,
  STATUS_LABEL,
  isDestructive,
  type OrderStatus,
} from "@/lib/order-status";

interface OrderStatusStepperProps {
  /** Current order status. */
  status: OrderStatus;
  /** Whether a transition is in flight (disables the buttons). */
  pending?: boolean;
  /** Called when the operator picks a target status. */
  onTransition: (next: OrderStatus) => void;
}

/**
 * OrderStatusStepper visualizes the linear order flow
 * (PLACED → PAID → READY → SHIPPING → DONE) with the current step highlighted,
 * then renders one button per allowed transition. Destructive transitions
 * (CANCEL/RETURN) are styled red; the confirmation modal is owned by the caller.
 */
export default function OrderStatusStepper({
  status,
  pending = false,
  onTransition,
}: OrderStatusStepperProps) {
  // Branch states (CANCEL/RETURN) sit outside the linear flow; show the flow up
  // to the last reached step and mark the order as terminated when branched.
  const currentIndex = STATUS_FLOW.indexOf(status);
  const isBranched = currentIndex === -1; // CANCEL or RETURN
  const transitions = ALLOWED_TRANSITIONS[status] ?? [];

  return (
    <div className="space-y-5">
      {/* Linear flow visualization */}
      <ol className="flex items-center">
        {STATUS_FLOW.map((step, index) => {
          const reached = !isBranched && index <= currentIndex;
          const isCurrent = !isBranched && index === currentIndex;
          const isLast = index === STATUS_FLOW.length - 1;

          return (
            <li
              key={step}
              className="flex flex-1 items-center last:flex-none"
            >
              <div className="flex flex-col items-center">
                <span
                  className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-semibold transition ${
                    isCurrent
                      ? "bg-brand text-white ring-4 ring-brand/20"
                      : reached
                        ? "bg-brand text-white"
                        : "bg-slate-100 text-slate-400"
                  }`}
                >
                  {reached && !isCurrent ? (
                    <Check className="h-4 w-4" />
                  ) : (
                    index + 1
                  )}
                </span>
                <span
                  className={`mt-1.5 text-xs ${
                    isCurrent
                      ? "font-semibold text-slate-900"
                      : reached
                        ? "text-slate-600"
                        : "text-slate-400"
                  }`}
                >
                  {STATUS_LABEL[step]}
                </span>
              </div>
              {!isLast && (
                <span
                  className={`mx-2 h-0.5 flex-1 ${
                    !isBranched && index < currentIndex
                      ? "bg-brand"
                      : "bg-slate-200"
                  }`}
                />
              )}
            </li>
          );
        })}
      </ol>

      {isBranched && (
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm font-medium text-red-700">
          이 주문은 {STATUS_LABEL[status]} 처리되었습니다.
        </p>
      )}

      {/* Transition buttons */}
      {transitions.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {transitions.map((next) => {
            const destructive = isDestructive(next);
            return (
              <button
                key={next}
                type="button"
                disabled={pending}
                onClick={() => onTransition(next)}
                className={`rounded-md px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-60 ${
                  destructive
                    ? "border border-red-300 text-red-600 hover:bg-red-50"
                    : "bg-brand text-white hover:bg-brand-dark"
                }`}
              >
                {STATUS_LABEL[next]}(으)로 변경
              </button>
            );
          })}
        </div>
      ) : (
        <p className="text-sm text-slate-400">변경 가능한 상태가 없습니다.</p>
      )}
    </div>
  );
}

"use client";

import { useEffect, useState } from "react";
import { X, CheckCircle2, CreditCard, ShieldAlert, Loader2 } from "lucide-react";
import clsx from "clsx";

/**
 * Payment methods shown in the checkout modal. Logos are rendered as styled text labels — no
 * external PG SDK is loaded (this is a client-only demo). Backend seam: a real flow would POST
 * /v1/payment/request then /v1/payment/approve (see payment spec §3.2); here approval is faked.
 */
type PayMethod = "KAKAO" | "TOSS" | "PAYPAL" | "CARD";

interface MethodMeta {
  id: PayMethod;
  label: string;
  /** Tailwind text color class for the logo-style label. */
  className: string;
}

const METHODS: MethodMeta[] = [
  { id: "KAKAO", label: "kakao pay", className: "text-[#FFE812]" },
  { id: "TOSS", label: "toss", className: "text-[#3182F6]" },
  { id: "PAYPAL", label: "PayPal", className: "text-[#00457C]" },
  { id: "CARD", label: "신용·체크카드", className: "text-active" },
];

/** Official Toss test card number — placeholder only, never sent anywhere. */
const TEST_CARD = "4242 4242 4242 4242";

export interface CheckoutItem {
  /** Display name, e.g. "찬양 1집 앨범". */
  name: string;
  /** Price in KRW. */
  price: number;
  /** Optional quantity (defaults to 1). */
  quantity?: number;
}

type Stage = "select" | "processing" | "done";

function formatKRW(n: number): string {
  return `₩${n.toLocaleString("ko-KR")}`;
}

/** Formats raw digits into "1234 5678 9012 3456" groups. */
function formatCardNo(value: string): string {
  const digits = value.replace(/\D/g, "").slice(0, 16);
  return digits.replace(/(.{4})/g, "$1 ").trim();
}

/**
 * Mock checkout modal for album/goods purchases (C4). Lets the user pick a method, enter a test
 * card number, and "pay" — producing a fake approval receipt. Always shows a "테스트 모드 · 실결제
 * 아님" badge. Export it and open from any buy button via the `open`/`onClose` props.
 */
export function CheckoutModal({
  open,
  onClose,
  item,
}: {
  open: boolean;
  onClose: () => void;
  item: CheckoutItem;
}) {
  const [method, setMethod] = useState<PayMethod>("KAKAO");
  const [cardNo, setCardNo] = useState("");
  const [stage, setStage] = useState<Stage>("select");

  // Reset transient state whenever the modal is (re)opened.
  useEffect(() => {
    if (open) {
      setStage("select");
      setMethod("KAKAO");
      setCardNo("");
    }
  }, [open]);

  // Lock body scroll while open.
  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [open]);

  if (!open) return null;

  const quantity = item.quantity ?? 1;
  const total = item.price * quantity;
  const txnId = `MOCK-${Date.now().toString().slice(-8)}`;

  const pay = () => {
    // Backend seam: replace with paymentRequest()/paymentApprove() mutations once wired.
    setStage("processing");
    window.setTimeout(() => setStage("done"), 1100);
  };

  return (
    <div
      className="fixed inset-0 z-[60] flex items-end justify-center bg-black/60"
      role="dialog"
      aria-modal="true"
      aria-label="결제"
      onClick={onClose}
    >
      <div
        className="mx-auto flex max-h-[88dvh] w-full max-w-[480px] flex-col overflow-hidden rounded-t-2xl border-x border-t border-border bg-bg animate-fade-up"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border bg-surface px-4 py-3.5">
          <h2 className="text-base font-bold text-active">결제하기</h2>
          <button
            type="button"
            aria-label="닫기"
            onClick={onClose}
            className="rounded-lg p-1.5 text-inactive transition active:bg-card"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Test-mode banner (required) */}
        <div className="flex items-center gap-2 border-b border-border bg-point/10 px-4 py-2.5">
          <ShieldAlert className="h-4 w-4 shrink-0 text-point" />
          <p className="text-[12px] font-semibold leading-tight text-point">
            테스트 모드 · 실결제 아님 — 실제 PG 미연동(가짜 승인)
          </p>
        </div>

        <div className="flex-1 overflow-y-auto">
          {stage === "done" ? (
            <Receipt item={item} total={total} method={method} txnId={txnId} />
          ) : (
            <div className="space-y-5 px-4 py-4">
              {/* Order summary */}
              <div className="rounded-card border border-border bg-surface px-4 py-3.5">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-active">{item.name}</span>
                  <span className="text-sm text-inactive">x{quantity}</span>
                </div>
                <div className="mt-2.5 flex items-center justify-between border-t border-border pt-2.5">
                  <span className="text-sm font-medium text-inactive">결제 금액</span>
                  <span className="text-lg font-bold text-primary">{formatKRW(total)}</span>
                </div>
              </div>

              {/* Method select */}
              <div>
                <p className="mb-2 text-xs font-semibold text-inactive">결제 수단</p>
                <div className="grid grid-cols-2 gap-2">
                  {METHODS.map((m) => (
                    <button
                      key={m.id}
                      type="button"
                      onClick={() => setMethod(m.id)}
                      className={clsx(
                        "flex h-12 items-center justify-center rounded-xl border text-sm font-bold transition",
                        method === m.id
                          ? "border-primary bg-primary/10"
                          : "border-border bg-surface active:bg-card",
                        m.className,
                      )}
                    >
                      {m.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Test card input */}
              <div>
                <label className="mb-2 flex items-center gap-1.5 text-xs font-semibold text-inactive">
                  <CreditCard className="h-3.5 w-3.5" />
                  테스트 카드번호
                </label>
                <input
                  inputMode="numeric"
                  value={cardNo}
                  onChange={(e) => setCardNo(formatCardNo(e.target.value))}
                  placeholder={TEST_CARD}
                  className="w-full rounded-xl border border-border bg-surface px-3.5 py-3 font-mono text-sm tracking-wider text-active outline-none transition placeholder:text-inactive focus:border-primary"
                />
                <button
                  type="button"
                  onClick={() => setCardNo(TEST_CARD)}
                  className="mt-1.5 text-[11px] text-primary underline-offset-2 active:underline"
                >
                  테스트 카드번호 자동 입력 (4242…)
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Footer action */}
        {stage !== "done" && (
          <div className="border-t border-border bg-surface px-4 py-3">
            <button
              type="button"
              onClick={pay}
              disabled={stage === "processing"}
              className="btn-primary w-full disabled:opacity-60"
            >
              {stage === "processing" ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  승인 중…
                </>
              ) : (
                <>{formatKRW(total)} 결제하기</>
              )}
            </button>
          </div>
        )}
        {stage === "done" && (
          <div className="border-t border-border bg-surface px-4 py-3">
            <button type="button" onClick={onClose} className="btn-primary w-full">
              확인
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function Receipt({
  item,
  total,
  method,
  txnId,
}: {
  item: CheckoutItem;
  total: number;
  method: PayMethod;
  txnId: string;
}) {
  const methodLabel = METHODS.find((m) => m.id === method)?.label ?? method;
  return (
    <div className="px-4 py-6">
      <div className="flex flex-col items-center text-center">
        <CheckCircle2 className="h-14 w-14 text-primary" />
        <h3 className="mt-3 text-lg font-bold text-active">결제가 완료되었어요</h3>
        <p className="mt-1 text-xs text-inactive">MOCK 승인(실거래 아님)</p>
      </div>

      <dl className="mt-6 space-y-2.5 rounded-card border border-border bg-surface px-4 py-4 text-sm">
        <Row label="상품" value={item.name} />
        <Row label="결제 수단" value={methodLabel} />
        <Row label="거래번호" value={txnId} mono />
        <div className="my-1 border-t border-border" />
        <Row label="결제 금액" value={formatKRW(total)} strong />
      </dl>

      <p className="mt-4 text-center text-[11px] text-inactive">
        본 영수증은 데모용이며 실제 결제가 이뤄지지 않았습니다.
      </p>
    </div>
  );
}

function Row({
  label,
  value,
  mono,
  strong,
}: {
  label: string;
  value: string;
  mono?: boolean;
  strong?: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-inactive">{label}</dt>
      <dd
        className={clsx(
          "text-right",
          mono && "font-mono text-xs",
          strong ? "text-base font-bold text-primary" : "text-active",
        )}
      >
        {value}
      </dd>
    </div>
  );
}

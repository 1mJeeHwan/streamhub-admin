"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { X, CheckCircle2, CreditCard, ShieldAlert, Loader2, LogIn } from "lucide-react";
import clsx from "clsx";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import { loadTossPayments } from "@/lib/toss";
import {
  orderApi,
  orderKeys,
  ORDER_STATUS_LABELS,
  type OrderResult,
  type PayProvider,
} from "@/lib/orders";

/**
 * Payment methods shown in the checkout modal. TOSS is a REAL PG integration (Toss v2 sandbox):
 * selecting it runs prepare → Toss payment window → /checkout/success → confirm, which calls the
 * live Toss confirm API (test keys, no real charge). The other methods are mock one-shot orders
 * (POST /pub/v1/orders) — a genuine order row is created, but no PG window opens and no money moves.
 */
interface MethodMeta {
  id: PayProvider;
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

/**
 * Providers routed through the real PG flow (prepare → window/redirect → confirm). Only Toss has a
 * public sandbox key, so it's the only real one in the demo; KAKAO/PAYPAL adapters exist on the
 * backend and join here once their sandbox keys are configured. Everything else is mock one-shot.
 */
const REAL_PG_PROVIDERS = new Set<PayProvider>(["TOSS"]);

/** Official Toss test card number — placeholder only, never sent anywhere. */
const TEST_CARD = "4242 4242 4242 4242";

export interface CheckoutItem {
  /** Album id — the order is created for this album. */
  albumId: number;
  /** Display name, e.g. "찬양 1집 앨범". */
  name: string;
  /** Price in KRW (display only — the server computes the authoritative total). */
  price: number;
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
 * Checkout modal for album purchases (C4). Lets the user pick a method and "pay" — which makes a
 * real POST /pub/v1/orders call with the member token, creating an actual order (no money moves).
 * The receipt shows the real orderNo/status/total returned by the server. A logged-out user is
 * routed to login (with a return path). Always shows a "테스트 모드 · 실결제 아님" badge.
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
  const { member, token } = useAuth();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const [method, setMethod] = useState<PayProvider>("KAKAO");
  const [cardNo, setCardNo] = useState("");
  const [couponCode, setCouponCode] = useState("");
  const [stage, setStage] = useState<Stage>("select");
  const [order, setOrder] = useState<OrderResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [authRequired, setAuthRequired] = useState(false);
  // Portal target. The modal is rendered into <body> (below) so its `position: fixed`
  // is anchored to the viewport — not to a transformed ancestor (e.g. a page wrapper
  // with `animate-fade-up`, whose lingering transform would otherwise make the bottom
  // sheet pin to the page's hidden bottom instead of the screen).
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  // Reset transient state whenever the modal is (re)opened.
  useEffect(() => {
    if (open) {
      setStage("select");
      setMethod("KAKAO");
      setCardNo("");
      setCouponCode("");
      setOrder(null);
      setError(null);
      setAuthRequired(false);
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

  if (!open || !mounted) return null;

  const total = item.price;
  const loginHref = `/login?from=${encodeURIComponent(pathname)}`;

  const pay = async () => {
    setError(null);
    if (!token) {
      setAuthRequired(true);
      return;
    }

    // Real-PG flow: prepare an order, then hand off to the PG. Toss uses its client SDK window;
    // redirect PGs (Kakao/PayPal) return a redirectUrl we navigate to. Both come back to
    // /checkout/success, which confirms against the live PG API. KAKAO/PAYPAL are mock by default
    // (no public sandbox key) and only become real PG here once their keys are configured — add
    // them to REAL_PG_PROVIDERS at that point.
    if (REAL_PG_PROVIDERS.has(method)) {
      setStage("processing");
      try {
        const prep = await orderApi.prepare(
          { albumId: item.albumId, provider: method, couponCode: couponCode.trim() || undefined },
          token,
        );
        if (prep.redirectUrl) {
          // Server-initiated redirect PG (Kakao/PayPal).
          window.location.href = prep.redirectUrl;
          return;
        }
        // Client-SDK PG (Toss): open the payment window.
        const TossPayments = await loadTossPayments();
        const tossPayments = TossPayments(prep.clientKey);
        const payment = tossPayments.payment({ customerKey: prep.customerKey });
        await payment.requestPayment({
          method: "CARD",
          amount: { currency: "KRW", value: prep.amount },
          orderId: prep.orderNo,
          orderName: prep.orderName,
          successUrl: `${window.location.origin}/checkout/success`,
          failUrl: `${window.location.origin}/checkout/fail`,
          customerName: member?.name,
        });
        // requestPayment redirects on success; reaching here without redirect is unexpected.
      } catch (err) {
        setStage("select");
        if (err instanceof ApiError && err.status === 401) {
          setAuthRequired(true);
          return;
        }
        // PG SDK throws on user-cancel/validation with a {code,message}-shaped error.
        const message =
          err && typeof err === "object" && "message" in err
            ? String((err as { message: unknown }).message)
            : "결제를 진행할 수 없습니다.";
        setError(message);
      }
      return;
    }

    // Other methods = mock one-shot order (no PG window).
    setStage("processing");
    try {
      const result = await orderApi.create(
        { albumId: item.albumId, payProvider: method, couponCode: couponCode.trim() || undefined },
        token,
      );
      setOrder(result);
      setStage("done");
      // Refresh the My-page order history so the new order shows up immediately.
      queryClient.invalidateQueries({ queryKey: orderKeys.list });
    } catch (err) {
      setStage("select");
      if (err instanceof ApiError && err.status === 401) {
        setAuthRequired(true);
        return;
      }
      setError(err instanceof Error ? err.message : "주문 생성에 실패했습니다.");
    }
  };

  const isToss = method === "TOSS";

  return createPortal(
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

        {/* Test-mode banner (required) — copy adapts to the selected method's honesty level. */}
        <div className="border-b border-border bg-point/10 px-4 py-2.5">
          <div className="flex items-center gap-2">
            <ShieldAlert className="h-4 w-4 shrink-0 text-point" />
            <p className="text-[12px] font-semibold leading-tight text-point">
              {isToss
                ? "토스 테스트 결제 · 실제 PG 연동(샌드박스) — 실제 출금 없음"
                : "목업 결제 · 실결제 아님 — PG 미연동(즉시 주문 생성)"}
            </p>
          </div>
          <p className="mt-1 pl-6 text-[11px] leading-tight text-point/80">
            {isToss
              ? "실제 토스 결제창이 열리고 토스 승인 API를 호출합니다. 테스트 키라 돈은 빠져나가지 않습니다."
              : "데모 주문은 회원 계정에 실제로 생성됩니다(마이페이지 구매 내역에서 확인)."}
          </p>
        </div>

        <div className="flex-1 overflow-y-auto">
          {stage === "done" && order ? (
            <Receipt item={item} order={order} method={method} />
          ) : authRequired ? (
            <AuthPrompt loginHref={loginHref} />
          ) : (
            <div className="space-y-5 px-4 py-4">
              {/* Order summary */}
              <div className="rounded-card border border-border bg-surface px-4 py-3.5">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-active">{item.name}</span>
                  <span className="text-sm text-inactive">x1</span>
                </div>
                <div className="mt-2.5 flex items-center justify-between border-t border-border pt-2.5">
                  <span className="text-sm font-medium text-inactive">결제 금액</span>
                  <span className="text-lg font-bold text-primary">{formatKRW(total)}</span>
                </div>
              </div>

              {/* Coupon code — redeemed and validated server-side; final amount is confirmed at payment. */}
              <div>
                <label className="mb-2 block text-xs font-semibold text-inactive">쿠폰 코드 (선택)</label>
                <input
                  type="text"
                  value={couponCode}
                  onChange={(e) => setCouponCode(e.target.value.toUpperCase())}
                  placeholder="예: YEAREND15"
                  className="h-11 w-full rounded-xl border border-border bg-surface px-3.5 text-sm text-active outline-none placeholder:text-inactive/60 focus:border-primary"
                />
                <p className="mt-1 text-[11px] leading-relaxed text-inactive">
                  쿠폰은 서버에서 유효성·사용한도·기간을 검증해 적용되며, 최종 결제 금액은 결제 단계에서 확정됩니다.
                </p>
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

              {/* Card input — only for mock methods. Toss collects card data in its own window. */}
              {isToss ? (
                <div className="rounded-card border border-primary/30 bg-primary/5 px-4 py-3">
                  <div className="flex items-center gap-1.5 text-xs font-semibold text-primary">
                    <CreditCard className="h-3.5 w-3.5" />
                    토스 결제창에서 카드 정보를 입력합니다
                  </div>
                  <p className="mt-1 text-[11px] leading-relaxed text-inactive">
                    “결제하기”를 누르면 토스 결제창이 열립니다. 테스트 환경이라 실제 카드로 결제해도
                    출금되지 않습니다(토스 공식 정책 — 별도 테스트 카드번호 없음).
                  </p>
                </div>
              ) : (
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
              )}

              {error && (
                <p className="rounded-lg border border-point/40 bg-point/10 px-3 py-2 text-[12px] font-medium text-point">
                  {error}
                </p>
              )}
            </div>
          )}
        </div>

        {/* Footer action */}
        {stage !== "done" && !authRequired && (
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
                  {isToss ? "토스 결제창 여는 중…" : "주문 생성 중…"}
                </>
              ) : member ? (
                <>{formatKRW(total)} 결제하기</>
              ) : (
                <>로그인하고 구매하기</>
              )}
            </button>
          </div>
        )}
        {(stage === "done" || authRequired) && (
          <div className="border-t border-border bg-surface px-4 py-3">
            {authRequired ? (
              <Link href={loginHref} className="btn-primary w-full">
                <LogIn className="h-4 w-4" />
                로그인하러 가기
              </Link>
            ) : (
              <button type="button" onClick={onClose} className="btn-primary w-full">
                확인
              </button>
            )}
          </div>
        )}
      </div>
    </div>,
    document.body,
  );
}

/** Logged-out gate: explains login is required and links to /login with a return path. */
function AuthPrompt({ loginHref }: { loginHref: string }) {
  return (
    <div className="px-4 py-8">
      <div className="flex flex-col items-center text-center">
        <div className="grid h-14 w-14 place-items-center rounded-full bg-primary/15">
          <LogIn className="h-7 w-7 text-primary" />
        </div>
        <h3 className="mt-3 text-lg font-bold text-active">로그인 후 구매 가능</h3>
        <p className="mt-1.5 text-sm leading-relaxed text-inactive">
          음반 구매는 회원 전용입니다.
          <br />
          로그인하면 바로 이 화면으로 돌아옵니다.
        </p>
      </div>
      <Link
        href={loginHref}
        className="mt-5 flex w-full items-center justify-center text-[12px] font-medium text-primary underline-offset-2 active:underline"
      >
        체험 계정으로 로그인하기
      </Link>
    </div>
  );
}

function Receipt({
  item,
  order,
  method,
}: {
  item: CheckoutItem;
  order: OrderResult;
  method: PayProvider;
}) {
  const methodLabel = METHODS.find((m) => m.id === method)?.label ?? method;
  const statusLabel = ORDER_STATUS_LABELS[order.status] ?? order.status;
  return (
    <div className="px-4 py-6">
      <div className="flex flex-col items-center text-center">
        <CheckCircle2 className="h-14 w-14 text-primary" />
        <h3 className="mt-3 text-lg font-bold text-active">주문이 생성되었어요</h3>
        <p className="mt-1 font-mono text-sm font-bold text-primary">
          {order.orderNo} · {statusLabel}
        </p>
      </div>

      <dl className="mt-6 space-y-2.5 rounded-card border border-border bg-surface px-4 py-4 text-sm">
        <Row label="상품" value={item.name} />
        <Row label="주문번호" value={order.orderNo} mono />
        <Row label="결제 수단" value={methodLabel} />
        <Row label="상태" value={statusLabel} />
        <div className="my-1 border-t border-border" />
        <Row label="결제 금액" value={formatKRW(order.total)} strong />
      </dl>

      <p className="mt-4 text-center text-[11px] text-inactive">
        실제 결제는 이뤄지지 않았지만, 이 주문은 마이페이지 구매 내역에 기록됩니다.
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

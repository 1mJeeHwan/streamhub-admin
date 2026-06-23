"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { CheckCircle2, Loader2, ShieldCheck, XCircle } from "lucide-react";
import { getStoredToken } from "@/lib/auth";
import { orderApi, ORDER_STATUS_LABELS, type OrderResult } from "@/lib/orders";

type State = "confirming" | "done" | "error";

function formatKRW(n: number): string {
  return `₩${n.toLocaleString("ko-KR")}`;
}

/**
 * PortOne redirect landing for a successful authorization. The window appends
 * `paymentKey`/`orderId`/`amount`; we forward them to POST /pub/v1/orders/confirm, which calls the
 * live PortOne confirm API and transitions the order to PAID. The member token is read straight
 * from storage because this is a fresh navigation (AuthProvider may not have revalidated yet).
 */
function CheckoutSuccessInner() {
  const params = useSearchParams();
  const [state, setState] = useState<State>("confirming");
  const [order, setOrder] = useState<OrderResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Authorization params parsed once from the redirect; kept so we can show them on failure and
  // allow a retry. Confirm is idempotent on an already-paid order, so retrying a transient failure
  // is safe.
  const [authInfo, setAuthInfo] = useState<{
    orderNo: string;
    paymentKey: string;
    amount: number;
    token: string;
  } | null>(null);
  // Guards against React 18 StrictMode double-invocation of the initial confirm only. The explicit
  // "다시 시도" button bypasses it so a transient failure is never permanently blocked.
  const autoConfirmed = useRef(false);

  function runConfirm(info: {
    orderNo: string;
    paymentKey: string;
    amount: number;
    token: string;
  }) {
    setError(null);
    setState("confirming");
    orderApi
      .confirm(
        { orderNo: info.orderNo, paymentKey: info.paymentKey, amount: info.amount },
        info.token,
      )
      .then((result) => {
        setOrder(result);
        setState("done");
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : "결제 승인에 실패했습니다.");
        setState("error");
      });
  }

  useEffect(() => {
    if (autoConfirmed.current) return;
    autoConfirmed.current = true;

    // Param names differ per PG: PortOne → orderId/paymentKey; Kakao → orderNo/pg_token;
    // PayPal → orderNo/token. Read whichever is present.
    const orderNo = params.get("orderId") ?? params.get("orderNo");
    const paymentKey =
      params.get("paymentKey") ?? params.get("pg_token") ?? params.get("token");
    const amountRaw = params.get("amount");
    const amount = amountRaw ? Number(amountRaw) : NaN;
    const token = getStoredToken();

    if (!orderNo || !paymentKey || !amountRaw || Number.isNaN(amount)) {
      setError("결제 정보를 확인할 수 없습니다.");
      setState("error");
      return;
    }
    if (!token) {
      setError("로그인이 필요합니다. 다시 로그인 후 시도해 주세요.");
      setState("error");
      return;
    }

    const info = { orderNo, paymentKey, amount, token };
    setAuthInfo(info);
    runConfirm(info);
  }, [params]);

  if (state === "confirming") {
    return (
      <section className="flex flex-col items-center px-5 pt-16 text-center">
        <Loader2 className="h-10 w-10 animate-spin text-primary" />
        <h1 className="mt-4 text-lg font-bold text-active">결제를 승인하는 중…</h1>
        <p className="mt-1 text-sm text-inactive">포트원 결제 결과를 확인하고 있습니다.</p>
      </section>
    );
  }

  if (state === "error") {
    return (
      <section className="animate-fade-up px-5 pt-12 text-center">
        <XCircle className="mx-auto h-14 w-14 text-point" />
        <h1 className="mt-3 text-xl font-bold text-active">결제 승인 실패</h1>
        <p className="mx-auto mt-2 max-w-xs text-sm leading-relaxed text-inactive">{error}</p>

        {authInfo && (
          <>
            <p className="mx-auto mt-3 max-w-xs text-xs leading-relaxed text-inactive">
              결제는 이미 승인되었을 수 있습니다. 아래 번호로 다시 시도하거나 고객센터에 문의해
              주세요.
            </p>
            <dl className="mx-auto mt-4 max-w-sm space-y-2 rounded-card border border-border bg-surface px-4 py-3 text-left text-sm">
              <div className="flex items-center justify-between gap-3">
                <dt className="text-inactive">주문번호</dt>
                <dd className="break-all text-right font-mono text-xs text-active">
                  {authInfo.orderNo}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-3">
                <dt className="text-inactive">결제키</dt>
                <dd className="break-all text-right font-mono text-xs text-active">
                  {authInfo.paymentKey}
                </dd>
              </div>
            </dl>
            <button
              type="button"
              onClick={() => runConfirm(authInfo)}
              className="btn-primary mt-6 inline-flex"
            >
              다시 시도
            </button>
          </>
        )}

        <div className="mt-4">
          <Link
            href="/albums"
            className="text-sm font-semibold text-inactive underline-offset-2 hover:underline"
          >
            음반으로 돌아가기
          </Link>
        </div>
      </section>
    );
  }

  const statusLabel = order ? (ORDER_STATUS_LABELS[order.status] ?? order.status) : "";
  return (
    <section className="animate-fade-up px-5 pt-12">
      <div className="flex flex-col items-center text-center">
        <CheckCircle2 className="h-16 w-16 text-primary" />
        <h1 className="mt-3 text-xl font-bold text-active">결제를 완료했어요</h1>
        <p className="mt-1 font-mono text-sm font-bold text-primary">
          {order?.orderNo} · {statusLabel}
        </p>
      </div>

      <dl className="mx-auto mt-7 max-w-sm space-y-2.5 rounded-card border border-border bg-surface px-4 py-4 text-sm">
        <div className="flex items-center justify-between">
          <dt className="text-inactive">주문번호</dt>
          <dd className="font-mono text-xs text-active">{order?.orderNo}</dd>
        </div>
        <div className="flex items-center justify-between">
          <dt className="text-inactive">상태</dt>
          <dd className="text-active">{statusLabel}</dd>
        </div>
        <div className="my-1 border-t border-border" />
        <div className="flex items-center justify-between">
          <dt className="text-inactive">결제 금액</dt>
          <dd className="text-base font-bold text-primary">
            {order ? formatKRW(order.total) : "-"}
          </dd>
        </div>
      </dl>

      <div className="mx-auto mt-3 flex max-w-sm items-center gap-1.5 rounded-lg bg-primary/5 px-3 py-2 text-[11px] text-inactive">
        <ShieldCheck className="h-3.5 w-3.5 shrink-0 text-primary" />
        포트원 샌드박스(테스트 키)로 승인되었습니다. 실제 출금은 없습니다.
      </div>

      <div className="mx-auto mt-6 flex max-w-sm gap-2">
        <Link href="/mypage" className="btn-primary flex-1">
          구매 내역 보기
        </Link>
        <Link
          href="/albums"
          className="flex-1 rounded-xl border border-border bg-surface px-4 py-3 text-center text-sm font-bold text-active transition active:bg-card"
        >
          음반 더 보기
        </Link>
      </div>
    </section>
  );
}

export default function CheckoutSuccessPage() {
  return (
    <Suspense
      fallback={
        <section className="flex flex-col items-center px-5 pt-16 text-center">
          <Loader2 className="h-10 w-10 animate-spin text-primary" />
        </section>
      }
    >
      <CheckoutSuccessInner />
    </Suspense>
  );
}

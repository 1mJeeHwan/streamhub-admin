"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  CalendarDays,
  Church,
  Loader2,
  LogOut,
  Mail,
  PackageCheck,
  Phone,
  Receipt,
  ShoppingBag,
  Truck,
  User,
} from "lucide-react";
import clsx from "clsx";
import { useAuth } from "@/lib/auth";
import {
  orderApi,
  useMyOrders,
  ORDER_STATUS_LABELS,
  type OrderListItem,
  type OrderStatus,
  type Tracking,
} from "@/lib/orders";
import { formatDate } from "@/lib/format";
import { NearbyChurchesSection } from "@/components/NearbyChurchesSection";
import { PurchasedAlbumsSection } from "@/components/mypage/PurchasedAlbumsSection";
import { PlaylistSection } from "@/components/mypage/PlaylistSection";
import { WatchHistorySection } from "@/components/mypage/WatchHistorySection";
import { ReviewsInquiriesSection } from "@/components/mypage/ReviewsInquiriesSection";

function Row({ icon: Icon, label, value }: { icon: typeof Mail; label: string; value: string | null }) {
  return (
    <div className="flex items-center gap-3 px-4 py-3.5">
      <Icon className="h-4 w-4 shrink-0 text-inactive" />
      <span className="w-16 shrink-0 text-sm text-inactive">{label}</span>
      <span className="truncate text-sm font-medium text-active">{value || "-"}</span>
    </div>
  );
}

/** Status pill color: PAID is primary, terminal-failure states are muted/point. */
function statusClass(status: OrderStatus): string {
  if (status === "PAID") return "bg-primary/15 text-primary";
  if (status === "CANCELLED" || status === "FAILED") return "bg-point/15 text-point";
  return "bg-card text-inactive";
}

/** Purchase history — only rendered for signed-in members (token-gated GET /pub/v1/orders). */
function OrderHistorySection({ token }: { token: string }) {
  const { data, isLoading, isError } = useMyOrders(token);
  const orders = data ?? [];

  return (
    <section className="mt-7">
      <h2 className="flex items-center gap-2 pb-3 text-base font-bold text-active">
        <ShoppingBag className="h-4.5 w-4.5 text-primary" />
        구매 내역
      </h2>

      {isLoading ? (
        <div className="space-y-2.5">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="skeleton h-[68px] rounded-card" />
          ))}
        </div>
      ) : isError ? (
        <p className="rounded-card border border-border bg-surface px-4 py-5 text-center text-sm text-inactive">
          구매 내역을 불러오지 못했습니다.
        </p>
      ) : orders.length === 0 ? (
        <div className="rounded-card border border-border bg-surface px-4 py-7 text-center">
          <Receipt className="mx-auto h-7 w-7 text-inactive" />
          <p className="mt-2 text-sm text-inactive">아직 구매한 음반이 없습니다.</p>
        </div>
      ) : (
        <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
          {orders.map((order) => (
            <OrderRow key={order.orderNo} order={order} token={token} />
          ))}
        </ul>
      )}
    </section>
  );
}

/** One order row with an on-demand "배송조회" expander backed by the courier API. */
function OrderRow({ order, token }: { order: OrderListItem; token: string }) {
  const [tracking, setTracking] = useState<Tracking | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadTracking = async () => {
    setOpen(true);
    if (tracking || loading) return;
    setLoading(true);
    setError(null);
    try {
      setTracking(await orderApi.tracking(order.orderNo, token));
    } catch (err) {
      setError(err instanceof Error ? err.message : "배송 조회에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <li className="px-4 py-3.5">
      <div className="flex items-center justify-between gap-3">
        <p className="ellipsis-1 text-sm font-bold text-active">{order.firstItemName}</p>
        <span
          className={clsx(
            "shrink-0 rounded-full px-2 py-0.5 text-[11px] font-bold",
            statusClass(order.status),
          )}
        >
          {ORDER_STATUS_LABELS[order.status] ?? order.status}
        </span>
      </div>
      <div className="mt-1 flex items-center justify-between gap-3">
        <span className="font-mono text-[11px] text-inactive">{order.orderNo}</span>
        <span className="text-sm font-bold text-primary">{order.total.toLocaleString()}원</span>
      </div>
      <div className="mt-1 flex items-center justify-between gap-3">
        <p className="text-[11px] text-inactive">{formatDate(order.orderedAt)}</p>
        <button
          type="button"
          onClick={open ? () => setOpen(false) : loadTracking}
          className="flex items-center gap-1 text-[11px] font-semibold text-primary active:underline"
        >
          <Truck className="h-3.5 w-3.5" />
          배송조회
        </button>
      </div>

      {open && (
        <div className="mt-2.5 rounded-lg border border-border bg-bg px-3 py-2.5">
          {loading ? (
            <div className="flex items-center gap-2 text-xs text-inactive">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> 배송 정보를 불러오는 중…
            </div>
          ) : error ? (
            <p className="text-xs text-point">{error}</p>
          ) : tracking ? (
            <div>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-inactive">
                <span>
                  택배사 <strong className="text-active">{tracking.carrierName ?? "-"}</strong>
                </span>
                <span
                  className={clsx(
                    "inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-bold",
                    tracking.completed ? "bg-primary/15 text-primary" : "bg-secondary/15 text-secondary",
                  )}
                >
                  {tracking.completed && <PackageCheck className="h-3 w-3" />}
                  {tracking.completed ? "배달완료" : "배송중"}
                </span>
              </div>
              {tracking.events.length > 0 ? (
                <ol className="mt-2 space-y-2 border-l border-border pl-3">
                  {tracking.events.map((event, i) => (
                    <li key={i} className="relative">
                      <span className="absolute -left-[15px] top-1 h-1.5 w-1.5 rounded-full bg-primary" />
                      <p className="text-xs text-active">{event.description ?? "-"}</p>
                      <p className="text-[11px] text-inactive">
                        {event.location ?? ""} · {event.time ?? ""}
                      </p>
                    </li>
                  ))}
                </ol>
              ) : (
                <p className="mt-2 text-[11px] text-inactive">아직 등록된 배송 이벤트가 없습니다.</p>
              )}
            </div>
          ) : null}
        </div>
      )}
    </li>
  );
}

export default function MyPage() {
  const { member, token, loading, logout } = useAuth();
  const router = useRouter();
  const [leaving, setLeaving] = useState(false);

  // Redirect unauthenticated visitors to login — but not while we're logging out (→ home).
  useEffect(() => {
    if (!loading && !member && !leaving) router.replace("/login");
  }, [loading, member, leaving, router]);

  if (loading || !member) {
    return (
      <div className="animate-fade-up px-5 pt-10">
        <div className="skeleton mx-auto h-20 w-20 rounded-full" />
        <div className="skeleton mx-auto mt-4 h-6 w-32 rounded" />
      </div>
    );
  }

  return (
    <section className="animate-fade-up px-5 pt-8">
      <div className="flex flex-col items-center">
        <div className="grid h-20 w-20 place-items-center rounded-full bg-gradient-to-br from-primary/40 to-secondary/40">
          <User className="h-9 w-9 text-active" />
        </div>
        <h1 className="mt-3 text-xl font-bold">{member.name}</h1>
        <p className="text-sm text-inactive">{member.email}</p>
      </div>

      <div className="mt-7 divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
        <Row icon={Mail} label="이메일" value={member.email} />
        <Row icon={Church} label="교회" value={member.churchName} />
        <Row icon={Phone} label="연락처" value={member.phone} />
        <Row icon={CalendarDays} label="가입일" value={formatDate(member.createdAt)} />
      </div>

      <button
        onClick={() => {
          setLeaving(true);
          logout();
          router.replace("/");
        }}
        className="mt-6 flex w-full items-center justify-center gap-2 rounded-xl border border-border py-3 text-sm font-medium text-active active:bg-card"
      >
        <LogOut className="h-4 w-4" />
        로그아웃
      </button>

      {/* Token is guaranteed non-null here (member is set); each section is token-gated. */}
      {token && (
        <>
          <OrderHistorySection token={token} />
          <PurchasedAlbumsSection token={token} />
          <PlaylistSection token={token} />
          <WatchHistorySection token={token} />
          <ReviewsInquiriesSection token={token} />
        </>
      )}

      {/* Near-me churches widget — parent already pads px-5, so cancel it here. */}
      <div className="-mx-5 mt-2">
        <NearbyChurchesSection />
      </div>
    </section>
  );
}

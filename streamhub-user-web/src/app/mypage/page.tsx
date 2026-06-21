"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  CalendarDays,
  ChevronRight,
  Church,
  LogOut,
  Mail,
  Phone,
  Receipt,
  ShoppingBag,
  User,
} from "lucide-react";
import clsx from "clsx";
import { useAuth } from "@/lib/auth";
import {
  useMyOrders,
  ORDER_STATUS_LABELS,
  type OrderListItem,
  type OrderStatus,
} from "@/lib/orders";
import { formatDate } from "@/lib/format";
import { Pagination } from "@/components/Pagination";
import { SectionShell } from "@/components/mypage/SectionShell";
import { ReceiptModal } from "@/components/mypage/ReceiptModal";
import { NearbyChurchesCta } from "@/components/NearbyChurchesCta";
import { PurchasedAlbumsSection } from "@/components/mypage/PurchasedAlbumsSection";
import { PlaylistSection } from "@/components/mypage/PlaylistSection";
import { WatchHistorySection } from "@/components/mypage/WatchHistorySection";
import { ReviewsInquiriesSection } from "@/components/mypage/ReviewsInquiriesSection";
import { PointsSection } from "@/components/mypage/PointsSection";
import { CouponsSection } from "@/components/mypage/CouponsSection";
import { DonationsSection } from "@/components/mypage/DonationsSection";
import { NotificationsSection } from "@/components/mypage/NotificationsSection";

function Row({ icon: Icon, label, value }: { icon: typeof Mail; label: string; value: string | null }) {
  return (
    <div className="flex items-center gap-3 px-4 py-3.5">
      <Icon className="h-4 w-4 shrink-0 text-inactive" />
      <span className="w-16 shrink-0 text-sm text-inactive">{label}</span>
      <span className="truncate text-sm font-medium text-active">{value || "-"}</span>
    </div>
  );
}

/** Status pill color: success states primary, return/cancel muted/point. */
function statusClass(status: OrderStatus): string {
  if (status === "PAID" || status === "DONE") return "bg-primary/15 text-primary";
  if (status === "CANCEL" || status === "RETURN") return "bg-point/15 text-point";
  return "bg-card text-inactive";
}

const ORDERS_PAGE_SIZE = 5;

/** Purchase history — paged, token-gated. A row opens its receipt detail modal. */
function OrderHistorySection({ token }: { token: string }) {
  const [page, setPage] = useState(0);
  const [receiptOrderNo, setReceiptOrderNo] = useState<string | null>(null);
  const { data, isLoading, isError } = useMyOrders(token, page, ORDERS_PAGE_SIZE);
  const orders = data?.contents ?? [];

  return (
    <>
      <SectionShell
        icon={ShoppingBag}
        title="구매 내역"
        count={data && data.totalCount > 0 ? `${data.totalCount}건` : undefined}
        isLoading={isLoading}
        isError={isError}
        isEmpty={orders.length === 0}
        errorMessage="구매 내역을 불러오지 못했습니다."
        emptyIcon={Receipt}
        emptyMessage="아직 구매한 음반이 없습니다."
      >
        <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
          {orders.map((order) => (
            <OrderRow
              key={order.orderNo}
              order={order}
              onOpen={() => setReceiptOrderNo(order.orderNo)}
            />
          ))}
        </ul>
        <Pagination pageNumber={page} totalPage={data?.totalPage ?? 1} onChange={setPage} />
      </SectionShell>

      {receiptOrderNo && (
        <ReceiptModal
          orderNo={receiptOrderNo}
          token={token}
          onClose={() => setReceiptOrderNo(null)}
        />
      )}
    </>
  );
}

/** One clickable order row → opens the receipt detail modal. */
function OrderRow({ order, onOpen }: { order: OrderListItem; onOpen: () => void }) {
  return (
    <li>
      <button
        type="button"
        onClick={onOpen}
        className="flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors active:bg-card"
      >
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-3">
            <p className="ellipsis-1 text-sm font-bold text-active">{order.productName}</p>
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
          <p className="mt-1 text-[11px] text-inactive">{formatDate(order.orderedAt)}</p>
        </div>
        <ChevronRight className="h-4 w-4 shrink-0 text-inactive" />
      </button>
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

      {/* Token guaranteed non-null here (member is set). Collapsible feature menu — each row is a
          token-gated section that expands in place; counts show on the collapsed row. */}
      {token && (
        <div className="mt-7 divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
          <OrderHistorySection token={token} />
          <PointsSection token={token} />
          <CouponsSection token={token} />
          <DonationsSection token={token} />
          <PurchasedAlbumsSection token={token} />
          <PlaylistSection token={token} />
          <WatchHistorySection token={token} />
          <ReviewsInquiriesSection token={token} />
          <NotificationsSection token={token} />
        </div>
      )}

      {/* Near-me churches → navigate to the finder tab (parent pads px-5, so cancel it here). */}
      <div className="-mx-5 mt-2">
        <NearbyChurchesCta />
      </div>
    </section>
  );
}

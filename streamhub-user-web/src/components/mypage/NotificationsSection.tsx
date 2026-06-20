"use client";

import { useState } from "react";
import { Bell, CheckCheck } from "lucide-react";
import clsx from "clsx";
import {
  useMyNotifications,
  useMyNotificationsUnread,
  useMarkNotificationRead,
  useMarkAllNotificationsRead,
  type NotificationItem,
} from "@/lib/me";
import { formatDate } from "@/lib/format";
import { SectionShell } from "./SectionShell";
import { Pagination } from "@/components/Pagination";

const PAGE_SIZE = 6;

function Row({ n, onRead }: { n: NotificationItem; onRead: (id: number) => void }) {
  return (
    <li>
      <button
        type="button"
        onClick={() => !n.read && onRead(n.id)}
        disabled={n.read}
        className={clsx(
          "flex w-full items-start gap-2.5 px-4 py-3 text-left transition-colors",
          n.read ? "cursor-default" : "active:bg-card",
        )}
      >
        <span
          className={clsx(
            "mt-1.5 h-2 w-2 shrink-0 rounded-full",
            n.read ? "bg-transparent" : "bg-primary",
          )}
          aria-hidden
        />
        <span className="min-w-0 flex-1">
          <span className={clsx("block text-sm", n.read ? "text-inactive" : "font-bold text-active")}>
            {n.title}
          </span>
          {n.body && (
            <span className={clsx("mt-0.5 block whitespace-pre-line text-sm", n.read ? "text-inactive/70" : "text-inactive")}>
              {n.body}
            </span>
          )}
          <span className="mt-1 block text-[11px] text-inactive">{formatDate(n.createdAt)}</span>
        </span>
      </button>
    </li>
  );
}

/** The member's notifications (newest first, paged) with per-member read state + unread badge. */
export function NotificationsSection({ token }: { token: string }) {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useMyNotifications(token, page, PAGE_SIZE);
  const unread = useMyNotificationsUnread(token);
  const markRead = useMarkNotificationRead(token);
  const markAll = useMarkAllNotificationsRead(token);
  const items = data?.contents ?? [];
  const unreadCount = unread.data ?? 0;

  return (
    <section className="mt-7">
      <div className="flex items-center justify-between pb-3">
        <h2 className="flex items-center gap-2 text-base font-bold text-active">
          <Bell className="h-4.5 w-4.5 text-primary" />
          알림
          {unreadCount > 0 && (
            <span className="rounded-full bg-point px-1.5 py-0.5 text-[10px] font-bold text-white">
              {unreadCount}
            </span>
          )}
        </h2>
        {unreadCount > 0 && (
          <button
            type="button"
            onClick={() => markAll.mutate()}
            disabled={markAll.isPending}
            className="flex items-center gap-1 text-[12px] font-semibold text-primary active:underline disabled:opacity-50"
          >
            <CheckCheck className="h-3.5 w-3.5" />
            모두 읽음
          </button>
        )}
      </div>

      {isLoading ? (
        <div className="space-y-2.5">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="skeleton h-[68px] rounded-card" />
          ))}
        </div>
      ) : isError ? (
        <p className="rounded-card border border-border bg-surface px-4 py-5 text-center text-sm text-inactive">
          알림을 불러오지 못했습니다.
        </p>
      ) : items.length === 0 ? (
        <div className="rounded-card border border-border bg-surface px-4 py-7 text-center">
          <Bell className="mx-auto h-7 w-7 text-inactive" />
          <p className="mt-2 text-sm text-inactive">받은 알림이 없습니다.</p>
        </div>
      ) : (
        <>
          <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
            {items.map((n) => (
              <Row key={n.id} n={n} onRead={(id) => markRead.mutate(id)} />
            ))}
          </ul>
          <Pagination pageNumber={page} totalPage={data?.totalPage ?? 1} onChange={setPage} />
        </>
      )}
    </section>
  );
}

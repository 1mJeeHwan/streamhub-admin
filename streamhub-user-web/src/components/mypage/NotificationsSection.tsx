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
    <SectionShell
      icon={Bell}
      title="알림"
      count={unreadCount > 0 ? `${unreadCount}` : undefined}
      countTone="danger"
      isLoading={isLoading}
      isError={isError}
      isEmpty={items.length === 0}
      errorMessage="알림을 불러오지 못했습니다."
      emptyIcon={Bell}
      emptyMessage="받은 알림이 없습니다."
    >
      {unreadCount > 0 && (
        <div className="mb-2.5 flex justify-end">
          <button
            type="button"
            onClick={() => markAll.mutate()}
            disabled={markAll.isPending}
            className="flex items-center gap-1 text-[12px] font-semibold text-primary active:underline disabled:opacity-50"
          >
            <CheckCheck className="h-3.5 w-3.5" />
            모두 읽음
          </button>
        </div>
      )}
      <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
        {items.map((n) => (
          <Row key={n.id} n={n} onRead={(id) => markRead.mutate(id)} />
        ))}
      </ul>
      <Pagination pageNumber={page} totalPage={data?.totalPage ?? 1} onChange={setPage} />
    </SectionShell>
  );
}

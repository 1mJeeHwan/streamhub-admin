"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { ChevronRight, MessageSquare, MessagesSquare, Star, X } from "lucide-react";
import clsx from "clsx";
import {
  useMyInquiries,
  useMyReviews,
  type InquiryStatus,
  type MyInquiryItem,
  type MyReviewItem,
} from "@/lib/me";
import { formatDate } from "@/lib/format";
import { SectionShell } from "./SectionShell";

/** Korean labels for inquiry status, used on the status pill. */
const INQUIRY_STATUS_LABELS: Record<InquiryStatus, string> = {
  WAITING: "답변대기",
  ANSWERED: "답변완료",
};

/** Five-star rating display; filled up to `rating`. */
function Stars({ rating }: { rating: number }) {
  return (
    <span className="flex items-center gap-0.5" aria-label={`별점 ${rating}점`}>
      {Array.from({ length: 5 }).map((_, i) => (
        <Star
          key={i}
          className={clsx("h-3.5 w-3.5", i < rating ? "fill-secondary text-secondary" : "text-inactive")}
        />
      ))}
    </span>
  );
}

/** Bottom-sheet modal shell (portal to body, escaping the page's animate-fade-up transform). */
function DetailModal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  if (!mounted) return null;

  return createPortal(
    <div
      className="fixed inset-0 z-[60] flex items-end justify-center bg-black/60"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={onClose}
    >
      <div
        className="mx-auto flex max-h-[88dvh] w-full max-w-[480px] flex-col overflow-hidden rounded-t-2xl border-x border-t border-border bg-bg animate-fade-up"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-border bg-surface px-4 py-3.5">
          <h2 className="text-base font-bold text-active">{title}</h2>
          <button
            type="button"
            aria-label="닫기"
            onClick={onClose}
            className="rounded-lg p-1.5 text-inactive transition active:bg-card"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-4 py-4">{children}</div>
      </div>
    </div>,
    document.body,
  );
}

function ReviewRow({ review, onOpen }: { review: MyReviewItem; onOpen: () => void }) {
  return (
    <li>
      <button type="button" onClick={onOpen} className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors active:bg-card">
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-3">
            <p className="ellipsis-1 text-sm font-bold text-active">{review.goodsName}</p>
            <Stars rating={review.rating} />
          </div>
          <p className="ellipsis-1 mt-1 text-sm text-inactive">{review.content}</p>
          <p className="mt-1 text-[11px] text-inactive">{formatDate(review.createdAt)}</p>
        </div>
        <ChevronRight className="h-4 w-4 shrink-0 text-inactive" />
      </button>
    </li>
  );
}

function InquiryRow({ inquiry, onOpen }: { inquiry: MyInquiryItem; onOpen: () => void }) {
  return (
    <li>
      <button type="button" onClick={onOpen} className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors active:bg-card">
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-3">
            <p className="ellipsis-1 text-sm font-bold text-active">{inquiry.title || inquiry.goodsName}</p>
            <span
              className={clsx(
                "shrink-0 rounded-full px-2 py-0.5 text-[11px] font-bold",
                inquiry.status === "ANSWERED" ? "bg-primary/15 text-primary" : "bg-card text-inactive",
              )}
            >
              {INQUIRY_STATUS_LABELS[inquiry.status]}
            </span>
          </div>
          <p className="ellipsis-1 mt-1 text-sm text-inactive">{inquiry.question}</p>
          <p className="mt-1 text-[11px] text-inactive">{inquiry.goodsName} · {formatDate(inquiry.createdAt)}</p>
        </div>
        <ChevronRight className="h-4 w-4 shrink-0 text-inactive" />
      </button>
    </li>
  );
}

/** Two stacked panels — the member's product reviews and inquiries, each opening a detail modal. */
export function ReviewsInquiriesSection({ token }: { token: string }) {
  const reviews = useMyReviews(token);
  const inquiries = useMyInquiries(token);
  const reviewList = reviews.data ?? [];
  const inquiryList = inquiries.data ?? [];

  const [openReview, setOpenReview] = useState<MyReviewItem | null>(null);
  const [openInquiry, setOpenInquiry] = useState<MyInquiryItem | null>(null);

  return (
    <>
      <SectionShell
        icon={MessageSquare}
        title="내 후기"
        count={reviewList.length > 0 ? `${reviewList.length}건` : undefined}
        isLoading={reviews.isLoading}
        isError={reviews.isError}
        isEmpty={reviewList.length === 0}
        errorMessage="후기를 불러오지 못했습니다."
        emptyIcon={MessageSquare}
        emptyMessage="작성한 후기가 없습니다. 구매한 음반 상세에서 후기를 남겨보세요."
      >
        <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
          {reviewList.map((review) => (
            <ReviewRow key={review.id} review={review} onOpen={() => setOpenReview(review)} />
          ))}
        </ul>
      </SectionShell>

      <SectionShell
        icon={MessagesSquare}
        title="내 문의"
        count={inquiryList.length > 0 ? `${inquiryList.length}건` : undefined}
        isLoading={inquiries.isLoading}
        isError={inquiries.isError}
        isEmpty={inquiryList.length === 0}
        errorMessage="문의를 불러오지 못했습니다."
        emptyIcon={MessagesSquare}
        emptyMessage="작성한 문의가 없습니다. 음반 상세에서 문의를 남겨보세요."
      >
        <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
          {inquiryList.map((inquiry) => (
            <InquiryRow key={inquiry.id} inquiry={inquiry} onOpen={() => setOpenInquiry(inquiry)} />
          ))}
        </ul>
      </SectionShell>

      {openReview && (
        <DetailModal title="후기 상세" onClose={() => setOpenReview(null)}>
          <p className="text-sm font-bold text-active">{openReview.goodsName}</p>
          <div className="mt-1.5">
            <Stars rating={openReview.rating} />
          </div>
          <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-active">{openReview.content}</p>
          <p className="mt-3 text-[11px] text-inactive">{formatDate(openReview.createdAt)} 작성</p>
        </DetailModal>
      )}

      {openInquiry && (
        <DetailModal title="문의 상세" onClose={() => setOpenInquiry(null)}>
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm font-bold text-active">{openInquiry.title || openInquiry.goodsName}</p>
            <span
              className={clsx(
                "shrink-0 rounded-full px-2 py-0.5 text-[11px] font-bold",
                openInquiry.status === "ANSWERED" ? "bg-primary/15 text-primary" : "bg-card text-inactive",
              )}
            >
              {INQUIRY_STATUS_LABELS[openInquiry.status]}
            </span>
          </div>
          <p className="mt-1 text-[11px] text-inactive">{openInquiry.goodsName} · {formatDate(openInquiry.createdAt)}</p>

          <div className="mt-4 rounded-card border border-border bg-surface px-4 py-3">
            <p className="text-[11px] font-bold text-inactive">질문</p>
            <p className="mt-1 whitespace-pre-line text-sm leading-relaxed text-active">{openInquiry.question}</p>
          </div>

          <div className="mt-3 rounded-card border border-primary/30 bg-primary/5 px-4 py-3">
            <p className="text-[11px] font-bold text-primary">답변</p>
            {openInquiry.answer ? (
              <p className="mt-1 whitespace-pre-line text-sm leading-relaxed text-active">{openInquiry.answer}</p>
            ) : (
              <p className="mt-1 text-sm text-inactive">아직 답변이 등록되지 않았습니다.</p>
            )}
          </div>
        </DetailModal>
      )}
    </>
  );
}

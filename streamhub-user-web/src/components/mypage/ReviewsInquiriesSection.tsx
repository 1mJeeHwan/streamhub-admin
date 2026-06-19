"use client";

import { MessageSquare, MessagesSquare, Star } from "lucide-react";
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
  PENDING: "답변대기",
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

function ReviewRow({ review }: { review: MyReviewItem }) {
  return (
    <li className="px-4 py-3">
      <div className="flex items-center justify-between gap-3">
        <p className="ellipsis-1 text-sm font-bold text-active">{review.goodsName}</p>
        <Stars rating={review.rating} />
      </div>
      <p className="mt-1 text-sm text-active">{review.content}</p>
      <p className="mt-1 text-[11px] text-inactive">{formatDate(review.createdAt)}</p>
    </li>
  );
}

function InquiryRow({ inquiry }: { inquiry: MyInquiryItem }) {
  return (
    <li className="px-4 py-3">
      <div className="flex items-center justify-between gap-3">
        <p className="ellipsis-1 text-sm font-bold text-active">{inquiry.goodsName}</p>
        <span
          className={clsx(
            "shrink-0 rounded-full px-2 py-0.5 text-[11px] font-bold",
            inquiry.status === "ANSWERED" ? "bg-primary/15 text-primary" : "bg-card text-inactive",
          )}
        >
          {INQUIRY_STATUS_LABELS[inquiry.status]}
        </span>
      </div>
      <p className="mt-1 text-sm text-active">Q. {inquiry.question}</p>
      {inquiry.answer && (
        <p className="mt-1 rounded-lg bg-bg px-2.5 py-2 text-sm text-inactive">A. {inquiry.answer}</p>
      )}
      <p className="mt-1 text-[11px] text-inactive">{formatDate(inquiry.createdAt)}</p>
    </li>
  );
}

/** Two stacked panels — the member's product reviews and inquiries, loaded independently. */
export function ReviewsInquiriesSection({ token }: { token: string }) {
  const reviews = useMyReviews(token);
  const inquiries = useMyInquiries(token);
  const reviewList = reviews.data ?? [];
  const inquiryList = inquiries.data ?? [];

  return (
    <>
      <SectionShell
        icon={MessageSquare}
        title="내 후기"
        isLoading={reviews.isLoading}
        isError={reviews.isError}
        isEmpty={reviewList.length === 0}
        errorMessage="후기를 불러오지 못했습니다."
        emptyIcon={MessageSquare}
        emptyMessage="작성한 후기가 없습니다."
      >
        <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
          {reviewList.map((review) => (
            <ReviewRow key={`${review.goodsId}-${review.createdAt}`} review={review} />
          ))}
        </ul>
      </SectionShell>

      <SectionShell
        icon={MessagesSquare}
        title="내 문의"
        isLoading={inquiries.isLoading}
        isError={inquiries.isError}
        isEmpty={inquiryList.length === 0}
        errorMessage="문의를 불러오지 못했습니다."
        emptyIcon={MessagesSquare}
        emptyMessage="작성한 문의가 없습니다."
      >
        <ul className="divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
          {inquiryList.map((inquiry) => (
            <InquiryRow key={`${inquiry.goodsId}-${inquiry.createdAt}`} inquiry={inquiry} />
          ))}
        </ul>
      </SectionShell>
    </>
  );
}

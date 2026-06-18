// Album order domain — authenticated member surface for the user site.
// Mirrors the backend public order DTOs (POST/GET /pub/v1/orders). Reuses the shared
// request helper; all calls require a member Bearer token (401 when missing/anonymous).

"use client";

import { useQuery } from "@tanstack/react-query";
import { request } from "./api";

/** Payment providers accepted by the order endpoint. Matches the checkout method buttons. */
export type PayProvider = "TOSS" | "KAKAO" | "PAYPAL" | "CARD";

/** Lifecycle status of an order. PAID is the terminal success state for the demo flow. */
export type OrderStatus = "PAID" | "PENDING" | "CANCELLED" | "FAILED";

/** Body of POST /pub/v1/orders. */
export interface CreateOrderInput {
  albumId: number;
  payProvider: PayProvider;
  /** Optional discount-coupon code redeemed server-side against this order. */
  couponCode?: string;
}

/** Result of POST /pub/v1/orders — a freshly created order. */
export interface OrderResult {
  orderNo: string;
  status: OrderStatus;
  total: number;
  paidAt: string | null;
}

/** Body of POST /pub/v1/orders/prepare (phase 1 of a real-PG purchase). */
export interface PreparePaymentInput {
  albumId: number;
  provider: PayProvider;
  /** Optional discount-coupon code redeemed server-side; the returned amount is post-discount. */
  couponCode?: string;
}

/** Result of /prepare — everything the browser needs to open the PG payment window. */
export interface PreparePaymentResult {
  orderNo: string;
  orderName: string;
  amount: number;
  provider: PayProvider;
  /** PG client (publishable) key for the browser SDK (Toss); empty for redirect PGs. */
  clientKey: string;
  /** Member-scoped customer key for the window. */
  customerKey: string;
  /** Redirect URL for server-initiated PGs (Kakao/PayPal); absent for client-SDK PGs (Toss). */
  redirectUrl?: string;
}

/** Body of POST /pub/v1/orders/confirm — the values the PG window redirects back with. */
export interface ConfirmPaymentInput {
  orderNo: string;
  paymentKey: string;
  amount: number;
}

/** One scan event in a shipment's progress (GET /pub/v1/orders/{orderNo}/tracking). */
export interface TrackingEvent {
  time: string | null;
  location: string | null;
  description: string | null;
}

/** Live shipment status for an order, from the courier API via the backend delivery seam. */
export interface Tracking {
  carrierCode: string | null;
  carrierName: string | null;
  invoiceNo: string | null;
  level: number;
  completed: boolean;
  senderName: string | null;
  receiverName: string | null;
  events: TrackingEvent[];
}

/** One row of GET /pub/v1/orders (my order history). */
export interface OrderListItem {
  orderNo: string;
  /** Name of the first product in the order, e.g. "찬양 1집". */
  firstItemName: string;
  total: number;
  status: OrderStatus;
  orderedAt: string;
}

/** Korean labels for each order status, used in the history list. */
export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PAID: "결제완료",
  PENDING: "결제대기",
  CANCELLED: "취소됨",
  FAILED: "결제실패",
};

export const orderApi = {
  /** One-shot mock purchase: creates an order and approves it server-side (no PG window). */
  create: (input: CreateOrderInput, token: string) =>
    request<OrderResult>("/pub/v1/orders", { method: "POST", body: input, token }),
  /** Real-PG phase 1: create the order and get the payment-window parameters (clientKey, amount…). */
  prepare: (input: PreparePaymentInput, token: string) =>
    request<PreparePaymentResult>("/pub/v1/orders/prepare", { method: "POST", body: input, token }),
  /** Real-PG phase 2: confirm with the key the window redirected back with (calls the live PG). */
  confirm: (input: ConfirmPaymentInput, token: string) =>
    request<OrderResult>("/pub/v1/orders/confirm", { method: "POST", body: input, token }),
  /** List the signed-in member's orders, newest first. */
  list: (token: string) => request<OrderListItem[]>("/pub/v1/orders", { token }),
  /** Live delivery tracking for one of the member's orders (calls the courier API). */
  tracking: (orderNo: string, token: string) =>
    request<Tracking>(`/pub/v1/orders/${orderNo}/tracking`, { token }),
};

export const orderKeys = {
  list: ["orders"] as const,
};

/** My order history — enabled only when a member token is present. */
export function useMyOrders(token: string | null) {
  return useQuery({
    queryKey: orderKeys.list,
    queryFn: () => orderApi.list(token as string),
    enabled: token != null,
  });
}

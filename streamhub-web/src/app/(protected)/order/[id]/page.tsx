"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Loader2, Truck, PackageCheck } from "lucide-react";

import {
  useOrderStatus,
  useOrderTracking,
  useOrderDetail,
  useOrderCarriers,
  orderDeliverySyncUpdate,
} from "@/apis/query/order/order";
import {
  OrderReceiptDtoKind,
  type OrderDetail,
  type OrderItemDto,
  type OrderReceiptDto,
  type Tracking,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDateTime } from "@/lib/format";
import OrderStatusBadge from "@/components/order/OrderStatusBadge";
import OrderStatusStepper from "@/components/order/OrderStatusStepper";
import {
  STATUS_LABEL,
  isDestructive,
  isTrackingEnabled,
  type OrderStatus,
} from "@/lib/order-status";
import { SUCCESS_CODE } from "@/types/api";

const PAY_METHOD_LABEL: Record<string, string> = {
  BANK: "무통장",
  CARD: "카드",
};

interface ReadonlyFieldProps {
  label: string;
  value: React.ReactNode;
}

function ReadonlyField({ label, value }: ReadonlyFieldProps) {
  return (
    <div>
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <div className="mt-1 text-sm text-slate-900">{value}</div>
    </div>
  );
}

function formatKrw(value?: number | null): string {
  return `${(value ?? 0).toLocaleString()}원`;
}

function payMethodLabel(value?: string): string {
  if (!value) {
    return "-";
  }
  return PAY_METHOD_LABEL[value] ?? value;
}

export default function OrderDetailPage() {
  const params = useParams<{ id: string }>();
  const orderId = Number(params.id);

  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pendingTransition, setPendingTransition] = useState<OrderStatus | null>(
    null,
  );
  const [memo, setMemo] = useState("");
  const [trackingNo, setTrackingNo] = useState("");
  const [shipCompany, setShipCompany] = useState("");

  // Live delivery tracking (C8) — courier list for the dropdown + on-demand shipment lookup.
  const [tracking, setTracking] = useState<Tracking | null>(null);
  const [trackingLoading, setTrackingLoading] = useState(false);
  const [trackingError, setTrackingError] = useState<string | null>(null);

  const detailQuery = useOrderDetail(orderId, {
    query: { enabled: Number.isFinite(orderId) },
  });
  const carriersQuery = useOrderCarriers();
  const carriers = carriersQuery.data?.resultObject ?? [];
  const changeStatusMutation = useOrderStatus();
  const changeTrackingMutation = useOrderTracking();

  const detail: OrderDetail | undefined = detailQuery.data?.resultObject;
  const status = detail?.status as OrderStatus | undefined;

  // Seed the tracking inputs from the fetched order.
  useEffect(() => {
    if (detail) {
      setTrackingNo(detail.trackingNo ?? "");
      setShipCompany(detail.shipCompany ?? "");
    }
  }, [detail]);

  const items: OrderItemDto[] = detail?.items ?? [];
  const receipts: OrderReceiptDto[] = detail?.receipts ?? [];

  const requestTransition = (next: OrderStatus) => {
    setMessage(null);
    setError(null);
    if (isDestructive(next)) {
      // Destructive transitions (CANCEL/RETURN) require explicit confirmation.
      setMemo("");
      setPendingTransition(next);
      return;
    }
    submitTransition(next, "");
  };

  const submitTransition = (next: OrderStatus, memoValue: string) => {
    changeStatusMutation.mutate(
      { id: orderId, data: { status: next, memo: memoValue.trim() || undefined } },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage(`${STATUS_LABEL[next]} 상태로 변경되었습니다.`);
            setPendingTransition(null);
            setMemo("");
            detailQuery.refetch();
          } else {
            setError(response.resultMessage ?? "상태 변경에 실패했습니다.");
          }
        },
        onError: () => setError("상태 변경 중 오류가 발생했습니다."),
      },
    );
  };

  const submitTracking = () => {
    setMessage(null);
    setError(null);
    if (!trackingNo.trim()) {
      setError("운송장번호를 입력하세요.");
      return;
    }
    changeTrackingMutation.mutate(
      {
        id: orderId,
        data: {
          trackingNo: trackingNo.trim(),
          shipCompany: shipCompany.trim() || undefined,
        },
      },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage("운송장 정보가 저장되었습니다.");
            detailQuery.refetch();
          } else {
            setError(response.resultMessage ?? "운송장 저장에 실패했습니다.");
          }
        },
        onError: () => setError("운송장 저장 중 오류가 발생했습니다."),
      },
    );
  };

  const loadTracking = async () => {
    setTracking(null);
    setTrackingError(null);
    setTrackingLoading(true);
    try {
      // delivery-sync both fetches the courier status AND advances the order state machine when the
      // carrier reports 배달완료/이동중 — so refetch the detail to reflect any auto-transition.
      const response = await orderDeliverySyncUpdate(orderId);
      if (response.resultCode === SUCCESS_CODE && response.resultObject) {
        setTracking(response.resultObject);
        detailQuery.refetch();
      } else {
        setTrackingError(response.resultMessage ?? "배송 조회에 실패했습니다.");
      }
    } catch {
      setTrackingError("배송 조회 중 오류가 발생했습니다.");
    } finally {
      setTrackingLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <Link
        href="/order"
        className="mb-4 inline-flex items-center gap-1 text-sm text-slate-600 transition hover:text-slate-900"
      >
        <ArrowLeft className="h-4 w-4" />
        목록으로
      </Link>

      <div className="mb-4 flex items-center gap-3">
        <h1 className="text-xl font-semibold text-slate-900">주문 상세</h1>
        {detail?.orderNo && (
          <span className="text-sm text-slate-500">{detail.orderNo}</span>
        )}
        {status && <OrderStatusBadge status={status} />}
      </div>

      {detailQuery.isPending ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : detailQuery.isError || !detail ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">
            주문 정보를 불러오지 못했습니다.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {message && (
            <p className="rounded-md bg-blue-50 px-3 py-2 text-sm text-blue-700">
              {message}
            </p>
          )}
          {error && (
            <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </p>
          )}

          {/* Status machine */}
          <section className="rounded-md border border-slate-200 bg-white p-6">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">
              주문 상태
            </h2>
            <OrderStatusStepper
              status={(status ?? "PLACED") as OrderStatus}
              pending={changeStatusMutation.isPending}
              onTransition={requestTransition}
            />
          </section>

          {/* Orderer / receiver */}
          <section className="rounded-md border border-slate-200 bg-white p-6">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">
              주문자 / 수령자
            </h2>
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
              <ReadonlyField label="주문번호" value={detail.orderNo ?? "-"} />
              <ReadonlyField
                label="주문일시"
                value={formatDateTime(detail.orderedAt)}
              />
              <ReadonlyField
                label="회원"
                value={detail.memberName ?? "-"}
              />
              <ReadonlyField
                label="결제수단"
                value={payMethodLabel(detail.payMethod)}
              />
              <ReadonlyField
                label="주문자명"
                value={detail.orderedName ?? "-"}
              />
              <ReadonlyField
                label="주문자 연락처"
                value={detail.orderedPhone ?? "-"}
              />
              <ReadonlyField
                label="받는분"
                value={detail.receiverName ?? "-"}
              />
              <ReadonlyField
                label="받는분 연락처"
                value={detail.receiverPhone ?? "-"}
              />
              <div className="sm:col-span-2">
                <ReadonlyField
                  label="배송지"
                  value={detail.receiverAddr ?? "-"}
                />
              </div>
            </div>
          </section>

          {/* Order items */}
          <section className="rounded-md border border-slate-200 bg-white p-6">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">
              주문 상품
            </h2>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left text-xs font-medium text-slate-500">
                    <th className="py-2 pr-3">상품명</th>
                    <th className="py-2 pr-3">옵션</th>
                    <th className="py-2 pr-3 text-right">단가</th>
                    <th className="py-2 pr-3 text-right">수량</th>
                    <th className="py-2 text-right">합계</th>
                  </tr>
                </thead>
                <tbody>
                  {items.length === 0 ? (
                    <tr>
                      <td
                        colSpan={5}
                        className="py-4 text-center text-sm text-slate-400"
                      >
                        주문 상품이 없습니다.
                      </td>
                    </tr>
                  ) : (
                    items.map((item, index) => (
                      <tr
                        key={item.id ?? index}
                        className="border-b border-slate-100 text-slate-900"
                      >
                        <td className="py-2.5 pr-3">{item.goodsName ?? "-"}</td>
                        <td className="py-2.5 pr-3 text-slate-600">
                          {item.optionName ?? "-"}
                        </td>
                        <td className="py-2.5 pr-3 text-right">
                          {formatKrw(item.unitPrice)}
                        </td>
                        <td className="py-2.5 pr-3 text-right">
                          {(item.qty ?? 0).toLocaleString()}
                        </td>
                        <td className="py-2.5 text-right font-medium">
                          {formatKrw(item.lineTotal)}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>

          {/* Totals summary */}
          <section className="rounded-md border border-slate-200 bg-white p-6">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">
              합계 요약
            </h2>
            <dl className="space-y-2 text-sm">
              <div className="flex items-center justify-between">
                <dt className="text-slate-500">상품합계</dt>
                <dd className="text-slate-900">{formatKrw(detail.goodsTotal)}</dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-slate-500">배송비</dt>
                <dd className="text-slate-900">{formatKrw(detail.shipFee)}</dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-slate-500">쿠폰할인</dt>
                <dd className="text-slate-900">
                  −{formatKrw(detail.couponDiscount)}
                </dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-slate-500">포인트 사용</dt>
                <dd className="text-slate-900">
                  −{formatKrw(detail.pointUsed)}
                </dd>
              </div>
              <div className="mt-2 flex items-center justify-between border-t border-slate-200 pt-3">
                <dt className="font-semibold text-slate-900">최종 결제금액</dt>
                <dd className="text-base font-semibold text-brand">
                  {formatKrw(detail.total)}
                </dd>
              </div>
            </dl>
          </section>

          {/* Receipts */}
          <section className="rounded-md border border-slate-200 bg-white p-6">
            <h2 className="mb-4 text-sm font-semibold text-slate-900">
              입금 / 환불 영수증
            </h2>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left text-xs font-medium text-slate-500">
                    <th className="py-2 pr-3">구분</th>
                    <th className="py-2 pr-3 text-right">금액</th>
                    <th className="py-2 pr-3">방법</th>
                    <th className="py-2 pr-3">비고</th>
                    <th className="py-2">처리일시</th>
                  </tr>
                </thead>
                <tbody>
                  {receipts.length === 0 ? (
                    <tr>
                      <td
                        colSpan={5}
                        className="py-4 text-center text-sm text-slate-400"
                      >
                        영수증 내역이 없습니다.
                      </td>
                    </tr>
                  ) : (
                    receipts.map((receipt, index) => {
                      const isPay = receipt.kind === OrderReceiptDtoKind.PAY;
                      return (
                        <tr
                          key={receipt.id ?? index}
                          className="border-b border-slate-100 text-slate-900"
                        >
                          <td className="py-2.5 pr-3">
                            <span
                              className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                                isPay
                                  ? "bg-emerald-100 text-emerald-700"
                                  : "bg-red-100 text-red-700"
                              }`}
                            >
                              {isPay ? "입금" : "환불"}
                            </span>
                          </td>
                          <td className="py-2.5 pr-3 text-right">
                            {isPay ? "" : "−"}
                            {formatKrw(receipt.amount)}
                          </td>
                          <td className="py-2.5 pr-3">
                            {payMethodLabel(receipt.method)}
                          </td>
                          <td className="py-2.5 pr-3 text-slate-600">
                            {receipt.memo ?? "-"}
                          </td>
                          <td className="py-2.5">
                            {formatDateTime(receipt.createdAt)}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </section>

          {/* Tracking */}
          <section className="rounded-md border border-slate-200 bg-white p-6">
            <h2 className="mb-1 text-sm font-semibold text-slate-900">
              운송장 정보
            </h2>
            <p className="mb-4 text-xs text-slate-500">
              배송준비 / 배송중 상태에서 운송장을 입력할 수 있습니다.
            </p>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label
                  htmlFor="trackingNo"
                  className="mb-1 block text-xs font-medium text-slate-500"
                >
                  운송장번호
                </label>
                <input
                  id="trackingNo"
                  type="text"
                  value={trackingNo}
                  disabled={!isTrackingEnabled(status)}
                  onChange={(event) => setTrackingNo(event.target.value)}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400"
                />
              </div>
              <div>
                <label
                  htmlFor="shipCompany"
                  className="mb-1 block text-xs font-medium text-slate-500"
                >
                  택배사
                </label>
                <select
                  id="shipCompany"
                  value={shipCompany}
                  disabled={!isTrackingEnabled(status) || carriersQuery.isLoading}
                  onChange={(event) => setShipCompany(event.target.value)}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400"
                >
                  <option value="">택배사 선택</option>
                  {carriers.map((carrier) => (
                    <option key={carrier.code} value={carrier.code ?? ""}>
                      {carrier.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="mt-4 flex justify-end">
              <button
                type="button"
                onClick={submitTracking}
                disabled={
                  !isTrackingEnabled(status) || changeTrackingMutation.isPending
                }
                className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
              >
                {changeTrackingMutation.isPending && (
                  <Loader2 className="h-4 w-4 animate-spin" />
                )}
                운송장 저장
              </button>
            </div>

            {/* Live delivery tracking (C8) — calls the courier API for the saved invoice. */}
            <div className="mt-6 border-t border-slate-200 pt-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5">
                  <Truck className="h-4 w-4 text-slate-400" />
                  <h3 className="text-sm font-semibold text-slate-900">배송 조회</h3>
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-500">
                    스마트택배 연동
                  </span>
                </div>
                <button
                  type="button"
                  onClick={loadTracking}
                  disabled={!detail?.trackingNo || trackingLoading}
                  className="flex items-center gap-1.5 rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {trackingLoading && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                  배송 조회
                </button>
              </div>

              {!detail?.trackingNo && (
                <p className="mt-3 text-xs text-slate-400">
                  운송장을 저장하면 택배사 API로 실시간 배송상황을 조회할 수 있습니다.
                </p>
              )}

              {trackingError && (
                <p className="mt-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
                  {trackingError}
                </p>
              )}

              {tracking && (
                <div className="mt-3">
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600">
                    <span>
                      택배사 <strong className="text-slate-900">{tracking.carrierName ?? "-"}</strong>
                    </span>
                    <span>
                      송장 <strong className="font-mono text-slate-900">{tracking.invoiceNo ?? "-"}</strong>
                    </span>
                    <span
                      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-medium ${
                        tracking.completed
                          ? "bg-emerald-100 text-emerald-700"
                          : "bg-blue-100 text-blue-700"
                      }`}
                    >
                      {tracking.completed && <PackageCheck className="h-3 w-3" />}
                      {tracking.completed ? "배달완료" : "배송중"}
                    </span>
                  </div>

                  {tracking.events && tracking.events.length > 0 ? (
                    <ol className="mt-3 space-y-3 border-l border-slate-200 pl-4">
                      {tracking.events.map((event, index) => (
                        <li key={index} className="relative">
                          <span className="absolute -left-[21px] top-1 h-2 w-2 rounded-full bg-brand" />
                          <p className="text-sm text-slate-900">{event.description ?? "-"}</p>
                          <p className="text-xs text-slate-500">
                            {event.location ?? ""} · {event.time ?? ""}
                          </p>
                        </li>
                      ))}
                    </ol>
                  ) : (
                    <p className="mt-3 text-xs text-slate-400">
                      아직 등록된 배송 이벤트가 없습니다(택배사 조회 결과 없음).
                    </p>
                  )}
                </div>
              )}
            </div>
          </section>
        </div>
      )}

      {/* Destructive transition confirmation modal */}
      {pendingTransition && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-md rounded-md bg-white p-6 shadow-lg">
            <h3 className="text-base font-semibold text-slate-900">
              {STATUS_LABEL[pendingTransition]} 처리 확인
            </h3>
            <p className="mt-2 text-sm text-slate-600">
              이 주문을 <strong>{STATUS_LABEL[pendingTransition]}</strong>(으)로
              변경하면 재고 복원과 환불 영수증이 기록됩니다. 계속하시겠습니까?
            </p>
            <div className="mt-4">
              <label
                htmlFor="memo"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                처리 메모 (선택)
              </label>
              <input
                id="memo"
                type="text"
                value={memo}
                onChange={(event) => setMemo(event.target.value)}
                placeholder="예: 고객 요청 취소"
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
              />
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setPendingTransition(null);
                  setMemo("");
                }}
                disabled={changeStatusMutation.isPending}
                className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:opacity-60"
              >
                취소
              </button>
              <button
                type="button"
                onClick={() => submitTransition(pendingTransition, memo)}
                disabled={changeStatusMutation.isPending}
                className="flex items-center gap-1.5 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {changeStatusMutation.isPending && (
                  <Loader2 className="h-4 w-4 animate-spin" />
                )}
                {STATUS_LABEL[pendingTransition]} 처리
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

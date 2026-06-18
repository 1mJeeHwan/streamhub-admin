"use client";

import { useState } from "react";
import {
  ChevronDown,
  ChevronUp,
  Image as ImageIcon,
  Loader2,
  Pencil,
  Plus,
  Trash2,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import {
  bannerList,
  useBannerDelete,
  useBannerSortUpdate,
} from "@/apis/query/banner/banner";
import {
  BannerDtoDevice,
  BannerDtoPosition,
  type BannerDto,
  type BannerSearchRequest,
  type BannerSearchRequestDevice,
  type BannerSearchRequestPosition,
} from "@/apis/query/streamHubAdminAPI.schemas";
import BannerFormDialog from "@/components/banners/BannerFormDialog";
import { SUCCESS_CODE } from "@/types/api";

const POSITION_LABELS: Record<BannerDtoPosition, string> = {
  MAIN_TOP: "메인 상단",
  MAIN_MIDDLE: "메인 중단",
  MAIN_BOTTOM: "메인 하단",
  SIDE: "사이드",
  POPUP: "팝업",
};

const DEVICE_LABELS: Record<BannerDtoDevice, string> = {
  PC: "PC",
  MOBILE: "모바일",
  ALL: "전체",
};

const POSITION_BADGE: Record<BannerDtoPosition, string> = {
  MAIN_TOP: "bg-indigo-100 text-indigo-700",
  MAIN_MIDDLE: "bg-sky-100 text-sky-700",
  MAIN_BOTTOM: "bg-cyan-100 text-cyan-700",
  SIDE: "bg-amber-100 text-amber-700",
  POPUP: "bg-rose-100 text-rose-700",
};

const FILTER_FIELD_CLASS =
  "rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

const formatPeriod = (value?: string) => {
  if (!value) {
    return "-";
  }
  return value.replace("T", " ").slice(0, 16);
};

const isExpired = (endAt?: string) => {
  if (!endAt) {
    return false;
  }
  const end = new Date(endAt);
  if (Number.isNaN(end.getTime())) {
    return false;
  }
  return end.getTime() < Date.now();
};

export default function BannersPage() {
  const [position, setPosition] = useState<BannerSearchRequestPosition | "">("");
  const [device, setDevice] = useState<BannerSearchRequestDevice | "">("");
  const [useYn, setUseYn] = useState<string>("");

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<BannerDto | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const searchRequest: BannerSearchRequest = {
    position: position || undefined,
    device: device || undefined,
    useYn: useYn || undefined,
  };

  const listQuery = useQuery({
    queryKey: ["banner-list", position, device, useYn],
    queryFn: ({ signal }) => bannerList(searchRequest, signal),
  });

  const deleteMutation = useBannerDelete();
  const sortMutation = useBannerSortUpdate();

  const banners: BannerDto[] = listQuery.data?.resultObject ?? [];

  const openCreate = () => {
    setEditing(null);
    setMessage(null);
    setDialogOpen(true);
  };

  const openEdit = (banner: BannerDto) => {
    setEditing(banner);
    setMessage(null);
    setDialogOpen(true);
  };

  const handleSaved = () => {
    setDialogOpen(false);
    setEditing(null);
    setMessage("저장되었습니다.");
    listQuery.refetch();
  };

  const handleDelete = (banner: BannerDto) => {
    if (banner.id == null) {
      return;
    }
    if (!window.confirm(`'${banner.title ?? "배너"}'을(를) 삭제하시겠습니까?`)) {
      return;
    }
    setMessage(null);
    deleteMutation.mutate(
      { id: banner.id },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage("삭제되었습니다.");
            listQuery.refetch();
          } else {
            setMessage(response.resultMessage ?? "삭제에 실패했습니다.");
          }
        },
        onError: () => setMessage("삭제 중 오류가 발생했습니다."),
      },
    );
  };

  const handleReorder = async (index: number, direction: "up" | "down") => {
    const target = banners[index];
    const swapWith = banners[direction === "up" ? index - 1 : index + 1];
    if (
      !target ||
      !swapWith ||
      target.id == null ||
      swapWith.id == null ||
      target.sortOrder == null ||
      swapWith.sortOrder == null
    ) {
      return;
    }
    setMessage(null);
    try {
      const targetResult = await sortMutation.mutateAsync({
        id: target.id,
        data: { sortOrder: swapWith.sortOrder },
      });
      if (targetResult.resultCode !== SUCCESS_CODE) {
        setMessage(targetResult.resultMessage ?? "정렬 변경에 실패했습니다.");
        return;
      }
      const swapResult = await sortMutation.mutateAsync({
        id: swapWith.id,
        data: { sortOrder: target.sortOrder },
      });
      if (swapResult.resultCode !== SUCCESS_CODE) {
        setMessage(swapResult.resultMessage ?? "정렬 변경에 실패했습니다.");
        return;
      }
      setMessage("정렬 순서가 변경되었습니다.");
      listQuery.refetch();
    } catch {
      setMessage("정렬 변경 중 오류가 발생했습니다.");
    }
  };

  return (
    <div>
      <div className="mb-4 flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">배너 관리</h1>
          <p className="mt-1 text-sm text-slate-500">
            노출 위치/디바이스별 배너 이미지와 노출 기간을 관리합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={openCreate}
          className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          <Plus className="h-4 w-4" />
          배너 등록
        </button>
      </div>

      {/* Filters */}
      <div className="mb-4 flex flex-wrap items-end gap-3 rounded-md border border-slate-200 bg-white p-4">
        <div className="flex flex-col">
          <label
            htmlFor="filter-position"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            노출 위치
          </label>
          <select
            id="filter-position"
            value={position}
            onChange={(event) =>
              setPosition(event.target.value as BannerSearchRequestPosition | "")
            }
            className={FILTER_FIELD_CLASS}
          >
            <option value="">전체</option>
            {Object.values(BannerDtoPosition).map((value) => (
              <option key={value} value={value}>
                {POSITION_LABELS[value]}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col">
          <label
            htmlFor="filter-device"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            디바이스
          </label>
          <select
            id="filter-device"
            value={device}
            onChange={(event) =>
              setDevice(event.target.value as BannerSearchRequestDevice | "")
            }
            className={FILTER_FIELD_CLASS}
          >
            <option value="">전체</option>
            {Object.values(BannerDtoDevice).map((value) => (
              <option key={value} value={value}>
                {DEVICE_LABELS[value]}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col">
          <label
            htmlFor="filter-use"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            사용 여부
          </label>
          <select
            id="filter-use"
            value={useYn}
            onChange={(event) => setUseYn(event.target.value)}
            className={FILTER_FIELD_CLASS}
          >
            <option value="">전체</option>
            <option value="Y">사용</option>
            <option value="N">미사용</option>
          </select>
        </div>
      </div>

      {/* Summary */}
      <div className="mb-3 flex items-center gap-3">
        <span className="text-sm text-slate-600">
          총 {banners.length.toLocaleString()}건
        </span>
        {message && (
          <span className="rounded-md bg-blue-50 px-2.5 py-1 text-xs text-blue-700">
            {message}
          </span>
        )}
      </div>

      {/* List */}
      {listQuery.isLoading ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : listQuery.isError ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">배너 목록을 불러오지 못했습니다.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-md border border-slate-200 bg-white">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
              <tr>
                <th className="px-4 py-3">썸네일</th>
                <th className="px-4 py-3">제목</th>
                <th className="px-4 py-3">위치</th>
                <th className="px-4 py-3">디바이스</th>
                <th className="px-4 py-3">기간</th>
                <th className="px-4 py-3">정렬</th>
                <th className="px-4 py-3">사용</th>
                <th className="px-4 py-3 text-right">관리</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {banners.length === 0 ? (
                <tr>
                  <td
                    colSpan={8}
                    className="px-4 py-10 text-center text-slate-400"
                  >
                    조회된 배너가 없습니다.
                  </td>
                </tr>
              ) : (
                banners.map((banner, index) => {
                  const expired = isExpired(banner.endAt);
                  const isFirst = index === 0;
                  const isLast = index === banners.length - 1;
                  return (
                    <tr key={banner.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3">
                        {banner.imageUrl ? (
                          // eslint-disable-next-line @next/next/no-img-element
                          <img
                            src={banner.imageUrl}
                            alt={banner.title ?? "배너"}
                            className="h-12 w-20 rounded border border-slate-200 object-cover"
                          />
                        ) : (
                          <div className="flex h-12 w-20 items-center justify-center rounded border border-dashed border-slate-200 text-slate-300">
                            <ImageIcon className="h-4 w-4" />
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3 font-medium text-slate-900">
                        {banner.title ?? "-"}
                      </td>
                      <td className="px-4 py-3">
                        {banner.position ? (
                          <span
                            className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${POSITION_BADGE[banner.position]}`}
                          >
                            {POSITION_LABELS[banner.position]}
                          </span>
                        ) : (
                          "-"
                        )}
                      </td>
                      <td className="px-4 py-3">
                        {banner.device ? (
                          <span className="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
                            {DEVICE_LABELS[banner.device]}
                          </span>
                        ) : (
                          "-"
                        )}
                      </td>
                      <td className="px-4 py-3 text-xs">
                        <div className="text-slate-700">
                          {formatPeriod(banner.startAt)}
                        </div>
                        <div className={expired ? "text-red-600" : "text-slate-500"}>
                          ~ {formatPeriod(banner.endAt)}
                          {expired && " (만료)"}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-slate-700">
                        <div className="flex items-center gap-2">
                          <span className="w-6 text-center tabular-nums">
                            {banner.sortOrder ?? "-"}
                          </span>
                          <div className="flex flex-col">
                            <button
                              type="button"
                              onClick={() => handleReorder(index, "up")}
                              disabled={isFirst || sortMutation.isPending}
                              className="rounded p-0.5 text-slate-400 transition hover:bg-slate-100 hover:text-brand disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-slate-400"
                              aria-label="위로"
                            >
                              <ChevronUp className="h-4 w-4" />
                            </button>
                            <button
                              type="button"
                              onClick={() => handleReorder(index, "down")}
                              disabled={isLast || sortMutation.isPending}
                              className="rounded p-0.5 text-slate-400 transition hover:bg-slate-100 hover:text-brand disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-slate-400"
                              aria-label="아래로"
                            >
                              <ChevronDown className="h-4 w-4" />
                            </button>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                            banner.useYn === "N"
                              ? "bg-slate-200 text-slate-600"
                              : "bg-emerald-100 text-emerald-700"
                          }`}
                        >
                          {banner.useYn === "N" ? "미사용" : "사용"}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            type="button"
                            onClick={() => openEdit(banner)}
                            className="rounded p-1.5 text-slate-500 transition hover:bg-slate-100 hover:text-brand"
                            aria-label="수정"
                          >
                            <Pencil className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDelete(banner)}
                            disabled={deleteMutation.isPending}
                            className="rounded p-1.5 text-slate-500 transition hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
                            aria-label="삭제"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      )}

      {dialogOpen && (
        <BannerFormDialog
          banner={editing}
          onClose={() => setDialogOpen(false)}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
}

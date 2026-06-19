"use client";

import { useEffect, useState } from "react";
import { Loader2, X } from "lucide-react";

import {
  useBannerCreate,
  useBannerUpdate,
} from "@/apis/query/banner/banner";
import {
  BannerDtoDevice,
  BannerDtoPosition,
  BannerDtoTargetType,
  type BannerDto,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { SUCCESS_CODE } from "@/types/api";

const FIELD_CLASS =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

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

/** Content-tab target on the user site. "" = not a tab banner (legacy main/side banner). */
const TARGET_LABELS: Record<BannerDtoTargetType, string> = {
  VIDEO: "영상 탭",
  SOUND: "음악 탭",
  ALL: "전체 탭",
};

interface BannerFormDialogProps {
  /** Banner being edited, or null/undefined when creating a new one. */
  banner?: BannerDto | null;
  onClose: () => void;
  /** Called after a successful create/update so the parent can refetch. */
  onSaved: () => void;
}

interface BannerFormState {
  title: string;
  subtitle: string;
  position: BannerDtoPosition;
  device: BannerDtoDevice;
  /** "" = not a content-tab banner. */
  targetType: "" | BannerDtoTargetType;
  imageUrl: string;
  linkUrl: string;
  startAt: string;
  endAt: string;
  sortOrder: string;
  useYn: string;
}

const buildFormState = (banner?: BannerDto | null): BannerFormState => ({
  title: banner?.title ?? "",
  subtitle: banner?.subtitle ?? "",
  position: banner?.position ?? BannerDtoPosition.MAIN_TOP,
  device: banner?.device ?? BannerDtoDevice.ALL,
  targetType: banner?.targetType ?? "",
  imageUrl: banner?.imageUrl ?? "",
  linkUrl: banner?.linkUrl ?? "",
  startAt: banner?.startAt ?? "",
  endAt: banner?.endAt ?? "",
  sortOrder: banner?.sortOrder != null ? String(banner.sortOrder) : "0",
  useYn: banner?.useYn ?? "Y",
});

/**
 * BannerFormDialog is a modal create/edit form for a banner. When `banner` has
 * an id it issues an update; otherwise it creates a new banner.
 */
export default function BannerFormDialog({
  banner,
  onClose,
  onSaved,
}: BannerFormDialogProps) {
  const isEdit = banner?.id != null;
  const [form, setForm] = useState<BannerFormState>(() => buildFormState(banner));
  const [message, setMessage] = useState<string | null>(null);

  const createMutation = useBannerCreate();
  const updateMutation = useBannerUpdate();
  const isPending = createMutation.isPending || updateMutation.isPending;

  useEffect(() => {
    setForm(buildFormState(banner));
  }, [banner]);

  const update = <K extends keyof BannerFormState>(
    key: K,
    value: BannerFormState[K],
  ) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const handleResult = (resultCode?: string, resultMessage?: string) => {
    if (resultCode === SUCCESS_CODE) {
      onSaved();
    } else {
      setMessage(resultMessage ?? "저장에 실패했습니다.");
    }
  };

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setMessage(null);

    if (!form.title.trim()) {
      setMessage("제목을 입력해 주세요.");
      return;
    }

    const parsedSort = Number(form.sortOrder);
    const payload: BannerDto = {
      title: form.title.trim(),
      subtitle: form.subtitle.trim() || undefined,
      position: form.position,
      device: form.device,
      targetType: form.targetType || undefined,
      imageUrl: form.imageUrl.trim() || undefined,
      linkUrl: form.linkUrl.trim() || undefined,
      startAt: form.startAt.trim() || undefined,
      endAt: form.endAt.trim() || undefined,
      sortOrder: Number.isFinite(parsedSort) ? parsedSort : 0,
      useYn: form.useYn,
    };

    if (isEdit && banner?.id != null) {
      updateMutation.mutate(
        { id: banner.id, data: payload },
        {
          onSuccess: (response) =>
            handleResult(response.resultCode, response.resultMessage),
          onError: () => setMessage("저장 중 오류가 발생했습니다."),
        },
      );
    } else {
      createMutation.mutate(
        { data: payload },
        {
          onSuccess: (response) =>
            handleResult(response.resultCode, response.resultMessage),
          onError: () => setMessage("저장 중 오류가 발생했습니다."),
        },
      );
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-md bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
          <h2 className="text-base font-semibold text-slate-900">
            {isEdit ? "배너 수정" : "배너 등록"}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
            aria-label="닫기"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-5 py-4" noValidate>
          {message && (
            <p className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
              {message}
            </p>
          )}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {/* Title */}
            <div className="sm:col-span-2">
              <label
                htmlFor="banner-title"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                제목 *
              </label>
              <input
                id="banner-title"
                type="text"
                className={FIELD_CLASS}
                value={form.title}
                onChange={(event) => update("title", event.target.value)}
              />
            </div>

            {/* Subtitle */}
            <div className="sm:col-span-2">
              <label
                htmlFor="banner-subtitle"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                부제 (탭 배너 설명)
              </label>
              <input
                id="banner-subtitle"
                type="text"
                className={FIELD_CLASS}
                value={form.subtitle}
                onChange={(event) => update("subtitle", event.target.value)}
              />
            </div>

            {/* Target tab */}
            <div className="sm:col-span-2">
              <label
                htmlFor="banner-target"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                사용자 사이트 탭 노출
              </label>
              <select
                id="banner-target"
                className={FIELD_CLASS}
                value={form.targetType}
                onChange={(event) =>
                  update("targetType", event.target.value as "" | BannerDtoTargetType)
                }
              >
                <option value="">노출 안 함 (일반 배너)</option>
                {Object.values(BannerDtoTargetType).map((target) => (
                  <option key={target} value={target}>
                    {TARGET_LABELS[target]}
                  </option>
                ))}
              </select>
            </div>

            {/* Position */}
            <div>
              <label
                htmlFor="banner-position"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                노출 위치
              </label>
              <select
                id="banner-position"
                className={FIELD_CLASS}
                value={form.position}
                onChange={(event) =>
                  update("position", event.target.value as BannerDtoPosition)
                }
              >
                {Object.values(BannerDtoPosition).map((position) => (
                  <option key={position} value={position}>
                    {POSITION_LABELS[position]}
                  </option>
                ))}
              </select>
            </div>

            {/* Device */}
            <div>
              <label
                htmlFor="banner-device"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                디바이스
              </label>
              <select
                id="banner-device"
                className={FIELD_CLASS}
                value={form.device}
                onChange={(event) =>
                  update("device", event.target.value as BannerDtoDevice)
                }
              >
                {Object.values(BannerDtoDevice).map((device) => (
                  <option key={device} value={device}>
                    {DEVICE_LABELS[device]}
                  </option>
                ))}
              </select>
            </div>

            {/* Image URL */}
            <div className="sm:col-span-2">
              <label
                htmlFor="banner-image"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                이미지 URL (탭 배너는 비워두면 그라데이션)
              </label>
              <input
                id="banner-image"
                type="text"
                placeholder="https://..."
                className={FIELD_CLASS}
                value={form.imageUrl}
                onChange={(event) => update("imageUrl", event.target.value)}
              />
            </div>

            {/* Link URL */}
            <div className="sm:col-span-2">
              <label
                htmlFor="banner-link"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                링크 URL
              </label>
              <input
                id="banner-link"
                type="text"
                placeholder="https://..."
                className={FIELD_CLASS}
                value={form.linkUrl}
                onChange={(event) => update("linkUrl", event.target.value)}
              />
            </div>

            {/* Start at */}
            <div>
              <label
                htmlFor="banner-start"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                노출 시작
              </label>
              <input
                id="banner-start"
                type="datetime-local"
                className={FIELD_CLASS}
                value={form.startAt}
                onChange={(event) => update("startAt", event.target.value)}
              />
            </div>

            {/* End at */}
            <div>
              <label
                htmlFor="banner-end"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                노출 종료
              </label>
              <input
                id="banner-end"
                type="datetime-local"
                className={FIELD_CLASS}
                value={form.endAt}
                onChange={(event) => update("endAt", event.target.value)}
              />
            </div>

            {/* Sort order */}
            <div>
              <label
                htmlFor="banner-sort"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                정렬 순서
              </label>
              <input
                id="banner-sort"
                type="number"
                className={FIELD_CLASS}
                value={form.sortOrder}
                onChange={(event) => update("sortOrder", event.target.value)}
              />
            </div>

            {/* Use yn */}
            <div>
              <label
                htmlFor="banner-use"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                사용 여부
              </label>
              <select
                id="banner-use"
                className={FIELD_CLASS}
                value={form.useYn}
                onChange={(event) => update("useYn", event.target.value)}
              >
                <option value="Y">사용</option>
                <option value="N">미사용</option>
              </select>
            </div>
          </div>

          <div className="mt-5 flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              disabled={isPending}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:opacity-60"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={isPending}
              className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              저장
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

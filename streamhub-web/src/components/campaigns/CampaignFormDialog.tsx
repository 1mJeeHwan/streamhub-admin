"use client";

import { useEffect, useState } from "react";
import { Loader2, X } from "lucide-react";

import {
  useCampaignCreate,
  useCampaignUpdate,
} from "@/apis/query/campaign/campaign";
import {
  CampaignDtoType,
  type CampaignDto,
  type CampaignDtoType as CampaignType,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { SUCCESS_CODE } from "@/types/api";

interface CampaignFormDialogProps {
  /** Campaign being edited, or null/undefined when creating a new one. */
  campaign?: CampaignDto | null;
  onClose: () => void;
  /** Called after a successful create/update so the parent can refetch. */
  onSaved: () => void;
}

const FIELD_CLASS =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

const TYPE_OPTIONS: { value: CampaignType; label: string }[] = [
  { value: CampaignDtoType.SPECIAL_DONATION, label: "특별헌금" },
  { value: CampaignDtoType.NEW_RELEASE, label: "신규 출시" },
  { value: CampaignDtoType.EVENT, label: "이벤트" },
  { value: CampaignDtoType.SEASONAL, label: "시즌" },
];

interface FormState {
  title: string;
  type: CampaignType;
  description: string;
  bannerImageUrl: string;
  linkedGoodsIds: string;
  targetAmount: string;
  startAt: string;
  endAt: string;
}

/** Trims a datetime-local string to the 16-char form the input expects. */
function toDateTimeLocal(value?: string): string {
  if (!value) {
    return "";
  }
  return value.slice(0, 16);
}

function buildDefaults(campaign?: CampaignDto | null): FormState {
  return {
    title: campaign?.title ?? "",
    type: campaign?.type ?? CampaignDtoType.EVENT,
    description: campaign?.description ?? "",
    bannerImageUrl: campaign?.bannerImageUrl ?? "",
    linkedGoodsIds: campaign?.linkedGoodsIds ?? "",
    targetAmount:
      campaign?.targetAmount != null ? String(campaign.targetAmount) : "",
    startAt: toDateTimeLocal(campaign?.startAt),
    endAt: toDateTimeLocal(campaign?.endAt),
  };
}

/**
 * CampaignFormDialog is a modal create/edit form for a campaign. When
 * `campaign` has an id it issues an update; otherwise it creates a new one.
 */
export default function CampaignFormDialog({
  campaign,
  onClose,
  onSaved,
}: CampaignFormDialogProps) {
  const isEdit = campaign?.id != null;
  const [form, setForm] = useState<FormState>(() => buildDefaults(campaign));
  const [message, setMessage] = useState<string | null>(null);

  const createMutation = useCampaignCreate();
  const updateMutation = useCampaignUpdate();
  const isPending = createMutation.isPending || updateMutation.isPending;

  useEffect(() => {
    setForm(buildDefaults(campaign));
    setMessage(null);
  }, [campaign]);

  const update = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const handleResult = (resultCode?: string, resultMessage?: string) => {
    if (resultCode === SUCCESS_CODE) {
      onSaved();
    } else {
      setMessage(resultMessage ?? "저장에 실패했습니다.");
    }
  };

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setMessage(null);

    if (!form.title.trim()) {
      setMessage("제목을 입력해 주세요.");
      return;
    }

    if (!form.startAt || !form.endAt) {
      setMessage("시작일과 종료일을 입력해 주세요.");
      return;
    }

    const payload: CampaignDto = {
      title: form.title.trim(),
      type: form.type,
      description: form.description.trim() || undefined,
      bannerImageUrl: form.bannerImageUrl.trim() || undefined,
      linkedGoodsIds: form.linkedGoodsIds.trim() || undefined,
      targetAmount: form.targetAmount.trim()
        ? Number(form.targetAmount)
        : undefined,
      startAt: form.startAt,
      endAt: form.endAt,
    };

    if (isEdit && campaign?.id != null) {
      updateMutation.mutate(
        { id: campaign.id, data: payload },
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
            {isEdit ? "캠페인 수정" : "캠페인 등록"}
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
                htmlFor="campaign-title"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                제목 *
              </label>
              <input
                id="campaign-title"
                type="text"
                className={FIELD_CLASS}
                value={form.title}
                onChange={(e) => update("title", e.target.value)}
              />
            </div>

            {/* Type */}
            <div>
              <label
                htmlFor="campaign-type"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                유형
              </label>
              <select
                id="campaign-type"
                className={FIELD_CLASS}
                value={form.type}
                onChange={(e) =>
                  update("type", e.target.value as CampaignType)
                }
              >
                {TYPE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>

            {/* Target amount */}
            <div>
              <label
                htmlFor="campaign-target"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                목표금액
              </label>
              <input
                id="campaign-target"
                type="number"
                min={0}
                placeholder="예: 1000000"
                className={FIELD_CLASS}
                value={form.targetAmount}
                onChange={(e) => update("targetAmount", e.target.value)}
              />
            </div>

            {/* Banner image url */}
            <div className="sm:col-span-2">
              <label
                htmlFor="campaign-banner"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                배너 이미지 URL
              </label>
              <input
                id="campaign-banner"
                type="text"
                placeholder="https://..."
                className={FIELD_CLASS}
                value={form.bannerImageUrl}
                onChange={(e) => update("bannerImageUrl", e.target.value)}
              />
            </div>

            {/* Start at */}
            <div>
              <label
                htmlFor="campaign-start"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                시작일시
              </label>
              <input
                id="campaign-start"
                type="datetime-local"
                className={FIELD_CLASS}
                value={form.startAt}
                onChange={(e) => update("startAt", e.target.value)}
              />
            </div>

            {/* End at */}
            <div>
              <label
                htmlFor="campaign-end"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                종료일시
              </label>
              <input
                id="campaign-end"
                type="datetime-local"
                className={FIELD_CLASS}
                value={form.endAt}
                onChange={(e) => update("endAt", e.target.value)}
              />
            </div>

            {/* Linked goods ids */}
            <div className="sm:col-span-2">
              <label
                htmlFor="campaign-goods"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                연계 상품 ID
              </label>
              <input
                id="campaign-goods"
                type="text"
                placeholder="예: 1,2,3"
                className={FIELD_CLASS}
                value={form.linkedGoodsIds}
                onChange={(e) => update("linkedGoodsIds", e.target.value)}
              />
            </div>

            {/* Description */}
            <div className="sm:col-span-2">
              <label
                htmlFor="campaign-desc"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                설명
              </label>
              <textarea
                id="campaign-desc"
                rows={3}
                className={FIELD_CLASS}
                value={form.description}
                onChange={(e) => update("description", e.target.value)}
              />
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

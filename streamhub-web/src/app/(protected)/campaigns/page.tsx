"use client";

import { useMemo, useState } from "react";
import {
  ArrowRight,
  ImageOff,
  Loader2,
  Pencil,
  Plus,
  Trash2,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import {
  campaignList,
  useCampaignDelete,
  useCampaignStatus,
} from "@/apis/query/campaign/campaign";
import {
  CampaignDtoStatus,
  CampaignDtoType,
  CampaignStatusChangeRequestStatus,
  type CampaignDto,
  type CampaignDtoStatus as CampaignStatus,
  type CampaignDtoType as CampaignType,
  type CampaignSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import CampaignFormDialog from "@/components/campaigns/CampaignFormDialog";
import { SUCCESS_CODE } from "@/types/api";

const TYPE_LABELS: Record<CampaignType, string> = {
  SPECIAL_DONATION: "특별헌금",
  NEW_RELEASE: "신규 출시",
  EVENT: "이벤트",
  SEASONAL: "시즌",
};

const STATUS_LABELS: Record<CampaignStatus, string> = {
  DRAFT: "초안",
  ACTIVE: "진행중",
  ENDED: "종료",
};

const STATUS_BADGE: Record<CampaignStatus, string> = {
  DRAFT: "bg-slate-200 text-slate-600",
  ACTIVE: "bg-emerald-100 text-emerald-700",
  ENDED: "bg-amber-100 text-amber-700",
};

/** Next status in the lifecycle, or null when there is none. */
const NEXT_STATUS: Record<CampaignStatus, CampaignStatus | null> = {
  DRAFT: CampaignDtoStatus.ACTIVE,
  ACTIVE: CampaignDtoStatus.ENDED,
  ENDED: null,
};

function formatDate(value?: string): string {
  if (!value) {
    return "-";
  }
  return value.slice(0, 10);
}

function formatPeriod(campaign: CampaignDto): string {
  if (!campaign.startAt && !campaign.endAt) {
    return "-";
  }
  return `${formatDate(campaign.startAt)} ~ ${formatDate(campaign.endAt)}`;
}

export default function CampaignsPage() {
  const [typeFilter, setTypeFilter] = useState<"" | CampaignType>("");
  const [statusFilter, setStatusFilter] = useState<"" | CampaignStatus>("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<CampaignDto | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const searchRequest = useMemo<CampaignSearchRequest>(
    () => ({
      type: typeFilter || undefined,
      status: statusFilter || undefined,
    }),
    [typeFilter, statusFilter],
  );

  const listQuery = useQuery({
    queryKey: ["campaign-list", searchRequest],
    queryFn: ({ signal }) => campaignList(searchRequest, signal),
  });

  const statusMutation = useCampaignStatus();
  const deleteMutation = useCampaignDelete();

  const campaigns: CampaignDto[] = listQuery.data?.resultObject ?? [];

  const openCreate = () => {
    setEditing(null);
    setMessage(null);
    setDialogOpen(true);
  };

  const openEdit = (campaign: CampaignDto) => {
    setEditing(campaign);
    setMessage(null);
    setDialogOpen(true);
  };

  const handleSaved = () => {
    setDialogOpen(false);
    setEditing(null);
    setMessage("저장되었습니다.");
    listQuery.refetch();
  };

  const handleStatusChange = (campaign: CampaignDto) => {
    if (campaign.id == null || campaign.status == null) {
      return;
    }
    const next = NEXT_STATUS[campaign.status];
    if (next == null) {
      return;
    }
    setMessage(null);
    statusMutation.mutate(
      {
        id: campaign.id,
        data: { status: next as CampaignStatusChangeRequestStatus },
      },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage("상태가 변경되었습니다.");
            listQuery.refetch();
          } else {
            setMessage(response.resultMessage ?? "상태 변경에 실패했습니다.");
          }
        },
        onError: () => setMessage("상태 변경 중 오류가 발생했습니다."),
      },
    );
  };

  const handleDelete = (campaign: CampaignDto) => {
    if (campaign.id == null) {
      return;
    }
    if (
      !window.confirm(
        `'${campaign.title ?? "캠페인"}'을(를) 삭제하시겠습니까?`,
      )
    ) {
      return;
    }
    setMessage(null);
    deleteMutation.mutate(
      { id: campaign.id },
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

  return (
    <div>
      <div className="mb-4 flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">
            캠페인·이벤트
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            기부 캠페인과 이벤트를 등록하고 진행 상태를 관리합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={openCreate}
          className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          <Plus className="h-4 w-4" />
          캠페인 등록
        </button>
      </div>

      {/* Filters */}
      <div className="mb-4 flex flex-wrap items-end gap-3 rounded-md border border-slate-200 bg-white p-4">
        <div className="flex flex-col">
          <label
            htmlFor="campaign-type-filter"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            유형
          </label>
          <select
            id="campaign-type-filter"
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value as "" | CampaignType)}
            className="w-48 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          >
            <option value="">전체</option>
            {Object.values(CampaignDtoType).map((value) => (
              <option key={value} value={value}>
                {TYPE_LABELS[value]}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col">
          <label
            htmlFor="campaign-status-filter"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            상태
          </label>
          <select
            id="campaign-status-filter"
            value={statusFilter}
            onChange={(e) =>
              setStatusFilter(e.target.value as "" | CampaignStatus)
            }
            className="w-48 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          >
            <option value="">전체</option>
            {Object.values(CampaignDtoStatus).map((value) => (
              <option key={value} value={value}>
                {STATUS_LABELS[value]}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Summary */}
      <div className="mb-3 flex items-center gap-3">
        <span className="text-sm text-slate-600">
          총 {campaigns.length.toLocaleString()}건
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
          <p className="text-sm text-red-600">
            캠페인 목록을 불러오지 못했습니다.
          </p>
        </div>
      ) : campaigns.length === 0 ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-slate-400">조회된 캠페인이 없습니다.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {campaigns.map((campaign) => {
            const status = campaign.status;
            const next = status ? NEXT_STATUS[status] : null;
            return (
              <div
                key={campaign.id}
                className="flex flex-col overflow-hidden rounded-md border border-slate-200 bg-white"
              >
                {/* Thumbnail */}
                <div className="flex h-36 items-center justify-center bg-slate-100">
                  {campaign.bannerImageUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={campaign.bannerImageUrl}
                      alt={campaign.title ?? "캠페인 배너"}
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    <ImageOff className="h-8 w-8 text-slate-300" />
                  )}
                </div>

                <div className="flex flex-1 flex-col p-4">
                  <div className="mb-2 flex items-start justify-between gap-2">
                    <h3 className="line-clamp-2 text-sm font-semibold text-slate-900">
                      <button
                        type="button"
                        onClick={() => openEdit(campaign)}
                        className="text-left transition hover:text-brand hover:underline"
                      >
                        {campaign.title ?? "-"}
                      </button>
                    </h3>
                    {status && (
                      <span
                        className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_BADGE[status]}`}
                      >
                        {STATUS_LABELS[status]}
                      </span>
                    )}
                  </div>

                  <div className="mb-3 flex flex-wrap items-center gap-1.5">
                    {campaign.type && (
                      <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
                        {TYPE_LABELS[campaign.type]}
                      </span>
                    )}
                  </div>

                  <dl className="mb-4 space-y-1 text-xs text-slate-500">
                    <div className="flex justify-between gap-2">
                      <dt>기간</dt>
                      <dd className="text-right text-slate-700">
                        {formatPeriod(campaign)}
                      </dd>
                    </div>
                    {campaign.targetAmount != null && (
                      <div className="flex justify-between gap-2">
                        <dt>목표금액</dt>
                        <dd className="text-right font-medium text-slate-700">
                          {campaign.targetAmount.toLocaleString()}원
                        </dd>
                      </div>
                    )}
                  </dl>

                  <div className="mt-auto flex items-center justify-between gap-2 border-t border-slate-100 pt-3">
                    {next && status ? (
                      <button
                        type="button"
                        onClick={() => handleStatusChange(campaign)}
                        disabled={statusMutation.isPending}
                        className="flex items-center gap-1 rounded-md border border-slate-300 px-2.5 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-50"
                      >
                        {STATUS_LABELS[next]}로 변경
                        <ArrowRight className="h-3.5 w-3.5" />
                      </button>
                    ) : (
                      <span className="text-xs text-slate-400">
                        상태 변경 불가
                      </span>
                    )}
                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => openEdit(campaign)}
                        className="rounded p-1.5 text-slate-500 transition hover:bg-slate-100 hover:text-brand"
                        aria-label="수정"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(campaign)}
                        disabled={deleteMutation.isPending}
                        className="rounded p-1.5 text-slate-500 transition hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
                        aria-label="삭제"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {dialogOpen && (
        <CampaignFormDialog
          campaign={editing}
          onClose={() => setDialogOpen(false)}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
}

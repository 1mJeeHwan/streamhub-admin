"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowLeft, ExternalLink, Loader2 } from "lucide-react";

import {
  useChannels,
  useDelete,
  useDetail1,
  useUpdate1,
} from "@/apis/query/content/content";
import {
  ContentCreateRequestStatus,
  ContentCreateRequestType,
  type ContentCreateRequest,
  type ContentDetail,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDate, formatDuration } from "@/lib/format";
import {
  ContentStatusBadge,
  TypeBadge,
} from "@/components/content/ContentBadges";
import HashtagChips from "@/components/content/HashtagChips";
import ThumbnailUpload from "@/components/content/ThumbnailUpload";

const SUCCESS_CODE = "0000";

const updateSchema = z.object({
  title: z.string().min(1, "제목을 입력하세요."),
  description: z.string().optional(),
  type: z.enum(["VIDEO", "SOUND"]),
  channelId: z
    .string()
    .min(1, "채널을 선택하세요.")
    .refine((value) => Number.isFinite(Number(value)), "채널을 선택하세요."),
  mediaUrl: z.string().optional(),
  durationSec: z.string().optional(),
  status: z.enum(["DRAFT", "PUBLISHED"]),
  hashtags: z.string().optional(),
});

type UpdateFormValues = z.infer<typeof updateSchema>;

function buildDefaults(detail?: ContentDetail): UpdateFormValues {
  return {
    title: detail?.title ?? "",
    description: detail?.description ?? "",
    type: detail?.type === "SOUND" ? "SOUND" : "VIDEO",
    channelId: detail?.channelId != null ? String(detail.channelId) : "",
    mediaUrl: detail?.mediaUrl ?? "",
    durationSec: detail?.durationSec != null ? String(detail.durationSec) : "",
    status: detail?.status === "PUBLISHED" ? "PUBLISHED" : "DRAFT",
    hashtags: detail?.hashtags?.join(", ") ?? "",
  };
}

function parseHashtags(input?: string): string[] | undefined {
  if (!input) {
    return undefined;
  }
  const list = input
    .split(",")
    .map((tag) => tag.trim().replace(/^#/, ""))
    .filter((tag) => tag.length > 0);
  return list.length > 0 ? list : undefined;
}

const FIELD_CLASS =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

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

export default function ContentDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const contentId = Number(params.id);

  const [isEditing, setIsEditing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [thumbnail, setThumbnail] = useState<{ key: string; url: string } | null>(
    null,
  );

  const detailQuery = useDetail1(contentId, {
    query: { enabled: Number.isFinite(contentId) },
  });
  const updateMutation = useUpdate1();
  const deleteMutation = useDelete();
  const channelsQuery = useChannels({ query: { enabled: isEditing } });
  const channels = channelsQuery.data?.resultObject ?? [];

  const detail = detailQuery.data?.resultObject;

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<UpdateFormValues>({
    resolver: zodResolver(updateSchema),
    defaultValues: buildDefaults(),
  });

  // Sync form values whenever the fetched detail changes.
  useEffect(() => {
    if (detail) {
      reset(buildDefaults(detail));
    }
  }, [detail, reset]);

  const startEditing = () => {
    setMessage(null);
    reset(buildDefaults(detail));
    setThumbnail(null);
    setIsEditing(true);
  };

  const cancelEditing = () => {
    reset(buildDefaults(detail));
    setThumbnail(null);
    setIsEditing(false);
  };

  const onSubmit = (values: UpdateFormValues) => {
    setMessage(null);

    const durationNum = values.durationSec
      ? Number(values.durationSec)
      : undefined;

    // Keep the existing thumbnail key unless the user uploaded a replacement.
    const thumbnailKey = thumbnail?.key ?? detail?.thumbnailKey;

    const payload: ContentCreateRequest = {
      title: values.title.trim(),
      description: values.description?.trim() || undefined,
      type: values.type,
      channelId: Number(values.channelId),
      mediaUrl: values.mediaUrl?.trim() || undefined,
      durationSec:
        durationNum != null && Number.isFinite(durationNum)
          ? durationNum
          : undefined,
      thumbnailKey: thumbnailKey || undefined,
      status: values.status,
      hashtags: parseHashtags(values.hashtags),
    };

    updateMutation.mutate(
      { id: contentId, data: payload },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage("저장되었습니다.");
            setIsEditing(false);
            setThumbnail(null);
            detailQuery.refetch();
          } else {
            setMessage(response.resultMessage ?? "저장에 실패했습니다.");
          }
        },
        onError: () => setMessage("저장 중 오류가 발생했습니다."),
      },
    );
  };

  const handleDelete = () => {
    if (!window.confirm("이 콘텐츠를 삭제하시겠습니까?")) {
      return;
    }
    setMessage(null);
    deleteMutation.mutate(
      { id: contentId },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            router.push("/content");
          } else {
            setMessage(response.resultMessage ?? "삭제에 실패했습니다.");
          }
        },
        onError: () => setMessage("삭제 중 오류가 발생했습니다."),
      },
    );
  };

  return (
    <div className="mx-auto max-w-2xl">
      <Link
        href="/content"
        className="mb-4 inline-flex items-center gap-1 text-sm text-slate-600 transition hover:text-slate-900"
      >
        <ArrowLeft className="h-4 w-4" />
        목록으로
      </Link>

      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">콘텐츠 상세</h1>
      </div>

      {detailQuery.isPending ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : detailQuery.isError || !detail ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">
            콘텐츠 정보를 불러오지 못했습니다.
          </p>
        </div>
      ) : (
        <form
          onSubmit={handleSubmit(onSubmit)}
          className="rounded-md border border-slate-200 bg-white p-6"
          noValidate
        >
          {message && (
            <p className="mb-4 rounded-md bg-blue-50 px-3 py-2 text-sm text-blue-700">
              {message}
            </p>
          )}

          {/* Thumbnail */}
          <div className="mb-6">
            <p className="mb-1 text-xs font-medium text-slate-500">썸네일</p>
            {isEditing ? (
              <ThumbnailUpload
                previewUrl={thumbnail?.url ?? detail.thumbnailUrl ?? undefined}
                onUploaded={setThumbnail}
                onClear={() => setThumbnail(null)}
              />
            ) : detail.thumbnailUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={detail.thumbnailUrl}
                alt="썸네일"
                className="h-40 w-full rounded-md object-contain"
              />
            ) : (
              <div className="flex h-40 w-full items-center justify-center rounded-md bg-slate-100 text-sm text-slate-400">
                썸네일 없음
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            {/* Title */}
            <div className="sm:col-span-2">
              <label
                htmlFor="title"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                제목
              </label>
              {isEditing ? (
                <>
                  <input
                    id="title"
                    type="text"
                    className={FIELD_CLASS}
                    {...register("title")}
                  />
                  {errors.title && (
                    <p className="mt-1 text-xs text-red-600">
                      {errors.title.message}
                    </p>
                  )}
                </>
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {detail.title ?? "-"}
                </p>
              )}
            </div>

            {/* Type */}
            <div>
              <label
                htmlFor="type"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                유형
              </label>
              {isEditing ? (
                <select id="type" className={FIELD_CLASS} {...register("type")}>
                  <option value={ContentCreateRequestType.VIDEO}>영상</option>
                  <option value={ContentCreateRequestType.SOUND}>음원</option>
                </select>
              ) : (
                <div className="mt-1">
                  <TypeBadge value={detail.type ?? undefined} />
                </div>
              )}
            </div>

            {/* Status */}
            <div>
              <label
                htmlFor="status"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                상태
              </label>
              {isEditing ? (
                <select
                  id="status"
                  className={FIELD_CLASS}
                  {...register("status")}
                >
                  <option value={ContentCreateRequestStatus.DRAFT}>임시</option>
                  <option value={ContentCreateRequestStatus.PUBLISHED}>
                    게시
                  </option>
                </select>
              ) : (
                <div className="mt-1">
                  <ContentStatusBadge value={detail.status ?? undefined} />
                </div>
              )}
            </div>

            {/* Channel */}
            <div>
              <label
                htmlFor="channelId"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                채널
              </label>
              {isEditing ? (
                <>
                  <select
                    id="channelId"
                    className={FIELD_CLASS}
                    {...register("channelId")}
                  >
                    <option value="">선택하세요</option>
                    {channels.map((channel) => (
                      <option key={channel.id} value={channel.id}>
                        {channel.name ?? `채널 ${channel.id}`}
                      </option>
                    ))}
                  </select>
                  {errors.channelId && (
                    <p className="mt-1 text-xs text-red-600">
                      {errors.channelId.message}
                    </p>
                  )}
                </>
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {detail.channelName ?? "-"}
                </p>
              )}
            </div>

            {/* Church (read-only) */}
            <ReadonlyField label="교회" value={detail.churchName ?? "-"} />

            {/* Media URL */}
            <div className="sm:col-span-2">
              <label
                htmlFor="mediaUrl"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                미디어 URL
              </label>
              {isEditing ? (
                <input
                  id="mediaUrl"
                  type="text"
                  className={FIELD_CLASS}
                  {...register("mediaUrl")}
                />
              ) : detail.mediaUrl ? (
                <a
                  href={detail.mediaUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-1 inline-flex items-center gap-1 text-sm text-brand hover:underline"
                >
                  {detail.mediaUrl}
                  <ExternalLink className="h-3.5 w-3.5" />
                </a>
              ) : (
                <p className="mt-1 text-sm text-slate-900">-</p>
              )}
            </div>

            {/* Duration */}
            <div>
              <label
                htmlFor="durationSec"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                길이
              </label>
              {isEditing ? (
                <input
                  id="durationSec"
                  type="number"
                  min={0}
                  className={FIELD_CLASS}
                  {...register("durationSec")}
                />
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {formatDuration(detail.durationSec)}
                </p>
              )}
            </div>

            {/* View count (read-only) */}
            <ReadonlyField
              label="조회수"
              value={(detail.viewCount ?? 0).toLocaleString()}
            />

            {/* Description */}
            <div className="sm:col-span-2">
              <label
                htmlFor="description"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                설명
              </label>
              {isEditing ? (
                <textarea
                  id="description"
                  rows={3}
                  className={FIELD_CLASS}
                  {...register("description")}
                />
              ) : (
                <p className="mt-1 whitespace-pre-wrap text-sm text-slate-900">
                  {detail.description ?? "-"}
                </p>
              )}
            </div>

            {/* Hashtags */}
            <div className="sm:col-span-2">
              <label
                htmlFor="hashtags"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                해시태그
              </label>
              {isEditing ? (
                <input
                  id="hashtags"
                  type="text"
                  placeholder="쉼표로 구분 (예: 찬양, 예배)"
                  className={FIELD_CLASS}
                  {...register("hashtags")}
                />
              ) : (
                <div className="mt-1">
                  <HashtagChips tags={detail.hashtags ?? undefined} />
                </div>
              )}
            </div>
          </div>

          {/* Attached files (read-only) */}
          {!isEditing && detail.files && detail.files.length > 0 && (
            <div className="mt-6">
              <p className="mb-2 text-xs font-medium text-slate-500">첨부파일</p>
              <ul className="space-y-1">
                {detail.files.map((file, index) => (
                  <li key={file.id ?? index}>
                    {file.url ? (
                      <a
                        href={file.url}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 text-sm text-brand hover:underline"
                      >
                        {file.s3Key ?? file.url}
                        <ExternalLink className="h-3.5 w-3.5" />
                      </a>
                    ) : (
                      <span className="text-sm text-slate-600">
                        {file.s3Key ?? "-"}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <hr className="my-6 border-slate-200" />

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            <ReadonlyField
              label="등록일"
              value={formatDate(detail.createdAt)}
            />
            <ReadonlyField
              label="수정일"
              value={formatDate(detail.updatedAt)}
            />
          </div>

          <div className="mt-6 flex items-center justify-between gap-2">
            <div>
              {!isEditing && (
                <button
                  type="button"
                  onClick={handleDelete}
                  disabled={deleteMutation.isPending}
                  className="flex items-center gap-1.5 rounded-md border border-red-300 px-4 py-2 text-sm font-medium text-red-600 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {deleteMutation.isPending && (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  )}
                  삭제
                </button>
              )}
            </div>

            <div className="flex gap-2">
              {isEditing ? (
                <>
                  <button
                    type="button"
                    onClick={cancelEditing}
                    disabled={updateMutation.isPending}
                    className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:opacity-60"
                  >
                    취소
                  </button>
                  <button
                    type="submit"
                    disabled={updateMutation.isPending}
                    className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {updateMutation.isPending && (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    )}
                    저장
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  onClick={startEditing}
                  className="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
                >
                  수정
                </button>
              )}
            </div>
          </div>
        </form>
      )}
    </div>
  );
}

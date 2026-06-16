"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowLeft, Loader2 } from "lucide-react";

import { useChannels, useCreate } from "@/apis/query/content/content";
import {
  ContentCreateRequestStatus,
  ContentCreateRequestType,
  type ContentCreateRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import ThumbnailUpload from "@/components/content/ThumbnailUpload";
import { FIELD_CLASS, parseHashtags } from "@/lib/content-form";
import { SUCCESS_CODE } from "@/types/api";

const createSchema = z.object({
  title: z.string().min(1, "제목을 입력하세요."),
  description: z.string().optional(),
  type: z.enum(["VIDEO", "SOUND"], {
    errorMap: () => ({ message: "유형을 선택하세요." }),
  }),
  channelId: z
    .string()
    .min(1, "채널을 선택하세요.")
    .refine((value) => Number.isFinite(Number(value)), "채널을 선택하세요."),
  mediaUrl: z.string().optional(),
  durationSec: z.string().optional(),
  status: z.enum(["DRAFT", "PUBLISHED"]),
  hashtags: z.string().optional(),
});

type CreateFormValues = z.infer<typeof createSchema>;

export default function ContentAddPage() {
  const router = useRouter();
  const [message, setMessage] = useState<string | null>(null);
  const [thumbnail, setThumbnail] = useState<{ key: string; url: string } | null>(
    null,
  );

  const channelsQuery = useChannels();
  const channels = channelsQuery.data?.resultObject ?? [];

  const createMutation = useCreate();

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      title: "",
      description: "",
      channelId: "",
      mediaUrl: "",
      durationSec: "",
      status: "DRAFT",
      hashtags: "",
    },
  });

  const onSubmit = (values: CreateFormValues) => {
    setMessage(null);

    const durationNum = values.durationSec
      ? Number(values.durationSec)
      : undefined;

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
      thumbnailKey: thumbnail?.key,
      status: values.status,
      hashtags: parseHashtags(values.hashtags),
    };

    createMutation.mutate(
      { data: payload },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            const newId = response.resultObject?.id;
            if (newId != null) {
              router.push(`/content/${newId}`);
            } else {
              router.push("/content");
            }
          } else {
            setMessage(response.resultMessage ?? "등록에 실패했습니다.");
          }
        },
        onError: () => setMessage("등록 중 오류가 발생했습니다."),
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
        <h1 className="text-xl font-semibold text-slate-900">콘텐츠 등록</h1>
      </div>

      <form
        onSubmit={handleSubmit(onSubmit)}
        className="rounded-md border border-slate-200 bg-white p-6"
        noValidate
      >
        {message && (
          <p className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {message}
          </p>
        )}

        <div className="grid grid-cols-1 gap-5">
          {/* Thumbnail */}
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-500">
              썸네일
            </label>
            <ThumbnailUpload
              previewUrl={thumbnail?.url}
              onUploaded={setThumbnail}
              onClear={() => setThumbnail(null)}
            />
          </div>

          {/* Title */}
          <div>
            <label
              htmlFor="title"
              className="mb-1 block text-xs font-medium text-slate-500"
            >
              제목 *
            </label>
            <input id="title" type="text" className={FIELD_CLASS} {...register("title")} />
            {errors.title && (
              <p className="mt-1 text-xs text-red-600">{errors.title.message}</p>
            )}
          </div>

          {/* Description */}
          <div>
            <label
              htmlFor="description"
              className="mb-1 block text-xs font-medium text-slate-500"
            >
              설명
            </label>
            <textarea
              id="description"
              rows={3}
              className={FIELD_CLASS}
              {...register("description")}
            />
          </div>

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            {/* Type */}
            <div>
              <label
                htmlFor="type"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                유형 *
              </label>
              <select
                id="type"
                defaultValue=""
                className={FIELD_CLASS}
                {...register("type")}
              >
                <option value="" disabled>
                  선택하세요
                </option>
                <option value={ContentCreateRequestType.VIDEO}>영상</option>
                <option value={ContentCreateRequestType.SOUND}>음원</option>
              </select>
              {errors.type && (
                <p className="mt-1 text-xs text-red-600">
                  {errors.type.message}
                </p>
              )}
            </div>

            {/* Channel */}
            <div>
              <label
                htmlFor="channelId"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                채널 *
              </label>
              <select id="channelId" className={FIELD_CLASS} {...register("channelId")}>
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
            </div>

            {/* Media URL */}
            <div>
              <label
                htmlFor="mediaUrl"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                미디어 URL
              </label>
              <input
                id="mediaUrl"
                type="text"
                className={FIELD_CLASS}
                {...register("mediaUrl")}
              />
            </div>

            {/* Duration */}
            <div>
              <label
                htmlFor="durationSec"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                길이 (초)
              </label>
              <input
                id="durationSec"
                type="number"
                min={0}
                className={FIELD_CLASS}
                {...register("durationSec")}
              />
            </div>

            {/* Status */}
            <div>
              <label
                htmlFor="status"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                상태
              </label>
              <select id="status" className={FIELD_CLASS} {...register("status")}>
                <option value={ContentCreateRequestStatus.DRAFT}>임시</option>
                <option value={ContentCreateRequestStatus.PUBLISHED}>
                  게시
                </option>
              </select>
            </div>

            {/* Hashtags */}
            <div>
              <label
                htmlFor="hashtags"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                해시태그 (쉼표로 구분)
              </label>
              <Controller
                control={control}
                name="hashtags"
                render={({ field }) => (
                  <input
                    id="hashtags"
                    type="text"
                    placeholder="예: 찬양, 예배, 라이브"
                    className={FIELD_CLASS}
                    value={field.value ?? ""}
                    onChange={field.onChange}
                  />
                )}
              />
            </div>
          </div>
        </div>

        <div className="mt-6 flex justify-end gap-2">
          <Link
            href="/content"
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100"
          >
            취소
          </Link>
          <button
            type="submit"
            disabled={createMutation.isPending}
            className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
          >
            {createMutation.isPending && (
              <Loader2 className="h-4 w-4 animate-spin" />
            )}
            등록
          </button>
        </div>
      </form>
    </div>
  );
}

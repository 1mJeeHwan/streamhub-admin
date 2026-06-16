"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowLeft, Loader2 } from "lucide-react";

import { useDetail, useUpdate } from "@/apis/query/member/member";
import type { MemberDetail } from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDate } from "@/lib/format";
import StatusBadge from "@/components/member/StatusBadge";

const SUCCESS_CODE = "0000";

const updateSchema = z.object({
  name: z.string().min(1, "이름을 입력하세요."),
  phone: z.string().optional(),
  liveYn: z.enum(["Y", "N"]),
});

type UpdateFormValues = z.infer<typeof updateSchema>;

function buildDefaults(detail?: MemberDetail): UpdateFormValues {
  return {
    name: detail?.name ?? "",
    phone: detail?.phone ?? "",
    liveYn: detail?.liveYn === "Y" ? "Y" : "N",
  };
}

interface ReadonlyFieldProps {
  label: string;
  value: React.ReactNode;
}

function ReadonlyField({ label, value }: ReadonlyFieldProps) {
  return (
    <div>
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <p className="mt-1 text-sm text-slate-900">{value}</p>
    </div>
  );
}

export default function MemberDetailPage() {
  const params = useParams<{ id: string }>();
  const memberId = Number(params.id);

  const [isEditing, setIsEditing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const detailQuery = useDetail(memberId, {
    query: { enabled: Number.isFinite(memberId) },
  });
  const updateMutation = useUpdate();

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
    setIsEditing(true);
  };

  const cancelEditing = () => {
    reset(buildDefaults(detail));
    setIsEditing(false);
  };

  const onSubmit = (values: UpdateFormValues) => {
    setMessage(null);
    updateMutation.mutate(
      {
        id: memberId,
        data: {
          name: values.name,
          phone: values.phone?.trim() || undefined,
          liveYn: values.liveYn,
        },
      },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage("저장되었습니다.");
            setIsEditing(false);
            detailQuery.refetch();
          } else {
            setMessage(response.resultMessage ?? "저장에 실패했습니다.");
          }
        },
        onError: () => setMessage("저장 중 오류가 발생했습니다."),
      },
    );
  };

  return (
    <div className="mx-auto max-w-2xl">
      <Link
        href="/member"
        className="mb-4 inline-flex items-center gap-1 text-sm text-slate-600 transition hover:text-slate-900"
      >
        <ArrowLeft className="h-4 w-4" />
        목록으로
      </Link>

      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">회원 상세</h1>
      </div>

      {detailQuery.isPending ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : detailQuery.isError || !detail ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">
            회원 정보를 불러오지 못했습니다.
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

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            <ReadonlyField label="이메일" value={detail.email ?? "-"} />
            <ReadonlyField
              label="상태"
              value={<StatusBadge status={detail.userStatus} />}
            />
            <ReadonlyField label="교회" value={detail.churchName ?? "-"} />
            <ReadonlyField label="지역" value={detail.regionName ?? "-"} />
            <ReadonlyField label="국가" value={detail.countryName ?? "-"} />
            <ReadonlyField
              label="가입일"
              value={formatDate(detail.createdAt)}
            />
            <ReadonlyField
              label="수정일"
              value={formatDate(detail.updatedAt)}
            />
          </div>

          <hr className="my-6 border-slate-200" />

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            {/* Name */}
            <div>
              <label
                htmlFor="name"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                이름
              </label>
              {isEditing ? (
                <>
                  <input
                    id="name"
                    type="text"
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                    {...register("name")}
                  />
                  {errors.name && (
                    <p className="mt-1 text-xs text-red-600">
                      {errors.name.message}
                    </p>
                  )}
                </>
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {detail.name ?? "-"}
                </p>
              )}
            </div>

            {/* Phone */}
            <div>
              <label
                htmlFor="phone"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                전화
              </label>
              {isEditing ? (
                <input
                  id="phone"
                  type="text"
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                  {...register("phone")}
                />
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {detail.phone ?? "-"}
                </p>
              )}
            </div>

            {/* Live */}
            <div>
              <label
                htmlFor="liveYn"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                라이브
              </label>
              {isEditing ? (
                <select
                  id="liveYn"
                  className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                  {...register("liveYn")}
                >
                  <option value="Y">가능</option>
                  <option value="N">불가</option>
                </select>
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {detail.liveYn === "Y" ? "가능" : "불가"}
                </p>
              )}
            </div>
          </div>

          <div className="mt-6 flex justify-end gap-2">
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
        </form>
      )}
    </div>
  );
}

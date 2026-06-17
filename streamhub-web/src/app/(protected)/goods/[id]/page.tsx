"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, Loader2 } from "lucide-react";

import {
  useCategories,
  useDelete1,
  useDetail2,
  useUpdate2,
} from "@/apis/query/goods/goods";
import {
  GoodsCreateRequestStatus,
  type GoodsCreateRequest,
  type GoodsDetail,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { formatDate } from "@/lib/format";
import {
  GoodsStatusBadge,
  SoldOutBadge,
} from "@/components/goods/GoodsStatusBadge";
import ThumbnailUpload from "@/components/content/ThumbnailUpload";
import OptionRows from "@/components/goods/OptionRows";
import ImageRows from "@/components/goods/ImageRows";
import {
  FIELD_CLASS,
  buildGoodsDefaults,
  buildGoodsPayload,
  goodsFormSchema,
  type GoodsFormValues,
} from "@/lib/goods-form";
import { SUCCESS_CODE } from "@/types/api";

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

function money(value?: number | null): string {
  return value != null ? Number(value).toLocaleString() : "-";
}

export default function GoodsDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const goodsId = Number(params.id);

  const [isEditing, setIsEditing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [thumbnail, setThumbnail] = useState<{ key: string; url: string } | null>(
    null,
  );

  const detailQuery = useDetail2(goodsId, {
    query: { enabled: Number.isFinite(goodsId) },
  });
  const updateMutation = useUpdate2();
  const deleteMutation = useDelete1();
  const categoriesQuery = useCategories({ query: { enabled: isEditing } });
  const categories = categoriesQuery.data?.resultObject ?? [];

  const detail: GoodsDetail | undefined = detailQuery.data?.resultObject;

  const {
    register,
    handleSubmit,
    control,
    setValue,
    reset,
    formState: { errors },
  } = useForm<GoodsFormValues>({
    resolver: zodResolver(goodsFormSchema),
    defaultValues: buildGoodsDefaults(),
  });

  // Sync form values whenever the fetched detail changes.
  useEffect(() => {
    if (detail) {
      reset(buildGoodsDefaults(detail));
    }
  }, [detail, reset]);

  const startEditing = () => {
    setMessage(null);
    reset(buildGoodsDefaults(detail));
    setThumbnail(null);
    setIsEditing(true);
  };

  const cancelEditing = () => {
    reset(buildGoodsDefaults(detail));
    setThumbnail(null);
    setIsEditing(false);
  };

  const onSubmit = (values: GoodsFormValues) => {
    setMessage(null);

    // Keep the existing thumbnail key unless the user uploaded a replacement.
    const thumbnailKey = thumbnail?.key ?? detail?.thumbnailKey;

    const payload: GoodsCreateRequest = buildGoodsPayload(values, thumbnailKey);

    updateMutation.mutate(
      { id: goodsId, data: payload },
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
    if (!window.confirm("이 굿즈를 삭제하시겠습니까?")) {
      return;
    }
    setMessage(null);
    deleteMutation.mutate(
      { id: goodsId },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            router.push("/goods");
          } else {
            setMessage(response.resultMessage ?? "삭제에 실패했습니다.");
          }
        },
        onError: () => setMessage("삭제 중 오류가 발생했습니다."),
      },
    );
  };

  return (
    <div className="mx-auto max-w-3xl">
      <Link
        href="/goods"
        className="mb-4 inline-flex items-center gap-1 text-sm text-slate-600 transition hover:text-slate-900"
      >
        <ArrowLeft className="h-4 w-4" />
        목록으로
      </Link>

      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">굿즈 상세</h1>
      </div>

      {detailQuery.isPending ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : detailQuery.isError || !detail ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">굿즈 정보를 불러오지 못했습니다.</p>
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
            <p className="mb-1 text-xs font-medium text-slate-500">대표 썸네일</p>
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
            {/* Name */}
            <div className="sm:col-span-2">
              <label
                htmlFor="name"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                상품명
              </label>
              {isEditing ? (
                <>
                  <input
                    id="name"
                    type="text"
                    className={FIELD_CLASS}
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

            {/* Code */}
            <div>
              <label
                htmlFor="code"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                상품코드
              </label>
              {isEditing ? (
                <>
                  <input
                    id="code"
                    type="text"
                    className={FIELD_CLASS}
                    {...register("code")}
                  />
                  {errors.code && (
                    <p className="mt-1 text-xs text-red-600">
                      {errors.code.message}
                    </p>
                  )}
                </>
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {detail.code ?? "-"}
                </p>
              )}
            </div>

            {/* Category */}
            <div>
              <label
                htmlFor="categoryId"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                분류
              </label>
              {isEditing ? (
                <>
                  <select
                    id="categoryId"
                    className={FIELD_CLASS}
                    {...register("categoryId")}
                  >
                    <option value="">선택하세요</option>
                    {categories.map((category) => (
                      <option key={category.id} value={category.id}>
                        {" ".repeat(((category.depth ?? 1) - 1) * 2)}
                        {category.name ?? `분류 ${category.id}`}
                      </option>
                    ))}
                  </select>
                  {errors.categoryId && (
                    <p className="mt-1 text-xs text-red-600">
                      {errors.categoryId.message}
                    </p>
                  )}
                </>
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {detail.categoryName ?? "-"}
                </p>
              )}
            </div>

            {/* Price */}
            <div>
              <label
                htmlFor="price"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                판매가
              </label>
              {isEditing ? (
                <>
                  <input
                    id="price"
                    type="number"
                    min={0}
                    className={FIELD_CLASS}
                    {...register("price")}
                  />
                  {errors.price && (
                    <p className="mt-1 text-xs text-red-600">
                      {errors.price.message}
                    </p>
                  )}
                </>
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {money(detail.price)}원
                </p>
              )}
            </div>

            {/* List price */}
            <div>
              <label
                htmlFor="listPrice"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                시중가
              </label>
              {isEditing ? (
                <input
                  id="listPrice"
                  type="number"
                  min={0}
                  className={FIELD_CLASS}
                  {...register("listPrice")}
                />
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {detail.listPrice != null ? `${money(detail.listPrice)}원` : "-"}
                </p>
              )}
            </div>

            {/* Stock */}
            <div>
              <label
                htmlFor="stock"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                재고
              </label>
              {isEditing ? (
                <input
                  id="stock"
                  type="number"
                  min={0}
                  className={FIELD_CLASS}
                  {...register("stock")}
                />
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {money(detail.stock)}
                </p>
              )}
            </div>

            {/* Noti qty */}
            <div>
              <label
                htmlFor="notiQty"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                재고통보수량
              </label>
              {isEditing ? (
                <input
                  id="notiQty"
                  type="number"
                  min={0}
                  className={FIELD_CLASS}
                  {...register("notiQty")}
                />
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {money(detail.notiQty)}
                </p>
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
                  <option value={GoodsCreateRequestStatus.SELLING}>
                    판매중
                  </option>
                  <option value={GoodsCreateRequestStatus.PAUSED}>
                    판매중지
                  </option>
                </select>
              ) : (
                <div className="mt-1">
                  <GoodsStatusBadge value={detail.status ?? undefined} />
                </div>
              )}
            </div>

            {/* Sold out */}
            <div>
              <label
                htmlFor="soldOut"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                품절
              </label>
              {isEditing ? (
                <select
                  id="soldOut"
                  className={FIELD_CLASS}
                  {...register("soldOut")}
                >
                  <option value="N">정상</option>
                  <option value="Y">품절</option>
                </select>
              ) : (
                <div className="mt-1">
                  <SoldOutBadge value={detail.soldOut ?? undefined} />
                </div>
              )}
            </div>

            {/* Use yn */}
            <div>
              <label
                htmlFor="useYn"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                판매여부
              </label>
              {isEditing ? (
                <select
                  id="useYn"
                  className={FIELD_CLASS}
                  {...register("useYn")}
                >
                  <option value="Y">판매</option>
                  <option value="N">중지</option>
                </select>
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {detail.useYn === "N" ? "중지" : "판매"}
                </p>
              )}
            </div>

            {/* Badges */}
            <div className="sm:col-span-2">
              <label
                htmlFor="badges"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                배지 (쉼표로 구분)
              </label>
              {isEditing ? (
                <input
                  id="badges"
                  type="text"
                  placeholder="예: HIT, NEW, SALE"
                  className={FIELD_CLASS}
                  {...register("badges")}
                />
              ) : (
                <p className="mt-1 text-sm text-slate-900">
                  {detail.badges || "-"}
                </p>
              )}
            </div>

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
          </div>

          <hr className="my-6 border-slate-200" />

          {/* Options */}
          {isEditing ? (
            <OptionRows control={control} register={register} />
          ) : (
            <div>
              <p className="mb-2 text-xs font-medium text-slate-500">옵션</p>
              {detail.options && detail.options.length > 0 ? (
                <ul className="space-y-1">
                  {detail.options.map((option, index) => (
                    <li
                      key={option.id ?? index}
                      className="flex flex-wrap items-center gap-2 text-sm text-slate-900"
                    >
                      <span className="font-medium">{option.name ?? "-"}</span>
                      {option.optionType && (
                        <span className="text-xs text-slate-500">
                          ({option.optionType})
                        </span>
                      )}
                      <span className="text-xs text-slate-500">
                        추가금 {money(option.extraPrice)}원 · 재고{" "}
                        {money(option.stock)}
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-slate-400">등록된 옵션이 없습니다.</p>
              )}
            </div>
          )}

          <hr className="my-6 border-slate-200" />

          {/* Images */}
          {isEditing ? (
            <ImageRows
              control={control}
              register={register}
              setValue={setValue}
            />
          ) : (
            <div>
              <p className="mb-2 text-xs font-medium text-slate-500">추가 이미지</p>
              {detail.images && detail.images.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {detail.images.map((image, index) =>
                    image.url ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        key={image.id ?? index}
                        src={image.url}
                        alt={`추가 이미지 ${index + 1}`}
                        className="h-24 w-24 rounded-md border border-slate-200 object-cover"
                      />
                    ) : null,
                  )}
                </div>
              ) : (
                <p className="text-sm text-slate-400">
                  등록된 추가 이미지가 없습니다.
                </p>
              )}
            </div>
          )}

          <hr className="my-6 border-slate-200" />

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
            <ReadonlyField
              label="판매수"
              value={(detail.saleCount ?? 0).toLocaleString()}
            />
            <ReadonlyField
              label="조회수"
              value={(detail.viewCount ?? 0).toLocaleString()}
            />
            <ReadonlyField label="등록일" value={formatDate(detail.createdAt)} />
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

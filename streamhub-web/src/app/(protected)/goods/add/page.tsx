"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, Loader2 } from "lucide-react";

import { useCategories, useCreate1 } from "@/apis/query/goods/goods";
import {
  GoodsCreateRequestStatus,
  type GoodsCreateRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
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

export default function GoodsAddPage() {
  const router = useRouter();
  const [message, setMessage] = useState<string | null>(null);
  const [thumbnail, setThumbnail] = useState<{ key: string; url: string } | null>(
    null,
  );

  const categoriesQuery = useCategories();
  const categories = categoriesQuery.data?.resultObject ?? [];

  const createMutation = useCreate1();

  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors },
  } = useForm<GoodsFormValues>({
    resolver: zodResolver(goodsFormSchema),
    defaultValues: buildGoodsDefaults(),
  });

  const onSubmit = (values: GoodsFormValues) => {
    setMessage(null);

    const payload: GoodsCreateRequest = buildGoodsPayload(values, thumbnail?.key);

    createMutation.mutate(
      { data: payload },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            const newId = response.resultObject?.id;
            if (newId != null) {
              router.push(`/goods/${newId}`);
            } else {
              router.push("/goods");
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
    <div className="mx-auto max-w-3xl">
      <Link
        href="/goods"
        className="mb-4 inline-flex items-center gap-1 text-sm text-slate-600 transition hover:text-slate-900"
      >
        <ArrowLeft className="h-4 w-4" />
        목록으로
      </Link>

      <div className="mb-4">
        <h1 className="text-xl font-semibold text-slate-900">굿즈 등록</h1>
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
              대표 썸네일
            </label>
            <ThumbnailUpload
              previewUrl={thumbnail?.url}
              onUploaded={setThumbnail}
              onClear={() => setThumbnail(null)}
            />
          </div>

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            {/* Name */}
            <div className="sm:col-span-2">
              <label
                htmlFor="name"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                상품명 *
              </label>
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
            </div>

            {/* Code */}
            <div>
              <label
                htmlFor="code"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                상품코드 *
              </label>
              <input
                id="code"
                type="text"
                placeholder="예: GD0001"
                className={FIELD_CLASS}
                {...register("code")}
              />
              {errors.code && (
                <p className="mt-1 text-xs text-red-600">
                  {errors.code.message}
                </p>
              )}
            </div>

            {/* Category */}
            <div>
              <label
                htmlFor="categoryId"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                분류 *
              </label>
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
            </div>

            {/* Price */}
            <div>
              <label
                htmlFor="price"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                판매가 *
              </label>
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
            </div>

            {/* List price */}
            <div>
              <label
                htmlFor="listPrice"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                시중가
              </label>
              <input
                id="listPrice"
                type="number"
                min={0}
                className={FIELD_CLASS}
                {...register("listPrice")}
              />
            </div>

            {/* Stock */}
            <div>
              <label
                htmlFor="stock"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                재고
              </label>
              <input
                id="stock"
                type="number"
                min={0}
                className={FIELD_CLASS}
                {...register("stock")}
              />
            </div>

            {/* Noti qty */}
            <div>
              <label
                htmlFor="notiQty"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                재고통보수량
              </label>
              <input
                id="notiQty"
                type="number"
                min={0}
                className={FIELD_CLASS}
                {...register("notiQty")}
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
              <select
                id="status"
                className={FIELD_CLASS}
                {...register("status")}
              >
                <option value={GoodsCreateRequestStatus.SELLING}>판매중</option>
                <option value={GoodsCreateRequestStatus.PAUSED}>판매중지</option>
              </select>
            </div>

            {/* Sold out */}
            <div>
              <label
                htmlFor="soldOut"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                품절
              </label>
              <select
                id="soldOut"
                className={FIELD_CLASS}
                {...register("soldOut")}
              >
                <option value="N">정상</option>
                <option value="Y">품절</option>
              </select>
            </div>

            {/* Use yn */}
            <div>
              <label
                htmlFor="useYn"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                판매여부
              </label>
              <select id="useYn" className={FIELD_CLASS} {...register("useYn")}>
                <option value="Y">판매</option>
                <option value="N">중지</option>
              </select>
            </div>

            {/* Badges */}
            <div className="sm:col-span-2">
              <label
                htmlFor="badges"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                배지 (쉼표로 구분)
              </label>
              <input
                id="badges"
                type="text"
                placeholder="예: HIT, NEW, SALE"
                className={FIELD_CLASS}
                {...register("badges")}
              />
            </div>

            {/* Description */}
            <div className="sm:col-span-2">
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
          </div>

          <hr className="border-slate-200" />

          {/* Dynamic option rows */}
          <OptionRows control={control} register={register} />

          <hr className="border-slate-200" />

          {/* Dynamic image rows */}
          <ImageRows control={control} register={register} setValue={setValue} />
        </div>

        <div className="mt-6 flex justify-end gap-2">
          <Link
            href="/goods"
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

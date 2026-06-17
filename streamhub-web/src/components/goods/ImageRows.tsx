"use client";

import {
  useFieldArray,
  useWatch,
  type Control,
  type UseFormRegister,
  type UseFormSetValue,
} from "react-hook-form";
import { Plus, Trash2 } from "lucide-react";

import ThumbnailUpload from "@/components/content/ThumbnailUpload";
import { FIELD_CLASS, type GoodsFormValues } from "@/lib/goods-form";

interface ImageRowsProps {
  control: Control<GoodsFormValues>;
  register: UseFormRegister<GoodsFormValues>;
  setValue: UseFormSetValue<GoodsFormValues>;
}

/**
 * ImageRows renders the dynamic additional-image list using useFieldArray.
 * Each row reuses the shared ThumbnailUpload widget; on upload it stores the
 * returned storage key (s3Key) + preview url into the row, and exposes a sort
 * input. The image src is the absolute url returned by the upload endpoint.
 */
export default function ImageRows({
  control,
  register,
  setValue,
}: ImageRowsProps) {
  const { fields, append, remove } = useFieldArray({
    control,
    name: "images",
  });

  // Watch the images array so each row's preview reflects its current url/key.
  const images = useWatch({ control, name: "images" });

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <p className="text-xs font-medium text-slate-500">추가 이미지</p>
        <button
          type="button"
          onClick={() => append({ s3Key: "", url: "", sort: "" })}
          className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-100"
        >
          <Plus className="h-3.5 w-3.5" />
          이미지 추가
        </button>
      </div>

      {fields.length === 0 ? (
        <p className="rounded-md border border-dashed border-slate-300 bg-slate-50 px-3 py-3 text-center text-xs text-slate-400">
          등록된 추가 이미지가 없습니다.
        </p>
      ) : (
        <div className="space-y-3">
          {fields.map((field, index) => {
            const previewUrl = images?.[index]?.url || undefined;
            return (
              <div
                key={field.id}
                className="grid grid-cols-12 items-start gap-3 rounded-md border border-slate-200 p-3"
              >
                <div className="col-span-7">
                  <ThumbnailUpload
                    previewUrl={previewUrl}
                    onUploaded={(result) => {
                      setValue(`images.${index}.s3Key`, result.key, {
                        shouldValidate: true,
                      });
                      setValue(`images.${index}.url`, result.url);
                    }}
                    onClear={() => {
                      setValue(`images.${index}.s3Key`, "", {
                        shouldValidate: true,
                      });
                      setValue(`images.${index}.url`, "");
                    }}
                  />
                </div>
                <div className="col-span-3">
                  <label className="mb-1 block text-xs font-medium text-slate-500">
                    정렬순서
                  </label>
                  <input
                    type="number"
                    min={0}
                    placeholder="0"
                    className={FIELD_CLASS}
                    {...register(`images.${index}.sort` as const)}
                  />
                  {/* s3Key is set via the upload widget, kept registered for RHF. */}
                  <input
                    type="hidden"
                    {...register(`images.${index}.s3Key` as const)}
                  />
                </div>
                <div className="col-span-2 flex justify-center pt-6">
                  <button
                    type="button"
                    onClick={() => remove(index)}
                    className="rounded p-1 text-slate-400 transition hover:bg-red-50 hover:text-red-600"
                    aria-label="이미지 삭제"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

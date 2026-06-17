"use client";

import { useFieldArray, type Control, type UseFormRegister } from "react-hook-form";
import { Plus, Trash2 } from "lucide-react";

import { FIELD_CLASS, type GoodsFormValues } from "@/lib/goods-form";

interface OptionRowsProps {
  control: Control<GoodsFormValues>;
  register: UseFormRegister<GoodsFormValues>;
}

/**
 * OptionRows renders the dynamic option list (사이즈·색상) using useFieldArray.
 * Each row carries an option name, type, extra price, stock, and use flag.
 */
export default function OptionRows({ control, register }: OptionRowsProps) {
  const { fields, append, remove } = useFieldArray({
    control,
    name: "options",
  });

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <p className="text-xs font-medium text-slate-500">옵션</p>
        <button
          type="button"
          onClick={() =>
            append({
              name: "",
              optionType: "",
              extraPrice: "",
              stock: "",
              useYn: "Y",
            })
          }
          className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-100"
        >
          <Plus className="h-3.5 w-3.5" />
          옵션 추가
        </button>
      </div>

      {fields.length === 0 ? (
        <p className="rounded-md border border-dashed border-slate-300 bg-slate-50 px-3 py-3 text-center text-xs text-slate-400">
          등록된 옵션이 없습니다.
        </p>
      ) : (
        <div className="space-y-2">
          {fields.map((field, index) => (
            <div
              key={field.id}
              className="grid grid-cols-12 items-start gap-2 rounded-md border border-slate-200 p-2"
            >
              <div className="col-span-3">
                <input
                  type="text"
                  placeholder="옵션명 (예: 블랙 / L)"
                  className={FIELD_CLASS}
                  {...register(`options.${index}.name` as const)}
                />
              </div>
              <div className="col-span-3">
                <input
                  type="text"
                  placeholder="유형 (예: 색상/사이즈)"
                  className={FIELD_CLASS}
                  {...register(`options.${index}.optionType` as const)}
                />
              </div>
              <div className="col-span-2">
                <input
                  type="number"
                  min={0}
                  placeholder="추가금"
                  className={FIELD_CLASS}
                  {...register(`options.${index}.extraPrice` as const)}
                />
              </div>
              <div className="col-span-2">
                <input
                  type="number"
                  min={0}
                  placeholder="재고"
                  className={FIELD_CLASS}
                  {...register(`options.${index}.stock` as const)}
                />
              </div>
              <div className="col-span-1">
                <select
                  className={FIELD_CLASS}
                  {...register(`options.${index}.useYn` as const)}
                >
                  <option value="Y">Y</option>
                  <option value="N">N</option>
                </select>
              </div>
              <div className="col-span-1 flex justify-center pt-1.5">
                <button
                  type="button"
                  onClick={() => remove(index)}
                  className="rounded p-1 text-slate-400 transition hover:bg-red-50 hover:text-red-600"
                  aria-label="옵션 삭제"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

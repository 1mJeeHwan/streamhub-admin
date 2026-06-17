"use client";

import { Plus, Trash2 } from "lucide-react";
import type { RegistrationFamily } from "@/lib/churchTypes";

const MAX_FAMILIES = 5;
const RELATIONS = ["배우자", "자녀", "부모", "형제자매", "기타"];

/** Dynamic family-member rows (max 5), worship-registration step ⑤. */
export function FamilyRows({
  families,
  onChange,
}: {
  families: RegistrationFamily[];
  onChange: (next: RegistrationFamily[]) => void;
}) {
  const update = (i: number, patch: Partial<RegistrationFamily>) =>
    onChange(families.map((f, idx) => (idx === i ? { ...f, ...patch } : f)));
  const add = () => onChange([...families, { name: "", relation: RELATIONS[0], birthDate: "" }]);
  const remove = (i: number) => onChange(families.filter((_, idx) => idx !== i));

  return (
    <div className="space-y-2.5">
      {families.map((f, i) => (
        <div key={i} className="rounded-card border border-border/70 bg-surface p-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-inactive">가족 {i + 1}</span>
            <button
              type="button"
              onClick={() => remove(i)}
              aria-label="가족 삭제"
              className="text-inactive active:text-point"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
          <div className="mt-2 grid grid-cols-2 gap-2">
            <input
              value={f.name}
              onChange={(e) => update(i, { name: e.target.value })}
              placeholder="이름"
              className="input !pl-3"
            />
            <select
              value={f.relation}
              onChange={(e) => update(i, { relation: e.target.value })}
              aria-label="관계"
              className="input !pl-3"
            >
              {RELATIONS.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
          </div>
          <input
            type="date"
            value={f.birthDate ?? ""}
            onChange={(e) => update(i, { birthDate: e.target.value })}
            aria-label="가족 생년월일"
            className="input !pl-3 mt-2"
          />
        </div>
      ))}

      {families.length < MAX_FAMILIES && (
        <button
          type="button"
          onClick={add}
          className="flex w-full items-center justify-center gap-1.5 rounded-card border border-dashed border-border py-2.5 text-sm font-medium text-inactive active:bg-card"
        >
          <Plus className="h-4 w-4" />
          가족 추가 ({families.length}/{MAX_FAMILIES})
        </button>
      )}
    </div>
  );
}

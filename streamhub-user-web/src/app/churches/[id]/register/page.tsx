"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { CheckCircle2 } from "lucide-react";
import { BackLink } from "@/components/BackLink";
import { DemoBadge } from "@/components/DemoBadge";
import { FamilyRows } from "@/components/register/FamilyRows";
import { useChurchDetail, useRegisterWorship } from "@/lib/churches";
import {
  BAPTISM_TYPE_OPTIONS,
  GENDER_OPTIONS,
  REGISTER_DEPT_OPTIONS,
  type RegistrationFamily,
  type WorshipRegisterRequest,
} from "@/lib/churchTypes";

interface FormState {
  name: string;
  gender: string;
  birthDate: string;
  phone: string;
  email: string;
  zipcode: string;
  addr1: string;
  addr2: string;
  registerDept: string;
  churchExperience: string; // "Y" | "N"
  prevChurch: string;
  baptismType: string;
  leaderName: string;
  leaderPhone: string;
  privacyAgreed: boolean;
  families: RegistrationFamily[];
}

const INITIAL: FormState = {
  name: "",
  gender: "MALE",
  birthDate: "",
  phone: "",
  email: "",
  zipcode: "",
  addr1: "",
  addr2: "",
  registerDept: "ADULT",
  churchExperience: "N",
  prevChurch: "",
  baptismType: "NONE",
  leaderName: "",
  leaderPhone: "",
  privacyAgreed: false,
  families: [],
};

/**
 * Labelled form field. Wraps a single control in a <label> for click-to-focus; for button groups
 * (multiple buttons) pass `group` so it renders a <div role="group"> instead — buttons must not be
 * nested inside a <label>.
 */
function Field({
  label,
  required,
  group,
  children,
}: {
  label: string;
  required?: boolean;
  group?: boolean;
  children: React.ReactNode;
}) {
  const head = (
    <span className="mb-1 block text-xs font-medium text-inactive">
      {label}
      {required && <span className="ml-0.5 text-point">*</span>}
    </span>
  );
  if (group) {
    return (
      <div role="group" aria-label={label} className="block">
        {head}
        {children}
      </div>
    );
  }
  return (
    <label className="block">
      {head}
      {children}
    </label>
  );
}

export default function WorshipRegisterPage({ params }: { params: { id: string } }) {
  const churchId = Number(params.id);
  const { data: church } = useChurchDetail(churchId);
  const register = useRegisterWorship();

  const [form, setForm] = useState<FormState>(INITIAL);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setForm((f) => ({ ...f, [k]: v }));

  const valid = useMemo(() => {
    if (!form.name.trim() || !form.birthDate || !form.phone.trim()) return false;
    if (!form.privacyAgreed) return false;
    if (form.churchExperience === "Y" && !form.prevChurch.trim()) return false;
    return true;
  }, [form]);

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitError(null);
    if (!valid) {
      setSubmitError("필수 항목과 개인정보 동의를 확인해 주세요.");
      return;
    }
    const payload: WorshipRegisterRequest = {
      churchId,
      name: form.name.trim(),
      gender: form.gender,
      birthDate: form.birthDate,
      phone: form.phone.trim(),
      email: form.email.trim() || undefined,
      zipcode: form.zipcode.trim() || undefined,
      addr1: form.addr1.trim() || undefined,
      addr2: form.addr2.trim() || undefined,
      registerDept: form.registerDept,
      churchExperience: form.churchExperience,
      prevChurch: form.churchExperience === "Y" ? form.prevChurch.trim() || undefined : undefined,
      baptismType: form.baptismType,
      leaderName: form.leaderName.trim() || undefined,
      leaderPhone: form.leaderPhone.trim() || undefined,
      privacyAgreed: "Y",
      families: form.families.filter((f) => f.name.trim()),
    };
    register.mutate(payload, {
      onError: (err) => setSubmitError((err as Error)?.message ?? "신청에 실패했습니다."),
    });
  };

  // Success screen.
  if (register.isSuccess && register.data) {
    return (
      <section className="animate-fade-up px-5 pt-10 text-center">
        <CheckCircle2 className="mx-auto h-14 w-14 text-primary" />
        <h1 className="mt-4 text-xl font-bold">신청이 접수되었습니다</h1>
        <p className="mt-1.5 text-sm text-inactive">담당자가 확인 후 연락드릴 예정입니다.</p>
        <div className="mx-auto mt-5 inline-block rounded-card border border-border/70 bg-surface px-5 py-3">
          <p className="text-xs text-inactive">신청번호</p>
          <p className="mt-0.5 text-lg font-bold text-primary">{register.data.regNo}</p>
        </div>
        <div className="mt-3">
          <DemoBadge label="데모 신청 · 실제 알림은 발송되지 않습니다" />
        </div>
        <Link href={`/churches/${churchId}`} className="btn-primary mt-7 w-full !py-3">
          교회 상세로 돌아가기
        </Link>
      </section>
    );
  }

  return (
    <form onSubmit={onSubmit} className="animate-fade-up px-5 pb-8 pt-4">
      <BackLink href={`/churches/${churchId}`} label={church?.name ?? "교회 상세"} />

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <h1 className="text-xl font-bold">예배·새가족 신청</h1>
        <DemoBadge label="데모 신청 · 실제 알림 미발송" />
      </div>
      {church && <p className="mt-1 text-sm text-inactive">{church.name}</p>}

      {/* ① 인적사항 */}
      <h2 className="mt-6 text-sm font-bold text-active">① 인적사항</h2>
      <div className="mt-3 space-y-3">
        <Field label="이름" required>
          <input value={form.name} onChange={(e) => set("name", e.target.value)} className="input !pl-3" />
        </Field>
        <Field label="성별" required group>
          <div className="flex gap-2">
            {GENDER_OPTIONS.map((g) => (
              <button
                key={g.value}
                type="button"
                onClick={() => set("gender", g.value)}
                aria-pressed={form.gender === g.value}
                className="pill flex-1"
                data-active={form.gender === g.value}
              >
                {g.label}
              </button>
            ))}
          </div>
        </Field>
        <Field label="생년월일" required>
          <input
            type="date"
            value={form.birthDate}
            onChange={(e) => set("birthDate", e.target.value)}
            className="input !pl-3"
          />
        </Field>
        <Field label="연락처" required>
          <input
            type="tel"
            value={form.phone}
            onChange={(e) => set("phone", e.target.value)}
            placeholder="010-0000-0000"
            className="input !pl-3"
          />
        </Field>
        <Field label="이메일">
          <input
            type="email"
            value={form.email}
            onChange={(e) => set("email", e.target.value)}
            className="input !pl-3"
          />
        </Field>
      </div>

      {/* ② 주소 */}
      <h2 className="mt-6 text-sm font-bold text-active">② 주소</h2>
      <div className="mt-3 space-y-3">
        <div className="flex gap-2">
          <input
            value={form.zipcode}
            onChange={(e) => set("zipcode", e.target.value)}
            placeholder="우편번호"
            aria-label="우편번호"
            className="input !pl-3 w-32"
          />
          <input
            value={form.addr1}
            onChange={(e) => set("addr1", e.target.value)}
            placeholder="기본주소"
            aria-label="기본주소"
            className="input !pl-3 flex-1"
          />
        </div>
        <input
          value={form.addr2}
          onChange={(e) => set("addr2", e.target.value)}
          placeholder="상세주소"
          aria-label="상세주소"
          className="input !pl-3"
        />
      </div>

      {/* ③ 등록부서 + 신앙배경 */}
      <h2 className="mt-6 text-sm font-bold text-active">③ 등록부서 · 신앙배경</h2>
      <div className="mt-3 space-y-3">
        <Field label="등록부서" required>
          <select
            value={form.registerDept}
            onChange={(e) => set("registerDept", e.target.value)}
            className="input !pl-3"
          >
            {REGISTER_DEPT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="교회 출석 경험" group>
          <div className="flex gap-2">
            {[
              { value: "N", label: "없음" },
              { value: "Y", label: "있음" },
            ].map((o) => (
              <button
                key={o.value}
                type="button"
                onClick={() => set("churchExperience", o.value)}
                aria-pressed={form.churchExperience === o.value}
                className="pill flex-1"
                data-active={form.churchExperience === o.value}
              >
                {o.label}
              </button>
            ))}
          </div>
        </Field>
        {form.churchExperience === "Y" && (
          <Field label="이전 교회명" required>
            <input
              value={form.prevChurch}
              onChange={(e) => set("prevChurch", e.target.value)}
              className="input !pl-3"
            />
          </Field>
        )}
        <Field label="세례 단계">
          <select
            value={form.baptismType}
            onChange={(e) => set("baptismType", e.target.value)}
            className="input !pl-3"
          >
            {BAPTISM_TYPE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </Field>
      </div>

      {/* ④ 인도자 */}
      <h2 className="mt-6 text-sm font-bold text-active">④ 인도자 (선택)</h2>
      <div className="mt-3 flex gap-2">
        <input
          value={form.leaderName}
          onChange={(e) => set("leaderName", e.target.value)}
          placeholder="인도자 이름"
          aria-label="인도자 이름"
          className="input !pl-3 flex-1"
        />
        <input
          value={form.leaderPhone}
          onChange={(e) => set("leaderPhone", e.target.value)}
          placeholder="인도자 연락처"
          aria-label="인도자 연락처"
          className="input !pl-3 flex-1"
        />
      </div>

      {/* ⑤ 가족 */}
      <h2 className="mt-6 text-sm font-bold text-active">⑤ 함께 등록할 가족 (선택, 최대 5명)</h2>
      <div className="mt-3">
        <FamilyRows families={form.families} onChange={(f) => set("families", f)} />
      </div>

      {/* ⑥ 동의 */}
      <h2 className="mt-6 text-sm font-bold text-active">⑥ 개인정보 수집·이용 동의</h2>
      <label className="mt-3 flex items-start gap-2.5 rounded-card border border-border/70 bg-surface p-3">
        <input
          type="checkbox"
          checked={form.privacyAgreed}
          onChange={(e) => set("privacyAgreed", e.target.checked)}
          className="mt-0.5 h-4 w-4 accent-[#40C1DF]"
        />
        <span className="text-xs leading-relaxed text-inactive">
          새가족 등록 처리를 위해 이름·연락처·주소 등 개인정보 수집·이용에 동의합니다.{" "}
          <span className="text-point">(필수)</span>
        </span>
      </label>

      {submitError && (
        <p role="alert" className="mt-4 text-sm text-point">
          {submitError}
        </p>
      )}

      <button type="submit" disabled={!valid || register.isPending} className="btn-primary mt-6 w-full !py-3.5 disabled:opacity-50">
        {register.isPending ? "신청 중…" : "신청하기"}
      </button>
    </form>
  );
}

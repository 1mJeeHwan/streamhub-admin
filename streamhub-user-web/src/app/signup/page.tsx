"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Check, ChevronDown, UserPlus } from "lucide-react";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";

const field =
  "w-full rounded-xl border border-border bg-surface px-4 py-3 text-active outline-none transition-colors placeholder:text-inactive focus:border-primary";

/** Required (필수) and optional (선택) consents shown on the terms step. */
const TERMS = [
  {
    key: "agreeTerms",
    required: true,
    label: "이용약관 동의",
    body: "은혜온 서비스 이용약관입니다. 회원은 관계 법령 및 본 약관을 준수해야 하며, 서비스는 예배 영상·콘텐츠·커뮤니티 기능을 제공합니다. 회원은 타인의 권리를 침해하거나 서비스 운영을 방해해서는 안 됩니다. 회사는 안정적인 서비스 제공을 위해 노력합니다.",
  },
  {
    key: "agreePrivacy",
    required: true,
    label: "개인정보 수집·이용 동의",
    body: "수집 항목: 이름, 휴대폰 번호, 이메일. 수집 목적: 회원 식별 및 서비스 제공·고지. 보유 기간: 회원 탈퇴 시까지(관계 법령에 따른 보존 의무가 있는 경우 해당 기간). 동의를 거부할 권리가 있으나, 거부 시 회원가입이 제한됩니다.",
  },
  {
    key: "agreeMarketing",
    required: false,
    label: "마케팅 정보 수신 동의 (선택)",
    body: "신규 콘텐츠, 이벤트 및 혜택 정보를 이메일·앱 알림으로 받아보실 수 있습니다. 동의하지 않아도 서비스 이용에 제한이 없으며, 동의 후에도 언제든 수신을 거부할 수 있습니다.",
  },
] as const;

type ConsentKey = (typeof TERMS)[number]["key"];
type Step = "terms" | "account";

export default function SignupPage() {
  const { member, loading, applySession } = useAuth();
  const router = useRouter();
  const [step, setStep] = useState<Step>("terms");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Already logged in → my page.
  useEffect(() => {
    if (!loading && member) router.replace("/mypage");
  }, [loading, member, router]);

  // Step 1: consents
  const [agree, setAgree] = useState<Record<ConsentKey, boolean>>({
    agreeTerms: false,
    agreePrivacy: false,
    agreeMarketing: false,
  });
  const [openTerm, setOpenTerm] = useState<ConsentKey | null>(null);
  const requiredOk = agree.agreeTerms && agree.agreePrivacy;
  const allChecked = TERMS.every((t) => agree[t.key]);

  // Step 2: account
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [password2, setPassword2] = useState("");

  function toggleAll(next: boolean) {
    setAgree({ agreeTerms: next, agreePrivacy: next, agreeMarketing: next });
  }

  async function submitSignup(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (password !== password2) {
      setError("비밀번호가 일치하지 않습니다.");
      return;
    }
    setSubmitting(true);
    try {
      const res = await api.signup({
        email: email.trim(),
        password,
        name: name.trim(),
        phone: phone.trim(),
        agreeTerms: agree.agreeTerms,
        agreePrivacy: agree.agreePrivacy,
        agreeMarketing: agree.agreeMarketing,
      });
      applySession(res);
      router.replace("/mypage");
    } catch (err) {
      setError(err instanceof Error ? err.message : "회원가입에 실패했습니다.");
      setSubmitting(false);
    }
  }

  return (
    <section className="animate-fade-up px-5 pt-10 pb-16">
      <div className="mb-8 text-center">
        <div className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-2xl bg-primary text-bg">
          <UserPlus className="h-6 w-6" />
        </div>
        <h1 className="text-2xl font-bold">회원가입</h1>
        <p className="mt-1 text-sm text-inactive">
          {step === "terms" ? "약관에 동의해 주세요." : "회원 정보를 입력해 주세요."}
        </p>
      </div>

      {/* step indicator */}
      <ol className="mb-6 flex items-center justify-center gap-2 text-xs text-inactive">
        {(["약관", "정보 입력"] as const).map((label, i) => {
          const active = (["terms", "account"][i] as Step) === step;
          const done = i === 0 && step === "account";
          return (
            <li key={label} className="flex items-center gap-2">
              <span
                className={`grid h-6 w-6 place-items-center rounded-full text-[11px] ${
                  active || done ? "bg-primary text-bg" : "bg-surface text-inactive"
                }`}
              >
                {done ? <Check className="h-3 w-3" /> : i + 1}
              </span>
              <span className={active ? "text-active" : ""}>{label}</span>
              {i < 1 && <span className="mx-1 h-px w-4 bg-border" />}
            </li>
          );
        })}
      </ol>

      {error && <p className="mb-3 text-sm text-point">{error}</p>}

      {/* STEP 1 — terms */}
      {step === "terms" && (
        <div className="space-y-3">
          <button
            type="button"
            onClick={() => toggleAll(!allChecked)}
            className="flex w-full items-center gap-3 rounded-xl border border-border bg-surface px-4 py-3 text-left font-semibold"
          >
            <span
              className={`grid h-5 w-5 place-items-center rounded-md border ${
                allChecked ? "border-primary bg-primary text-bg" : "border-border"
              }`}
            >
              {allChecked && <Check className="h-3.5 w-3.5" />}
            </span>
            전체 동의
          </button>

          {TERMS.map((t) => (
            <div key={t.key} className="rounded-xl border border-border/70 bg-surface">
              <div className="flex items-center gap-3 px-4 py-3">
                <button
                  type="button"
                  onClick={() => setAgree((p) => ({ ...p, [t.key]: !p[t.key] }))}
                  className="flex flex-1 items-center gap-3 text-left text-sm"
                >
                  <span
                    className={`grid h-5 w-5 place-items-center rounded-md border ${
                      agree[t.key] ? "border-primary bg-primary text-bg" : "border-border"
                    }`}
                  >
                    {agree[t.key] && <Check className="h-3.5 w-3.5" />}
                  </span>
                  <span>
                    {t.required && <span className="text-primary">[필수] </span>}
                    {t.label}
                  </span>
                </button>
                <button
                  type="button"
                  aria-label="약관 보기"
                  onClick={() => setOpenTerm((o) => (o === t.key ? null : t.key))}
                >
                  <ChevronDown
                    className={`h-4 w-4 text-inactive transition-transform ${
                      openTerm === t.key ? "rotate-180" : ""
                    }`}
                  />
                </button>
              </div>
              {openTerm === t.key && (
                <p className="border-t border-border/70 px-4 py-3 text-xs leading-relaxed text-inactive">
                  {t.body}
                </p>
              )}
            </div>
          ))}

          <button
            type="button"
            disabled={!requiredOk}
            onClick={() => setStep("account")}
            className="btn-primary w-full disabled:opacity-60"
          >
            다음
          </button>
        </div>
      )}

      {/* STEP 2 — account */}
      {step === "account" && (
        <form onSubmit={submitSignup} className="space-y-3">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="이름"
            aria-label="이름"
            required
            className={field}
          />
          <input
            type="tel"
            inputMode="numeric"
            autoComplete="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="휴대폰 번호 (- 없이)"
            aria-label="휴대폰 번호"
            required
            className={field}
          />
          <input
            type="email"
            inputMode="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="이메일"
            aria-label="이메일"
            required
            className={field}
          />
          <input
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호 (8자 이상)"
            aria-label="비밀번호"
            required
            minLength={8}
            className={field}
          />
          <input
            type="password"
            autoComplete="new-password"
            value={password2}
            onChange={(e) => setPassword2(e.target.value)}
            placeholder="비밀번호 확인"
            aria-label="비밀번호 확인"
            required
            className={field}
          />
          <button
            type="submit"
            disabled={submitting}
            className="btn-primary w-full disabled:opacity-60"
          >
            {submitting ? "가입 중…" : "가입하기"}
          </button>
          <button
            type="button"
            onClick={() => setStep("terms")}
            className="w-full rounded-xl border border-border py-3 text-sm text-inactive"
          >
            이전
          </button>
        </form>
      )}

      <p className="mt-6 text-center text-sm text-inactive">
        이미 회원이신가요?{" "}
        <Link href="/login" className="font-semibold text-primary">
          로그인
        </Link>
      </p>
    </section>
  );
}

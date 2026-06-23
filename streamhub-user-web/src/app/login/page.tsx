"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { LogIn } from "lucide-react";
import { useAuth } from "@/lib/auth";

/** Returns a safe in-app return path from `?from=`, defaulting to /mypage. */
function useReturnPath(): string {
  const params = useSearchParams();
  const from = params.get("from");
  // Only allow same-origin relative paths to avoid open-redirects.
  return from && from.startsWith("/") && !from.startsWith("//") ? from : "/mypage";
}

function LoginForm() {
  const { member, loading, login } = useAuth();
  const router = useRouter();
  const returnPath = useReturnPath();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Already logged in → go back where the user came from (or my page).
  useEffect(() => {
    if (!loading && member) router.replace(returnPath);
  }, [loading, member, router, returnPath]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (submitting) return;
    setError(null);
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      router.replace(returnPath);
    } catch (err) {
      setError(err instanceof Error ? err.message : "로그인에 실패했습니다.");
      setSubmitting(false);
    }
  }

  const field =
    "w-full rounded-xl border border-border bg-surface px-4 py-3 text-active outline-none transition-colors placeholder:text-inactive focus:border-primary";

  return (
    <section className="animate-fade-up px-5 pt-10">
      <div className="mb-8 text-center">
        <div className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-2xl bg-primary text-bg">
          <LogIn className="h-6 w-6" />
        </div>
        <h1 className="text-2xl font-bold">로그인</h1>
        <p className="mt-1 text-sm text-inactive">은혜온 회원으로 로그인하세요.</p>
      </div>

      <form onSubmit={onSubmit} className="space-y-3">
        <input
          type="email"
          inputMode="email"
          autoComplete="email"
          aria-label="이메일"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="이메일"
          required
          className={field}
        />
        <input
          type="password"
          autoComplete="current-password"
          aria-label="비밀번호"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="비밀번호"
          required
          className={field}
        />

        {error && <p className="text-sm text-point">{error}</p>}

        <button
          type="submit"
          disabled={submitting}
          className="btn-primary w-full disabled:opacity-60"
        >
          {submitting ? "로그인 중…" : "로그인"}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-inactive">
        아직 회원이 아니신가요?{" "}
        <Link href="/signup" className="font-semibold text-primary">
          회원가입
        </Link>
      </p>

      <div className="mt-6 rounded-xl border border-border/70 bg-surface p-4 text-xs leading-relaxed text-inactive">
        <p className="mb-1 font-semibold text-active">체험 계정</p>
        이메일 <span className="text-primary">member01@streamhub.test</span>
        <br />
        비밀번호 <span className="text-primary">member1234</span>
      </div>
    </section>
  );
}

export default function LoginPage() {
  // useSearchParams (in LoginForm) requires a Suspense boundary under the App Router.
  return (
    <Suspense fallback={<div className="px-5 pt-10" aria-busy="true" />}>
      <LoginForm />
    </Suspense>
  );
}

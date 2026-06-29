"use client";

import Link from "next/link";
import {
  Activity,
  ArrowUpRight,
  Boxes,
  Cloud,
  Code2,
  CreditCard,
  Database,
  GitBranch,
  Home,
  LayoutDashboard,
  MonitorSmartphone,
  Palette,
  Server,
  Sparkles,
} from "lucide-react";
import { CountUp } from "@/components/roadmap/CountUp";

/* ------------------------------------------------------------------ data --- */

const STATS = [
  { label: "백엔드 도메인", value: 26, suffix: "" },
  { label: "REST API", value: 37, suffix: "" },
  { label: "화면 (관리자+사용자)", value: 63, suffix: "" },
  { label: "코드 라인", value: 74, suffix: "K+" },
  { label: "커밋", value: 51, suffix: "" },
  { label: "기능 카탈로그", value: 31, suffix: "개" },
];

const PHASES = [
  {
    icon: Server,
    title: "백엔드 코어",
    desc: "인증 · RBAC · 콘텐츠 · 회원 · 비동기 감사로그. 동작하는 수직 슬라이스로 시작.",
    tags: ["Spring Boot 4", "JPA + MyBatis", "Redis", "S3 · SQS"],
  },
  {
    icon: LayoutDashboard,
    title: "관리자 콘솔",
    desc: "기능 카탈로그 31개 화면 — 대부분 실동작, 일부는 데모·외부연동 대기로 정직하게 표기. 대시보드·커머스·후원·통계까지.",
    tags: ["Next.js 14", "AG Grid", "ApexCharts", "Orval"],
  },
  {
    icon: MonitorSmartphone,
    title: "사용자 미디어 사이트",
    desc: "예배 영상 · 찬양 · 교회찾기(지도) · CCM 음반 커머스. 모바일 퍼스트.",
    tags: ["React Query", "Leaflet", "30초 미리듣기"],
  },
  {
    icon: CreditCard,
    title: "실 외부 연동",
    desc: "토스 PG 샌드박스 실결제 · 스마트택배 배송조회까지 실제 외부 API 연동.",
    tags: ["Toss", "스마트택배", "어댑터 seam"],
  },
  {
    icon: Cloud,
    title: "클라우드 배포 · CI/CD",
    desc: "Terraform으로 AWS 프로비저닝, main 푸시 시 GitHub Actions 자동 배포.",
    tags: ["AWS", "Terraform", "GitHub Actions", "Vercel"],
  },
  {
    icon: Palette,
    title: "UX 폴리시",
    desc: "라이트/다크 테마 · 드래그 이동 챗봇 · 첫 방문 공지 모달.",
    tags: ["라이트모드", "드래그 챗봇", "공지 모달"],
  },
  {
    icon: Activity,
    title: "웹 애널리틱스",
    desc: "콘텐츠 활동을 기록·통계화해 무엇이 인기이고 무엇이 저조한지 파악.",
    tags: ["이벤트 트래킹", "인기/저조 분석"],
    soon: true,
  },
];

const AREAS = [
  {
    icon: LayoutDashboard,
    title: "관리자 콘솔",
    color: "from-cyan-400/20 to-cyan-400/0",
    accent: "text-cyan-600",
    items: [
      "통합 운영 대시보드 · 접속/콘텐츠 통계",
      "회원 · 포인트 원장",
      "굿즈 · 주문 · 쿠폰 · 카테고리 · 재고",
      "후원 · 구독 · 결제일정 · 결제내역",
      "교회 · 예배신청 · 앨범 · 매장",
      "SMS · 배너 · 캠페인 · 알림센터",
      "게시판 · 글 · 1:1문의 · 챗봇 상담 · 감사로그",
    ],
  },
  {
    icon: MonitorSmartphone,
    title: "사용자 사이트",
    color: "from-violet-500/20 to-violet-500/0",
    accent: "text-violet-600",
    items: [
      "홈 · 예배 영상 · 찬양 음악",
      "CCM 음반 + 30초 미리듣기 · 실구매",
      "교회찾기(지도) · 예배(새가족) 신청",
      "매장찾기 · 소식",
      "마이페이지 · 주문/배송조회",
      "AI 챗봇 · 첫 방문 공지 모달",
    ],
  },
  {
    icon: Database,
    title: "백엔드",
    color: "from-emerald-400/20 to-emerald-400/0",
    accent: "text-emerald-600",
    items: [
      "JWT 인증 · 역할 기반 권한(RBAC)",
      "JPA(Hibernate) + MyBatis 하이브리드",
      "Redis 캐시 · QueryDSL",
      "S3 업로드 · SQS 비동기 감사로그",
      "결제 · 배송 어댑터 seam",
      "26개 도메인 · 37개 API",
    ],
  },
  {
    icon: Cloud,
    title: "인프라 · 배포",
    color: "from-rose-500/20 to-rose-500/0",
    accent: "text-rose-600",
    items: [
      "Docker Compose 로컬 (MySQL·Redis·MinIO·LocalStack)",
      "Terraform IaC — AWS 18+ 자원",
      "EC2 · RDS · S3 · SQS · ECR",
      "CloudFront HTTPS",
      "GitHub Actions 무중단 배포",
      "Vercel — 두 프론트 자동 배포",
    ],
  },
];

const STACK = [
  "Spring Boot 4.1",
  "Java 21",
  "MySQL 8.4",
  "Redis",
  "Next.js 14",
  "React 18",
  "TypeScript",
  "Tailwind CSS",
  "React Query",
  "AG Grid",
  "ApexCharts",
  "AWS",
  "Terraform",
  "Docker",
  "GitHub Actions",
];

const LINKS = [
  { label: "사용자 사이트", href: "https://streamhub-user.vercel.app" },
  // ?demo=1 → 로그인 폼에 읽기 전용 viewer 계정 자동 입력
  { label: "관리자 콘솔 (구경하기)", href: "https://streamhub-admin.vercel.app/login?demo=1" },
  { label: "GitHub", href: "https://github.com/1mJeeHwan/graceon" },
];

/* --------------------------------------------------------------- markup --- */

export default function RoadmapPage() {
  return (
    <div className="fixed inset-0 z-[100] overflow-y-auto bg-slate-50 text-slate-700">
      {/* ambient glows */}
      <div className="pointer-events-none fixed inset-0 overflow-hidden">
        <div className="absolute -left-24 -top-24 h-96 w-96 rounded-full bg-cyan-500/20 blur-[120px]" />
        <div className="absolute right-0 top-1/3 h-96 w-96 rounded-full bg-violet-600/20 blur-[120px]" />
        <div className="absolute bottom-0 left-1/3 h-80 w-80 rounded-full bg-rose-500/10 blur-[120px]" />
      </div>

      {/* home link */}
      <Link
        href="/"
        className="fixed right-4 top-4 z-20 flex items-center gap-1.5 rounded-full border border-slate-300 bg-slate-100 px-3.5 py-2 text-xs font-medium text-slate-700 backdrop-blur transition hover:bg-slate-200"
      >
        <Home className="h-3.5 w-3.5" /> 홈으로
      </Link>

      <div className="relative mx-auto max-w-5xl px-6 pb-24">
        {/* ---- hero ---- */}
        <header className="flex min-h-[88vh] flex-col justify-center py-20">
          <div className="animate-fade-up">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-slate-300 bg-slate-100 px-3 py-1 text-[12px] font-medium text-cyan-600">
              <Sparkles className="h-3.5 w-3.5" /> 풀스택 포트폴리오 · 라이브 운영 중
            </span>
            <h1 className="mt-6 text-5xl font-black leading-[1.05] tracking-tight text-slate-900 sm:text-7xl">
              Grace
              <span className="bg-gradient-to-r from-cyan-400 via-sky-400 to-violet-500 bg-clip-text text-transparent">
                On
              </span>
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-relaxed text-slate-600 sm:text-xl">
              교회 · 예배 미디어 플랫폼을 <span className="font-semibold text-slate-900">실제 운영 서비스의 프로덕션 스택 그대로</span>{" "}
              재현했습니다. 하나의 Spring Boot 백엔드 위에 관리자 콘솔과 공개 사용자 사이트, 그리고
              클라우드 배포 파이프라인까지 — 직접 만든 모든 것의 로드맵입니다.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              {LINKS.map((l) => (
                <a
                  key={l.href}
                  href={l.href}
                  target="_blank"
                  rel="noreferrer"
                  className="group inline-flex items-center gap-1.5 rounded-xl border border-slate-300 bg-slate-100 px-4 py-2.5 text-sm font-semibold text-slate-900 transition hover:border-cyan-400/50 hover:bg-cyan-400/10"
                >
                  {l.label}
                  <ArrowUpRight className="h-4 w-4 text-slate-500 transition group-hover:text-cyan-600" />
                </a>
              ))}
            </div>
          </div>
        </header>

        {/* ---- stats ---- */}
        <section className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          {STATS.map((s) => (
            <div
              key={s.label}
              className="rounded-2xl border border-slate-200 bg-white p-5 backdrop-blur transition hover:border-slate-300 hover:bg-slate-100"
            >
              <div className="bg-gradient-to-r from-cyan-600 to-violet-600 bg-clip-text text-4xl font-black tracking-tight text-transparent sm:text-5xl">
                <CountUp value={s.value} suffix={s.suffix} />
              </div>
              <div className="mt-1.5 text-xs font-medium text-slate-500">{s.label}</div>
            </div>
          ))}
        </section>

        {/* ---- timeline ---- */}
        <section className="mt-24">
          <SectionTitle icon={GitBranch} kicker="JOURNEY" title="구현 로드맵" />
          <ol className="relative mt-10 space-y-6 border-l border-slate-200 pl-8">
            {PHASES.map((p, i) => {
              const Icon = p.icon;
              return (
                <li key={p.title} className="relative">
                  <span className="absolute -left-[42px] grid h-7 w-7 place-items-center rounded-full border border-slate-300 bg-slate-50">
                    <span className="h-2.5 w-2.5 rounded-full bg-gradient-to-br from-cyan-400 to-violet-500 shadow-[0_0_12px] shadow-cyan-500/50" />
                  </span>
                  <div className="rounded-2xl border border-slate-200 bg-white p-5 transition hover:border-slate-300 hover:bg-slate-100">
                    <div className="flex items-center gap-2.5">
                      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-slate-100 text-cyan-600">
                        <Icon className="h-4.5 w-4.5" />
                      </span>
                      <h3 className="text-base font-bold text-slate-900">
                        <span className="mr-1.5 text-slate-500">{String(i + 1).padStart(2, "0")}</span>
                        {p.title}
                      </h3>
                      {p.soon && (
                        <span className="rounded-full bg-rose-500/15 px-2 py-0.5 text-[10px] font-bold text-rose-600">
                          NEW
                        </span>
                      )}
                    </div>
                    <p className="mt-2.5 text-sm leading-relaxed text-slate-600">{p.desc}</p>
                    <div className="mt-3 flex flex-wrap gap-1.5">
                      {p.tags.map((t) => (
                        <span key={t} className="rounded-md border border-slate-200 bg-slate-100 px-2 py-0.5 text-[11px] text-slate-500">
                          {t}
                        </span>
                      ))}
                    </div>
                  </div>
                </li>
              );
            })}
          </ol>
        </section>

        {/* ---- areas ---- */}
        <section className="mt-24">
          <SectionTitle icon={Boxes} kicker="WHAT'S INSIDE" title="만든 것 전부" />
          <div className="mt-10 grid gap-4 sm:grid-cols-2">
            {AREAS.map((a) => {
              const Icon = a.icon;
              return (
                <div
                  key={a.title}
                  className={`relative overflow-hidden rounded-2xl border border-slate-200 bg-gradient-to-b ${a.color} p-6 transition hover:border-slate-300`}
                >
                  <div className="flex items-center gap-2.5">
                    <span className="grid h-10 w-10 place-items-center rounded-xl bg-slate-200">
                      <Icon className={`h-5 w-5 ${a.accent}`} />
                    </span>
                    <h3 className="text-lg font-bold text-slate-900">{a.title}</h3>
                  </div>
                  <ul className="mt-4 space-y-2">
                    {a.items.map((it) => (
                      <li key={it} className="flex gap-2 text-sm leading-relaxed text-slate-600">
                        <span className={`mt-2 h-1 w-1 shrink-0 rounded-full ${a.accent} bg-current`} />
                        {it}
                      </li>
                    ))}
                  </ul>
                </div>
              );
            })}
          </div>
        </section>

        {/* ---- stack ---- */}
        <section className="mt-24">
          <SectionTitle icon={Code2} kicker="STACK" title="기술 스택" />
          <div className="mt-8 flex flex-wrap gap-2.5">
            {STACK.map((s) => (
              <span
                key={s}
                className="rounded-full border border-slate-200 bg-slate-100 px-4 py-2 text-sm font-medium text-slate-700 transition hover:border-cyan-400/40 hover:text-slate-900"
              >
                {s}
              </span>
            ))}
          </div>
        </section>

        {/* ---- cta ---- */}
        <section className="mt-24 rounded-3xl border border-slate-200 bg-gradient-to-br from-cyan-500/10 via-violet-600/10 to-rose-500/10 p-10 text-center">
          <h2 className="text-2xl font-bold text-slate-900 sm:text-3xl">직접 둘러보세요</h2>
          <p className="mx-auto mt-3 max-w-md text-sm text-slate-600">
            라이브로 운영 중입니다. 관리자 콘솔은 <code className="rounded bg-slate-200 px-1.5 py-0.5 text-cyan-600">viewer / viewer1234</code> (읽기 전용)로 둘러보세요.
          </p>
          <div className="mt-7 flex flex-wrap justify-center gap-3">
            {LINKS.map((l) => (
              <a
                key={l.href}
                href={l.href}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1.5 rounded-xl bg-slate-200 px-5 py-3 text-sm font-semibold text-slate-900 transition hover:bg-slate-200"
              >
                {l.label}
                <ArrowUpRight className="h-4 w-4" />
              </a>
            ))}
          </div>
        </section>

        <footer className="mt-16 text-center text-xs text-slate-500">
          GraceOn — 풀스택 포트폴리오 · Spring Boot · Next.js · AWS
        </footer>
      </div>
    </div>
  );
}

function SectionTitle({
  icon: Icon,
  kicker,
  title,
}: {
  icon: typeof GitBranch;
  kicker: string;
  title: string;
}) {
  return (
    <div>
      <div className="flex items-center gap-2 text-[12px] font-bold uppercase tracking-[0.2em] text-cyan-400/80">
        <Icon className="h-4 w-4" />
        {kicker}
      </div>
      <h2 className="mt-2 text-3xl font-black tracking-tight text-slate-900 sm:text-4xl">{title}</h2>
    </div>
  );
}

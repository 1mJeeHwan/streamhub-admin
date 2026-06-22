"use client";

// 전체 기능도 — 사용자 사이트 + 관리자 콘솔의 모든 기능을 한 화면에 트리로.
// 관리자 트리는 Sidebar의 NAV_SECTIONS를 그대로 재사용(단일 출처), 사용자 트리는
// 공개 라우트를 큐레이트해 라이브 user-web으로 링크한다.

import Link from "next/link";
import { ExternalLink, ShieldCheck, Users } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { NAV_SECTIONS } from "@/components/layout/Sidebar";

const USER_BASE = "https://streamhub-user.vercel.app";

interface UserItem {
  label: string;
  path: string;
  note?: string;
}
interface UserSection {
  title: string;
  items: UserItem[];
}

const USER_SECTIONS: UserSection[] = [
  {
    title: "둘러보기",
    items: [
      { label: "홈", path: "/" },
      { label: "예배 영상", path: "/video" },
      { label: "찬양 음악", path: "/music" },
      { label: "CCM 음반 (30초 미리듣기)", path: "/albums" },
      { label: "소식·공지", path: "/posts" },
    ],
  },
  {
    title: "찾기",
    items: [
      { label: "교회찾기 (카카오맵·내 주변)", path: "/churches" },
      { label: "매장찾기", path: "/stores" },
      { label: "통합 검색", path: "/search" },
    ],
  },
  {
    title: "쇼핑·참여",
    items: [
      { label: "굿즈샵", path: "/goods" },
      { label: "캠페인·이벤트", path: "/campaigns" },
    ],
  },
  {
    title: "내 정보",
    items: [
      { label: "마이페이지 (구매·포인트·쿠폰·후원·찜·문의)", path: "/mypage" },
      { label: "로그인", path: "/login" },
    ],
  },
  {
    title: "기타",
    items: [
      { label: "AI 챗봇 상담 (Gemini)", path: "/", note: "우측 하단 위젯" },
      { label: "포트폴리오 로드맵", path: "/roadmap" },
    ],
  },
];

function RootCard({
  title,
  icon: Icon,
  subtitle,
  children,
}: {
  title: string;
  icon: LucideIcon;
  subtitle: string;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white">
      <header className="flex items-center gap-2 border-b border-slate-200 px-5 py-3">
        <Icon className="h-5 w-5 text-brand" />
        <h2 className="text-base font-semibold text-slate-900">{title}</h2>
        <span className="text-xs text-slate-400">{subtitle}</span>
      </header>
      <div className="grid gap-x-8 gap-y-5 p-5 sm:grid-cols-2 lg:grid-cols-3">{children}</div>
    </section>
  );
}

function Branch({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-slate-400">{title}</p>
      <ul className="space-y-0.5 border-l border-slate-200 pl-3">{children}</ul>
    </div>
  );
}

export default function FeatureMapPage() {
  const total =
    USER_SECTIONS.reduce((n, s) => n + s.items.length, 0) +
    NAV_SECTIONS.reduce((n, s) => n + s.items.length, 0);

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="text-xl font-bold text-slate-900">전체 기능도</h1>
        <p className="mt-1 text-sm text-slate-500">
          사용자 사이트와 관리자 콘솔의 모든 기능을 한눈에. 항목을 누르면 해당 화면으로 이동합니다. (총 {total}개)
        </p>
      </div>

      <RootCard title="사용자 사이트" icon={Users} subtitle="streamhub-user.vercel.app · 공개">
        {USER_SECTIONS.map((section) => (
          <Branch key={section.title} title={section.title}>
            {section.items.map((item) => (
              <li key={item.label}>
                <a
                  href={`${USER_BASE}${item.path}`}
                  target="_blank"
                  rel="noreferrer"
                  className="group flex items-center gap-1.5 rounded px-1.5 py-1 text-sm text-slate-700 hover:bg-slate-100"
                >
                  <span>{item.label}</span>
                  {item.note && <span className="text-xs text-slate-400">· {item.note}</span>}
                  <ExternalLink className="h-3 w-3 shrink-0 text-slate-300 group-hover:text-slate-500" />
                </a>
              </li>
            ))}
          </Branch>
        ))}
      </RootCard>

      <RootCard title="관리자 콘솔" icon={ShieldCheck} subtitle="현재 콘솔 · 권한별 접근">
        {NAV_SECTIONS.map((section) => (
          <Branch key={section.title} title={section.title}>
            {section.items.map((item) => {
              const Icon = item.icon;
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    className="flex items-center gap-2 rounded px-1.5 py-1 text-sm text-slate-700 hover:bg-slate-100"
                  >
                    <Icon className="h-3.5 w-3.5 shrink-0 text-slate-400" />
                    <span>{item.label}</span>
                    {item.systemOnly && (
                      <span className="rounded-full bg-amber-100 px-1.5 text-[10px] font-semibold text-amber-700">
                        SYSTEM
                      </span>
                    )}
                  </Link>
                </li>
              );
            })}
          </Branch>
        ))}
      </RootCard>
    </div>
  );
}

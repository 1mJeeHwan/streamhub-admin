"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CalendarDays, Church, LogOut, Mail, Phone, User } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { formatDate } from "@/lib/format";
import { NearbyChurchesSection } from "@/components/NearbyChurchesSection";

function Row({ icon: Icon, label, value }: { icon: typeof Mail; label: string; value: string | null }) {
  return (
    <div className="flex items-center gap-3 px-4 py-3.5">
      <Icon className="h-4 w-4 shrink-0 text-inactive" />
      <span className="w-16 shrink-0 text-sm text-inactive">{label}</span>
      <span className="truncate text-sm font-medium text-active">{value || "-"}</span>
    </div>
  );
}

export default function MyPage() {
  const { member, loading, logout } = useAuth();
  const router = useRouter();
  const [leaving, setLeaving] = useState(false);

  // Redirect unauthenticated visitors to login — but not while we're logging out (→ home).
  useEffect(() => {
    if (!loading && !member && !leaving) router.replace("/login");
  }, [loading, member, leaving, router]);

  if (loading || !member) {
    return (
      <div className="animate-fade-up px-5 pt-10">
        <div className="skeleton mx-auto h-20 w-20 rounded-full" />
        <div className="skeleton mx-auto mt-4 h-6 w-32 rounded" />
      </div>
    );
  }

  return (
    <section className="animate-fade-up px-5 pt-8">
      <div className="flex flex-col items-center">
        <div className="grid h-20 w-20 place-items-center rounded-full bg-gradient-to-br from-primary/40 to-secondary/40">
          <User className="h-9 w-9 text-active" />
        </div>
        <h1 className="mt-3 text-xl font-bold">{member.name}</h1>
        <p className="text-sm text-inactive">{member.email}</p>
      </div>

      <div className="mt-7 divide-y divide-border/60 overflow-hidden rounded-card border border-border/70 bg-surface">
        <Row icon={Mail} label="이메일" value={member.email} />
        <Row icon={Church} label="교회" value={member.churchName} />
        <Row icon={Phone} label="연락처" value={member.phone} />
        <Row icon={CalendarDays} label="가입일" value={formatDate(member.createdAt)} />
      </div>

      <button
        onClick={() => {
          setLeaving(true);
          logout();
          router.replace("/");
        }}
        className="mt-6 flex w-full items-center justify-center gap-2 rounded-xl border border-border py-3 text-sm font-medium text-active active:bg-card"
      >
        <LogOut className="h-4 w-4" />
        로그아웃
      </button>

      {/* Near-me churches widget — parent already pads px-5, so cancel it here. */}
      <div className="-mx-5 mt-2">
        <NearbyChurchesSection />
      </div>
    </section>
  );
}

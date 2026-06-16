"use client";

import { useState } from "react";
import { useSession, signOut } from "next-auth/react";
import { LogOut } from "lucide-react";

import { isSystem } from "@/lib/auth-utils";

/**
 * Header shows the logged-in operator and a logout control. Logout clears the
 * session; the backend refresh-token revocation is handled server-side by the
 * NextAuth `signOut` event (see auth.options.ts), so the refresh token never
 * leaves the encrypted cookie.
 */
export default function Header() {
  const { data: session } = useSession();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const name = session?.user?.name ?? "운영자";
  const role = session?.user?.role;
  const roleLabel = isSystem(role) ? "시스템 관리자" : "교회 관리자";

  const handleLogout = async () => {
    setIsLoggingOut(true);
    await signOut({ callbackUrl: "/login" });
  };

  return (
    <header className="flex h-14 items-center justify-between border-b border-slate-200 bg-white px-6">
      <div />
      <div className="flex items-center gap-4">
        <div className="text-right">
          <p className="text-sm font-medium text-slate-900">{name}</p>
          <p className="text-xs text-slate-500">{roleLabel}</p>
        </div>
        <button
          type="button"
          onClick={handleLogout}
          disabled={isLoggingOut}
          className="flex items-center gap-1.5 rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 transition hover:bg-slate-100 disabled:opacity-60"
        >
          <LogOut className="h-4 w-4" />
          로그아웃
        </button>
      </div>
    </header>
  );
}

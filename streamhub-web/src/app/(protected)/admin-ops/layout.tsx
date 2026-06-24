import type { ReactNode } from "react";
import { notFound } from "next/navigation";

import { auth } from "@/../auth";
import { canViewSystem } from "@/lib/auth-utils";

/**
 * AdminOpsLayout gates the integrated operations dashboard to roles holding system :read — SYSTEM
 * (full) and VIEWER (read-only portfolio browse). CHURCH_MANAGER holds no system :read, so it gets
 * a 404 (notFound) rather than a redirect, so the page's existence is not disclosed.
 */
export default async function AdminOpsLayout({
  children,
}: {
  children: ReactNode;
}) {
  const session = await auth();
  if (!canViewSystem(session?.user?.role)) {
    notFound();
  }
  return <>{children}</>;
}

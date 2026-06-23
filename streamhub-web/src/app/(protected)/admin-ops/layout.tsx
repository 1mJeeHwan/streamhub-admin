import type { ReactNode } from "react";
import { notFound } from "next/navigation";

import { auth } from "@/../auth";
import { isSystem } from "@/lib/auth-utils";

/**
 * AdminOpsLayout gates the integrated operations dashboard to the SYSTEM (super-admin) role. The
 * protected layout only verifies that a session exists, so without this any logged-in admin could
 * reach the org-wide ops control room. Non-SYSTEM sessions get a 404 (notFound) rather than a
 * redirect, so the page's existence is not disclosed.
 */
export default async function AdminOpsLayout({
  children,
}: {
  children: ReactNode;
}) {
  const session = await auth();
  if (!isSystem(session?.user?.role)) {
    notFound();
  }
  return <>{children}</>;
}

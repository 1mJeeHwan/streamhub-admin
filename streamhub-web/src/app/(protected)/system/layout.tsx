import type { ReactNode } from "react";
import { notFound } from "next/navigation";

import { auth } from "@/../auth";
import { isSystem } from "@/lib/auth-utils";

/**
 * SystemLayout gates the system-status page to the SYSTEM (super-admin) role. The protected layout
 * only verifies that a session exists, so without this any logged-in admin (CHURCH_MANAGER/VIEWER)
 * could reach the infrastructure health page. Non-SYSTEM sessions get a 404 (notFound) rather than
 * a redirect, so the page's existence is not disclosed.
 */
export default async function SystemLayout({
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

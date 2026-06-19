/**
 * Admin role union as returned by the backend.
 */
export type AdminRole = "SYSTEM" | "CHURCH_MANAGER" | "VIEWER";

/**
 * hasRole reports whether the given role matches the target role.
 */
export function hasRole(role: AdminRole | undefined | null, target: AdminRole): boolean {
  return role === target;
}

/**
 * isSystem reports whether the given role is the SYSTEM (super-admin) role.
 */
export function isSystem(role: AdminRole | undefined | null): boolean {
  return role === "SYSTEM";
}

/**
 * isReadOnly reports whether the role may only view (VIEWER). The backend enforces this with
 * resource:write permissions; the frontend uses it to hide create/edit/delete actions so a VIEWER
 * is not shown buttons that would return 403.
 */
export function isReadOnly(role: AdminRole | undefined | null): boolean {
  return role === "VIEWER";
}

/**
 * canWrite reports whether the role may perform write actions on operational pages
 * (SYSTEM and CHURCH_MANAGER). VIEWER is read-only.
 */
export function canWrite(role: AdminRole | undefined | null): boolean {
  return role === "SYSTEM" || role === "CHURCH_MANAGER";
}

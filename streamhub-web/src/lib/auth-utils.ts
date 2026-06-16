/**
 * Admin role union as returned by the backend.
 */
export type AdminRole = "SYSTEM" | "CHURCH_MANAGER";

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

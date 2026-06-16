import type { AdminRole } from "@/lib/auth-utils";

/**
 * ApiResponse is the common envelope every backend endpoint returns.
 * Success is indicated by resultCode === "0000".
 */
export interface ApiResponse<T> {
  resultCode: string;
  resultMessage: string;
  resultObject: T;
}

/** Success result code returned by the backend. */
export const SUCCESS_CODE = "0000";

/** Token payload returned by /auth/login and /auth/refresh. */
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresIn: number;
}

/** Decoded access-token JWT claims. */
export interface AccessTokenClaims {
  sub: string;
  name: string;
  role: AdminRole;
  church_id: number | null;
  exp: number;
}

/** Admin profile returned by GET /v1/admin/me. */
export interface AdminMe {
  id: number;
  loginId: string;
  name: string;
  role: AdminRole;
  churchId: number | null;
}

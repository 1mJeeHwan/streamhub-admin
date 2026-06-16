import type { DefaultSession } from "next-auth";

type AdminRole = "SYSTEM" | "CHURCH_MANAGER";

declare module "next-auth" {
  /**
   * Returned by `auth`, `useSession`, `getSession`. The refresh token is
   * intentionally NOT exposed here — it lives only in the encrypted JWT cookie
   * and is used server-side by the jwt callback.
   */
  interface Session {
    accessToken: string;
    error?: "RefreshTokenError";
    user: {
      role: AdminRole;
      churchId: number | null;
    } & DefaultSession["user"];
  }
}

declare module "@auth/core/jwt" {
  /**
   * Returned by the `jwt` callback and `getToken`, when using JWT sessions.
   * The `JWT` interface is declared in `@auth/core/jwt` and only re-exported by
   * `next-auth/jwt`, so the augmentation must target the original module.
   */
  interface JWT {
    accessToken: string;
    refreshToken: string;
    /** Absolute access-token expiry, epoch milliseconds. */
    accessTokenExpires: number;
    name?: string | null;
    role: AdminRole;
    churchId: number | null;
    error?: "RefreshTokenError";
  }
}

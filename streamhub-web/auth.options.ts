import type { NextAuthConfig } from "next-auth";
import Credentials from "next-auth/providers/credentials";
import { jwtDecode } from "jwt-decode";

import type {
  AccessTokenClaims,
  ApiResponse,
  TokenResponse,
} from "@/types/api";
import { SUCCESS_CODE } from "@/types/api";
import type { AdminRole } from "@/lib/auth-utils";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

/** Refresh this many ms before the access token actually expires, to avoid edge races. */
const REFRESH_BUFFER_MS = 30_000;

interface LoginCredentials {
  loginId: string;
  password: string;
}

/** Absolute expiry (epoch ms) decoded from an access token's `exp` claim. */
function accessTokenExpiryMs(accessToken: string): number {
  return jwtDecode<AccessTokenClaims>(accessToken).exp * 1000;
}

/**
 * loginRequest calls the backend /auth/login and returns the token payload,
 * or null when authentication fails.
 */
async function loginRequest(
  credentials: LoginCredentials,
): Promise<TokenResponse | null> {
  const res = await fetch(`${API_BASE_URL}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(credentials),
  });

  if (!res.ok) {
    return null;
  }

  const body = (await res.json()) as ApiResponse<TokenResponse | null>;
  if (body.resultCode !== SUCCESS_CODE || !body.resultObject) {
    return null;
  }

  return body.resultObject;
}

/**
 * refreshRequest exchanges a refresh token for a fresh token pair, or returns
 * null when the refresh token is no longer valid (expired or revoked/rotated).
 */
async function refreshRequest(
  refreshToken: string,
): Promise<TokenResponse | null> {
  const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });

  if (!res.ok) {
    return null;
  }

  const body = (await res.json()) as ApiResponse<TokenResponse | null>;
  if (body.resultCode !== SUCCESS_CODE || !body.resultObject) {
    return null;
  }

  return body.resultObject;
}

/**
 * authConfig is the shared NextAuth configuration. It is kept free of any
 * Node-only imports so it can also be consumed by the Edge middleware.
 */
export const authConfig: NextAuthConfig = {
  session: { strategy: "jwt" },
  pages: {
    signIn: "/login",
  },
  providers: [
    Credentials({
      name: "credentials",
      credentials: {
        loginId: { label: "loginId", type: "text" },
        password: { label: "password", type: "password" },
      },
      async authorize(rawCredentials) {
        const loginId = rawCredentials?.loginId;
        const password = rawCredentials?.password;
        if (typeof loginId !== "string" || typeof password !== "string") {
          return null;
        }

        const tokens = await loginRequest({ loginId, password });
        if (!tokens) {
          return null;
        }

        const claims = jwtDecode<AccessTokenClaims>(tokens.accessToken);

        return {
          id: claims.sub,
          name: claims.name,
          role: claims.role,
          churchId: claims.church_id ?? null,
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
        };
      },
    }),
  ],
  callbacks: {
    async jwt({ token, user }) {
      // 1) Initial sign-in: seed the token from the authorized user.
      if (user) {
        const authedUser = user as typeof user & {
          role: AdminRole;
          churchId: number | null;
          accessToken: string;
          refreshToken: string;
        };
        token.name = authedUser.name;
        token.role = authedUser.role;
        token.churchId = authedUser.churchId;
        token.accessToken = authedUser.accessToken;
        token.refreshToken = authedUser.refreshToken;
        token.accessTokenExpires = accessTokenExpiryMs(authedUser.accessToken);
        delete token.error;
        return token;
      }

      // 2) Access token still valid: nothing to do.
      if (Date.now() < token.accessTokenExpires - REFRESH_BUFFER_MS) {
        return token;
      }

      // 3) Expired (or about to): rotate via the refresh token.
      const refreshed = await refreshRequest(token.refreshToken);
      if (!refreshed) {
        token.error = "RefreshTokenError";
        return token;
      }
      token.accessToken = refreshed.accessToken;
      token.refreshToken = refreshed.refreshToken;
      token.accessTokenExpires = accessTokenExpiryMs(refreshed.accessToken);
      delete token.error;
      return token;
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken;
      session.error = token.error;
      session.user.role = token.role;
      session.user.churchId = token.churchId;
      if (token.name) {
        session.user.name = token.name;
      }
      return session;
    },
    authorized({ auth }) {
      return Boolean(auth);
    },
  },
  events: {
    /**
     * On sign-out, revoke the refresh token on the backend (best-effort). For
     * the jwt strategy the event carries the decoded token, so the refresh
     * token is read server-side and never exposed to the client.
     */
    async signOut(message) {
      const refreshToken =
        "token" in message ? message.token?.refreshToken : undefined;
      if (!refreshToken) {
        return;
      }
      try {
        await fetch(`${API_BASE_URL}/auth/logout`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken }),
        });
      } catch {
        // Best-effort revoke; local sign-out proceeds regardless.
      }
    },
  },
};

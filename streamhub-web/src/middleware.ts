import NextAuth from "next-auth";

import { authConfig } from "@/../auth.options";

const { auth } = NextAuth(authConfig);

const PUBLIC_PATHS = ["/login"];

export default auth((req) => {
  const { nextUrl } = req;
  const isLoggedIn = Boolean(req.auth);
  const isPublic = PUBLIC_PATHS.some((path) =>
    nextUrl.pathname.startsWith(path),
  );

  // Authenticated user landing on /login -> push to dashboard.
  if (isLoggedIn && isPublic) {
    return Response.redirect(new URL("/dashboard", nextUrl));
  }

  // Unauthenticated user on a protected route -> push to login.
  if (!isLoggedIn && !isPublic) {
    const loginUrl = new URL("/login", nextUrl);
    loginUrl.searchParams.set("callbackUrl", nextUrl.pathname);
    return Response.redirect(loginUrl);
  }

  return undefined;
});

export const config = {
  // Run on everything except Next internals, static assets and the auth API.
  matcher: ["/((?!api/auth|_next/static|_next/image|favicon.ico).*)"],
};

import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

const CONSOLE_PREFIXES = [
  "/dashboard",
  "/agents",
  "/calls",
  "/bookings",
  "/analytics",
  "/billing",
  "/settings",
  "/help",
  "/onboarding",
];

// These routes are part of completing authentication or are intentionally
// shared with callers outside the workspace. They must remain reachable even
// when the browser also has an authenticated Sauti workspace session.
const SESSION_NEUTRAL_PREFIXES = ["/oauth/callback", "/call"];

function matchesRoute(pathname: string, prefix: string) {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

export function middleware(request: NextRequest) {
  if (process.env.SAUTI_AUTH_BYPASS === "true") {
    return NextResponse.next();
  }

  const pathname = request.nextUrl.pathname;
  const hasSession = request.cookies.get("sauti.session.present")?.value === "1";
  const isConsoleRoute = CONSOLE_PREFIXES.some((prefix) => matchesRoute(pathname, prefix));

  if (isConsoleRoute) {
    if (hasSession) return NextResponse.next();

    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = "/login";
    loginUrl.searchParams.set("next", pathname);
    return NextResponse.redirect(loginUrl);
  }

  const isSessionNeutralRoute = SESSION_NEUTRAL_PREFIXES.some((prefix) => matchesRoute(pathname, prefix));
  if (!hasSession || isSessionNeutralRoute) return NextResponse.next();

  const dashboardUrl = request.nextUrl.clone();
  dashboardUrl.pathname = "/dashboard";
  dashboardUrl.search = "";
  return NextResponse.redirect(dashboardUrl);
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|api|favicon.ico|.*\\..*).*)"],
};

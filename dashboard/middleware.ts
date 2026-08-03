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
  "/admin",
];

// These routes are part of completing authentication or are intentionally
// shared with callers outside the workspace. They must remain reachable even
// when the browser also has an authenticated Sauti workspace session.
const SESSION_NEUTRAL_PREFIXES = ["/oauth/callback", "/call"];

function requestHostname(request: NextRequest) {
  const forwarded = request.headers.get("x-forwarded-host")?.split(",")[0]?.trim();
  return (forwarded || request.headers.get("host") || request.nextUrl.hostname).split(":")[0].toLowerCase();
}

function redirectToHost(request: NextRequest, hostname: string, pathname = request.nextUrl.pathname) {
  const target = request.nextUrl.clone();
  target.protocol = "https:";
  target.hostname = hostname;
  target.port = "";
  target.pathname = pathname;
  return NextResponse.redirect(target);
}

function matchesRoute(pathname: string, prefix: string) {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

export function middleware(request: NextRequest) {
  if (process.env.SAUTI_AUTH_BYPASS === "true") {
    return NextResponse.next();
  }

  const pathname = request.nextUrl.pathname;
  const hostname = requestHostname(request);
  const apexDomain = (process.env.SAUTI_DOMAIN || "sauti.uk").toLowerCase();
  const adminDomain = (process.env.SAUTI_ADMIN_DOMAIN || `admin.${apexDomain}`).toLowerCase();
  const hasSession = request.cookies.get("sauti.session.present")?.value === "1";
  const isAdminHost = hostname === adminDomain;
  const isAdminRoute = matchesRoute(pathname, "/admin");

  if (isAdminHost) {
    if (pathname === "/") {
      const target = request.nextUrl.clone();
      target.pathname = "/admin";
      target.search = "";
      return NextResponse.redirect(target);
    }
    if (pathname === "/login") {
      if (request.nextUrl.searchParams.get("surface") === "admin") return NextResponse.next();
      const target = request.nextUrl.clone();
      target.searchParams.set("surface", "admin");
      return NextResponse.redirect(target);
    }
    if (isAdminRoute) {
      if (hasSession) return NextResponse.next();
      const loginUrl = request.nextUrl.clone();
      loginUrl.pathname = "/login";
      loginUrl.search = "";
      loginUrl.searchParams.set("surface", "admin");
      loginUrl.searchParams.set("next", pathname);
      return NextResponse.redirect(loginUrl);
    }
    return redirectToHost(request, apexDomain);
  }

  if ((hostname === apexDomain || hostname === `www.${apexDomain}`) && isAdminRoute) {
    return redirectToHost(request, adminDomain);
  }
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

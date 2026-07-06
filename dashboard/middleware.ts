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

export function middleware(request: NextRequest) {
  if (process.env.SAUTI_AUTH_BYPASS === "true") {
    return NextResponse.next();
  }

  const isConsoleRoute = CONSOLE_PREFIXES.some((prefix) =>
    request.nextUrl.pathname.startsWith(prefix),
  );
  if (!isConsoleRoute) return NextResponse.next();

  const hasSession = request.cookies.get("sauti.session.present")?.value === "1";
  if (hasSession) return NextResponse.next();

  const loginUrl = request.nextUrl.clone();
  loginUrl.pathname = "/login";
  loginUrl.searchParams.set("next", request.nextUrl.pathname);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|api|favicon.ico|.*\\..*).*)"],
};

/**
 * Routes that need a signed-in workspace. The middleware sends visitors
 * without a session to /login, and treats every other route as public
 * (where signed-in users are redirected to /dashboard).
 *
 * Every top-level folder in app/(console), app/(onboarding) and app/(admin)
 * must be listed here; console-routes.test.ts checks this.
 */
export const CONSOLE_PREFIXES = [
  "/dashboard",
  "/agents",
  "/calls",
  "/bookings",
  "/inbox",
  "/analytics",
  "/billing",
  "/settings",
  "/help",
  "/onboarding",
  "/admin",
];

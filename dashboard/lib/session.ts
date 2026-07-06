import type { AuthSession } from "@/types/api";

const SESSION_KEY = "sauti.auth.session";
const PENDING_EMAIL_KEY = "sauti.auth.pending-email";
export const SESSION_PRESENT_COOKIE = "sauti.session.present";
export const SESSION_CHANGED_EVENT = "sauti:session-changed";

export function readSession(): AuthSession | null {
  if (typeof window === "undefined") return null;
  try {
    const value = window.localStorage.getItem(SESSION_KEY);
    return value ? (JSON.parse(value) as AuthSession) : null;
  } catch {
    window.localStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function writeSession(session: AuthSession) {
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  document.cookie = `${SESSION_PRESENT_COOKIE}=1; Path=/; Max-Age=2592000; SameSite=Lax`;
  window.dispatchEvent(new Event(SESSION_CHANGED_EVENT));
}

export function clearSession() {
  window.localStorage.removeItem(SESSION_KEY);
  document.cookie = `${SESSION_PRESENT_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
  window.dispatchEvent(new Event(SESSION_CHANGED_EVENT));
}

export function writePendingEmail(email: string) {
  window.localStorage.setItem(PENDING_EMAIL_KEY, email);
}

export function readPendingEmail() {
  if (typeof window === "undefined") return "";
  return window.localStorage.getItem(PENDING_EMAIL_KEY) ?? "";
}

export function clearPendingEmail() {
  window.localStorage.removeItem(PENDING_EMAIL_KEY);
}

export function consumeOAuthSessionFromHash(): AuthSession | null {
  if (typeof window === "undefined" || !window.location.hash) return null;
  const values = new URLSearchParams(window.location.hash.slice(1));
  const accessToken = values.get("accessToken");
  const refreshToken = values.get("refreshToken");
  if (!accessToken || !refreshToken) return null;

  try {
    const payloadPart = accessToken.split(".")[1] ?? "";
    const normalizedPayload = payloadPart.replace(/-/g, "+").replace(/_/g, "/")
      .padEnd(Math.ceil(payloadPart.length / 4) * 4, "=");
    const payload = JSON.parse(window.atob(normalizedPayload)) as { tenant_id?: string; email?: string };
    const session: AuthSession = {
      accessToken,
      refreshToken,
      tenant: {
        id: values.get("tenantId") ?? payload.tenant_id ?? "",
        businessName: values.get("businessName") ?? "Sauti workspace",
        email: values.get("email") ?? payload.email ?? "",
        countryCode: values.get("countryCode") ?? "",
        plan: values.get("plan") ?? "starter",
        status: values.get("status") ?? "active",
        monthlyMinutesLimit: Number(values.get("monthlyMinutesLimit") ?? 0),
        minutesUsedThisCycle: Number(values.get("minutesUsedThisCycle") ?? 0),
      },
    };
    writeSession(session);
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
    return session;
  } catch {
    return null;
  }
}

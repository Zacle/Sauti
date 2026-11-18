import type { Account, AuthSession, SessionResponse, WorkspaceMembership } from "@/types/api";

/**
 * The signed-in session for this tab.
 *
 * The access token is kept in memory only. The refresh token is an HttpOnly cookie that page
 * scripts can never read; the backend rotates it on each refresh. A non-secret marker cookie
 * tells the Next.js middleware that a session probably exists, so it can redirect to /login early.
 *
 * Each tab remembers its own active workspace (sessionStorage), so two tabs can work in two
 * workspaces at once. New tabs start from the last workspace used (localStorage, then the account).
 */
export const SESSION_PRESENT_COOKIE = "sauti.session.present";
export const SESSION_CHANGED_EVENT = "sauti:session-changed";

const PENDING_EMAIL_KEY = "sauti.auth.pending-email";
const TAB_WORKSPACE_KEY = "sauti.workspace.active";
const LAST_WORKSPACE_KEY = "sauti.workspace.last";
const SIGN_OUT_SIGNAL_KEY = "sauti.auth.signed-out";
/** Tokens used to live here. Removed on load so they no longer sit in browser storage. */
const LEGACY_SESSION_KEY = "sauti.auth.session";

let current: AuthSession | null = null;

function storage(kind: "local" | "session"): Storage | null {
  try {
    return kind === "local" ? window.localStorage : window.sessionStorage;
  } catch {
    return null;
  }
}

export function removeLegacyStoredSession() {
  if (typeof window === "undefined") return;
  storage("local")?.removeItem(LEGACY_SESSION_KEY);
}

export function readSession(): AuthSession | null {
  return current;
}

export function hasSessionMarker() {
  if (typeof document === "undefined") return false;
  return document.cookie.split(";").some((part) => part.trim() === `${SESSION_PRESENT_COOKIE}=1`);
}

export function writeSession(session: AuthSession) {
  current = session;
  document.cookie = `${SESSION_PRESENT_COOKIE}=1; Path=/; Max-Age=2592000; SameSite=Lax`;
  if (session.tenant) rememberWorkspaceLocally(session.tenant.id);
  window.dispatchEvent(new Event(SESSION_CHANGED_EVENT));
}

export function clearSession() {
  current = null;
  document.cookie = `${SESSION_PRESENT_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
  storage("session")?.removeItem(TAB_WORKSPACE_KEY);
  window.dispatchEvent(new Event(SESSION_CHANGED_EVENT));
}

/** Tells other open tabs to drop their in-memory session after this tab signs out. */
export function announceSignOut() {
  storage("local")?.setItem(SIGN_OUT_SIGNAL_KEY, String(Date.now()));
}

export function isSignOutSignal(event: StorageEvent) {
  return event.key === SIGN_OUT_SIGNAL_KEY;
}

/** Builds the tab's session from a sign-in or refresh response, keeping this tab's workspace if it still can. */
export function sessionFromResponse(response: SessionResponse, preferredWorkspaceId?: string | null): AuthSession {
  const membership = chooseWorkspace(response.account, preferredWorkspaceId);
  return {
    accessToken: response.accessToken,
    account: response.account,
    tenant: membership?.workspace ?? null,
    role: membership?.role ?? null,
    platformAdminSession: Boolean(response.platformAdminSession),
  };
}

/** Switches this tab to another workspace the person belongs to. Returns false if they don't. */
export function selectWorkspace(workspaceId: string) {
  if (!current) return false;
  const membership = current.account.workspaces.find((item) => item.workspace.id === workspaceId);
  if (!membership) return false;
  writeSession({ ...current, tenant: membership.workspace, role: membership.role });
  return true;
}

export function activeWorkspaceId() {
  return current?.tenant?.id ?? storage("session")?.getItem(TAB_WORKSPACE_KEY) ?? null;
}

function chooseWorkspace(account: Account, preferredWorkspaceId?: string | null): WorkspaceMembership | null {
  const candidates = [
    preferredWorkspaceId,
    typeof window === "undefined" ? null : storage("session")?.getItem(TAB_WORKSPACE_KEY),
    typeof window === "undefined" ? null : storage("local")?.getItem(LAST_WORKSPACE_KEY),
    account.lastWorkspaceId,
  ];
  for (const id of candidates) {
    const match = id ? account.workspaces.find((item) => item.workspace.id === id) : undefined;
    if (match) return match;
  }
  return account.workspaces[0] ?? null;
}

function rememberWorkspaceLocally(workspaceId: string) {
  storage("session")?.setItem(TAB_WORKSPACE_KEY, workspaceId);
  storage("local")?.setItem(LAST_WORKSPACE_KEY, workspaceId);
}

export function writePendingEmail(email: string) {
  storage("local")?.setItem(PENDING_EMAIL_KEY, email);
}

export function readPendingEmail() {
  if (typeof window === "undefined") return "";
  return storage("local")?.getItem(PENDING_EMAIL_KEY) ?? "";
}

export function clearPendingEmail() {
  storage("local")?.removeItem(PENDING_EMAIL_KEY);
}

/** The same session with the active workspace renamed everywhere it appears. */
export function renameActiveWorkspace(session: AuthSession, businessName: string): AuthSession {
  if (!session.tenant) return session;
  const id = session.tenant.id;
  return {
    ...session,
    tenant: { ...session.tenant, businessName },
    account: {
      ...session.account,
      workspaces: session.account.workspaces.map((item) =>
        item.workspace.id === id ? { ...item, workspace: { ...item.workspace, businessName } } : item,
      ),
    },
  };
}

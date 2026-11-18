import type { AuthSession, SessionResponse } from "@/types/api";
import {
  activeWorkspaceId,
  clearSession,
  hasSessionMarker,
  readSession,
  removeLegacyStoredSession,
  sessionFromResponse,
  writeSession,
} from "@/lib/session";
import { isPreviewMode } from "@/lib/preview/mode";
import { parseSuccessfulJsonResponse } from "./response";
import { messageForFailedResponse, NETWORK_ERROR_MESSAGE } from "./errors";
import { workspaceApiUrl } from "./workspace-path";

export class ApiError extends Error {
  /** status 0 means the request never reached the server (offline, DNS, CORS, aborted). */
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = "ApiError";
  }

  get isNetworkError() {
    return this.status === 0;
  }
}

const SESSION_EXPIRED = "Your session has expired. Please log in again.";
const SESSION_REFRESH_LOCK = "sauti-auth-session-refresh";

/** The URL for an API path, addressed to this tab's active workspace when the path is a workspace feature. */
export function apiUrl(path: string) {
  return workspaceApiUrl(path, activeWorkspaceId());
}

/** fetch() that reports connection failures as a readable ApiError instead of "Failed to fetch". */
async function send(input: string, init: RequestInit) {
  try {
    return await fetch(input, { credentials: "same-origin", ...init });
  } catch (caught) {
    if (caught instanceof DOMException && caught.name === "AbortError") throw caught;
    throw new ApiError(NETWORK_ERROR_MESSAGE, 0);
  }
}

async function parseError(response: Response) {
  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  return messageForFailedResponse(response.status, body);
}

let refreshPromise: Promise<AuthSession | null> | null = null;
let bootstrapPromise: Promise<AuthSession | null> | null = null;

/**
 * Exchanges the HttpOnly refresh cookie for a new access token. Tabs take turns through a Web
 * Lock so each one sends the newest cookie. Returns null when there is no valid session.
 */
async function requestRefresh(): Promise<AuthSession | null> {
  const previousWorkspace = activeWorkspaceId();
  let response: Response;
  try {
    response = await fetch("/api/v1/auth/refresh", { method: "POST", credentials: "same-origin" });
  } catch {
    // The server may have rotated the cookie even though the response was lost; the grace window allows one retry.
    response = await fetch("/api/v1/auth/refresh", { method: "POST", credentials: "same-origin" });
  }
  if (!response.ok) {
    clearSession();
    return null;
  }
  const session = sessionFromResponse((await response.json()) as SessionResponse, previousWorkspace);
  writeSession(session);
  return session;
}

export function refreshSession(): Promise<AuthSession | null> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      if (typeof navigator !== "undefined" && navigator.locks) {
        return navigator.locks.request(SESSION_REFRESH_LOCK, () => requestRefresh());
      }
      return requestRefresh();
    })().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

/**
 * Restores the session once per page load. Every API call waits for it, so screens that load data
 * before the auth provider finishes still send a token.
 */
export function ensureSession(): Promise<AuthSession | null> {
  const existing = readSession();
  if (existing) return Promise.resolve(existing);
  if (typeof window === "undefined") return Promise.resolve(null);
  if (!bootstrapPromise) {
    removeLegacyStoredSession();
    bootstrapPromise = hasSessionMarker() ? refreshSession().catch(() => null) : Promise.resolve(null);
  }
  return bootstrapPromise;
}

/** Called after an explicit sign-in or sign-out so the next page load bootstraps again. */
export function resetSessionBootstrap() {
  bootstrapPromise = null;
}

/** Dev-only: answer from sample data. The import is dynamic so fixtures never enter production bundles. */
async function previewAnswer(path: string, init: RequestInit) {
  const { previewResponse } = await import("@/lib/preview/fixtures");
  await new Promise((resolve) => setTimeout(resolve, 120));
  return previewResponse(path, init.method ?? "GET", init.body);
}

/**
 * Sends an authenticated request. A 401 triggers one refresh and one retry; if that fails the
 * session is cleared and the caller sees "session expired".
 */
async function authorizedFetch(path: string, init: RequestInit, retry: boolean): Promise<Response> {
  const session = await ensureSession();
  const headers = new Headers(init.headers);
  if (session?.accessToken) headers.set("Authorization", `Bearer ${session.accessToken}`);
  const response = await send(apiUrl(path), { ...init, headers });
  if (response.status === 401 && session && retry) {
    const refreshed = await refreshSession();
    if (!refreshed) throw new ApiError(SESSION_EXPIRED, 401);
    return authorizedFetch(path, init, false);
  }
  return response;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (isPreviewMode()) return (await previewAnswer(path, init)) as T;
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  const response = await authorizedFetch(path, { ...init, headers }, true);
  if (!response.ok) throw new ApiError(await parseError(response), response.status);
  return parseSuccessfulJsonResponse<T>(response);
}

export async function apiBlobRequest(path: string, init: RequestInit = {}): Promise<Blob> {
  if (isPreviewMode()) throw new ApiError("Not available in preview mode", 404);
  const response = await authorizedFetch(path, init, true);
  if (!response.ok) throw new ApiError(await parseError(response), response.status);
  return response.blob();
}

export async function apiTextRequest(path: string, init: RequestInit = {}): Promise<string> {
  if (isPreviewMode()) return JSON.stringify(await previewAnswer(path, init));
  const response = await authorizedFetch(path, init, true);
  if (!response.ok) throw new ApiError(await parseError(response), response.status);
  return response.text();
}

export async function apiUpload<T>(path: string, body: Blob): Promise<T> {
  if (isPreviewMode()) return (await previewAnswer(path, { method: "POST" })) as T;
  const headers = new Headers({ "Content-Type": body.type || "application/octet-stream", Accept: "application/json" });
  const response = await authorizedFetch(path, { method: "POST", headers, body }, true);
  if (!response.ok) throw new ApiError(await parseError(response), response.status);
  return response.json() as Promise<T>;
}

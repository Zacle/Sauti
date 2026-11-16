import type { AuthSession } from "@/types/api";
import { clearSession, readSession, writeSession } from "@/lib/session";
import { isPreviewMode } from "@/lib/preview/mode";
import { parseSuccessfulJsonResponse } from "./response";
import { messageForFailedResponse, NETWORK_ERROR_MESSAGE } from "./errors";

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

/** fetch() that reports connection failures as a readable ApiError instead of "Failed to fetch". */
async function send(input: string, init: RequestInit) {
  try {
    return await fetch(input, init);
  } catch (caught) {
    if (caught instanceof DOMException && caught.name === "AbortError") throw caught;
    throw new ApiError(NETWORK_ERROR_MESSAGE, 0);
  }
}

let refreshPromise: Promise<AuthSession> | null = null;
const SESSION_REFRESH_LOCK = "sauti-auth-session-refresh";

async function parseError(response: Response) {
  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  return messageForFailedResponse(response.status, body);
}

async function requestSessionRefresh(session: AuthSession) {
  const latest = readSession();
  if (!latest) throw new ApiError("Your session has expired. Please log in again.", 401);
  if (latest.refreshToken !== session.refreshToken) return latest;

  let response: Response;
  try {
    response = await fetch("/api/v1/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: latest.refreshToken }),
    });
  } catch {
    // The server may have rotated the token even when the response was lost.
    // Retry immediately while the backend's bounded rotation grace is active.
    response = await fetch("/api/v1/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: latest.refreshToken }),
    });
  }

  if (!response.ok) {
    const concurrentSession = readSession();
    if (concurrentSession && concurrentSession.refreshToken !== latest.refreshToken) {
      return concurrentSession;
    }
    clearSession();
    throw new ApiError("Your session has expired. Please log in again.", 401);
  }
  const refreshed = (await response.json()) as AuthSession;
  writeSession(refreshed);
  return refreshed;
}

async function refreshSession(session: AuthSession) {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      if (typeof navigator !== "undefined" && navigator.locks) {
        return navigator.locks.request(
          SESSION_REFRESH_LOCK,
          () => requestSessionRefresh(session),
        );
      }
      return requestSessionRefresh(session);
    })().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

/** Dev-only: answer from sample data. The import is dynamic so fixtures never enter production bundles. */
async function previewAnswer(path: string, init: RequestInit) {
  const { previewResponse } = await import("@/lib/preview/fixtures");
  await new Promise((resolve) => setTimeout(resolve, 120));
  return previewResponse(path, init.method ?? "GET", init.body);
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  if (isPreviewMode()) return (await previewAnswer(path, init)) as T;
  const session = readSession();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  if (session?.accessToken) headers.set("Authorization", `Bearer ${session.accessToken}`);

  const response = await send(`/api/v1${path}`, { ...init, headers });
  if (response.status === 401 && session?.refreshToken && retry) {
    await refreshSession(session);
    return apiRequest<T>(path, init, false);
  }
  if (!response.ok) throw new ApiError(await parseError(response), response.status);
  return parseSuccessfulJsonResponse<T>(response);
}

export async function apiBlobRequest(path: string, init: RequestInit = {}, retry = true): Promise<Blob> {
  if (isPreviewMode()) throw new ApiError("Not available in preview mode", 404);
  const session = readSession();
  const headers = new Headers(init.headers);
  if (session?.accessToken) headers.set("Authorization", `Bearer ${session.accessToken}`);
  const response = await send(`/api/v1${path}`, { ...init, headers });
  if (response.status === 401 && session?.refreshToken && retry) {
    await refreshSession(session);
    return apiBlobRequest(path, init, false);
  }
  if (!response.ok) throw new ApiError(await parseError(response), response.status);
  return response.blob();
}

export async function apiTextRequest(path: string, init: RequestInit = {}, retry = true): Promise<string> {
  if (isPreviewMode()) return JSON.stringify(await previewAnswer(path, init));
  const session = readSession();
  const headers = new Headers(init.headers);
  if (session?.accessToken) headers.set("Authorization", `Bearer ${session.accessToken}`);
  const response = await send(`/api/v1${path}`, { ...init, headers });
  if (response.status === 401 && session?.refreshToken && retry) {
    await refreshSession(session);
    return apiTextRequest(path, init, false);
  }
  if (!response.ok) throw new ApiError(await parseError(response), response.status);
  return response.text();
}

export async function apiUpload<T>(path: string, body: Blob, retry = true): Promise<T> {
  if (isPreviewMode()) return (await previewAnswer(path, { method: "POST" })) as T;
  const session = readSession();
  const headers = new Headers({ "Content-Type": body.type || "application/octet-stream", Accept: "application/json" });
  if (session?.accessToken) headers.set("Authorization", `Bearer ${session.accessToken}`);
  const response = await send(`/api/v1${path}`, { method: "POST", headers, body });
  if (response.status === 401 && session?.refreshToken && retry) {
    await refreshSession(session);
    return apiUpload<T>(path, body, false);
  }
  if (!response.ok) throw new ApiError(await parseError(response), response.status);
  return response.json() as Promise<T>;
}

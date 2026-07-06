import type { AuthSession } from "@/types/api";
import { clearSession, readSession, writeSession } from "@/lib/session";

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

let refreshPromise: Promise<AuthSession> | null = null;

async function parseError(response: Response) {
  try {
    const body = (await response.json()) as { message?: string; error?: string };
    return body.message ?? body.error ?? `Request failed (${response.status})`;
  } catch {
    return `Request failed (${response.status})`;
  }
}

async function refreshSession(session: AuthSession) {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const response = await fetch("/api/v1/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      });
      if (!response.ok) {
        clearSession();
        throw new ApiError("Your session has expired. Please log in again.", 401);
      }
      const refreshed = (await response.json()) as AuthSession;
      writeSession(refreshed);
      return refreshed;
    })().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const session = readSession();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  if (session?.accessToken) headers.set("Authorization", `Bearer ${session.accessToken}`);

  const response = await fetch(`/api/v1${path}`, { ...init, headers });
  if (response.status === 401 && session?.refreshToken && retry) {
    await refreshSession(session);
    return apiRequest<T>(path, init, false);
  }
  if (!response.ok) throw new ApiError(await parseError(response), response.status);
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function apiBlobRequest(path: string, init: RequestInit = {}, retry = true): Promise<Blob> {
  const session = readSession();
  const headers = new Headers(init.headers);
  if (session?.accessToken) headers.set("Authorization", `Bearer ${session.accessToken}`);
  const response = await fetch(`/api/v1${path}`, { ...init, headers });
  if (response.status === 401 && session?.refreshToken && retry) {
    await refreshSession(session);
    return apiBlobRequest(path, init, false);
  }
  if (!response.ok) throw new ApiError(await parseError(response), response.status);
  return response.blob();
}

export async function apiUpload<T>(path: string, body: Blob, retry = true): Promise<T> {
  const session = readSession();
  const headers = new Headers({ "Content-Type": body.type || "application/octet-stream", Accept: "application/json" });
  if (session?.accessToken) headers.set("Authorization", `Bearer ${session.accessToken}`);
  const response = await fetch(`/api/v1${path}`, { method: "POST", headers, body });
  if (response.status === 401 && session?.refreshToken && retry) {
    await refreshSession(session);
    return apiUpload<T>(path, body, false);
  }
  if (!response.ok) throw new ApiError(await parseError(response), response.status);
  return response.json() as Promise<T>;
}

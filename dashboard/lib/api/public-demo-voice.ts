import type { BrowserVoiceRuntimeSession } from "@/types/api";

export type PublicDemoVoiceConfiguration = {
  name: string;
  description: string;
  greeting: string;
  maxDurationSeconds: number;
  runtime: BrowserVoiceRuntimeSession;
};

export type PublicDemoVoiceSession = {
  sessionId: string;
  token: string;
  maxDurationSeconds: number;
  runtime: BrowserVoiceRuntimeSession;
};

async function responseError(response: Response, fallback: string) {
  const payload = await response.json().catch(() => ({})) as { message?: string };
  return new Error(payload.message ?? fallback);
}

export async function getPublicDemoVoiceConfiguration(origin: string) {
  const response = await fetch(
    `/api/v1/public/demo-voice/configuration?origin=${encodeURIComponent(origin)}`,
    { cache: "no-store" },
  );
  if (!response.ok) throw await responseError(response, "The voice demo is temporarily unavailable.");
  return response.json() as Promise<PublicDemoVoiceConfiguration>;
}

export async function startPublicDemoVoiceSession(deviceId: string, origin: string) {
  const response = await fetch("/api/v1/public/demo-voice/sessions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ deviceId, origin, consentAccepted: true }),
  });
  if (!response.ok) throw await responseError(response, "Unable to start the voice demo.");
  return response.json() as Promise<PublicDemoVoiceSession>;
}

export async function completePublicDemoVoiceSession(sessionId: string, token: string) {
  const response = await fetch(
    `/api/v1/public/demo-voice/sessions/${encodeURIComponent(sessionId)}/complete`,
    { method: "POST", headers: { Authorization: `Bearer ${token}` } },
  );
  if (!response.ok) throw await responseError(response, "Unable to close the voice demo session.");
}

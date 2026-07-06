export type PublicWebVoiceAgent = {
  publicId: string;
  name: string;
  description: string;
  defaultLanguage: string;
  languages: string[];
  consentRequired: boolean;
  recordingEnabled: boolean;
};

export type WebVoiceSession = {
  callId: string;
  sessionId: string;
  token: string;
  websocketUrl: string;
  greeting: string;
  inputSampleRate: number;
  language: string;
};

export async function getPublicWebVoiceAgent(publicId: string) {
  const response = await fetch(`/api/v1/public/web-voice/agents/${encodeURIComponent(publicId)}`, {
    cache: "no-store",
  });
  if (!response.ok) throw new Error(response.status === 404 ? "This voice agent is unavailable." : "Unable to load this voice agent.");
  return response.json() as Promise<PublicWebVoiceAgent>;
}

export async function startPublicWebVoiceSession(
  publicId: string,
  consentAccepted: boolean,
  origin: string,
  preferredLanguage: string,
) {
  const response = await fetch(`/api/v1/public/web-voice/agents/${encodeURIComponent(publicId)}/sessions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ consentAccepted, origin, preferredLanguage }),
  });
  const payload = await response.json().catch(() => ({})) as { message?: string };
  if (!response.ok) throw new Error(payload.message ?? "Unable to start this voice session.");
  return payload as WebVoiceSession;
}

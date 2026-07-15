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
  greetingAudioBase64: string | null;
  inputSampleRate: number;
  language: string;
  mode: "openai_realtime" | "hybrid_realtime" | "realtime" | "turn";
};

export type WebVoiceAudioTurn = {
  callerTranscript: string;
  language: string;
  response: string;
  outcome: string;
  audioBase64: string | null;
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

export async function sendPublicWebVoiceAudioTurn(sessionId: string, token: string, recording: Blob) {
  const response = await fetch(`/api/v1/public/web-voice/sessions/${encodeURIComponent(sessionId)}/turn-audio`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": recording.type || "audio/webm",
    },
    body: recording,
  });
  const payload = await response.json().catch(() => ({})) as { message?: string };
  if (!response.ok) throw new Error(payload.message ?? "Unable to process your voice message.");
  return payload as WebVoiceAudioTurn;
}

export async function completePublicWebVoiceSession(sessionId: string, token: string) {
  const response = await fetch(`/api/v1/public/web-voice/sessions/${encodeURIComponent(sessionId)}/complete`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(payload.message ?? "Unable to end this voice session.");
  }
}

export async function connectPublicRealtime(sessionId: string, token: string, sdp: string) {
  const response = await fetch(`/api/v1/public/web-voice/sessions/${encodeURIComponent(sessionId)}/realtime/connect`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/sdp", Accept: "application/sdp" },
    body: sdp,
  });
  if (!response.ok) throw new Error("Unable to establish the low-latency voice connection.");
  return response.text();
}

export async function recordPublicRealtimeTranscript(sessionId: string, token: string, role: "caller" | "agent", text: string, interrupted = false) {
  await fetch(`/api/v1/public/web-voice/sessions/${encodeURIComponent(sessionId)}/realtime/transcript`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ role, text, interrupted }),
  });
}

export async function executePublicRealtimeTool(sessionId: string, token: string, callId: string, name: string, argumentsJson: string) {
  const response = await fetch(`/api/v1/public/web-voice/sessions/${encodeURIComponent(sessionId)}/realtime/tool`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ callId, name, arguments: argumentsJson }),
  });
  if (!response.ok) return { success: false, error: "The requested action could not be completed" };
  return response.json() as Promise<Record<string, unknown>>;
}

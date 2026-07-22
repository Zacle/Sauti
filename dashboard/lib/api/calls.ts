import type { Call, CallTurn, StartTestCallResponse } from "@/types/api";
import { apiBlobRequest, apiRequest, apiTextRequest, apiUpload } from "./client";

export function listCalls() {
  return apiRequest<Call[]>("/calls");
}

export function startTestCall(agentId: string, ttsVoiceId?: string, runtimeProvider?: string) {
  return apiRequest<StartTestCallResponse>("/calls/test", {
    method: "POST",
    body: JSON.stringify({
      agentId,
      ttsVoiceId: ttsVoiceId?.trim() ?? "",
      runtimeProvider: runtimeProvider?.trim() ?? "",
    }),
  });
}

export function sendTestTurn(callSid: string, transcript: string) {
  return apiRequest<{ language: string; response: string; transcript: string; outcome: string; acceptedTranscript: boolean }>(
    `/calls/${encodeURIComponent(callSid)}/simulate-turn`,
    { method: "POST", body: JSON.stringify({ transcript }) },
  );
}

export function sendTestAudioTurn(callId: string, recording: Blob) {
  return apiUpload<{
    callerTranscript: string;
    language: string;
    response: string;
    outcome: string;
    audioBase64: string | null;
    sttLatencyMs: number;
    llmLatencyMs: number;
    ttsLatencyMs: number;
    totalLatencyMs: number;
  }>(
    `/calls/${callId}/test-turn-audio`,
    recording,
  );
}

export function completeTestCall(callId: string, outcome = "completed") {
  return apiRequest<Call>(`/calls/${callId}/complete-test`, {
    method: "POST",
    body: JSON.stringify({ outcome }),
  });
}

export function markTestInterruption(callId: string) {
  return apiRequest<void>(`/calls/${callId}/test-interruption`, { method: "POST" });
}

export function sendTestReminder(callId: string) {
  return apiRequest<{ callerTranscript: string; language: string; response: string; outcome: string }>(
    `/calls/${callId}/test-reminder`,
    { method: "POST" },
  );
}

export function sendTestFarewell(callId: string) {
  return apiRequest<{ callerTranscript: string; language: string; response: string; outcome: string }>(
    `/calls/${callId}/test-farewell`,
    { method: "POST" },
  );
}

export function listCallTurns(callId: string) {
  return apiRequest<CallTurn[]>(`/calls/${callId}/turns`);
}

export function getTestCallAudio(callId: string) {
  return apiBlobRequest(`/calls/${callId}/test-audio`, { method: "POST" });
}

export function uploadCallRecording(callId: string, recording: Blob) {
  return apiUpload<Call>(`/calls/${callId}/recording`, recording);
}

export function connectTestRealtime(callId: string, sdp: string) {
  return apiTextRequest(`/calls/${callId}/realtime/connect`, {
    method: "POST",
    headers: { "Content-Type": "application/sdp", Accept: "application/sdp" },
    body: sdp,
  });
}

export function recordTestRealtimeTranscript(callId: string, role: "caller" | "agent", text: string, interrupted = false) {
  return apiRequest<{ instructions: string; directResponse: string; requiredTool: string }>(`/calls/${callId}/realtime/transcript`, {
    method: "POST",
    body: JSON.stringify({ role, text, interrupted }),
  });
}

export function executeTestRealtimeTool(callId: string, toolCallId: string, name: string, argumentsJson: string) {
  return apiRequest<Record<string, unknown>>(`/calls/${callId}/realtime/tool`, {
    method: "POST",
    body: JSON.stringify({ callId: toolCallId, name, arguments: argumentsJson }),
  });
}

export function getCallRecording(callId: string) {
  return apiBlobRequest(`/calls/${callId}/recording`);
}

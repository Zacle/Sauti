import type { Call, CallTurn, StartTestCallResponse } from "@/types/api";
import { apiBlobRequest, apiRequest, apiUpload } from "./client";

export function listCalls() {
  return apiRequest<Call[]>("/calls");
}

export function startTestCall(agentId: string) {
  return apiRequest<StartTestCallResponse>("/calls/test", {
    method: "POST",
    body: JSON.stringify({ agentId }),
  });
}

export function sendTestTurn(callSid: string, transcript: string) {
  return apiRequest<{ language: string; response: string; transcript: string; outcome: string }>(
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

export function getCallRecording(callId: string) {
  return apiBlobRequest(`/calls/${callId}/recording`);
}

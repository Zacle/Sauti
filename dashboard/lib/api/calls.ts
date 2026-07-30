import type {
  BrowserVoiceRuntimeSession,
  Call,
  CallTurn,
  StartTestCallResponse,
} from "@/types/api";
import { apiBlobRequest, apiRequest } from "./client";

export function listCalls() {
  return apiRequest<Call[]>("/calls");
}

export function startTestCall(agentId: string, ttsVoiceId?: string, language?: string) {
  return apiRequest<StartTestCallResponse>("/calls/test", {
    method: "POST",
    body: JSON.stringify({
      agentId,
      ttsVoiceId: ttsVoiceId?.trim() ?? "",
      language: language?.trim() ?? "",
    }),
  });
}

export function prepareTestCallRuntime(agentId: string, ttsVoiceId?: string, language?: string) {
  return apiRequest<BrowserVoiceRuntimeSession>("/calls/test/runtime", {
    method: "POST",
    body: JSON.stringify({
      agentId,
      ttsVoiceId: ttsVoiceId?.trim() ?? "",
      language: language?.trim() ?? "",
    }),
  });
}

export function completeTestCall(
  callId: string,
  outcome = "completed",
  providerCallControlId = "",
  providerCallLegId = "",
) {
  return apiRequest<Call>(`/calls/${callId}/complete-test`, {
    method: "POST",
    body: JSON.stringify({ outcome, providerCallControlId, providerCallLegId }),
  });
}

export function correlateTestCall(
  callId: string,
  providerCallControlId = "",
  providerCallLegId = "",
) {
  return apiRequest<Call>(`/calls/${callId}/provider-correlation`, {
    method: "POST",
    body: JSON.stringify({ providerCallControlId, providerCallLegId }),
  });
}

export function getCall(callId: string) {
  return apiRequest<Call>(`/calls/${callId}`);
}

export function listCallTurns(callId: string) {
  return apiRequest<CallTurn[]>(`/calls/${callId}/turns`);
}

export function recordTestRealtimeTranscript(callId: string, role: "caller" | "agent", text: string, interrupted = false) {
  return apiRequest<{ instructions: string; directResponse: string; requiredTool: string }>(`/calls/${callId}/realtime/transcript`, {
    method: "POST",
    body: JSON.stringify({ role, text, interrupted }),
  });
}

export function getCallRecording(callId: string) {
  return apiBlobRequest(`/calls/${callId}/recording`);
}

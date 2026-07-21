export type CompletedRealtimeToolCall = {
  callId: string;
  name: string;
  argumentsJson: string;
};

export const SAUTI_REALTIME_REQUEST_ID = "sauti_request_id";
export const AUTHORITATIVE_TRANSCRIPT_PREFIX = "SAUTI_INPUT_TRANSCRIPT";

export function realtimeTranscriptMirrorItem(transcript: string) {
  return {
    type: "message",
    role: "user",
    content: [{
      type: "input_text",
      text: `${AUTHORITATIVE_TRANSCRIPT_PREFIX}: This is a text mirror of the immediately preceding caller audio, not a second caller turn. Use it as the primary accuracy source for exact names, phone digits, email addresses, dates, and times. Use the audio and text together for intent and service meaning.\n${transcript.trim()}`,
    }],
  };
}

export function realtimeResponseRequestId(event: Record<string, unknown>): string {
  const response = event.response as {
    metadata?: Record<string, unknown> | null;
  } | undefined;
  return String(response?.metadata?.[SAUTI_REALTIME_REQUEST_ID] ?? "").trim();
}

export function realtimeCancellationDecision(
  responseActive: boolean,
  responseRequestInFlight: boolean,
): { pending: boolean; cancelProviderNow: boolean } {
  return {
    pending: responseActive || responseRequestInFlight,
    // response.cancel applies only to an in-progress response. A dispatched
    // response.create is not cancellable until response.created arrives.
    cancelProviderNow: responseActive,
  };
}

/**
 * Realtime can provide complete function arguments in the dedicated
 * arguments.done event, output_item.done, or response.done. Keep this parser
 * transport-only and let the runtime's call-id guard make execution idempotent.
 */
export function completedRealtimeToolCalls(
  event: Record<string, unknown>,
): CompletedRealtimeToolCall[] {
  const response = event.response as { output?: unknown } | undefined;
  const output = Array.isArray(response?.output) ? response.output : [];
  const directItem = event.item && typeof event.item === "object" ? event.item : null;
  const topLevelItem = String(event.type ?? "") === "response.function_call_arguments.done"
    ? event
    : null;
  const calls: CompletedRealtimeToolCall[] = [];
  const seenCallIds = new Set<string>();

  for (const rawItem of [directItem, topLevelItem, ...output]) {
    if (!rawItem || typeof rawItem !== "object") continue;
    const item = rawItem as {
      type?: unknown;
      call_id?: unknown;
      name?: unknown;
      arguments?: unknown;
    };
    if (rawItem !== topLevelItem && String(item.type ?? "") !== "function_call") continue;
    const callId = String(item.call_id ?? "").trim();
    const name = String(item.name ?? "").trim();
    if (!callId || !name || seenCallIds.has(callId)) continue;
    const argumentsJson = typeof item.arguments === "string"
      ? item.arguments
      : JSON.stringify(item.arguments ?? {});
    seenCallIds.add(callId);
    calls.push({ callId, name, argumentsJson });
  }

  return calls;
}

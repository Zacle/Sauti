export type CompletedRealtimeToolCall = {
  callId: string;
  name: string;
  argumentsJson: string;
};

export type AuthorizedNextToolRequest = {
  name: string;
  argumentsJson: string;
};

export const SAUTI_REALTIME_REQUEST_ID = "sauti_request_id";
export const AUTHORITATIVE_TRANSCRIPT_PREFIX = "SAUTI_INPUT_TRANSCRIPT";

export function businessActionProgressInstruction(toolName: string) {
  const operation = toolName === "book_slot"
    ? "saving the appointment"
    : toolName === "check_availability"
      ? "checking the live schedule"
      : "completing the requested business action";
  return `The system is still ${operation} and the caller has been waiting longer than expected. `
    + "Give one brief, natural, professional progress update in the caller's current language. "
    + "Include a short apology for the wait and make clear that you are still working on it. "
    + "Do not claim success or failure, invent a result, repeat booking details, ask a question, or call a tool.";
}

export function realtimeTranscriptMirrorItem(transcript: string) {
  return {
    type: "message",
    role: "user",
    content: [{
      type: "input_text",
      text: `${AUTHORITATIVE_TRANSCRIPT_PREFIX}: Text mirror of the immediately preceding caller audio, not a new turn. Prefer it for exact names, digits, emails, dates, and times; combine it with audio for meaning. If it is incoherent or not a clear answer or choice, do not update state, reuse a stored choice, or call a business tool; ask for repetition.\n${transcript.trim()}`,
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

export function authorizedNextToolRequest(
  toolResult: Record<string, unknown>,
): AuthorizedNextToolRequest | null {
  if (toolResult.success !== true) return null;
  const payload = toolResult.result;
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return null;
  const values = payload as Record<string, unknown>;
  const name = typeof values.nextTool === "string" ? values.nextTool.trim() : "";
  const authorized = name === "book_slot" || values.nextToolAuthorized === true;
  if (!authorized || !/^[A-Za-z][A-Za-z0-9_]{1,63}$/.test(name)) return null;
  const rawArguments = values.nextToolArguments;
  const argumentsJson = values.nextToolAuthorized === true
    && rawArguments && typeof rawArguments === "object" && !Array.isArray(rawArguments)
    ? JSON.stringify(rawArguments)
    : "";
  return { name, argumentsJson };
}

export function callerGuidanceInstruction(
  toolName: string,
  toolResult: Record<string, unknown>,
): string {
  if (toolName !== "book_slot" || toolResult.success !== true) return "";
  const payload = toolResult.result;
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return "";
  const instruction = (payload as Record<string, unknown>).callerGuidanceInstruction;
  if (typeof instruction !== "string") return "";
  const value = instruction.trim();
  return value.length <= 1_200 ? value : "";
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

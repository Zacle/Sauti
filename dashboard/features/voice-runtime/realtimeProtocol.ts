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
export const SLOW_RESPONSE_PROGRESS_PURPOSE = "sauti_slow_response_progress";

const IMMEDIATE_TOOL_NAMES = new Set([
  "update_conversation_state",
  "get_business_hours",
  "end_call",
]);

/**
 * Tools outside this small protocol-only set may cross a network or wait on a
 * customer system. Progress is armed by capability instead of maintaining a
 * list of booking phrases, so configured CRM, payment, messaging, and custom
 * tools get the same caller experience.
 */
export function callerWaitExpected(toolName: string) {
  const normalized = toolName.trim().toLocaleLowerCase();
  return Boolean(normalized) && !IMMEDIATE_TOOL_NAMES.has(normalized);
}

export function slowResponseProgressRequest(requestId: string) {
  return {
    type: "response.create",
    response: {
      conversation: "none",
      instructions: "The main response to the caller is still being prepared and the caller has waited longer than expected. "
        + "Give one very brief, natural, professional progress update in the caller's current language. "
        + "Briefly apologize for the wait and say you are still working on their request. "
        + "Do not answer the request, claim success or failure, repeat details, ask a question, or call a tool.",
      tool_choice: "none",
      output_modalities: ["text"],
      metadata: {
        [SAUTI_REALTIME_REQUEST_ID]: requestId,
        purpose: SLOW_RESPONSE_PROGRESS_PURPOSE,
      },
    },
  };
}

export function isSlowResponseProgressEvent(
  event: Record<string, unknown>,
  knownResponseIds: ReadonlySet<string>,
) {
  const response = event.response as { id?: unknown; metadata?: Record<string, unknown> } | undefined;
  const responseId = String(event.response_id ?? response?.id ?? "").trim();
  return response?.metadata?.purpose === SLOW_RESPONSE_PROGRESS_PURPOSE
    || Boolean(responseId && knownResponseIds.has(responseId));
}

export function shouldRetrySlowResponse(progressOnDelay: boolean, alreadyRetried: boolean) {
  return progressOnDelay && !alreadyRetried;
}

export function confirmedEndCallResult(
  toolName: string,
  toolResult: Record<string, unknown>,
) {
  if (toolName !== "end_call" || toolResult.success !== true) return false;
  const payload = toolResult.result;
  return Boolean(payload && typeof payload === "object" && !Array.isArray(payload)
    && (payload as Record<string, unknown>).ended === true);
}

export function completedResponseText(event: Record<string, unknown>) {
  const response = event.response as { output?: unknown } | undefined;
  const output = Array.isArray(response?.output) ? response.output : [];
  const parts: string[] = [];
  for (const rawItem of output) {
    if (!rawItem || typeof rawItem !== "object") continue;
    const item = rawItem as { content?: unknown };
    const content = Array.isArray(item.content) ? item.content : [];
    for (const rawContent of content) {
      if (!rawContent || typeof rawContent !== "object") continue;
      const value = rawContent as { type?: unknown; text?: unknown; transcript?: unknown };
      const text = String(value.type ?? "") === "output_audio"
        ? String(value.transcript ?? "")
        : String(value.text ?? "");
      if (text.trim()) parts.push(text.trim());
    }
  }
  return parts.join("\n").trim();
}

/**
 * A Realtime response can be marked incomplete after it already emitted a
 * usable caller-facing sentence (for example, when a provider-side limit is
 * reached after the final text item). Do not turn that into a second response
 * and a false failure message. Phase-aware responses remain strict so private
 * commentary is never mistaken for the final answer.
 */
export function hasUsableCallerFacingResponse(
  event: Record<string, unknown>,
  streamedText: string,
  hasPhases: boolean,
  finalAnswerText: string,
) {
  const text = hasPhases
    ? finalAnswerText
    : streamedText.trim() || completedResponseText(event);
  return Boolean(text.trim());
}

/**
 * Server-authorized chained tools still need a native function-call item in
 * Realtime history before their function-call output is published. This keeps
 * the model grounded in the factual tool result instead of asking it to infer
 * what an invisible server-side operation returned.
 */
export function realtimeAuthorizedFunctionCallItem(
  callId: string,
  name: string,
  argumentsJson: string,
) {
  return {
    type: "function_call",
    status: "completed",
    call_id: callId,
    name,
    arguments: argumentsJson,
  };
}

export function businessActionProgressInstruction(toolName: string) {
  const operation = toolName === "book_slot"
    ? "saving the appointment"
    : toolName === "check_availability"
      ? "checking the live schedule"
      : toolName === "reschedule_booking"
        ? "rescheduling the appointment"
      : toolName === "cancel_booking"
        ? "cancelling the appointment"
      : "working on the caller's request";
  return `The system is still ${operation} and the caller has been waiting longer than expected. `
    + "Give one brief, natural, professional progress update in the caller's current language. "
    + "Include a short apology for the wait and make clear that you are still working on it. "
    + "Do not claim success or failure, invent a result, repeat booking details, ask a question, or call a tool.";
}

export function businessActionProgressRequest(toolName: string, requestId: string) {
  return {
    type: "response.create",
    response: {
      // A progress update is an out-of-band lifecycle notification, not an
      // assistant turn that waits for a caller reply.
      conversation: "none",
      instructions: businessActionProgressInstruction(toolName),
      tool_choice: "none",
      output_modalities: ["text"],
      metadata: {
        [SAUTI_REALTIME_REQUEST_ID]: requestId,
      },
    },
  };
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
  const authorized = values.nextToolAuthorized === true;
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

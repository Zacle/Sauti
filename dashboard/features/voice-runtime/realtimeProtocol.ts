export type CompletedRealtimeToolCall = {
  callId: string;
  name: string;
  argumentsJson: string;
};

export type AuthorizedNextToolRequest = {
  name: string;
  argumentsJson: string;
};

export type AiRecoverySpeechKind =
  | "provider_delay"
  | "provider_unavailable"
  | "request_failed"
  | "protocol_failed"
  | "tool_failed";

export const SAUTI_REALTIME_REQUEST_ID = "sauti_request_id";
export const AUTHORITATIVE_TRANSCRIPT_PREFIX = "SAUTI_INPUT_TRANSCRIPT";
export const REALTIME_CALL_ID_MAX_LENGTH = 32;
const DEFAULT_RATE_LIMIT_RETRY_DELAY_MS = 2_000;
const MAX_RATE_LIMIT_RETRY_DELAY_MS = 20_000;

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

export function ownsOriginatingToolResponse(
  originatingResponseId: string,
  activeResponseId: string,
  originatingGeneration: number,
  activeGeneration: number,
  responseActive: boolean,
  responseHasToolCall: boolean,
) {
  return Boolean(originatingResponseId)
    && originatingResponseId === activeResponseId
    && originatingGeneration === activeGeneration
    && responseActive
    && responseHasToolCall;
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

/**
 * OpenAI Realtime limits function call IDs to 32 characters. Chained tools are
 * created by Sauti rather than the provider, so use a compact opaque ID instead
 * of concatenating the parent ID and tool name.
 */
export function newRealtimeChainedCallId() {
  const random = globalThis.crypto?.randomUUID?.().replaceAll("-", "")
    ?? `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`.padEnd(29, "0");
  return `sc_${random.slice(0, REALTIME_CALL_ID_MAX_LENGTH - 3)}`;
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

export function protocolRecoveryResponseRequest(
  requiredToolName: string,
  requestId: string,
) {
  const normalizedToolName = /^[A-Za-z][A-Za-z0-9_]{1,63}$/.test(requiredToolName)
    ? requiredToolName
    : "";
  return {
    type: "response.create",
    response: {
      tool_choice: normalizedToolName
        ? { type: "function", name: normalizedToolName }
        : "none",
      metadata: {
        [SAUTI_REALTIME_REQUEST_ID]: requestId,
      },
    },
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
      // assistant turn that waits for a caller reply. Empty input and tools
      // prevent the full call prompt and tool catalog from consuming thousands
      // of tokens for one short, model-authored sentence.
      conversation: "none",
      input: [],
      instructions: businessActionProgressInstruction(toolName),
      tools: [],
      tool_choice: "none",
      output_modalities: ["text"],
      metadata: {
        [SAUTI_REALTIME_REQUEST_ID]: requestId,
      },
    },
  };
}

export function aiRecoverySpeechInstruction(
  kind: AiRecoverySpeechKind,
  language: string | undefined,
  toolName = "",
) {
  const situation = recoverySituation(kind, toolName);
  const targetLanguage = language?.trim() || "the caller's current language";
  return [
    "Write one brief, natural, professional caller-facing utterance for a live voice conversation.",
    `Respond in ${targetLanguage}.`,
    `Situation: ${situation}`,
    "Choose the wording yourself and vary it naturally.",
    "Do not mention a model, provider, API, token limit, internal system, error code, or tool name.",
    "Do not invent a result or imply that an action succeeded.",
    kind === "provider_delay"
      ? "Briefly acknowledge the wait, say that work is continuing, and do not ask a question."
      : "Briefly explain the outcome and give one clear next step. Ask at most one short question.",
    "Output only the words to say to the caller.",
  ].join(" ");
}

export function aiRecoverySpeechRequest(
  kind: AiRecoverySpeechKind,
  language: string | undefined,
  requestId: string,
  toolName = "",
) {
  return {
    type: "response.create",
    response: {
      // Recovery wording must remain available even when the main conversation
      // is near its TPM ceiling. Generate it from a tiny isolated context rather
      // than replaying the full prompt, history, and tool schemas.
      conversation: "none",
      input: [],
      instructions: aiRecoverySpeechInstruction(kind, language, toolName),
      tools: [],
      tool_choice: "none",
      output_modalities: ["text"],
      metadata: {
        [SAUTI_REALTIME_REQUEST_ID]: requestId,
      },
    },
  };
}

function recoverySituation(kind: AiRecoverySpeechKind, toolName: string) {
  if (kind === "provider_delay") {
    return "A previous response attempt was temporarily delayed. No outcome is known yet, and the accepted caller request is still being processed automatically.";
  }
  if (kind === "provider_unavailable") {
    return "The caller's requested response could not be completed after an automatic retry. No action should be claimed.";
  }
  if (kind === "protocol_failed") {
    return "The caller's request could not be interpreted into a valid response after automatic recovery. No side effect was confirmed.";
  }
  if (kind === "tool_failed") {
    const effect = toolName === "book_slot"
      ? "The requested booking was not saved."
      : toolName === "reschedule_booking" || toolName === "cancel_booking"
        ? "The existing booking remains unchanged."
        : toolName === "check_availability"
          ? "Live availability could not be confirmed and no booking was made."
          : "The requested operation did not complete and no side effect was confirmed.";
    return effect;
  }
  return "The caller's request did not complete, and no side effect was confirmed.";
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

/**
 * A recovery raised while processing response.done owns a provider-terminal
 * response and must release local queue ownership immediately. A recovery
 * raised by an unexpected streaming audio event must instead wait for the
 * requested provider cancellation to settle.
 */
export function releaseTerminalResponseForProtocolRecovery(cancelProviderResponse: boolean) {
  return !cancelProviderResponse;
}

/**
 * Realtime rate-limit failures include a provider-recommended retry delay in
 * status_details.error.message. Honor that delay instead of immediately
 * sending the same request into the same rolling rate-limit window.
 *
 * A zero result means this is not a rate-limit failure, or that the requested
 * wait is too long for a live caller. Callers should receive a truthful final
 * failure in that case instead of being left waiting indefinitely.
 */
export function realtimeRateLimitRetryDelayMs(event: Record<string, unknown>) {
  const response = asRecord(event.response);
  const statusDetails = asRecord(response.status_details);
  const error = asRecord(statusDetails.error);
  if (String(error.code ?? "").trim().toLocaleLowerCase() !== "rate_limit_exceeded") return 0;

  const message = String(error.message ?? "");
  const retryMilliseconds = Number(message.match(/try again in\s+(\d+(?:\.\d+)?)ms\b/iu)?.[1]);
  if (Number.isFinite(retryMilliseconds)) {
    const delayMs = Math.ceil(retryMilliseconds) + 250;
    return delayMs >= 500 && delayMs <= MAX_RATE_LIMIT_RETRY_DELAY_MS ? delayMs : 0;
  }
  const retrySeconds = Number(message.match(/try again in\s+(\d+(?:\.\d+)?)s\b/iu)?.[1]);
  if (!Number.isFinite(retrySeconds)) return DEFAULT_RATE_LIMIT_RETRY_DELAY_MS;

  const delayMs = Math.ceil(retrySeconds * 1_000) + 250;
  return delayMs >= 1_000 && delayMs <= MAX_RATE_LIMIT_RETRY_DELAY_MS ? delayMs : 0;
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

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

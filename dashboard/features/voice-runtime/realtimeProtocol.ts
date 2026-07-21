export type CompletedRealtimeToolCall = {
  callId: string;
  name: string;
  argumentsJson: string;
};

/**
 * Realtime may provide complete function arguments only in response.done.
 * Keep this parser transport-only and let the runtime's call-id guard make
 * execution idempotent when the dedicated arguments.done event also arrives.
 */
export function completedRealtimeToolCalls(
  event: Record<string, unknown>,
): CompletedRealtimeToolCall[] {
  const response = event.response as { output?: unknown } | undefined;
  const output = Array.isArray(response?.output) ? response.output : [];
  const calls: CompletedRealtimeToolCall[] = [];

  for (const rawItem of output) {
    if (!rawItem || typeof rawItem !== "object") continue;
    const item = rawItem as {
      type?: unknown;
      call_id?: unknown;
      name?: unknown;
      arguments?: unknown;
    };
    if (String(item.type ?? "") !== "function_call") continue;
    const callId = String(item.call_id ?? "").trim();
    const name = String(item.name ?? "").trim();
    if (!callId || !name) continue;
    const argumentsJson = typeof item.arguments === "string"
      ? item.arguments
      : JSON.stringify(item.arguments ?? {});
    calls.push({ callId, name, argumentsJson });
  }

  return calls;
}

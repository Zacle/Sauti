export type CompletedRealtimeToolCall = {
  callId: string;
  name: string;
  argumentsJson: string;
};

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

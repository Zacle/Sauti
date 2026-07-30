export const TELNYX_BROWSER_VAD = {
  volumeThreshold: 10,
  silenceDurationMs: 700,
  minSpeechDurationMs: 80,
  maxLatencyMs: 15_000,
  remoteSilenceThresholdMs: 1_000,
} as const;

export const TERMINAL_END_AFTER_DRAIN_MS = 2_500;
export const TERMINAL_END_FALLBACK_MS = 15_000;

export function terminalToolEndDelay({
  agentSpeaking,
}: {
  agentSpeaking: boolean;
  lastAgentStoppedAt: number;
  now: number;
}) {
  if (agentSpeaking) return TERMINAL_END_FALLBACK_MS;
  // The terminal tool contract requires invocation after the farewell has
  // finished. Telnyx can already report "thinking" by then, so a missing or
  // stale stopped-at timestamp must not keep a completed call alive for the
  // full fallback interval.
  return TERMINAL_END_AFTER_DRAIN_MS;
}

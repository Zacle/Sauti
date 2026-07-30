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
  lastAgentStoppedAt,
  now,
}: {
  agentSpeaking: boolean;
  lastAgentStoppedAt: number;
  now: number;
}) {
  if (agentSpeaking) return TERMINAL_END_FALLBACK_MS;
  if (lastAgentStoppedAt && now - lastAgentStoppedAt < 3_000) {
    return TERMINAL_END_AFTER_DRAIN_MS;
  }
  return TERMINAL_END_FALLBACK_MS;
}

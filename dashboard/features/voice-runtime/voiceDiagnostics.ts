export type VoiceDiagnosticLevel = "info" | "warn" | "error";

export type VoiceRuntimeDiagnostic = {
  component: "openai_realtime" | "cartesia_tts" | "test_call" | "vapi";
  event: string;
  level?: VoiceDiagnosticLevel;
  details?: Record<string, string | number | boolean | null>;
};

export type VoiceDiagnosticEntry = VoiceRuntimeDiagnostic & {
  sequence: number;
  occurredAt: string;
  elapsedMs: number;
  callId: string;
  runtime: string;
  status: string;
};

export function providerResponseDiagnosticDetails(event: Record<string, unknown>) {
  const response = asRecord(event.response);
  const statusDetails = asRecord(response.status_details);
  const providerError = asRecord(statusDetails.error);
  return compactDiagnosticDetails({
    providerStatus: stringValue(response.status),
    providerReason: diagnosticMessage(statusDetails.reason),
    providerErrorType: stringValue(providerError.type),
    providerErrorCode: stringValue(providerError.code),
    providerErrorParam: stringValue(providerError.param),
    providerErrorMessage: diagnosticMessage(providerError.message),
  });
}

export function providerErrorDiagnosticDetails(event: Record<string, unknown>) {
  const providerError = asRecord(event.error);
  return compactDiagnosticDetails({
    providerErrorType: stringValue(providerError.type),
    providerErrorCode: stringValue(providerError.code),
    providerErrorParam: stringValue(providerError.param),
    providerErrorMessage: diagnosticMessage(providerError.message),
  });
}

export function diagnosticMessage(value: unknown) {
  const text = stringValue(value);
  if (!text) return "";
  return text
    .replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi, "[email]")
    .replace(/\b(?:https?|wss?):\/\/\S+/gi, "[url]")
    .replace(/(?:\+?\d[\d\s().-]{6,}\d)/g, "[number]")
    .replace(/\b(bearer|token|api[_ -]?key)\s*[:=]?\s*\S+/gi, "$1 [redacted]")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 240);
}

export function compactDiagnosticDetails(
  details: Record<string, string | number | boolean | null | undefined>,
) {
  return Object.fromEntries(
    Object.entries(details).filter(([, value]) =>
      value !== undefined && value !== null && value !== ""
    ),
  ) as Record<string, string | number | boolean | null>;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function stringValue(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}

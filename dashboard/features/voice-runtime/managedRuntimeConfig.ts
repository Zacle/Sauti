export function configString(configuration: Record<string, unknown>, name: string) {
  const value = configuration[name];
  return typeof value === "string" ? value.trim() : "";
}

export function configStringArray(configuration: Record<string, unknown>, name: string) {
  const value = configuration[name];
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
    : [];
}

export function configPrimitives(configuration: Record<string, unknown>, name: string) {
  const value = configuration[name];
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  return Object.fromEntries(
    Object.entries(value).filter((entry): entry is [string, string | number | boolean] =>
      ["string", "number", "boolean"].includes(typeof entry[1])
    ),
  );
}

export function providerError(provider: string, error: unknown, context?: unknown) {
  const message = error instanceof Error
    ? error.message.trim()
    : typeof error === "string"
      ? error.trim()
      : "";
  if (message) {
    const safeMessage = sanitizeProviderText(message);
    const safeContext = providerErrorContext(context);
    return `${provider}: ${safeMessage}${safeContext ? ` (${safeContext})` : ""}`.slice(0, 320);
  }
  return `${provider} ended the voice session unexpectedly.`;
}

function providerErrorContext(context: unknown) {
  if (!context || typeof context !== "object" || Array.isArray(context)) return "";
  const values = context as Record<string, unknown>;
  const parts: string[] = [];
  for (const [label, field] of [
    ["type", "errorType"],
    ["code", "code"],
    ["debug", "debugMessage"],
    ["details", "details"],
  ] as const) {
    const value = values[field];
    if (typeof value !== "string" && typeof value !== "number") continue;
    const safe = sanitizeProviderText(String(value));
    if (safe) parts.push(`${label}=${safe}`);
  }
  return parts.join(", ").slice(0, 220);
}

function sanitizeProviderText(value: string) {
  return value
    .replace(/\b(?:https?|wss?):\/\/\S+/gi, "[provider endpoint]")
    .replace(/\b(bearer|token|api[_ -]?key)\s*[:=]?\s*\S+/gi, "$1 [redacted]")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 240);
}

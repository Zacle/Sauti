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

export function providerError(provider: string, error: unknown) {
  const message = error instanceof Error
    ? error.message.trim()
    : typeof error === "string"
      ? error.trim()
      : "";
  if (message) {
    const safe = message
      .replace(/\b(?:https?|wss?):\/\/\S+/gi, "[provider endpoint]")
      .replace(/\b(bearer|token|api[_ -]?key)\s*[:=]?\s*\S+/gi, "$1 [redacted]")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 240);
    return `${provider}: ${safe}`;
  }
  return `${provider} ended the voice session unexpectedly.`;
}

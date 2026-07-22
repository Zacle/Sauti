export function vapiErrorMessage(error: unknown): string {
  return nestedMessage(error, new Set(), 0)
    ?? "The Vapi voice session encountered an error.";
}

function nestedMessage(value: unknown, seen: Set<object>, depth: number): string | undefined {
  if (depth > 5) return undefined;
  if (value instanceof Error && value.message.trim()) return value.message.trim();
  if (typeof value === "string" && value.trim()) return value.trim();
  if (!value || typeof value !== "object" || seen.has(value)) return undefined;

  seen.add(value);
  const candidate = value as Record<string, unknown>;
  for (const key of ["message", "error", "data", "body", "response", "cause"]) {
    const message = nestedMessage(candidate[key], seen, depth + 1);
    if (message) return message;
  }
  return undefined;
}

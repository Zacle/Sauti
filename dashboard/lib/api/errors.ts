/**
 * Turns failed requests into messages people can act on. Raw server text such
 * as "Internal Server Error", "No message available" or a stack trace never
 * reaches the screen; the user gets what happened and what to do next.
 */

export const NETWORK_ERROR_MESSAGE = "We couldn’t reach Sauti. Check your internet connection and try again.";

const GENERIC_SERVER_PHRASES = new Set([
  "bad request",
  "unauthorized",
  "forbidden",
  "not found",
  "method not allowed",
  "conflict",
  "payload too large",
  "too many requests",
  "internal server error",
  "bad gateway",
  "service unavailable",
  "gateway timeout",
  "no message available",
  "error",
]);

export function friendlyStatusMessage(status: number): string {
  if (status === 0) return NETWORK_ERROR_MESSAGE;
  if (status === 400 || status === 422) return "Some of the details weren’t accepted. Check the highlighted fields and try again.";
  if (status === 401) return "Your session has expired. Please log in again.";
  if (status === 403) return "You don’t have permission to do that. Ask a workspace owner if you need access.";
  if (status === 404) return "We couldn’t find that. It may have been moved or deleted.";
  if (status === 408 || status === 504) return "This is taking longer than expected. Please try again.";
  if (status === 409) return "This was changed somewhere else. Refresh to see the latest version, then try again.";
  if (status === 413) return "That file is too large. Try a smaller one.";
  if (status === 429) return "Too many requests in a short time. Wait a moment and try again.";
  if (status === 503) return "Sauti is briefly unavailable, usually for maintenance. Please try again in a minute.";
  if (status >= 500) return "Something went wrong on our side. Please try again in a moment.";
  return "Something went wrong. Please try again.";
}

/** True when a server-provided message is safe and useful to show as-is. */
export function isReadableServerMessage(message: unknown): message is string {
  if (typeof message !== "string") return false;
  const text = message.trim();
  if (text.length < 3 || text.length > 300) return false;
  if (GENERIC_SERVER_PHRASES.has(text.toLowerCase().replace(/[.!]$/, ""))) return false;
  if (/^request failed/i.test(text)) return false;
  // Looks like code, markup or a stack trace rather than a sentence.
  if (/<\/?[a-z][\s\S]*>|\bat [\w.$]+\(|Exception\b|^\s*[{[]|java\.|org\.springframework|SQL/i.test(text)) return false;
  return true;
}

/** Picks the message for a failed response body (already parsed JSON or null). */
export function messageForFailedResponse(status: number, body: unknown): string {
  // Server errors are never shown verbatim: they are rarely meant for people.
  if (status >= 500) return friendlyStatusMessage(status);
  if (body && typeof body === "object") {
    const record = body as Record<string, unknown>;
    for (const key of ["detail", "message", "error"] as const) {
      if (isReadableServerMessage(record[key])) return (record[key] as string).trim();
    }
  }
  return friendlyStatusMessage(status);
}

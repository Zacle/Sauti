export type TelnyxReadinessSubscription = (handlers: {
  ready(): void;
  failed(error: unknown): void;
  disconnected(): void;
}) => () => void;

type StartTelnyxConversationOptions = {
  connect(): Promise<void>;
  startConversation(): Promise<void>;
  subscribe: TelnyxReadinessSubscription;
  timeoutMs?: number;
};

type TelnyxAuthenticationRetryOptions = {
  start(): Promise<void>;
  clearReconnectToken(): void;
  onRetry?(attempt: number): void;
  retryDelaysMs?: readonly number[];
  wait?(delayMs: number): Promise<void>;
};

export async function startTelnyxConversationWhenReady({
  connect,
  startConversation,
  subscribe,
  timeoutMs = 15_000,
}: StartTelnyxConversationOptions): Promise<void> {
  const timeout = { id: undefined as ReturnType<typeof setTimeout> | undefined };
  let settled = false;
  let resolveReady!: () => void;
  let rejectReady!: (error: Error) => void;
  const ready = new Promise<void>((resolve, reject) => {
    resolveReady = resolve;
    rejectReady = reject;
  });
  const settle = (error?: unknown) => {
    if (settled) return;
    settled = true;
    if (timeout.id !== undefined) clearTimeout(timeout.id);
    if (error === undefined) {
      resolveReady();
      return;
    }
    rejectReady(error instanceof Error ? error : new Error(String(error)));
  };
  const unsubscribe = subscribe({
    ready: () => settle(),
    failed: (error) => settle(error),
    disconnected: () => settle(new Error("Telnyx disconnected before it became ready.")),
  });
  timeout.id = setTimeout(
    () => settle(new Error("Telnyx did not become ready before the connection timeout.")),
    timeoutMs,
  );

  try {
    await Promise.all([connect(), ready]);
    await startConversation();
  } finally {
    if (timeout.id !== undefined) clearTimeout(timeout.id);
    unsubscribe();
  }
}

export async function startTelnyxConversationWithAuthenticationRetry({
  start,
  clearReconnectToken,
  onRetry,
  retryDelaysMs = [500, 1_500],
  wait = (delayMs) => new Promise((resolve) => setTimeout(resolve, delayMs)),
}: TelnyxAuthenticationRetryOptions): Promise<void> {
  for (let attempt = 0; ; attempt += 1) {
    try {
      await start();
      return;
    } catch (error) {
      const retryDelay = retryDelaysMs[attempt];
      if (retryDelay === undefined || !isTelnyxAuthenticationFailure(error)) throw error;
      // Telnyx documents this reset for transient "Login Incorrect" failures
      // caused by assistant/version propagation lag between RTC edges.
      clearReconnectToken();
      onRetry?.(attempt + 1);
      await wait(retryDelay);
    }
  }
}

export function isTelnyxAuthenticationFailure(error: unknown): boolean {
  if (typeof error === "string") return /authentication failed|login incorrect/i.test(error);
  if (!error || typeof error !== "object") return false;
  const candidate = error as {
    code?: unknown;
    message?: unknown;
    name?: unknown;
    originalError?: unknown;
  };
  if (candidate.code === 46001 || String(candidate.code ?? "") === "46001") return true;
  const summary = [candidate.name, candidate.message]
    .filter((value): value is string => typeof value === "string")
    .join(" ");
  return /authentication failed|login incorrect/i.test(summary)
    || (candidate.originalError !== undefined
      && candidate.originalError !== error
      && isTelnyxAuthenticationFailure(candidate.originalError));
}

export type TelnyxReadinessSubscription = (handlers: {
  ready(): void;
  failed(error: unknown): void;
  disconnected(): void;
}) => () => void;

export type TelnyxConversationSubscription = (handlers: {
  active(): void;
  failed(error: unknown): void;
  disconnected(): void;
}) => () => void;

type StartTelnyxConversationOptions = {
  connect(): Promise<void>;
  startConversation(): Promise<void>;
  subscribe: TelnyxReadinessSubscription;
  subscribeConversation?: TelnyxConversationSubscription;
  timeoutMs?: number;
  conversationTimeoutMs?: number;
};

type TelnyxAuthenticationRetryOptions = {
  start(): Promise<void>;
  clearReconnectToken(): void;
  onRetry?(attempt: number): void;
  resetConversation?(): Promise<void>;
  onConversationRetry?(attempt: number): void;
  retryDelaysMs?: readonly number[];
  conversationRetryDelaysMs?: readonly number[];
  wait?(delayMs: number): Promise<void>;
};

export async function startTelnyxConversationWhenReady({
  connect,
  startConversation,
  subscribe,
  subscribeConversation,
  timeoutMs = 15_000,
  conversationTimeoutMs = 5_000,
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
    if (!subscribeConversation) {
      await startConversation();
      return;
    }
    let conversationSettled = false;
    let resolveConversation!: () => void;
    let rejectConversation!: (error: Error) => void;
    const conversationReady = new Promise<void>((resolve, reject) => {
      resolveConversation = resolve;
      rejectConversation = reject;
    });
    const settleConversation = (error?: unknown) => {
      if (conversationSettled) return;
      conversationSettled = true;
      if (error === undefined) {
        resolveConversation();
        return;
      }
      rejectConversation(error instanceof Error ? error : new Error(String(error)));
    };
    const unsubscribeConversation = subscribeConversation({
      active: () => settleConversation(),
      failed: (error) => settleConversation(error),
      disconnected: () => settleConversation(
        new Error("Telnyx disconnected before the conversation became active."),
      ),
    });
    const conversationTimeout = setTimeout(
      () => settleConversation(
        new Error("Telnyx signaling connected, but the conversation did not become active."),
      ),
      conversationTimeoutMs,
    );
    try {
      await startConversation();
      await conversationReady;
    } finally {
      clearTimeout(conversationTimeout);
      unsubscribeConversation();
    }
  } finally {
    if (timeout.id !== undefined) clearTimeout(timeout.id);
    unsubscribe();
  }
}

export async function startTelnyxConversationWithAuthenticationRetry({
  start,
  clearReconnectToken,
  onRetry,
  resetConversation,
  onConversationRetry,
  retryDelaysMs = [500, 1_500, 3_000, 5_000],
  conversationRetryDelaysMs = [500],
  wait = (delayMs) => new Promise((resolve) => setTimeout(resolve, delayMs)),
}: TelnyxAuthenticationRetryOptions): Promise<void> {
  let authenticationAttempt = 0;
  let conversationAttempt = 0;
  for (;;) {
    try {
      await start();
      return;
    } catch (error) {
      if (isTelnyxAuthenticationFailure(error)) {
        const retryDelay = retryDelaysMs[authenticationAttempt];
        if (retryDelay === undefined) {
          throw new Error(
            "Telnyx could not establish the voice session after retrying. "
            + "This is separate from your Sauti dashboard session; please try the call again shortly.",
          );
        }
        authenticationAttempt += 1;
        clearReconnectToken();
        onRetry?.(authenticationAttempt);
        await wait(retryDelay);
        continue;
      }
      if (!isTelnyxConversationStartupFailure(error)) throw error;
      const retryDelay = conversationRetryDelaysMs[conversationAttempt];
      if (retryDelay === undefined) {
        throw new Error(
          "Telnyx connected to signaling but did not establish conversation audio after retrying.",
        );
      }
      conversationAttempt += 1;
      clearReconnectToken();
      await resetConversation?.();
      onConversationRetry?.(conversationAttempt);
      await wait(retryDelay);
    }
  }
}

export function isTelnyxConversationStartupFailure(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? "");
  return /conversation did not become active/i.test(message);
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

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

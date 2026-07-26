import type { BrowserVoiceRuntimeSession } from "@/types/api";

export type BrowserVoiceRuntimeCallbacks = {
  onConnected(): void;
  onCallerSpeechStarted(): void;
  onCallerSpeechEnded(): void;
  onCallerTranscript(text: string): void;
  onAgentCaption(text: string, turn?: number): void;
  onAgentTranscript(text: string, interrupted: boolean): void;
  onAgentSpeaking(value: boolean): void;
  onInterrupted(): void;
  onToolInvoked?(toolName: string): void;
  onToolCompleted?(toolName: string, isError: boolean): void;
  onToolError?(toolName: string, reason: string): void;
  onError(message: string): void;
  onEnded(outcome?: string): void;
};

export type BrowserVoiceRuntimeConnection = {
  sendUserText(text: string): void;
  providerCallControlId(): string;
  stop(): Promise<void>;
};

export function connectBrowserVoiceRuntime(
  session: BrowserVoiceRuntimeSession,
  callbacks: BrowserVoiceRuntimeCallbacks,
): Promise<BrowserVoiceRuntimeConnection> {
  if (session.provider.toLowerCase() !== "telnyx") {
    throw new Error(`Unsupported browser voice runtime: ${session.provider}`);
  }
  return import("./telnyxRuntime").then(({ connectTelnyxRuntime }) =>
    connectTelnyxRuntime(session, callbacks)
  );
}

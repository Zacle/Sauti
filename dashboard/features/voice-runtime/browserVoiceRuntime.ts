import type { BrowserVoiceRuntimeSession } from "@/types/api";
import { connectVapiRuntime } from "./vapiRuntime";

export type BrowserVoiceRuntimeCallbacks = {
  onConnected(): void;
  onCallerSpeechStarted(): void;
  onCallerSpeechEnded(): void;
  onCallerTranscript(text: string): void;
  onAgentCaption(text: string, turn?: number): void;
  onAgentTranscript(text: string, interrupted: boolean): void;
  onAgentSpeaking(value: boolean): void;
  onInterrupted(): void;
  onError(message: string): void;
  onEnded(outcome?: string): void;
};

export type BrowserVoiceRuntimeConnection = {
  sendUserText(text: string): void;
  stop(): Promise<void>;
};

export function connectBrowserVoiceRuntime(
  session: BrowserVoiceRuntimeSession,
  callbacks: BrowserVoiceRuntimeCallbacks,
): Promise<BrowserVoiceRuntimeConnection> {
  switch (session.provider.toLowerCase()) {
    case "vapi":
      return connectVapiRuntime(session, callbacks);
    default:
      throw new Error(`Unsupported browser voice runtime: ${session.provider}`);
  }
}

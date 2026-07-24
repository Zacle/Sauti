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
  executeTool(toolCallId: string, name: string, argumentsJson: string): Promise<Record<string, unknown>>;
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
      return import("./vapiRuntime").then(({ connectVapiRuntime }) =>
        connectVapiRuntime(session, callbacks)
      );
    case "retell":
      return import("./retellRuntime").then(({ connectRetellRuntime }) =>
        connectRetellRuntime(session, callbacks)
      );
    case "elevenlabs":
      return import("./elevenLabsRuntime").then(({ connectElevenLabsRuntime }) =>
        connectElevenLabsRuntime(session, callbacks)
      );
    case "telnyx":
      return import("./telnyxRuntime").then(({ connectTelnyxRuntime }) =>
        connectTelnyxRuntime(session, callbacks)
      );
    default:
      throw new Error(`Unsupported browser voice runtime: ${session.provider}`);
  }
}

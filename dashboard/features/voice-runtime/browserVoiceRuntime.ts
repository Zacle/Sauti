import type { BrowserVoiceRuntimeSession } from "@/types/api";

export type BrowserVoiceRuntimeCallbacks = {
  onStartupStage?(
    stage: "sdk_loaded" | "authentication_retry" | "signaling_ready" | "conversation_started",
    details?: Record<string, string | number | boolean | null>,
  ): void;
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
  onProviderCallControlId?(callControlId: string): void;
  onProviderCallLegId?(callLegId: string): void;
  onError(message: string): void;
  onEnded(outcome?: string): void;
};

export type BrowserVoiceRuntimeConnection = {
  sendUserText(text: string): void;
  providerCallControlId(): string;
  providerCallLegId(): string;
  stop(): Promise<void>;
};

type TelnyxRuntimeModule = typeof import("./telnyxRuntime");

let telnyxRuntimeModule: Promise<TelnyxRuntimeModule> | undefined;

function loadTelnyxRuntime() {
  telnyxRuntimeModule ??= import("./telnyxRuntime").catch((error) => {
    telnyxRuntimeModule = undefined;
    throw error;
  });
  return telnyxRuntimeModule;
}

export function preloadBrowserVoiceRuntime() {
  return loadTelnyxRuntime().then(() => undefined);
}

export async function warmBrowserMicrophone() {
  if (!navigator.mediaDevices?.getUserMedia) return;
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
    },
  });
  stream.getTracks().forEach((track) => track.stop());
}

export function connectBrowserVoiceRuntime(
  session: BrowserVoiceRuntimeSession,
  callbacks: BrowserVoiceRuntimeCallbacks,
): Promise<BrowserVoiceRuntimeConnection> {
  if (session.provider.toLowerCase() !== "telnyx") {
    throw new Error(`Unsupported browser voice runtime: ${session.provider}`);
  }
  return loadTelnyxRuntime().then(({ connectTelnyxRuntime }) =>
    connectTelnyxRuntime(session, callbacks)
  );
}

import type { BrowserVoiceRuntimeSession } from "@/types/api";

export type BrowserVoiceRuntimeCallbacks = {
  onStartupStage?(
    stage:
      | "sdk_loaded"
      | "authentication_retry"
      | "signaling_ready"
      | "conversation_retry"
      | "conversation_active"
      | "remote_audio_ready"
      | "conversation_started",
    details?: Record<string, string | number | boolean | null>,
  ): void;
  onConnected(): void;
  onCallerSpeechStarted(): void;
  onCallerSpeechEnded(): void;
  onCallerTranscript(text: string): void;
  onAgentCaption(text: string, turn?: number): void;
  onAgentTranscript(text: string, interrupted: boolean): void;
  onAgentSpeaking(value: boolean): void;
  onLatencyMeasured?(kind: "greeting" | "turn", latencyMs: number): void;
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

export type BrowserMicrophone = {
  deviceId: string;
  label: string;
};

export type BrowserMicrophoneSnapshot = {
  deviceId: string;
  label: string;
  autoGainControl: boolean | null;
  echoCancellation: boolean | null;
  noiseSuppression: boolean | null;
  channelCount: number | null;
  sampleRate: number | null;
};

export type BrowserVoiceRuntimeOptions = {
  microphoneDeviceId?: string;
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
  return loadTelnyxRuntime().then(({ preloadTelnyxRuntime }) => preloadTelnyxRuntime());
}

export function preconnectBrowserVoiceRuntime(session: BrowserVoiceRuntimeSession) {
  if (session.provider.toLowerCase() !== "telnyx") {
    throw new Error(`Unsupported browser voice runtime: ${session.provider}`);
  }
  return loadTelnyxRuntime().then(({ preconnectTelnyxRuntime }) =>
    preconnectTelnyxRuntime(session)
  );
}

export function releasePreconnectedBrowserVoiceRuntime() {
  return loadTelnyxRuntime().then(({ releasePreconnectedTelnyxRuntime }) =>
    releasePreconnectedTelnyxRuntime()
  );
}

export function browserMicrophoneConstraints(
  microphoneDeviceId?: string,
): MediaTrackConstraints {
  return {
    ...(microphoneDeviceId
      ? { deviceId: { exact: microphoneDeviceId } }
      : {}),
    echoCancellation: true,
    noiseSuppression: true,
    autoGainControl: true,
    channelCount: { ideal: 1 },
  };
}

export async function listBrowserMicrophones(): Promise<BrowserMicrophone[]> {
  if (!navigator.mediaDevices?.enumerateDevices) return [];
  const devices = await navigator.mediaDevices.enumerateDevices();
  return devices
    .filter((device) => device.kind === "audioinput")
    .map((device, index) => ({
      deviceId: device.deviceId,
      label: device.label || `Microphone ${index + 1}`,
    }));
}

export async function warmBrowserMicrophone(
  microphoneDeviceId?: string,
): Promise<BrowserMicrophoneSnapshot | undefined> {
  if (!navigator.mediaDevices?.getUserMedia) return undefined;
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: browserMicrophoneConstraints(microphoneDeviceId),
  });
  const track = stream.getAudioTracks()[0];
  const settings = track?.getSettings();
  const snapshot = track && settings
    ? {
        deviceId: settings.deviceId ?? microphoneDeviceId ?? "",
        label: track.label || "Default microphone",
        autoGainControl: settings.autoGainControl ?? null,
        echoCancellation: settings.echoCancellation ?? null,
        noiseSuppression: settings.noiseSuppression ?? null,
        channelCount: settings.channelCount ?? null,
        sampleRate: settings.sampleRate ?? null,
      }
    : undefined;
  stream.getTracks().forEach((item) => item.stop());
  return snapshot;
}

export function connectBrowserVoiceRuntime(
  session: BrowserVoiceRuntimeSession,
  callbacks: BrowserVoiceRuntimeCallbacks,
  options: BrowserVoiceRuntimeOptions = {},
): Promise<BrowserVoiceRuntimeConnection> {
  if (session.provider.toLowerCase() !== "telnyx") {
    throw new Error(`Unsupported browser voice runtime: ${session.provider}`);
  }
  return loadTelnyxRuntime().then(({ connectTelnyxRuntime }) =>
    connectTelnyxRuntime(session, callbacks, options)
  );
}

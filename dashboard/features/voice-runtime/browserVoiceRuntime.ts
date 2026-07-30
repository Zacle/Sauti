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
  onMicrophoneLevel?(rmsDb: number, peakDb: number): void;
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

export type BrowserMicrophoneSnapshot = {
  label: string;
  autoGainControl: boolean | null;
  echoCancellation: boolean | null;
  noiseSuppression: boolean | null;
  channelCount: number | null;
  sampleRate: number | null;
  appliedGainDb: number;
};

export type BrowserMicrophoneCapture = {
  stream: MediaStream;
  snapshot: BrowserMicrophoneSnapshot;
  readLevel(): { rmsDb: number; peakDb: number };
  stop(): Promise<void>;
};

export type BrowserVoiceRuntimeOptions = {
  microphone?: BrowserMicrophoneCapture;
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

export function browserMicrophoneConstraints(): MediaTrackConstraints {
  return {
    echoCancellation: true,
    noiseSuppression: true,
    autoGainControl: true,
    channelCount: { ideal: 1 },
  };
}

export async function prepareBrowserMicrophone(): Promise<BrowserMicrophoneCapture> {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new Error("This browser cannot access a microphone.");
  }
  const sourceStream = await navigator.mediaDevices.getUserMedia({
    audio: browserMicrophoneConstraints(),
  });
  const track = sourceStream.getAudioTracks()[0];
  const settings = track?.getSettings();
  if (!track || !settings) {
    sourceStream.getTracks().forEach((item) => item.stop());
    throw new Error("The browser did not return a usable microphone track.");
  }

  const AudioContextClass = window.AudioContext;
  const context = new AudioContextClass({ latencyHint: "interactive" });
  const source = context.createMediaStreamSource(sourceStream);
  const gain = context.createGain();
  const compressor = context.createDynamicsCompressor();
  const analyser = context.createAnalyser();
  const destination = context.createMediaStreamDestination();
  const gainFactor = 1.5;
  gain.gain.value = gainFactor;
  compressor.threshold.value = -14;
  compressor.knee.value = 10;
  compressor.ratio.value = 8;
  compressor.attack.value = 0.003;
  compressor.release.value = 0.18;
  analyser.fftSize = 2048;
  source.connect(analyser);
  source.connect(gain);
  gain.connect(compressor);
  compressor.connect(destination);
  await context.resume();
  const samples = new Float32Array(analyser.fftSize);
  let stopped = false;

  return {
    stream: destination.stream,
    snapshot: {
      label: track.label || "Default microphone",
      autoGainControl: settings.autoGainControl ?? null,
      echoCancellation: settings.echoCancellation ?? null,
      noiseSuppression: settings.noiseSuppression ?? null,
      channelCount: settings.channelCount ?? null,
      sampleRate: settings.sampleRate ?? null,
      appliedGainDb: Math.round(20 * Math.log10(gainFactor) * 10) / 10,
    },
    readLevel() {
      analyser.getFloatTimeDomainData(samples);
      let sumSquares = 0;
      let peak = 0;
      for (const sample of samples) {
        sumSquares += sample * sample;
        peak = Math.max(peak, Math.abs(sample));
      }
      const rms = Math.sqrt(sumSquares / samples.length);
      const toDb = (value: number) =>
        Math.round(20 * Math.log10(Math.max(value, 0.00001)) * 10) / 10;
      return { rmsDb: toDb(rms), peakDb: toDb(peak) };
    },
    async stop() {
      if (stopped) return;
      stopped = true;
      sourceStream.getTracks().forEach((item) => item.stop());
      destination.stream.getTracks().forEach((item) => item.stop());
      source.disconnect();
      gain.disconnect();
      compressor.disconnect();
      analyser.disconnect();
      await context.close().catch(() => undefined);
    },
  };
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

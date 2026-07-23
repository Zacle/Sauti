import Cartesia from "@cartesia/cartesia-js";
import { PCMPlayer } from "@speechmatics/web-pcm-player";
import {
  compactDiagnosticDetails,
  diagnosticMessage,
  type VoiceRuntimeDiagnostic,
} from "./voiceDiagnostics.ts";

export type BrowserAgentSpeech = {
  id: string;
  generation: number;
  text: string;
};

type BrowserTtsConfiguration = {
  clientToken: string;
  voiceId: string;
  modelId: string;
};

type CartesiaBrowserTtsCallbacks = {
  onPlaybackStarted: (speech: BrowserAgentSpeech) => void;
  onPlaybackEnded: (speech: BrowserAgentSpeech) => void;
  onError: (message: string) => void;
  onDiagnostic?: (diagnostic: VoiceRuntimeDiagnostic) => void;
};

const SUPPORTED_SAMPLE_RATES = [8000, 16000, 22050, 24000, 44100, 48000] as const;
type SupportedSampleRate = (typeof SUPPORTED_SAMPLE_RATES)[number];

/**
 * Browser-owned Cartesia playback.
 *
 * Cartesia's maintained SDK owns the provider WebSocket/context protocol and
 * Speechmatics' PCMPlayer owns Web Audio scheduling. Sauti only coordinates
 * utterance order, interruption, captions, and lifecycle callbacks.
 */
export class CartesiaBrowserTts {
  private readonly audioContext: AudioContext;
  private readonly configuration: BrowserTtsConfiguration;
  private readonly language: string;
  private readonly callbacks: CartesiaBrowserTtsCallbacks;
  private readonly player: PCMPlayer;
  private readonly client: Cartesia;
  private socket: Awaited<ReturnType<Cartesia["tts"]["websocket"]>> | null = null;
  private readonly pending: BrowserAgentSpeech[] = [];
  private processing = false;
  private closed = false;
  private epoch = 0;
  private contextId = "";
  private scheduledUntil = 0;
  private drainTimer = 0;
  private drainResolve: (() => void) | null = null;

  private constructor(
    audioContext: AudioContext,
    configuration: BrowserTtsConfiguration,
    language: string,
    callbacks: CartesiaBrowserTtsCallbacks,
    recordingDestination?: MediaStreamAudioDestinationNode | null,
  ) {
    this.audioContext = audioContext;
    this.configuration = configuration;
    this.language = language;
    this.callbacks = callbacks;
    this.player = new PCMPlayer(audioContext);
    if (recordingDestination) this.player.analyser.connect(recordingDestination);
    this.client = new Cartesia({ token: configuration.clientToken, maxRetries: 1, timeout: 10_000 });
  }

  static async connect(options: {
    audioContext: AudioContext;
    configuration: BrowserTtsConfiguration;
    language: string;
    callbacks: CartesiaBrowserTtsCallbacks;
    recordingDestination?: MediaStreamAudioDestinationNode | null;
  }) {
    const connectionStartedAt = performance.now();
    const connection = new CartesiaBrowserTts(
      options.audioContext,
      options.configuration,
      options.language,
      options.callbacks,
      options.recordingDestination,
    );
    connection.diagnostic("connection_started", {
      sampleRate: options.audioContext.sampleRate,
      modelId: options.configuration.modelId,
    });
    try {
      connection.socket = await connection.client.tts.websocket();
      await connection.socket.connect();
      connection.diagnostic("connection_ready", {
        durationMs: Math.round(performance.now() - connectionStartedAt),
      });
      return connection;
    } catch (caught) {
      connection.diagnostic("connection_failed", {
        durationMs: Math.round(performance.now() - connectionStartedAt),
        message: diagnosticMessage(caught instanceof Error ? caught.message : caught),
      }, "error");
      throw caught;
    }
  }

  speak(speech: BrowserAgentSpeech) {
    if (this.closed || !speech.text.trim()) return;
    this.pending.push(speech);
    this.diagnostic("speech_queued", {
      speechId: speech.id,
      generation: speech.generation,
      textChars: speech.text.length,
      queueDepth: this.pending.length,
    });
    void this.process();
  }

  interrupt() {
    this.diagnostic("playback_interrupted", {
      contextActive: Boolean(this.contextId),
      queuedSpeech: this.pending.length,
      epoch: this.epoch,
    }, "warn");
    this.epoch += 1;
    this.pending.length = 0;
    this.finishDrainWait();
    this.player.interrupt();
    this.scheduledUntil = this.audioContext.currentTime;
    const contextId = this.contextId;
    this.contextId = "";
    if (contextId && this.socket) void this.socket.cancelContext(contextId).catch(() => undefined);
  }

  close() {
    if (this.closed) return;
    this.diagnostic("connection_closed", {
      contextActive: Boolean(this.contextId),
      queuedSpeech: this.pending.length,
    });
    this.closed = true;
    this.interrupt();
    this.socket?.close({ code: 1000, reason: "Browser voice session ended" });
    this.socket = null;
    try {
      this.player.analyser.disconnect();
    } catch {
      // The recording graph may already have been disconnected.
    }
  }

  private async process() {
    if (this.processing || this.closed) return;
    this.processing = true;
    try {
      while (!this.closed && this.pending.length > 0) {
        const speech = this.pending.shift();
        if (speech) await this.playSpeech(speech);
      }
    } finally {
      this.processing = false;
      if (!this.closed && this.pending.length > 0) void this.process();
    }
  }

  private async playSpeech(speech: BrowserAgentSpeech) {
    const socket = this.socket;
    if (!socket) throw new Error("The Cartesia browser voice connection is not open.");
    const epoch = this.epoch;
    const contextId = crypto.randomUUID();
    this.contextId = contextId;
    let started = false;
    let pendingBytes = new Uint8Array(0);
    let chunks = 0;
    let audioBytes = 0;
    const synthesisStartedAt = performance.now();

    try {
      this.diagnostic("synthesis_started", {
        speechId: speech.id,
        generation: speech.generation,
        textChars: speech.text.length,
        contextId,
      });
      const events = socket.generate({
        context_id: contextId,
        model_id: this.configuration.modelId,
        transcript: speech.text,
        voice: { mode: "id", id: this.configuration.voiceId },
        language: normalizedCartesiaLanguage(this.language),
        output_format: {
          container: "raw",
          encoding: "pcm_s16le",
          sample_rate: supportedCartesiaSampleRate(this.audioContext.sampleRate),
        },
        max_buffer_delay_ms: 0,
        continue: false,
      });
      for await (const event of events) {
        if (this.closed || epoch !== this.epoch) return;
        if (event.type === "error") throw new Error(event.message || "Cartesia could not synthesize the response.");
        if (event.type !== "chunk") continue;
        const bytes = event.audio ?? decodeBase64(event.data);
        const aligned = appendAlignedPcm16(pendingBytes, bytes);
        pendingBytes = aligned.remainder;
        if (aligned.samples.length === 0) continue;
        chunks += 1;
        audioBytes += bytes.length;
        if (!started) {
          started = true;
          this.scheduledUntil = Math.max(this.audioContext.currentTime, this.scheduledUntil);
          this.diagnostic("first_audio_received", {
            speechId: speech.id,
            generation: speech.generation,
            contextId,
            synthesisToFirstAudioMs: Math.round(performance.now() - synthesisStartedAt),
            firstChunkBytes: bytes.length,
          });
          this.callbacks.onPlaybackStarted(speech);
        }
        this.player.playAudio(aligned.samples);
        this.scheduledUntil += aligned.samples.length / this.audioContext.sampleRate;
      }
      if (pendingBytes.length > 0) {
        throw new Error("Cartesia returned an incomplete PCM audio sample.");
      }
      if (!started || this.closed || epoch !== this.epoch) return;
      await this.waitUntilDrained(epoch);
      if (!this.closed && epoch === this.epoch) {
        this.diagnostic("playback_completed", {
          speechId: speech.id,
          generation: speech.generation,
          contextId,
          totalMs: Math.round(performance.now() - synthesisStartedAt),
          chunks,
          audioBytes,
        });
        this.callbacks.onPlaybackEnded(speech);
      }
    } catch (caught) {
      if (this.closed || epoch !== this.epoch) return;
      this.player.interrupt();
      this.scheduledUntil = this.audioContext.currentTime;
      const message = caught instanceof Error
        ? caught.message
        : "Cartesia could not play the agent response.";
      this.diagnostic("synthesis_failed", {
        speechId: speech.id,
        generation: speech.generation,
        contextId,
        durationMs: Math.round(performance.now() - synthesisStartedAt),
        chunks,
        audioBytes,
        message: diagnosticMessage(message),
      }, "error");
      this.callbacks.onError(message);
    } finally {
      if (this.contextId === contextId) this.contextId = "";
    }
  }

  private waitUntilDrained(epoch: number) {
    const remainingMs = Math.max(0, this.scheduledUntil - this.audioContext.currentTime) * 1000;
    return new Promise<void>((resolve) => {
      this.drainResolve = resolve;
      this.drainTimer = window.setTimeout(() => {
        this.drainTimer = 0;
        this.drainResolve = null;
        if (epoch === this.epoch) this.scheduledUntil = this.audioContext.currentTime;
        resolve();
      }, remainingMs + 40);
    });
  }

  private finishDrainWait() {
    window.clearTimeout(this.drainTimer);
    this.drainTimer = 0;
    const resolve = this.drainResolve;
    this.drainResolve = null;
    resolve?.();
  }

  private diagnostic(
    event: string,
    details: Record<string, string | number | boolean | null | undefined> = {},
    level: VoiceRuntimeDiagnostic["level"] = "info",
  ) {
    this.callbacks.onDiagnostic?.({
      component: "cartesia_tts",
      event,
      level,
      details: compactDiagnosticDetails(details),
    });
  }
}

export function supportedCartesiaSampleRate(sampleRate: number): SupportedSampleRate {
  const rounded = Math.round(sampleRate);
  const supported = SUPPORTED_SAMPLE_RATES.find((candidate) => candidate === rounded);
  if (!supported) {
    throw new Error(`The browser audio sample rate ${rounded} Hz is not supported by Cartesia.`);
  }
  return supported;
}

export function normalizedCartesiaLanguage(language: string) {
  const normalized = language.trim().toLowerCase().split(/[-_]/)[0];
  return normalized || "en";
}

function decodeBase64(encoded: string) {
  const binary = window.atob(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

export function appendAlignedPcm16(previous: Uint8Array, next: Uint8Array) {
  const combined = new Uint8Array(previous.length + next.length);
  combined.set(previous);
  combined.set(next, previous.length);
  const alignedLength = combined.length - (combined.length % Int16Array.BYTES_PER_ELEMENT);
  const alignedBytes = combined.slice(0, alignedLength);
  return {
    samples: new Int16Array(alignedBytes.buffer),
    remainder: combined.slice(alignedLength),
  };
}

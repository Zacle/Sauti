import { pcmPlaybackBufferProfile } from "./pcmPlaybackBuffer";

type PcmStreamPlayerOptions = {
  recordingDestination?: MediaStreamAudioDestinationNode | null;
  onPlaybackStarted: () => void;
  onPlaybackDrained: () => void;
  onPlaybackStalled: () => void;
  onPlaybackUnderrun?: () => void;
};

type PlaybackEvent = {
  type?: "started" | "drained" | "underrun";
};

const STALL_RECOVERY_MS = 1_200;

/**
 * Feeds streamed 16 kHz PCM into one continuous audio render loop.
 *
 * Scheduling every provider frame as a separate AudioBufferSourceNode makes
 * browser/main-thread jitter audible. The worklet owns the sample clock,
 * prebuffers once, and only pauses to build a larger buffer after a real
 * underrun.
 */
export class PcmStreamPlayer {
  private stallTimer = 0;
  private closed = false;
  private inputComplete = false;
  private previousInputSample: number | null = null;
  private pendingInputByte: number | null = null;
  private inputSampleIndex = 0;
  private nextOutputPosition = 0;

  private constructor(
    private readonly node: AudioWorkletNode,
    private readonly options: PcmStreamPlayerOptions,
    private readonly outputSampleRate: number,
  ) {
    this.node.port.onmessage = (message: MessageEvent<PlaybackEvent>) => {
      if (this.closed) return;
      if (message.data.type === "started") {
        this.clearStallTimer();
        this.options.onPlaybackStarted();
      } else if (message.data.type === "drained") {
        this.clearStallTimer();
        if (this.inputComplete) this.resetResampler();
        this.options.onPlaybackDrained();
      } else if (message.data.type === "underrun") {
        this.options.onPlaybackUnderrun?.();
        this.armStallRecovery();
      }
    };
  }

  static async create(
    context: AudioContext,
    options: PcmStreamPlayerOptions,
  ): Promise<PcmStreamPlayer> {
    await context.audioWorklet.addModule("/pcm-stream-player.js?v=20260723-1");
    const bufferProfile = pcmPlaybackBufferProfile(context.sampleRate);
    const node = new AudioWorkletNode(context, "sauti-pcm-stream-player", {
      numberOfInputs: 0,
      numberOfOutputs: 1,
      outputChannelCount: [1],
      processorOptions: bufferProfile,
    });
    node.connect(context.destination);
    if (options.recordingDestination) node.connect(options.recordingDestination);
    return new PcmStreamPlayer(node, options, context.sampleRate);
  }

  begin() {
    if (this.closed) return;
    this.clearStallTimer();
    if (this.inputComplete) this.resetResampler();
    this.inputComplete = false;
    this.node.port.postMessage({ type: "begin" });
  }

  pushPcm16(data: ArrayBuffer) {
    if (this.closed || data.byteLength === 0) return;
    this.clearStallTimer();
    const pcmBytes = new Uint8Array(data);
    const prefixLength = this.pendingInputByte === null ? 0 : 1;
    const combinedLength = prefixLength + pcmBytes.byteLength;
    const alignedLength = combinedLength - (combinedLength % 2);
    if (alignedLength === 0) {
      this.pendingInputByte = pcmBytes[0] ?? this.pendingInputByte;
      return;
    }
    let alignedBytes: Uint8Array;
    if (prefixLength === 0 && alignedLength === pcmBytes.byteLength) {
      alignedBytes = pcmBytes;
    } else {
      const combined = new Uint8Array(combinedLength);
      if (this.pendingInputByte !== null) combined[0] = this.pendingInputByte;
      combined.set(pcmBytes, prefixLength);
      alignedBytes = combined.subarray(0, alignedLength);
      this.pendingInputByte = alignedLength < combinedLength ? combined[combinedLength - 1]! : null;
    }
    if (prefixLength === 0) {
      this.pendingInputByte = alignedLength < pcmBytes.byteLength ? pcmBytes[pcmBytes.byteLength - 1]! : null;
    }
    const pcm = new Int16Array(alignedBytes.buffer, alignedBytes.byteOffset, alignedBytes.byteLength / 2);
    const output = new Float32Array(Math.ceil(pcm.length * this.outputSampleRate / 16_000) + 4);
    const outputStep = 16_000 / this.outputSampleRate;
    let outputIndex = 0;
    for (let index = 0; index < pcm.length; index += 1) {
      const current = pcm[index]! / 32768;
      if (this.previousInputSample === null) {
        output[outputIndex++] = current;
        this.previousInputSample = current;
        this.inputSampleIndex = 1;
        this.nextOutputPosition = outputStep;
        continue;
      }
      const currentInputIndex = this.inputSampleIndex;
      const previousInputIndex = currentInputIndex - 1;
      while (this.nextOutputPosition <= currentInputIndex && outputIndex < output.length) {
        const fraction = this.nextOutputPosition - previousInputIndex;
        output[outputIndex++] = this.previousInputSample
          + (current - this.previousInputSample) * fraction;
        this.nextOutputPosition += outputStep;
      }
      this.previousInputSample = current;
      this.inputSampleIndex += 1;
    }
    if (outputIndex === 0) return;
    const samples = outputIndex === output.length ? output : output.slice(0, outputIndex);
    this.node.port.postMessage({ type: "push", samples }, [samples.buffer]);
  }

  complete() {
    if (this.closed) return;
    this.clearStallTimer();
    this.inputComplete = true;
    this.node.port.postMessage({ type: "complete" });
  }

  clear() {
    if (this.closed) return;
    this.clearStallTimer();
    this.inputComplete = false;
    this.resetResampler();
    this.node.port.postMessage({ type: "clear" });
  }

  close() {
    if (this.closed) return;
    this.closed = true;
    this.clearStallTimer();
    this.node.port.postMessage({ type: "clear" });
    this.node.port.onmessage = null;
    this.node.disconnect();
  }

  private armStallRecovery() {
    this.clearStallTimer();
    this.stallTimer = window.setTimeout(() => {
      this.stallTimer = 0;
      if (this.closed) return;
      // If the provider omitted its terminal event, stop displaying/sending a
      // response that has no audible samples left and reset the server TTS
      // context before the next caller turn.
      this.complete();
      this.options.onPlaybackStalled();
    }, STALL_RECOVERY_MS);
  }

  private clearStallTimer() {
    window.clearTimeout(this.stallTimer);
    this.stallTimer = 0;
  }

  private resetResampler() {
    this.previousInputSample = null;
    this.pendingInputByte = null;
    this.inputSampleIndex = 0;
    this.nextOutputPosition = 0;
  }
}

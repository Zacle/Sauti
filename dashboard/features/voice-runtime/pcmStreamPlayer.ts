type PcmStreamPlayerOptions = {
  recordingDestination?: MediaStreamAudioDestinationNode | null;
  onPlaybackStarted: () => void;
  onPlaybackDrained: () => void;
  onPlaybackStalled: () => void;
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
        this.armStallRecovery();
      }
    };
  }

  static async create(
    context: AudioContext,
    options: PcmStreamPlayerOptions,
  ): Promise<PcmStreamPlayer> {
    await context.audioWorklet.addModule("/pcm-stream-player.js");
    const node = new AudioWorkletNode(context, "sauti-pcm-stream-player", {
      numberOfInputs: 0,
      numberOfOutputs: 1,
      outputChannelCount: [1],
      processorOptions: {
        initialBufferFrames: Math.round(0.28 * context.sampleRate),
        maxBufferFrames: Math.round(0.8 * context.sampleRate),
        underrunStepFrames: Math.round(0.12 * context.sampleRate),
      },
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
    const pcm = new Int16Array(data);
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
    this.inputSampleIndex = 0;
    this.nextOutputPosition = 0;
  }
}

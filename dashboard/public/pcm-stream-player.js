class SautiPcmStreamPlayer extends AudioWorkletProcessor {
  constructor(options) {
    super();
    const configured = options.processorOptions || {};
    this.initialBufferFrames = Math.max(128, configured.initialBufferFrames || Math.round(sampleRate * 0.28));
    this.maxBufferFrames = Math.max(this.initialBufferFrames, configured.maxBufferFrames || Math.round(sampleRate * 0.72));
    this.underrunStepFrames = Math.max(128, configured.underrunStepFrames || Math.round(sampleRate * 0.08));
    this.arrivalJitterHeadroomFrames = Math.max(
      128,
      configured.arrivalJitterHeadroomFrames || Math.round(sampleRate * 0.04),
    );
    this.learnedBufferFrames = this.initialBufferFrames;
    this.targetBufferFrames = this.learnedBufferFrames;
    this.chunks = [];
    this.chunkOffset = 0;
    this.bufferedFrames = 0;
    this.playing = false;
    this.started = false;
    this.complete = false;
    this.drainedNotified = false;
    this.rampFramesRemaining = 0;
    this.lastPushAtFrame = -1;
    this.lastPushFrames = 0;
    this.port.onmessage = (message) => this.handle(message.data || {});
  }

  handle(message) {
    if (message.type === "begin") {
      this.complete = false;
      this.drainedNotified = false;
      this.lastPushAtFrame = -1;
      this.lastPushFrames = 0;
      if (!this.started && this.bufferedFrames === 0) {
        this.targetBufferFrames = this.learnedBufferFrames;
      }
      return;
    }
    if (message.type === "push" && message.samples?.length) {
      this.learnFromArrival(message.samples.length);
      this.chunks.push(message.samples);
      this.bufferedFrames += message.samples.length;
      this.drainedNotified = false;
      return;
    }
    if (message.type === "complete") {
      this.complete = true;
      return;
    }
    if (message.type === "clear") this.reset();
  }

  reset() {
    this.chunks = [];
    this.chunkOffset = 0;
    this.bufferedFrames = 0;
    this.playing = false;
    this.started = false;
    this.complete = false;
    this.drainedNotified = false;
    this.rampFramesRemaining = 0;
    this.lastPushAtFrame = -1;
    this.lastPushFrames = 0;
    // Clearing an interrupted utterance must not discard what this call has
    // already learned about its provider/network jitter.
    this.targetBufferFrames = this.learnedBufferFrames;
  }

  learnFromArrival(pushedFrames) {
    if (this.lastPushAtFrame >= 0) {
      const arrivalGapFrames = Math.max(0, currentFrame - this.lastPushAtFrame);
      const uncoveredGapFrames = Math.max(0, arrivalGapFrames - this.lastPushFrames);
      if (uncoveredGapFrames > this.arrivalJitterHeadroomFrames) {
        const observedTarget = Math.min(
          this.maxBufferFrames,
          this.initialBufferFrames + uncoveredGapFrames + this.arrivalJitterHeadroomFrames,
        );
        this.learnedBufferFrames = Math.max(this.learnedBufferFrames, observedTarget);
        if (!this.playing) {
          this.targetBufferFrames = Math.max(this.targetBufferFrames, this.learnedBufferFrames);
        }
      }
    }
    this.lastPushAtFrame = currentFrame;
    this.lastPushFrames = pushedFrames;
  }

  process(_inputs, outputs) {
    const output = outputs[0]?.[0];
    if (!output) return true;
    output.fill(0);

    if (!this.playing) {
      const ready = this.bufferedFrames >= this.targetBufferFrames
        || (this.complete && this.bufferedFrames > 0);
      if (ready) {
        this.playing = true;
        this.rampFramesRemaining = 64;
        if (!this.started) {
          this.started = true;
          this.port.postMessage({ type: "started" });
        }
      } else {
        this.notifyDrainedIfComplete();
        return true;
      }
    }

    let outputOffset = 0;
    while (outputOffset < output.length && this.bufferedFrames > 0) {
      const chunk = this.chunks[0];
      if (!chunk) break;
      const available = chunk.length - this.chunkOffset;
      const count = Math.min(available, output.length - outputOffset);
      for (let index = 0; index < count; index += 1) {
        let value = chunk[this.chunkOffset + index];
        if (this.rampFramesRemaining > 0) {
          value *= (65 - this.rampFramesRemaining) / 64;
          this.rampFramesRemaining -= 1;
        }
        output[outputOffset + index] = value;
      }
      outputOffset += count;
      this.chunkOffset += count;
      this.bufferedFrames -= count;
      if (this.chunkOffset >= chunk.length) {
        this.chunks.shift();
        this.chunkOffset = 0;
      }
    }

    if (this.bufferedFrames === 0) {
      this.playing = false;
      if (this.complete) {
        this.notifyDrainedIfComplete();
      } else {
        this.learnedBufferFrames = Math.min(
          this.maxBufferFrames,
          Math.max(this.learnedBufferFrames, this.targetBufferFrames) + this.underrunStepFrames,
        );
        this.targetBufferFrames = this.learnedBufferFrames;
        this.port.postMessage({ type: "underrun", targetBufferFrames: this.targetBufferFrames });
      }
    }
    return true;
  }

  notifyDrainedIfComplete() {
    if (!this.complete || this.bufferedFrames > 0 || this.drainedNotified) return;
    this.drainedNotified = true;
    this.started = false;
    this.lastPushAtFrame = -1;
    this.lastPushFrames = 0;
    // Retain the adaptive target for the rest of the call. Resetting it here
    // made every sentence repeat the same first underrun.
    this.targetBufferFrames = this.learnedBufferFrames;
    this.port.postMessage({ type: "drained" });
  }
}

registerProcessor("sauti-pcm-stream-player", SautiPcmStreamPlayer);

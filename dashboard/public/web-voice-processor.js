class SautiVoiceProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.pending = [];
    this.pendingLength = 0;
  }

  process(inputs) {
    const input = inputs[0]?.[0];
    if (!input?.length) return true;
    const copy = new Float32Array(input);
    this.pending.push(copy);
    this.pendingLength += copy.length;
    if (this.pendingLength >= 2048) {
      const merged = new Float32Array(this.pendingLength);
      let offset = 0;
      for (const chunk of this.pending) {
        merged.set(chunk, offset);
        offset += chunk.length;
      }
      this.port.postMessage({ samples: merged, sampleRate }, [merged.buffer]);
      this.pending = [];
      this.pendingLength = 0;
    }
    return true;
  }
}

registerProcessor("sauti-voice-processor", SautiVoiceProcessor);

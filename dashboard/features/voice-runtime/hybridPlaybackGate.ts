/**
 * Rejects binary PCM that arrives after an interruption but before the hybrid
 * server has closed the old TTS generation. WebSocket ordering guarantees that
 * a new `speaking: true` event opens the gate before that generation's audio.
 */
export class HybridPlaybackGate {
  private open = false;

  speaking(value: boolean) {
    this.open = value;
  }

  clear() {
    this.open = false;
  }

  accepts(isHybridRealtime: boolean) {
    return !isHybridRealtime || this.open;
  }
}

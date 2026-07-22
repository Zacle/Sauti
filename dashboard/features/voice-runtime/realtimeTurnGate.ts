/**
 * Separates raw VAD from a recognized caller interruption.
 *
 * Server VAD can fire for echo, a chair movement, or background speech. Those
 * events are transport hints only: they must not change the visible call state
 * or cancel a valid model/TTS turn until the transcription stream contains
 * meaningful caller words.
 */
export class RealtimeTurnGate {
  private partialTranscript = "";
  private debounceElapsed = false;
  private confirmed = false;

  begin() {
    this.partialTranscript = "";
    this.debounceElapsed = false;
    this.confirmed = false;
  }

  addTranscriptDelta(delta: string) {
    this.partialTranscript += delta;
    return this.confirmIfReady();
  }

  markDebounceElapsed() {
    this.debounceElapsed = true;
    return this.confirmIfReady();
  }

  confirmFinal(transcript: string) {
    if (this.confirmed || !isMeaningfulPartial(transcript)) return false;
    this.confirmed = true;
    return true;
  }

  reset() {
    this.begin();
  }

  private confirmIfReady() {
    if (this.confirmed || !this.debounceElapsed || !isMeaningfulPartial(this.partialTranscript)) {
      return false;
    }
    this.confirmed = true;
    return true;
  }
}

function isMeaningfulPartial(value: string) {
  const compact = value.normalize("NFKC").replace(/[^\p{L}\p{N}]+/gu, "");
  return compact.length >= 2;
}

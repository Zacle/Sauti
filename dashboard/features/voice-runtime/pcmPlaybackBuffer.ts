export type PcmPlaybackBufferProfile = {
  initialBufferFrames: number;
  maxBufferFrames: number;
  underrunStepFrames: number;
  arrivalJitterHeadroomFrames: number;
};

export function pcmPlaybackBufferProfile(sampleRate: number): PcmPlaybackBufferProfile {
  const safeRate = Math.max(8_000, Math.round(sampleRate));
  return {
    // A 160 ms start buffer was repeatedly exhausted by normal provider ->
    // backend -> browser WebSocket jitter. The extra 120 ms is small relative
    // to speech generation latency and avoids audible stop/rebuffer cycles.
    initialBufferFrames: Math.round(0.28 * safeRate),
    maxBufferFrames: Math.round(0.72 * safeRate),
    underrunStepFrames: Math.round(0.08 * safeRate),
    arrivalJitterHeadroomFrames: Math.round(0.04 * safeRate),
  };
}

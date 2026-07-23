import assert from "node:assert/strict";
import test from "node:test";
import { HybridPlaybackGate } from "./hybridPlaybackGate.ts";
import { pcmPlaybackBufferProfile } from "./pcmPlaybackBuffer.ts";

test("drops late hybrid PCM after interruption until a new speech generation starts", () => {
  const gate = new HybridPlaybackGate();

  gate.speaking(true);
  assert.equal(gate.accepts(true), true);

  gate.clear();
  assert.equal(gate.accepts(true), false);

  gate.speaking(true);
  assert.equal(gate.accepts(true), true);
});

test("does not gate non-hybrid realtime PCM", () => {
  const gate = new HybridPlaybackGate();
  assert.equal(gate.accepts(false), true);
});

test("uses a jitter buffer that grows after a real PCM underrun", () => {
  const profile = pcmPlaybackBufferProfile(48_000);

  assert.equal(profile.initialBufferFrames, 13_440);
  assert.equal(profile.underrunStepFrames, 3_840);
  assert.equal(profile.maxBufferFrames, 34_560);
  assert.equal(profile.arrivalJitterHeadroomFrames, 1_920);
  assert.ok(profile.initialBufferFrames < profile.maxBufferFrames);
});

import assert from "node:assert/strict";
import test from "node:test";
import { HybridPlaybackGate } from "./hybridPlaybackGate.ts";

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

import assert from "node:assert/strict";
import test from "node:test";
import { RealtimeTurnGate } from "./realtimeTurnGate.ts";

test("raw VAD without recognized words never confirms an interruption", () => {
  const gate = new RealtimeTurnGate();
  gate.begin();

  assert.equal(gate.markDebounceElapsed(), false);
  assert.equal(gate.addTranscriptDelta(" "), false);
});

test("recognized speech confirms once after the debounce", () => {
  const gate = new RealtimeTurnGate();
  gate.begin();

  assert.equal(gate.addTranscriptDelta("hel"), false);
  assert.equal(gate.markDebounceElapsed(), true);
  assert.equal(gate.addTranscriptDelta("lo"), false);
});

test("a final transcript confirms a short caller turn even without deltas", () => {
  const gate = new RealtimeTurnGate();
  gate.begin();

  assert.equal(gate.confirmFinal("yes"), true);
  assert.equal(gate.confirmFinal("yes"), false);
});

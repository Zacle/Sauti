import assert from "node:assert/strict";
import test from "node:test";

import {
  TELNYX_BROWSER_VAD,
  TERMINAL_END_AFTER_DRAIN_MS,
  TERMINAL_END_FALLBACK_MS,
  terminalToolEndDelay,
} from "./telnyxAudioPolicy.ts";

test("keeps remote agent noise below the speaking threshold", () => {
  assert.equal(TELNYX_BROWSER_VAD.volumeThreshold, 10);
  assert.equal(TELNYX_BROWSER_VAD.remoteSilenceThresholdMs, 1_000);
});

test("does not end while farewell audio may still be starting or playing", () => {
  assert.equal(terminalToolEndDelay({
    agentSpeaking: true,
    lastAgentStoppedAt: 0,
    now: 10_000,
  }), TERMINAL_END_FALLBACK_MS);
  assert.equal(terminalToolEndDelay({
    agentSpeaking: false,
    lastAgentStoppedAt: 0,
    now: 10_000,
  }), TERMINAL_END_FALLBACK_MS);
});

test("allows the WebRTC playout buffer to drain after speech stops", () => {
  assert.equal(terminalToolEndDelay({
    agentSpeaking: false,
    lastAgentStoppedAt: 9_000,
    now: 10_000,
  }), TERMINAL_END_AFTER_DRAIN_MS);
});

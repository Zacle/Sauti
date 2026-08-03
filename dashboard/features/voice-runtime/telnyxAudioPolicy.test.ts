import assert from "node:assert/strict";
import test from "node:test";

import {
  MAX_DURATION_ERROR_GRACE_MS,
  reachedConfiguredMaxDuration,
  shouldReportAudioPlaybackError,
  TELNYX_BROWSER_VAD,
  TERMINAL_END_AFTER_DRAIN_MS,
  TERMINAL_END_FALLBACK_MS,
  terminalToolEndDelay,
} from "./telnyxAudioPolicy.ts";

test("ignores audio play rejection after call teardown removes the media", () => {
  assert.equal(shouldReportAudioPlaybackError({
    stopped: false,
    ended: true,
    audioConnected: false,
    hasSource: false,
  }), false);
});

test("reports audio play rejection while the active media is still attached", () => {
  assert.equal(shouldReportAudioPlaybackError({
    stopped: false,
    ended: false,
    audioConnected: true,
    hasSource: true,
  }), true);
});

test("keeps remote agent noise below the speaking threshold", () => {
  assert.equal(TELNYX_BROWSER_VAD.volumeThreshold, 10);
  assert.equal(TELNYX_BROWSER_VAD.remoteSilenceThresholdMs, 1_000);
});

test("keeps the fallback only while farewell audio is still playing", () => {
  assert.equal(terminalToolEndDelay({
    agentSpeaking: true,
    lastAgentStoppedAt: 0,
    now: 10_000,
  }), TERMINAL_END_FALLBACK_MS);
});

test("drains briefly when the terminal tool arrives after speech", () => {
  assert.equal(terminalToolEndDelay({
    agentSpeaking: false,
    lastAgentStoppedAt: 0,
    now: 10_000,
  }), TERMINAL_END_AFTER_DRAIN_MS);
});

test("allows the WebRTC playout buffer to drain after speech stops", () => {
  assert.equal(terminalToolEndDelay({
    agentSpeaking: false,
    lastAgentStoppedAt: 9_000,
    now: 10_000,
  }), TERMINAL_END_AFTER_DRAIN_MS);
});

test("recognizes a provider disconnect at the configured duration boundary", () => {
  assert.equal(reachedConfiguredMaxDuration(150, 150_100), true);
  assert.equal(
    reachedConfiguredMaxDuration(150, 150_000 - MAX_DURATION_ERROR_GRACE_MS - 1),
    false,
  );
  assert.equal(reachedConfiguredMaxDuration(0, 150_100), false);
});

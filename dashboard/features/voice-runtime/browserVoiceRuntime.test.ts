import assert from "node:assert/strict";
import test from "node:test";
import { browserMicrophoneConstraints } from "./browserVoiceRuntime.ts";

test("browser microphone constraints preserve normal speech processing", () => {
  assert.deepEqual(browserMicrophoneConstraints(), {
    echoCancellation: true,
    noiseSuppression: true,
    autoGainControl: true,
    channelCount: { ideal: 1 },
  });
});

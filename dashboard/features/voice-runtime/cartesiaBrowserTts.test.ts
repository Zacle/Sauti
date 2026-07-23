import assert from "node:assert/strict";
import test from "node:test";
import {
  appendAlignedPcm16,
  normalizedCartesiaLanguage,
  supportedCartesiaSampleRate,
} from "./cartesiaBrowserTts.ts";

test("keeps partial PCM samples between provider chunks", () => {
  const expected = new Int16Array([8192, -16384]);
  const bytes = new Uint8Array(expected.buffer);

  const first = appendAlignedPcm16(new Uint8Array(0), bytes.slice(0, 3));
  assert.deepEqual(Array.from(first.samples), [8192]);
  assert.equal(first.remainder.length, 1);

  const second = appendAlignedPcm16(first.remainder, bytes.slice(3));
  assert.deepEqual(Array.from(second.samples), [-16384]);
  assert.equal(second.remainder.length, 0);
});

test("uses the browser rate only when Cartesia supports it directly", () => {
  assert.equal(supportedCartesiaSampleRate(48_000), 48_000);
  assert.equal(supportedCartesiaSampleRate(44_100), 44_100);
  assert.throws(() => supportedCartesiaSampleRate(96_000), /not supported/);
});

test("normalizes regional language tags without hard-coded phrases", () => {
  assert.equal(normalizedCartesiaLanguage("en-US"), "en");
  assert.equal(normalizedCartesiaLanguage("fr_FR"), "fr");
  assert.equal(normalizedCartesiaLanguage(" ar "), "ar");
});

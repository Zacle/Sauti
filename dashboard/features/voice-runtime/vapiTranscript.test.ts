import assert from "node:assert/strict";
import test from "node:test";
import {
  accumulateVapiCaption,
  isVapiOpeningTranscript,
  mergeVapiTranscript,
} from "./vapiTranscript.ts";

test("merges consecutive Vapi speech fragments into one response", () => {
  assert.equal(
    mergeVapiTranscript(
      "What type of service",
      "would you like to book? We offer men's hairstyles.",
    ),
    "What type of service would you like to book? We offer men's hairstyles.",
  );
});

test("removes word overlap and accepts cumulative provider transcripts", () => {
  assert.equal(
    mergeVapiTranscript(
      "What date and time would you prefer",
      "prefer for your appointment?",
    ),
    "What date and time would you prefer for your appointment?",
  );
  assert.equal(
    mergeVapiTranscript("Could you confirm", "Could you confirm the date?"),
    "Could you confirm the date?",
  );
});

test("recognizes Vapi's imperfect transcription of the configured opening only", () => {
  assert.equal(
    isVapiOpeningTranscript(
      "Hello, this is Ailsa from Hairy. How can I help?",
      "Hello. This is Aelsa from Harry. How can I help?",
    ),
    true,
  );
  assert.equal(
    isVapiOpeningTranscript(
      "Hello, this is Ailsa from Hairy. How can I help?",
      "I can help with that appointment. Which service would you like?",
    ),
    false,
  );
});

test("accumulates Vapi TTS chunks within one turn and resets for the next turn", () => {
  const first = accumulateVapiCaption(null, "The 10AM slot next", 4);
  const second = accumulateVapiCaption(first, "Monday is available.", 4);
  const nextTurn = accumulateVapiCaption(second, "Before I save the booking", 5);

  assert.deepEqual(second, {
    text: "The 10AM slot next Monday is available.",
    turn: 4,
  });
  assert.deepEqual(nextTurn, {
    text: "Before I save the booking",
    turn: 5,
  });
});

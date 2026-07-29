import assert from "node:assert/strict";
import test from "node:test";
import { TelnyxAgentTranscriptAccumulator } from "./telnyxTranscript.ts";

test("combines Telnyx assistant deltas into one utterance", () => {
  const transcript = new TelnyxAgentTranscriptAccumulator();

  assert.deepEqual(transcript.append("response-1-1785349753001", "Bien "), {
    caption: "Bien",
    completed: undefined,
  });
  assert.deepEqual(transcript.append("response-1-1785349753002", "sûr, je "), {
    caption: "Bien sûr, je",
    completed: undefined,
  });
  assert.deepEqual(transcript.append("response-1-1785349753003", "serais ravi de vous aider."), {
    caption: "Bien sûr, je serais ravi de vous aider.",
    completed: undefined,
  });
  assert.deepEqual(transcript.flush(false), {
    text: "Bien sûr, je serais ravi de vous aider.",
    interrupted: false,
  });
});

test("flushes the previous utterance when a new response starts", () => {
  const transcript = new TelnyxAgentTranscriptAccumulator();
  transcript.append("response-1-1785349753001", "Première réponse.");

  assert.deepEqual(transcript.append("response-2-1785349754001", "Deuxième réponse."), {
    caption: "Deuxième réponse.",
    completed: {
      text: "Première réponse.",
      interrupted: false,
    },
  });
});

test("does not duplicate a future cumulative transcript item", () => {
  const transcript = new TelnyxAgentTranscriptAccumulator();
  transcript.append("response-1-1785349753001", "Bonjour");

  assert.equal(
    transcript.append("response-1-1785349753002", "Bonjour Zachary").caption,
    "Bonjour Zachary",
  );
});

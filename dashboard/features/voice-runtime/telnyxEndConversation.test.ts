import assert from "node:assert/strict";
import test from "node:test";

import { finalizeTelnyxEndConversation } from "./telnyxEndConversation.ts";

test("finalizes the browser call after Telnyx ends the conversation", async () => {
  const events: string[] = [];

  await finalizeTelnyxEndConversation(
    async () => {
      events.push("ended");
    },
    () => {
      events.push("error");
    },
    () => {
      events.push("finished");
    },
  );

  assert.deepEqual(events, ["ended", "finished"]);
});

test("finalizes the browser call when Telnyx does not return a promise", async () => {
  let finished = false;

  await finalizeTelnyxEndConversation(
    () => undefined,
    () => undefined,
    () => {
      finished = true;
    },
  );

  assert.equal(finished, true);
});

test("reports Telnyx hang-up failures and still finalizes the browser call", async () => {
  const failure = new Error("hang-up failed");
  let reported: unknown;
  let finished = false;

  await finalizeTelnyxEndConversation(
    async () => {
      throw failure;
    },
    (error) => {
      reported = error;
    },
    () => {
      finished = true;
    },
  );

  assert.equal(reported, failure);
  assert.equal(finished, true);
});

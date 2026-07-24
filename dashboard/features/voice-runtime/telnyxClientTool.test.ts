import assert from "node:assert/strict";
import test from "node:test";
import { executeTelnyxClientTool } from "./telnyxClientTool.ts";

test("a confirmed end_call result schedules provider session completion", async () => {
  let scheduled: (() => void) | undefined;
  let ended = false;
  const result = await executeTelnyxClientTool(
    "tool-1",
    "end_call",
    {},
    async () => ({ success: true, result: { ended: true } }),
    () => {
      ended = true;
    },
    (callback) => {
      scheduled = callback;
    },
  );

  assert.deepEqual(result, { success: true, result: { ended: true } });
  assert.equal(ended, false);
  assert.ok(scheduled);
  scheduled();
  assert.equal(ended, true);
});

test("a business tool result never schedules session completion", async () => {
  let scheduled = false;
  await executeTelnyxClientTool(
    "tool-2",
    "book_slot",
    {},
    async () => ({ success: true, result: { bookingId: "SAT-1" } }),
    () => undefined,
    () => {
      scheduled = true;
    },
  );

  assert.equal(scheduled, false);
});

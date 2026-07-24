import assert from "node:assert/strict";
import test from "node:test";
import { RetellTranscriptReconciler } from "./retellTranscript.ts";

test("emits only the latest caller text after cumulative Retell revisions", () => {
  const reconciler = new RetellTranscriptReconciler();

  assert.deepEqual(reconciler.update([
    { index: 0, role: "caller", text: "Hello" },
  ]), []);
  assert.deepEqual(reconciler.update([
    { index: 0, role: "caller", text: "Hello, my name is" },
  ]), []);
  assert.deepEqual(reconciler.update([
    { index: 0, role: "caller", text: "Hello, my name is Zachary." },
  ]), []);

  assert.deepEqual(reconciler.completeLatest("caller"), [
    { index: 0, role: "caller", text: "Hello, my name is Zachary." },
  ]);
  assert.deepEqual(reconciler.completeLatest("caller"), []);
});

test("reconciles completed Retell turns once while retaining the active agent caption", () => {
  const reconciler = new RetellTranscriptReconciler();
  const completed = reconciler.update([
    { index: 0, role: "caller", text: "Is Friday available?" },
    { index: 1, role: "agent", text: "Friday is available." },
  ]);

  assert.deepEqual(completed, [
    { index: 0, role: "caller", text: "Is Friday available?" },
  ]);
  assert.deepEqual(reconciler.activeAgentTurn(), {
    index: 1,
    role: "agent",
    text: "Friday is available.",
  });
  assert.deepEqual(reconciler.completeLatest("agent"), [
    { index: 1, role: "agent", text: "Friday is available." },
  ]);
  assert.deepEqual(reconciler.completeAll(), []);
});

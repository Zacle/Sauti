import assert from "node:assert/strict";
import test from "node:test";
import { startTelnyxConversationWhenReady } from "./telnyxReadiness.ts";

test("does not start a Telnyx conversation until the full client-ready event", async () => {
  const order: string[] = [];
  let markReady: (() => void) | undefined;
  const starting = startTelnyxConversationWhenReady({
    connect: async () => {
      order.push("connect");
    },
    startConversation: async () => {
      order.push("start");
    },
    subscribe: (handlers) => {
      markReady = handlers.ready;
      return () => {
        order.push("unsubscribe");
      };
    },
  });

  await Promise.resolve();
  assert.deepEqual(order, ["connect"]);

  markReady?.();
  await starting;
  assert.deepEqual(order, ["connect", "start", "unsubscribe"]);
});

test("does not start a Telnyx conversation after a startup disconnect", async () => {
  const order: string[] = [];
  let disconnectBeforeReady: (() => void) | undefined;
  const starting = startTelnyxConversationWhenReady({
    connect: async () => {
      order.push("connect");
    },
    startConversation: async () => {
      order.push("start");
    },
    subscribe: (handlers) => {
      disconnectBeforeReady = handlers.disconnected;
      return () => {
        order.push("unsubscribe");
      };
    },
  });

  disconnectBeforeReady?.();
  await assert.rejects(starting, /disconnected before it became ready/);
  assert.deepEqual(order, ["connect", "unsubscribe"]);
});

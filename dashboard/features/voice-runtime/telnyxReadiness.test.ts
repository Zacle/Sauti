import assert from "node:assert/strict";
import test from "node:test";
import {
  isTelnyxAuthenticationFailure,
  startTelnyxConversationWhenReady,
  startTelnyxConversationWithAuthenticationRetry,
} from "./telnyxReadiness.ts";

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

test("clears the sticky Telnyx edge and retries a transient authentication failure", async () => {
  const order: string[] = [];
  let attempts = 0;

  await startTelnyxConversationWithAuthenticationRetry({
    start: async () => {
      attempts += 1;
      order.push(`start:${attempts}`);
      if (attempts === 1) {
        throw Object.assign(new Error("Authentication failed"), { code: 46001 });
      }
    },
    clearReconnectToken: () => order.push("clear"),
    onRetry: (attempt) => order.push(`retry:${attempt}`),
    retryDelaysMs: [500],
    wait: async (delayMs) => {
      order.push(`wait:${delayMs}`);
    },
  });

  assert.deepEqual(order, ["start:1", "clear", "retry:1", "wait:500", "start:2"]);
});

test("does not retry unrelated Telnyx startup failures", async () => {
  let cleared = false;

  await assert.rejects(
    startTelnyxConversationWithAuthenticationRetry({
      start: async () => {
        throw new Error("Microphone access denied");
      },
      clearReconnectToken: () => {
        cleared = true;
      },
      retryDelaysMs: [0],
    }),
    /Microphone access denied/,
  );

  assert.equal(cleared, false);
  assert.equal(isTelnyxAuthenticationFailure({ code: "46001" }), true);
  assert.equal(isTelnyxAuthenticationFailure(new Error("Login Incorrect")), true);
});

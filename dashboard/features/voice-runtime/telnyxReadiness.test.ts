import assert from "node:assert/strict";
import test from "node:test";
import {
  isTelnyxAuthenticationFailure,
  isTelnyxConversationStartupFailure,
  startTelnyxConversationWhenReady,
  startTelnyxConversationWithAuthenticationRetry,
  TELNYX_CONVERSATION_START_TIMEOUT_MS,
} from "./telnyxReadiness.ts";

test("allows a normal WebRTC conversation more than five seconds to become active", () => {
  assert.equal(TELNYX_CONVERSATION_START_TIMEOUT_MS, 12_000);
});

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

test("does not report startup complete until the Telnyx conversation is active", async () => {
  const order: string[] = [];
  let markSignalingReady: (() => void) | undefined;
  let markConversationActive: (() => void) | undefined;
  const starting = startTelnyxConversationWhenReady({
    connect: async () => {
      order.push("connect");
    },
    startConversation: async () => {
      order.push("start");
    },
    subscribe: (handlers) => {
      markSignalingReady = handlers.ready;
      return () => order.push("unsubscribe-signaling");
    },
    subscribeConversation: (handlers) => {
      markConversationActive = handlers.active;
      return () => order.push("unsubscribe-conversation");
    },
  });

  markSignalingReady?.();
  while (!markConversationActive) await Promise.resolve();
  assert.deepEqual(order, ["connect", "start"]);

  markConversationActive?.();
  await starting;
  assert.deepEqual(order, [
    "connect",
    "start",
    "unsubscribe-conversation",
    "unsubscribe-signaling",
  ]);
});

test("fails a silent Telnyx start that never produces an active call", async () => {
  let markSignalingReady: (() => void) | undefined;
  const starting = startTelnyxConversationWhenReady({
    connect: async () => undefined,
    startConversation: async () => undefined,
    subscribe: (handlers) => {
      markSignalingReady = handlers.ready;
      return () => undefined;
    },
    subscribeConversation: () => () => undefined,
    conversationTimeoutMs: 1,
  });

  markSignalingReady?.();
  await assert.rejects(starting, /conversation did not become active/);
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

test("allows the Telnyx assistant version time to propagate across signaling edges", async () => {
  const delays: number[] = [];
  let attempts = 0;

  await startTelnyxConversationWithAuthenticationRetry({
    start: async () => {
      attempts += 1;
      if (attempts <= 4) {
        throw Object.assign(new Error("Authentication failed"), { code: 46001 });
      }
    },
    clearReconnectToken: () => undefined,
    wait: async (delayMs) => {
      delays.push(delayMs);
    },
  });

  assert.equal(attempts, 5);
  assert.deepEqual(delays, [500, 1_500, 3_000, 5_000]);
});

test("distinguishes exhausted Telnyx authentication from a Sauti login failure", async () => {
  await assert.rejects(
    startTelnyxConversationWithAuthenticationRetry({
      start: async () => {
        throw Object.assign(new Error("Authentication failed"), { code: 46001 });
      },
      clearReconnectToken: () => undefined,
      retryDelaysMs: [],
    }),
    /separate from your Sauti dashboard session/,
  );
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

test("resets and retries once when signaling never creates an active conversation", async () => {
  const order: string[] = [];
  let attempts = 0;

  await startTelnyxConversationWithAuthenticationRetry({
    start: async () => {
      attempts += 1;
      order.push(`start:${attempts}`);
      if (attempts === 1) {
        throw new Error("Telnyx signaling connected, but the conversation did not become active.");
      }
    },
    clearReconnectToken: () => order.push("clear"),
    resetConversation: async () => {
      order.push("reset");
    },
    onConversationRetry: (attempt) => order.push(`conversation-retry:${attempt}`),
    conversationRetryDelaysMs: [500],
    wait: async (delayMs) => {
      order.push(`wait:${delayMs}`);
    },
  });

  assert.deepEqual(order, [
    "start:1",
    "clear",
    "reset",
    "conversation-retry:1",
    "wait:500",
    "start:2",
  ]);
  assert.equal(isTelnyxConversationStartupFailure(
    new Error("The conversation did not become active."),
  ), true);
});

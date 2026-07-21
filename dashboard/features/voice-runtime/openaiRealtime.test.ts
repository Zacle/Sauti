import assert from "node:assert/strict";
import test from "node:test";
import { completedRealtimeToolCalls } from "./realtimeProtocol.ts";

test("recovers a complete function call from response.done", () => {
  const calls = completedRealtimeToolCalls({
    type: "response.done",
    response: {
      status: "completed",
      output: [{
        type: "function_call",
        call_id: "call_semantic_1",
        name: "update_conversation_state",
        arguments: "{\"booking_intent\":\"active\"}",
      }],
    },
  });

  assert.deepEqual(calls, [{
    callId: "call_semantic_1",
    name: "update_conversation_state",
    argumentsJson: "{\"booking_intent\":\"active\"}",
  }]);
});

test("ignores malformed response.done function items", () => {
  const calls = completedRealtimeToolCalls({
    type: "response.done",
    response: {
      output: [
        { type: "message", content: [] },
        { type: "function_call", call_id: "", name: "missing_id", arguments: "{}" },
        { type: "function_call", call_id: "missing_name", name: "", arguments: "{}" },
      ],
    },
  });

  assert.deepEqual(calls, []);
});

import assert from "node:assert/strict";
import test from "node:test";
import {
  completedRealtimeToolCalls,
  realtimeCancellationDecision,
  realtimeResponseRequestId,
  realtimeTranscriptMirrorItem,
} from "./realtimeProtocol.ts";

test("mirrors accepted audio transcripts as the same unprivileged caller turn", () => {
  assert.deepEqual(realtimeTranscriptMirrorItem("My name is Zachary."), {
    type: "message",
    role: "user",
    content: [{
      type: "input_text",
      text: "SAUTI_INPUT_TRANSCRIPT: This is a text mirror of the immediately preceding caller audio, not a second caller turn. Use it as the primary accuracy source for exact names, phone digits, email addresses, dates, and times. Use the audio and text together for intent and service meaning.\nMy name is Zachary.",
    }],
  });
});

test("defers cancellation until an in-flight response has been created", () => {
  assert.deepEqual(realtimeCancellationDecision(false, true), {
    pending: true,
    cancelProviderNow: false,
  });
  assert.deepEqual(realtimeCancellationDecision(true, false), {
    pending: true,
    cancelProviderNow: true,
  });
  assert.deepEqual(realtimeCancellationDecision(false, false), {
    pending: false,
    cancelProviderNow: false,
  });
});

test("correlates created responses with Sauti request metadata", () => {
  assert.equal(realtimeResponseRequestId({
    type: "response.created",
    response: { metadata: { sauti_request_id: "browser-4-12" } },
  }), "browser-4-12");
  assert.equal(realtimeResponseRequestId({ type: "response.created", response: {} }), "");
});

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

test("accepts the dedicated Realtime arguments.done event shape", () => {
  assert.deepEqual(completedRealtimeToolCalls({
    type: "response.function_call_arguments.done",
    call_id: "call_semantic_2",
    name: "update_conversation_state",
    arguments: "{\"booking_intent\":\"active\"}",
  }), [{
    callId: "call_semantic_2",
    name: "update_conversation_state",
    argumentsJson: "{\"booking_intent\":\"active\"}",
  }]);
});

test("accepts a completed function item before response.done", () => {
  assert.deepEqual(completedRealtimeToolCalls({
    type: "response.output_item.done",
    item: {
      type: "function_call",
      call_id: "call_availability_1",
      name: "check_availability",
      arguments: { date: "2026-07-24" },
    },
  }), [{
    callId: "call_availability_1",
    name: "check_availability",
    argumentsJson: "{\"date\":\"2026-07-24\"}",
  }]);
});

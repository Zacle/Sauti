import assert from "node:assert/strict";
import test from "node:test";
import {
  authorizedNextToolRequest,
  businessActionProgressInstruction,
  callerGuidanceInstruction,
  completedRealtimeToolCalls,
  realtimeCancellationDecision,
  realtimeResponseRequestId,
  realtimeTranscriptMirrorItem,
} from "./realtimeProtocol.ts";

test("executes a server-authorized booking approval without another model response", () => {
  assert.deepEqual(authorizedNextToolRequest({
    success: true,
    result: {
      nextTool: "book_slot",
      nextToolAuthorized: true,
      nextToolArguments: { review_token: "signed-review-token" },
    },
  }), {
    name: "book_slot",
    argumentsJson: "{\"review_token\":\"signed-review-token\"}",
  });
});

test("executes server-authorized availability arguments without another model response", () => {
  assert.deepEqual(authorizedNextToolRequest({
    success: true,
    result: {
      nextTool: "check_availability",
      nextToolAuthorized: true,
      nextToolArguments: { date: "2026-07-22", time_preference: "16:00" },
    },
  }), {
    name: "check_availability",
    argumentsJson: "{\"date\":\"2026-07-22\",\"time_preference\":\"16:00\"}",
  });

  assert.deepEqual(authorizedNextToolRequest({
    success: true,
    result: { nextTool: "check_availability", nextToolAuthorized: true },
  }), {
    name: "check_availability",
    argumentsJson: "",
  });
  assert.equal(authorizedNextToolRequest({
    success: true,
    result: { nextTool: "untrusted_tool", nextToolAuthorized: false },
  }), null);
  assert.equal(authorizedNextToolRequest({
    success: false,
    result: {
      nextTool: "check_availability",
      nextToolAuthorized: true,
      nextToolArguments: { date: "2026-07-22" },
    },
  }), null);
  assert.deepEqual(authorizedNextToolRequest({
    success: true,
    result: {
      nextTool: "book_slot",
      nextToolAuthorized: false,
      nextToolArguments: { review_token: "must-not-run-directly" },
    },
  }), {
    name: "book_slot",
    argumentsJson: "",
  });
});

test("asks the model for contextual delayed-operation speech instead of a translated template", () => {
  const booking = businessActionProgressInstruction("book_slot");
  const availability = businessActionProgressInstruction("check_availability");

  assert.match(booking, /caller's current language/i);
  assert.match(booking, /apology/i);
  assert.match(booking, /still saving the appointment/i);
  assert.match(booking, /Do not claim success or failure/i);
  assert.match(availability, /still checking the live schedule/i);
});

test("accepts trusted post-booking guidance only after a successful save", () => {
  const instruction = "Use the caller's current language and tell them to keep the booking number.";

  assert.equal(callerGuidanceInstruction("book_slot", {
    success: true,
    result: { callerGuidanceInstruction: instruction },
  }), instruction);
  assert.equal(callerGuidanceInstruction("book_slot", {
    success: false,
    result: { callerGuidanceInstruction: instruction },
  }), "");
  assert.equal(callerGuidanceInstruction("cancel_booking", {
    success: true,
    result: { callerGuidanceInstruction: instruction },
  }), "");
  assert.equal(callerGuidanceInstruction("book_slot", {
    success: true,
    result: { callerGuidanceInstruction: "x".repeat(1_201) },
  }), "");
});

test("mirrors accepted audio transcripts as the same unprivileged caller turn", () => {
  assert.deepEqual(realtimeTranscriptMirrorItem("My name is Zachary."), {
    type: "message",
    role: "user",
    content: [{
      type: "input_text",
      text: "SAUTI_INPUT_TRANSCRIPT: Text mirror of the immediately preceding caller audio, not a new turn. Prefer it for exact names, digits, emails, dates, and times; combine it with audio for meaning. If it is incoherent or not a clear answer or choice, do not update state, reuse a stored choice, or call a business tool; ask for repetition.\nMy name is Zachary.",
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

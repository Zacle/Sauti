import assert from "node:assert/strict";
import test from "node:test";
import {
  authorizedNextToolRequest,
  businessActionProgressInstruction,
  callerWaitExpected,
  callerGuidanceInstruction,
  confirmedEndCallResult,
  completedResponseText,
  completedRealtimeToolCalls,
  realtimeCancellationDecision,
  realtimeResponseRequestId,
  realtimeTranscriptMirrorItem,
  isSlowResponseProgressEvent,
  shouldRetrySlowResponse,
  slowResponseProgressRequest,
  SLOW_RESPONSE_PROGRESS_PURPOSE,
} from "./realtimeProtocol.ts";

test("creates concurrent model-generated progress outside the caller conversation", () => {
  const request = slowResponseProgressRequest("progress-7");
  assert.equal(request.response.conversation, "none");
  assert.equal(request.response.tool_choice, "none");
  assert.deepEqual(request.response.output_modalities, ["text"]);
  assert.equal(request.response.metadata.sauti_request_id, "progress-7");
  assert.equal(request.response.metadata.purpose, SLOW_RESPONSE_PROGRESS_PURPOSE);
  assert.match(request.response.instructions, /caller's current language/i);
  assert.match(request.response.instructions, /still working/i);
  assert.match(request.response.instructions, /Do not answer the request/i);

  assert.equal(isSlowResponseProgressEvent({
    type: "response.created",
    response: { id: "progress-response", metadata: request.response.metadata },
  }, new Set()), true);
  assert.equal(isSlowResponseProgressEvent({
    type: "response.output_text.delta",
    response_id: "progress-response",
  }, new Set(["progress-response"])), true);
  assert.equal(isSlowResponseProgressEvent({
    type: "response.done",
    response: { id: "main-response" },
  }, new Set(["progress-response"])), false);
  assert.equal(completedResponseText({
    response: {
      output: [{ content: [{ type: "output_text", text: "Sorry for the wait. I am still working on that." }] }],
    },
  }), "Sorry for the wait. I am still working on that.");
});

test("retries one delayed main response without surfacing a false repeat request", () => {
  assert.equal(shouldRetrySlowResponse(true, false), true);
  assert.equal(shouldRetrySlowResponse(true, true), false);
  assert.equal(shouldRetrySlowResponse(false, false), false);
});

test("authorizes browser call closure only from a successful end-call tool result", () => {
  assert.equal(confirmedEndCallResult("end_call", {
    success: true,
    result: { ended: true, outcome: "completed" },
  }), true);
  assert.equal(confirmedEndCallResult("book_slot", {
    success: true,
    result: { ended: true },
  }), false);
  assert.equal(confirmedEndCallResult("end_call", {
    success: false,
    result: { ended: true },
  }), false);
});

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
  assert.equal(authorizedNextToolRequest({
    success: true,
    result: {
      nextTool: "book_slot",
      nextToolAuthorized: false,
      nextToolArguments: { review_token: "must-not-run-directly" },
    },
  }), null);
});

test("asks the model for contextual delayed-operation speech instead of a translated template", () => {
  const booking = businessActionProgressInstruction("book_slot");
  const availability = businessActionProgressInstruction("check_availability");
  const reschedule = businessActionProgressInstruction("reschedule_booking");
  const cancellation = businessActionProgressInstruction("cancel_booking");

  assert.match(booking, /caller's current language/i);
  assert.match(booking, /apology/i);
  assert.match(booking, /still saving the appointment/i);
  assert.match(booking, /Do not claim success or failure/i);
  assert.match(availability, /still checking the live schedule/i);
  assert.match(reschedule, /still rescheduling the appointment/i);
  assert.match(cancellation, /still cancelling the appointment/i);
});

test("arms delayed progress for configured remote tools without business-name lists", () => {
  assert.equal(callerWaitExpected("check_availability"), true);
  assert.equal(callerWaitExpected("synchronize_customer_record"), true);
  assert.equal(callerWaitExpected("collect_payment"), true);
  assert.equal(callerWaitExpected("update_conversation_state"), false);
  assert.equal(callerWaitExpected("get_business_hours"), false);
  assert.equal(callerWaitExpected("end_call"), false);

  assert.match(
    businessActionProgressInstruction("synchronize_customer_record"),
    /still working on the caller's request/i,
  );
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

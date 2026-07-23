import assert from "node:assert/strict";
import test from "node:test";
import {
  aiRecoverySpeechInstruction,
  aiRecoverySpeechRequest,
  authorizedNextToolRequest,
  businessActionProgressRequest,
  businessActionProgressInstruction,
  callerWaitExpected,
  callerGuidanceInstruction,
  confirmedEndCallResult,
  completedRealtimeToolCalls,
  hasUsableCallerFacingResponse,
  realtimeAuthorizedFunctionCallItem,
  newRealtimeChainedCallId,
  REALTIME_CALL_ID_MAX_LENGTH,
  realtimeCancellationDecision,
  realtimeRateLimitRetryDelayMs,
  releaseTerminalResponseForProtocolRecovery,
  realtimeResponseRequestId,
  realtimeToolResultOutput,
  realtimeTranscriptMirrorItem,
  ownsOriginatingToolResponse,
  protocolRecoveryResponseRequest,
} from "./realtimeProtocol.ts";

test("settles only the exact response that originated a completed tool", () => {
  assert.equal(ownsOriginatingToolResponse(
    "response-tool",
    "response-tool",
    7,
    7,
    true,
    true,
  ), true);
  assert.equal(ownsOriginatingToolResponse(
    "response-tool",
    "response-followup",
    7,
    7,
    true,
    true,
  ), false);
  assert.equal(ownsOriginatingToolResponse(
    "response-tool",
    "response-tool",
    7,
    8,
    true,
    true,
  ), false);
  assert.equal(ownsOriginatingToolResponse("", "", 7, 7, true, true), false);
  assert.equal(ownsOriginatingToolResponse(
    "response-tool",
    "response-tool",
    7,
    7,
    false,
    true,
  ), false);
});

test("does not retry an incomplete response that already delivered caller-facing text", () => {
  const incompleteWithText = {
    response: {
      status: "incomplete",
      output: [{
        type: "message",
        content: [{ type: "output_text", text: "We are open Monday through Friday." }],
      }],
    },
  };

  assert.equal(
    hasUsableCallerFacingResponse(incompleteWithText, "", false, ""),
    true,
  );
  assert.equal(
    hasUsableCallerFacingResponse({ response: { status: "incomplete", output: [] } }, "", false, ""),
    false,
  );
  assert.equal(
    hasUsableCallerFacingResponse(incompleteWithText, "private preamble", true, ""),
    false,
  );
});

test("represents a server-authorized chained tool as a native realtime function call", () => {
  const chainedCallId = newRealtimeChainedCallId();
  assert.equal(chainedCallId.length, REALTIME_CALL_ID_MAX_LENGTH);
  assert.match(chainedCallId, /^sc_[a-z0-9]{29}$/);
  assert.deepEqual(
    realtimeAuthorizedFunctionCallItem(
      chainedCallId,
      "get_business_hours",
      "{\"question\":\"When are you open?\"}",
    ),
    {
      type: "function_call",
      status: "completed",
      call_id: chainedCallId,
      name: "get_business_hours",
      arguments: "{\"question\":\"When are you open?\"}",
    },
  );
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

test("accepts only the exact server-supplied definition for a required next tool", () => {
  const definition = {
    type: "function",
    name: "update_conversation_state",
    description: "Interpret the latest caller turn.",
    parameters: {
      type: "object",
      properties: { review_decision: { type: "string" } },
    },
  };
  assert.deepEqual(authorizedNextToolRequest({
    success: true,
    result: {
      nextTool: "update_conversation_state",
      nextToolAuthorized: true,
      nextToolDefinition: definition,
    },
  }), {
    name: "update_conversation_state",
    argumentsJson: "",
    definition,
  });
  assert.deepEqual(authorizedNextToolRequest({
    success: true,
    result: {
      nextTool: "update_conversation_state",
      nextToolAuthorized: true,
      nextToolDefinition: { ...definition, name: "book_slot" },
    },
  }), {
    name: "update_conversation_state",
    argumentsJson: "",
  });
});

test("does not retain the temporary required-tool schema in conversation history", () => {
  const output = JSON.parse(realtimeToolResultOutput({
    success: true,
    result: {
      nextTool: "update_conversation_state",
      nextToolAuthorized: true,
      nextToolDefinition: {
        type: "function",
        name: "update_conversation_state",
        description: "large transient schema",
        parameters: { type: "object" },
      },
      status: "booking_review_decision_required",
    },
  })) as Record<string, unknown>;

  assert.deepEqual(output, {
    success: true,
    result: {
      nextTool: "update_conversation_state",
      nextToolAuthorized: true,
      status: "booking_review_decision_required",
    },
  });
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

test("keeps delayed-operation progress outside the caller conversation", () => {
  const request = businessActionProgressRequest("book_slot", "business-progress-4");

  assert.equal(request.response.conversation, "none");
  assert.deepEqual(request.response.input, []);
  assert.deepEqual(request.response.tools, []);
  assert.equal(request.response.tool_choice, "none");
  assert.equal(request.response.metadata.sauti_request_id, "business-progress-4");
  assert.match(request.response.instructions, /still saving the appointment/i);
  assert.match(request.response.instructions, /ask a question/i);
});

test("asks the model to author recovery speech from a compact semantic situation", () => {
  const request = aiRecoverySpeechRequest(
    "provider_delay",
    "fr",
    "recovery-12",
  );

  assert.equal(request.response.conversation, "none");
  assert.deepEqual(request.response.input, []);
  assert.deepEqual(request.response.tools, []);
  assert.equal(request.response.tool_choice, "none");
  assert.equal(request.response.metadata.sauti_request_id, "recovery-12");
  assert.match(request.response.instructions, /Respond in fr/i);
  assert.match(request.response.instructions, /Choose the wording yourself/i);
  assert.match(request.response.instructions, /acknowledge the wait/i);
  assert.doesNotMatch(
    request.response.instructions,
    /voice service is temporarily busy/i,
  );
});

test("grounds AI-authored tool failure speech in the operation's factual outcome", () => {
  const booking = aiRecoverySpeechInstruction("tool_failed", "en", "book_slot");
  const cancellation = aiRecoverySpeechInstruction("tool_failed", "en", "cancel_booking");
  const availability = aiRecoverySpeechInstruction("tool_failed", "en", "check_availability");

  assert.match(booking, /booking was not saved/i);
  assert.match(cancellation, /existing booking remains unchanged/i);
  assert.match(availability, /availability could not be confirmed/i);
  assert.match(availability, /no booking was made/i);
  assert.match(booking, /Do not invent a result/i);
  assert.match(booking, /Output only the words to say/i);
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

test("releases a terminal response before protocol recovery is queued", () => {
  assert.equal(releaseTerminalResponseForProtocolRecovery(false), true);
  assert.equal(releaseTerminalResponseForProtocolRecovery(true), false);
});

test("preserves a mandatory state tool during terminal protocol recovery", () => {
  assert.deepEqual(
    protocolRecoveryResponseRequest("update_conversation_state", "recovery-state-13"),
    {
      type: "response.create",
      response: {
        tool_choice: { type: "function", name: "update_conversation_state" },
        metadata: { sauti_request_id: "recovery-state-13" },
      },
    },
  );
  assert.deepEqual(
    protocolRecoveryResponseRequest("", "recovery-answer-4"),
    {
      type: "response.create",
      response: {
        tool_choice: "none",
        metadata: { sauti_request_id: "recovery-answer-4" },
      },
    },
  );
});

test("isolates a required semantic transition from the full call context", () => {
  const definition = {
    type: "function" as const,
    name: "update_conversation_state",
    description: "Interpret the latest caller turn.",
    parameters: {
      type: "object",
      properties: {
        review_decision: { type: "string" },
        action_authorization: { type: "string" },
      },
    },
  };
  const request = protocolRecoveryResponseRequest(
    "update_conversation_state",
    "required-state-18",
    definition,
    "Everything is right right now.",
  );

  assert.equal(request.response.conversation, "none");
  assert.equal(request.response.max_output_tokens, 256);
  assert.deepEqual(request.response.tools, [definition]);
  assert.deepEqual(request.response.tool_choice, {
    type: "function",
    name: "update_conversation_state",
  });
  assert.ok(request.response.input);
  assert.ok(request.response.instructions);
  assert.match(
    request.response.input[0].content[0].text,
    /Everything is right right now\./,
  );
  assert.match(request.response.instructions, /question, condition, hesitation/i);
  assert.doesNotMatch(request.response.instructions, /approve_review/i);
});

test("honors a bounded provider rate-limit retry delay", () => {
  const event = {
    response: {
      status: "failed",
      status_details: {
        error: {
          code: "rate_limit_exceeded",
          message: "Please try again in 7.722s.",
        },
      },
    },
  };

  assert.equal(realtimeRateLimitRetryDelayMs(event), 7_972);
  assert.equal(realtimeRateLimitRetryDelayMs({
    response: {
      status_details: {
        error: { code: "rate_limit_exceeded", message: "Please try again in 544ms." },
      },
    },
  }), 794);
  assert.equal(realtimeRateLimitRetryDelayMs({
    response: {
      status_details: {
        error: { code: "rate_limit_exceeded", message: "Please retry shortly." },
      },
    },
  }), 2_000);
  assert.equal(realtimeRateLimitRetryDelayMs({
    response: {
      status_details: {
        error: { code: "rate_limit_exceeded", message: "Please try again in 45s." },
      },
    },
  }), 0);
  assert.equal(realtimeRateLimitRetryDelayMs({
    response: {
      status_details: {
        error: { code: "server_error", message: "Please try again in 2s." },
      },
    },
  }), 0);
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

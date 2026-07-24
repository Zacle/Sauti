import assert from "node:assert/strict";
import test from "node:test";
import {
  configPrimitives,
  configString,
  configStringArray,
  providerError,
} from "./managedRuntimeConfig.ts";

test("managed runtime configuration accepts only expected public value types", () => {
  const configuration = {
    agentId: "agent-42",
    toolNames: ["read_data", "", 42, "save_data"],
    dynamicVariables: {
      callId: "call-42",
      test: true,
      count: 2,
      nested: { secret: "not forwarded" },
      missing: null,
    },
  };

  assert.equal(configString(configuration, "agentId"), "agent-42");
  assert.deepEqual(configStringArray(configuration, "toolNames"), ["read_data", "save_data"]);
  assert.deepEqual(configPrimitives(configuration, "dynamicVariables"), {
    callId: "call-42",
    test: true,
    count: 2,
  });
});

test("managed provider errors do not stringify arbitrary provider objects", () => {
  assert.equal(providerError("Retell", { apiKey: "secret" }), "Retell ended the voice session unexpectedly.");
  assert.equal(providerError("Telnyx", new Error("connection refused")), "Telnyx: connection refused");
  assert.equal(
    providerError("ElevenLabs", "token=secret wss://provider.example/session?token=secret"),
    "ElevenLabs: token [redacted] [provider endpoint]",
  );
  assert.equal(
    providerError("ElevenLabs", "Server error: Unknown error", {
      errorType: "tool_execution",
      code: 500,
      debugMessage: "token=secret failed",
      apiKey: "must-not-appear",
      nested: { customer: "must-not-appear" },
    }),
    "ElevenLabs: Server error: Unknown error (type=tool_execution, code=500, debug=token [redacted] failed)",
  );
});

import assert from "node:assert/strict";
import test from "node:test";
import {
  diagnosticMessage,
  providerErrorDiagnosticDetails,
  providerResponseDiagnosticDetails,
} from "./voiceDiagnostics.ts";

test("extracts provider response failure details without conversation content", () => {
  assert.deepEqual(providerResponseDiagnosticDetails({
    response: {
      status: "incomplete",
      status_details: {
        reason: "max_output_tokens",
        error: {
          type: "invalid_request_error",
          code: "item_call_id_too_long",
          param: "item.call_id",
          message: "Expected a maximum length of 32.",
        },
      },
      output: [{ content: [{ text: "customer transcript must not be exported" }] }],
    },
  }), {
    providerStatus: "incomplete",
    providerReason: "max_output_tokens",
    providerErrorType: "invalid_request_error",
    providerErrorCode: "item_call_id_too_long",
    providerErrorParam: "item.call_id",
    providerErrorMessage: "Expected a maximum length of 32.",
  });
});

test("redacts customer contact data and credentials from provider messages", () => {
  assert.equal(
    diagnosticMessage("Bad input for +1 (202) 555-0198 and me@example.com at https://example.test/x token=secret"),
    "Bad input for [number] and [email] at [url] token [redacted]",
  );
  assert.deepEqual(providerErrorDiagnosticDetails({
    error: {
      code: "bad_request",
      message: "Phone 010-575-3442 could not be parsed",
    },
  }), {
    providerErrorCode: "bad_request",
    providerErrorMessage: "Phone [number] could not be parsed",
  });
});

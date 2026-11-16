import assert from "node:assert/strict";
import test from "node:test";
import { friendlyStatusMessage, isReadableServerMessage, messageForFailedResponse, NETWORK_ERROR_MESSAGE } from "./errors.ts";

test("keeps a readable validation message from the server", () => {
  assert.equal(
    messageForFailedResponse(400, { detail: "Business name must be at least 2 characters." }),
    "Business name must be at least 2 characters.",
  );
});

test("replaces generic reason phrases with a friendly message", () => {
  assert.equal(messageForFailedResponse(404, { error: "Not Found" }), friendlyStatusMessage(404));
  assert.equal(messageForFailedResponse(400, { message: "No message available" }), friendlyStatusMessage(400));
});

test("never shows server error text for 5xx responses", () => {
  assert.equal(
    messageForFailedResponse(500, { detail: "NullPointerException at com.sauti.call.CallService" }),
    friendlyStatusMessage(500),
  );
});

test("rejects stack traces and markup", () => {
  assert.equal(isReadableServerMessage("java.lang.IllegalStateException: boom"), false);
  assert.equal(isReadableServerMessage("<html><body>502</body></html>"), false);
});

test("falls back when the body is missing", () => {
  assert.equal(messageForFailedResponse(429, null), friendlyStatusMessage(429));
  assert.equal(friendlyStatusMessage(0), NETWORK_ERROR_MESSAGE);
});

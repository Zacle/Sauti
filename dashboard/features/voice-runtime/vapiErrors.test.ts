import assert from "node:assert/strict";
import test from "node:test";
import { vapiErrorMessage } from "./vapiErrors.ts";

test("preserves the provider error nested inside a Vapi SDK event", () => {
  assert.equal(vapiErrorMessage({
    type: "start-method-error",
    error: { message: "Invalid public key" },
  }), "Invalid public key");
});

test("uses a stable fallback when Vapi supplies no readable message", () => {
  assert.equal(
    vapiErrorMessage({ type: "start-method-error", error: {} }),
    "The Vapi voice session encountered an error.",
  );
});

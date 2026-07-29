import assert from "node:assert/strict";
import test from "node:test";
import { parseSuccessfulJsonResponse } from "./response.ts";

test("accepts a successful 200 response with an empty body", async () => {
  const response = new Response(null, { status: 200 });

  assert.equal(await parseSuccessfulJsonResponse<void>(response), undefined);
});

test("accepts a successful 204 response without parsing JSON", async () => {
  const response = new Response(null, { status: 204 });

  assert.equal(await parseSuccessfulJsonResponse<void>(response), undefined);
});

test("parses a successful JSON response", async () => {
  const response = new Response(JSON.stringify({ status: "ok" }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });

  assert.deepEqual(
    await parseSuccessfulJsonResponse<{ status: string }>(response),
    { status: "ok" },
  );
});

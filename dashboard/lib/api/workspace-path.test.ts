import assert from "node:assert/strict";
import { test } from "node:test";
import { isAccountLevelPath, workspaceApiUrl } from "./workspace-path.ts";

test("workspace features are sent under the active workspace", () => {
  assert.equal(workspaceApiUrl("/agents", "ws-1"), "/api/v1/workspaces/ws-1/agents");
  assert.equal(workspaceApiUrl("/calls/42/turns?limit=5", "ws-1"), "/api/v1/workspaces/ws-1/calls/42/turns?limit=5");
});

test("account and platform routes never carry a workspace", () => {
  for (const path of ["/auth/login", "/me", "/me/last-workspace", "/workspaces", "/invitations/accept", "/admin/overview", "/voices/x/preview"]) {
    assert.equal(isAccountLevelPath(path), true, path);
    assert.equal(workspaceApiUrl(path, "ws-1"), `/api/v1${path}`);
  }
});

test("look-alike prefixes are still workspace features", () => {
  assert.equal(isAccountLevelPath("/authority"), false);
  assert.equal(isAccountLevelPath("/messages"), false);
});

test("without an active workspace the path is sent as is", () => {
  assert.equal(workspaceApiUrl("/agent-templates", null), "/api/v1/agent-templates");
});

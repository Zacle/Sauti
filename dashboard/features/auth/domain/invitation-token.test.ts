import assert from "node:assert/strict";
import test from "node:test";
import {
  clearInvitationToken,
  PILOT_INVITATION_SESSION_KEY,
  retainInvitationToken,
} from "./invitation-token.ts";

function memoryStorage() {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  };
}

test("retains the invitation token before the URL fragment is cleared", () => {
  const storage = memoryStorage();

  assert.equal(retainInvitationToken("#token=invite-token", storage), "invite-token");
  assert.equal(storage.getItem(PILOT_INVITATION_SESSION_KEY), "invite-token");
  assert.equal(retainInvitationToken("", storage), "invite-token");
});

test("clears an invitation token after activation or a terminal failure", () => {
  const storage = memoryStorage();
  retainInvitationToken("#token=invite-token", storage);

  clearInvitationToken(storage);

  assert.equal(retainInvitationToken("", storage), "");
});

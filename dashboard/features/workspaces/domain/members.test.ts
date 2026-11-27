import assert from "node:assert/strict";
import test from "node:test";
import { actionsFor, assignableRoles, initialsFor, isValidEmail, memberLabel, ownershipCandidates, type MemberLike } from "./members.ts";

const owner: MemberLike = { userId: "o", role: "OWNER" };
const admin: MemberLike = { userId: "a", role: "ADMIN" };
const member: MemberLike = { userId: "m", role: "MEMBER" };
const team = [owner, admin, member];

test("only an Owner can create another Owner", () => {
  assert.deepEqual(assignableRoles("OWNER"), ["OWNER", "ADMIN", "MEMBER"]);
  assert.deepEqual(assignableRoles("ADMIN"), ["ADMIN", "MEMBER"]);
  assert.deepEqual(assignableRoles("MEMBER"), []);
});

test("admins manage members but never touch an Owner", () => {
  assert.equal(actionsFor("ADMIN", "a", member, team).changeRole, true);
  assert.equal(actionsFor("ADMIN", "a", owner, team).remove, false);
  assert.equal(actionsFor("MEMBER", "m", admin, team).remove, false);
});

test("the last Owner cannot leave or be demoted", () => {
  const self = actionsFor("OWNER", "o", owner, team);
  assert.equal(self.leave, false);
  assert.match(self.leaveBlockedReason ?? "", /only Owner/);

  const twoOwners = [owner, { userId: "o2", role: "OWNER" as const }];
  assert.equal(actionsFor("OWNER", "o", owner, twoOwners).leave, true);
  assert.equal(actionsFor("OWNER", "o", twoOwners[1], twoOwners).changeRole, true);
});

test("anyone except the last Owner can leave", () => {
  assert.equal(actionsFor("MEMBER", "m", member, team).leave, true);
  assert.equal(actionsFor("ADMIN", "a", admin, team).leave, true);
});

test("ownership can be handed only by an Owner, to someone else", () => {
  assert.deepEqual(ownershipCandidates("OWNER", "o", team).map((item) => item.userId), ["a", "m"]);
  assert.deepEqual(ownershipCandidates("ADMIN", "a", team), []);
});

test("initials and email checks", () => {
  assert.equal(initialsFor("amina.otieno@clinic.example"), "AO");
  assert.equal(initialsFor("joe@clinic.example"), "JO");
  assert.equal(initialsFor("Amina Otieno"), "AO");
  assert.equal(isValidEmail(" new.person@example.com "), true);
  assert.equal(isValidEmail("not-an-email"), false);
});

test("members are named by the name they chose, or their email", () => {
  assert.equal(memberLabel({ email: "amina@clinic.example", displayName: "Amina Otieno" }), "Amina Otieno");
  assert.equal(memberLabel({ email: "amina@clinic.example", displayName: "  " }), "amina@clinic.example");
  assert.equal(memberLabel({ email: "amina@clinic.example", displayName: null }), "amina@clinic.example");
});

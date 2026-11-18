/**
 * What the console lets a member do on the Members screen. The backend enforces the same rules;
 * these only decide which controls to show, so a hidden control is never the only protection.
 */

export type Role = "OWNER" | "ADMIN" | "MEMBER";

export type MemberLike = { userId: string; role: Role };

export const ROLE_LABELS: Record<Role, string> = {
  OWNER: "Owner",
  ADMIN: "Admin",
  MEMBER: "Member",
};

export const ROLE_DESCRIPTIONS: Record<Role, string> = {
  OWNER: "Everything, including billing, closing the workspace and handing over ownership.",
  ADMIN: "Set up agents, numbers and integrations, and manage members.",
  MEMBER: "See calls, bookings and analytics, and reply to conversations.",
};

export function roleLabel(role: Role | null | undefined) {
  return role ? ROLE_LABELS[role] : "Member";
}

export function canManageMembers(role: Role | null | undefined) {
  return role === "OWNER" || role === "ADMIN";
}

/** Roles this person may give to others. Only an Owner can create another Owner. */
export function assignableRoles(actor: Role | null | undefined): Role[] {
  if (actor === "OWNER") return ["OWNER", "ADMIN", "MEMBER"];
  if (actor === "ADMIN") return ["ADMIN", "MEMBER"];
  return [];
}

export function ownerCount(members: MemberLike[]) {
  return members.filter((member) => member.role === "OWNER").length;
}

export type MemberActions = {
  changeRole: boolean;
  remove: boolean;
  leave: boolean;
  /** Why the person cannot leave, when that is the only thing stopping them. */
  leaveBlockedReason: string | null;
};

export function actionsFor(actorRole: Role | null | undefined, actorId: string, member: MemberLike, members: MemberLike[]): MemberActions {
  const self = member.userId === actorId;
  const lastOwner = member.role === "OWNER" && ownerCount(members) <= 1;
  if (self) {
    return {
      changeRole: false,
      remove: false,
      leave: !lastOwner,
      leaveBlockedReason: lastOwner ? "You're the only Owner. Make someone else Owner before you leave." : null,
    };
  }
  const manages = canManageMembers(actorRole);
  const touchesOwner = member.role === "OWNER";
  const allowed = manages && (!touchesOwner || actorRole === "OWNER");
  return {
    changeRole: allowed && !lastOwner,
    remove: allowed && !lastOwner,
    leave: false,
    leaveBlockedReason: null,
  };
}

/** Owners can hand the workspace to anyone else already in it. */
export function ownershipCandidates<T extends MemberLike>(actorRole: Role | null | undefined, actorId: string, members: T[]): T[] {
  if (actorRole !== "OWNER") return [];
  return members.filter((member) => member.userId !== actorId);
}

/** Two letters for an avatar, from the part of the email before the @. */
export function initialsFor(email: string) {
  const name = email.split("@")[0] ?? "";
  const parts = name.split(/[._-]+/).filter(Boolean);
  const letters = parts.length >= 2 ? parts[0][0] + parts[1][0] : name.slice(0, 2);
  return letters.toUpperCase() || "?";
}

export function isValidEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
}

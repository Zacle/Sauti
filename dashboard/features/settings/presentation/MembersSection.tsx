"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { LoaderCircle, Send } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent } from "@/components/ui/overlays";
import { Select } from "@/components/ui/select";
import { toast } from "@/components/ui/sonner";
import { useAuth } from "@/hooks/useAuth";
import { authApi, workspacesApi } from "@/lib/api/auth";
import type { WorkspaceInvitation, WorkspaceMember, WorkspaceRole } from "@/types/api";
import {
  actionsFor,
  assignableRoles,
  canManageMembers,
  initialsFor,
  isValidEmail,
  ownershipCandidates,
  ROLE_DESCRIPTIONS,
  ROLE_LABELS,
  roleLabel,
  type Role,
} from "@/features/workspaces/domain/members";
import styles from "./WorkspaceSettings.module.css";

type Confirmation =
  | { kind: "remove"; member: WorkspaceMember }
  | { kind: "leave" }
  | { kind: "transfer"; member: WorkspaceMember };

const dateFormat = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });

/** Settings → Members: who is in this workspace, their roles, invitations, and ownership. */
export function MembersSection() {
  const { session, updateSession, selectWorkspace } = useAuth();
  const workspaceId = session?.tenant?.id ?? "";
  const myId = session?.account.id ?? "";
  const myRole = (session?.role ?? null) as Role | null;
  const manages = canManageMembers(myRole);

  const [members, setMembers] = useState<WorkspaceMember[]>([]);
  const [invitations, setInvitations] = useState<WorkspaceInvitation[]>([]);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState("");
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<WorkspaceRole>("MEMBER");
  const [inviting, setInviting] = useState(false);
  const [busyRow, setBusyRow] = useState("");
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [newOwnerId, setNewOwnerId] = useState("");

  const load = useCallback(async () => {
    if (!workspaceId) return;
    setFailure("");
    try {
      const [people, pending] = await Promise.all([
        workspacesApi.members(workspaceId),
        manages ? workspacesApi.invitations(workspaceId) : Promise.resolve([]),
      ]);
      setMembers(people);
      setInvitations(pending);
    } catch (caught) {
      setFailure(messageFrom(caught, "Unable to load members."));
    } finally {
      setLoading(false);
    }
  }, [workspaceId, manages]);

  useEffect(() => {
    void load();
  }, [load]);

  const roleOptions = useMemo(
    () => assignableRoles(myRole).map((role) => ({ value: role, label: ROLE_LABELS[role], description: ROLE_DESCRIPTIONS[role] })),
    [myRole],
  );
  const candidates = ownershipCandidates(myRole, myId, members);

  async function refreshAccount() {
    if (!session) return;
    const account = await authApi.me();
    updateSession({ ...session, account });
    if (session.tenant) selectWorkspace(session.tenant.id);
  }

  async function invite(event: FormEvent) {
    event.preventDefault();
    if (!isValidEmail(inviteEmail)) {
      setFailure("Enter a valid email address.");
      return;
    }
    setInviting(true);
    setFailure("");
    try {
      const sent = await workspacesApi.invite(workspaceId, inviteEmail.trim(), inviteRole);
      setInviteEmail("");
      toast.success("Invitation sent", { description: `${sent.email} will get an email with a link to join.` });
      await load();
    } catch (caught) {
      setFailure(messageFrom(caught, "Unable to send the invitation."));
    } finally {
      setInviting(false);
    }
  }

  async function changeRole(member: WorkspaceMember, role: string) {
    if (role === member.role) return;
    setBusyRow(member.userId);
    setFailure("");
    try {
      await workspacesApi.changeRole(workspaceId, member.userId, role as WorkspaceRole);
      toast.success("Role updated", { description: `${member.email} is now ${roleLabel(role as Role)}.` });
      await load();
    } catch (caught) {
      setFailure(messageFrom(caught, "Unable to change the role."));
    } finally {
      setBusyRow("");
    }
  }

  async function invitationAction(invitation: WorkspaceInvitation, action: "resend" | "revoke") {
    setBusyRow(invitation.id);
    setFailure("");
    try {
      if (action === "resend") {
        await workspacesApi.resendInvitation(workspaceId, invitation.id);
        toast.success("Invitation sent again", { description: `A new link went to ${invitation.email}. The old link no longer works.` });
      } else {
        await workspacesApi.revokeInvitation(workspaceId, invitation.id);
        toast.success("Invitation cancelled", { description: `The link sent to ${invitation.email} no longer works.` });
      }
      await load();
    } catch (caught) {
      setFailure(messageFrom(caught, "Unable to update the invitation."));
    } finally {
      setBusyRow("");
    }
  }

  async function confirm() {
    if (!confirmation) return;
    setBusyRow("confirm");
    setFailure("");
    try {
      if (confirmation.kind === "remove") {
        await workspacesApi.removeMember(workspaceId, confirmation.member.userId);
        toast.success("Member removed", { description: `${confirmation.member.email} can no longer open this workspace.` });
        setConfirmation(null);
        await load();
      } else if (confirmation.kind === "leave") {
        await workspacesApi.removeMember(workspaceId, myId);
        const account = await authApi.me();
        if (session) updateSession({ ...session, account });
        const next = account.workspaces[0]?.workspace.id;
        if (next) selectWorkspace(next);
        window.location.assign("/dashboard");
      } else {
        await workspacesApi.transferOwnership(workspaceId, confirmation.member.userId);
        toast.success("Ownership transferred", { description: `${confirmation.member.email} is now the Owner. You're an Admin.` });
        setConfirmation(null);
        setNewOwnerId("");
        await refreshAccount();
        await load();
      }
    } catch (caught) {
      setFailure(messageFrom(caught, "That change could not be made."));
      setConfirmation(null);
    } finally {
      setBusyRow("");
    }
  }

  const me = members.find((member) => member.userId === myId);
  const myActions = me ? actionsFor(myRole, myId, me, members) : null;

  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <div>
          <h2>Members</h2>
          <p>People who can open this workspace and what each of them can do.</p>
        </div>
        {!loading ? <span className={styles.statusNeutral}>{members.length} {members.length === 1 ? "member" : "members"}</span> : null}
      </div>

      {failure ? <div className={styles.inlineError} role="alert">{failure}</div> : null}

      {manages ? (
        <form className={styles.inviteForm} onSubmit={invite}>
          <label className={styles.inputField}>
            <span>Invite by email</span>
            <input
              type="email"
              autoComplete="off"
              placeholder="colleague@business.com"
              value={inviteEmail}
              onChange={(event) => setInviteEmail(event.target.value)}
            />
          </label>
          <label className={styles.inputField}>
            <span>Role</span>
            <Select size="sm" ariaLabel="Role for the invited person" value={inviteRole} onValueChange={(value) => setInviteRole(value as WorkspaceRole)} options={roleOptions} />
          </label>
          <Button size="xs" type="submit" disabled={inviting || !inviteEmail.trim()}>
            {inviting ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <Send aria-hidden="true" />}
            {inviting ? "Sending…" : "Send invitation"}
          </Button>
        </form>
      ) : null}

      <h3 className={styles.groupTitle}>People</h3>
      {loading ? (
        <p className={styles.memberEmpty}><LoaderCircle className="animate-spin" size={15} aria-hidden="true" /> Loading members…</p>
      ) : (
        <ul className={styles.memberList}>
          {members.map((member) => {
            const actions = actionsFor(myRole, myId, member, members);
            const self = member.userId === myId;
            return (
              <li key={member.userId} className={styles.memberRow}>
                <span className={styles.avatar} aria-hidden="true">{initialsFor(member.email)}</span>
                <span className={styles.memberIdentity}>
                  <strong>{member.email}{self ? <em> (you)</em> : null}</strong>
                  <small>Joined {dateFormat.format(new Date(member.joinedAt))}</small>
                </span>
                <span className={styles.memberRole}>
                  {actions.changeRole ? (
                    <Select
                      size="sm"
                      ariaLabel={`Role for ${member.email}`}
                      value={member.role}
                      disabled={busyRow === member.userId}
                      onValueChange={(value) => void changeRole(member, value)}
                      options={roleOptions.some((option) => option.value === member.role)
                        ? roleOptions
                        : [...roleOptions, { value: member.role, label: ROLE_LABELS[member.role], disabled: true }]}
                    />
                  ) : (
                    <b>{roleLabel(member.role)}</b>
                  )}
                </span>
                <span className={styles.memberActions}>
                  {actions.remove ? (
                    <Button size="xs" variant="ghost" onClick={() => setConfirmation({ kind: "remove", member })}>Remove</Button>
                  ) : null}
                </span>
              </li>
            );
          })}
        </ul>
      )}

      {manages && invitations.length > 0 ? (
        <>
          <h3 className={styles.groupTitle}>Waiting to join</h3>
          <ul className={styles.memberList}>
            {invitations.map((invitation) => (
              <li key={invitation.id} className={styles.memberRow}>
                <span className={styles.avatar} aria-hidden="true">{initialsFor(invitation.email)}</span>
                <span className={styles.memberIdentity}>
                  <strong>{invitation.email}</strong>
                  <small>{invitation.expired ? "Link expired" : `Link expires ${dateFormat.format(new Date(invitation.expiresAt))}`}</small>
                </span>
                <span className={styles.memberRole}><b>{roleLabel(invitation.role)}</b></span>
                <span className={styles.memberActions}>
                  <Button size="xs" variant="ghost" disabled={busyRow === invitation.id} onClick={() => void invitationAction(invitation, "resend")}>
                    {invitation.expired ? "Send new link" : "Resend"}
                  </Button>
                  <Button size="xs" variant="ghost" disabled={busyRow === invitation.id} onClick={() => void invitationAction(invitation, "revoke")}>
                    Cancel
                  </Button>
                </span>
              </li>
            ))}
          </ul>
        </>
      ) : null}

      <h3 className={styles.groupTitle}>What each role can do</h3>
      <div className={styles.roleGuide}>
        {(Object.keys(ROLE_LABELS) as Role[]).map((role) => (
          <div key={role} className={styles.readOnlyRow}>
            <span>
              <strong>{ROLE_LABELS[role]}{role === myRole ? <em> · your role</em> : null}</strong>
              <small>{ROLE_DESCRIPTIONS[role]}</small>
            </span>
          </div>
        ))}
      </div>

      {candidates.length > 0 ? (
        <div className={styles.dangerZone}>
          <div>
            <span>Ownership</span>
            <h3>Hand over this workspace</h3>
            <p>The new Owner gets billing and closing rights. You stay on as Admin.</p>
          </div>
          <div className={styles.transferControls}>
            <Select
              size="sm"
              ariaLabel="New Owner"
              placeholder="Choose a member"
              value={newOwnerId}
              onValueChange={setNewOwnerId}
              options={candidates.map((member) => ({ value: member.userId, label: member.email }))}
            />
            <Button
              size="xs"
              variant="danger-soft"
              disabled={!newOwnerId}
              onClick={() => {
                const member = members.find((item) => item.userId === newOwnerId);
                if (member) setConfirmation({ kind: "transfer", member });
              }}
            >
              Transfer
            </Button>
          </div>
        </div>
      ) : null}

      {myActions ? (
        <div className={styles.actionBar}>
          <small>{myActions.leaveBlockedReason ?? "Leaving removes your access to this workspace. Your other workspaces are not affected."}</small>
          <Button size="xs" variant="secondary" disabled={!myActions.leave} onClick={() => setConfirmation({ kind: "leave" })}>
            Leave workspace
          </Button>
        </div>
      ) : null}

      <Dialog open={confirmation !== null} onOpenChange={(open) => !open && busyRow !== "confirm" && setConfirmation(null)}>
        {confirmation ? (
          <DialogContent title={confirmTitle(confirmation, session?.tenant?.businessName ?? "this workspace")} description={confirmText(confirmation)}>
            <div className="flex justify-end gap-2">
              <Button size="sm" variant="secondary" disabled={busyRow === "confirm"} onClick={() => setConfirmation(null)}>Keep as is</Button>
              <Button size="sm" variant="danger" disabled={busyRow === "confirm"} onClick={() => void confirm()}>
                {busyRow === "confirm" ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : null}
                {confirmation.kind === "remove" ? "Remove" : confirmation.kind === "leave" ? "Leave" : "Transfer ownership"}
              </Button>
            </div>
          </DialogContent>
        ) : null}
      </Dialog>
    </section>
  );
}

function confirmTitle(confirmation: Confirmation, workspaceName: string) {
  if (confirmation.kind === "remove") return `Remove ${confirmation.member.email}?`;
  if (confirmation.kind === "leave") return `Leave ${workspaceName}?`;
  return `Make ${confirmation.member.email} the Owner?`;
}

function confirmText(confirmation: Confirmation) {
  if (confirmation.kind === "remove") return "They lose access straight away. You can invite them again later.";
  if (confirmation.kind === "leave") return "You lose access straight away. Someone in the workspace will need to invite you to come back.";
  return "They get billing and closing rights. You stay on as Admin and can't undo this yourself.";
}

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error && caught.message ? caught.message : fallback;
}

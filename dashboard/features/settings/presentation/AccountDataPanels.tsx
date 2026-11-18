"use client";

import { useEffect, useState } from "react";
import { Download, LoaderCircle, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/primitives";
import { Dialog, DialogContent } from "@/components/ui/overlays";
import { useAuth } from "@/hooks/useAuth";
import { authApi, workspaceAdminApi } from "@/lib/api/auth";
import type { ActivityEntry, SoleOwnedWorkspace } from "@/types/api";
import styles from "./WorkspaceSettings.module.css";

const timeFormat = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
const CONFIRMATION_PHRASE = "delete my account";

/** Settings → Data & privacy: the Owner downloads everything the workspace holds. */
export function WorkspaceExportPanel() {
  const { session } = useAuth();
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");
  if (session?.role !== "OWNER" || !session.tenant) return null;
  const workspace = session.tenant;

  async function download() {
    setBusy(true);
    setFailure("");
    try {
      const blob = await workspaceAdminApi.exportWorkspace(workspace.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `sauti-${workspace.businessName.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-${new Date().toISOString().slice(0, 10)}.json`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (caught) {
      setFailure(caught instanceof Error ? caught.message : "Unable to prepare the download.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className={styles.panel} style={{ marginTop: 16 }}>
      <div className={styles.panelHeader}>
        <div>
          <h2>Download your data</h2>
          <p>One file with the workspace profile, members, agents, bookings and calls with their transcripts. Recordings are not included.</p>
        </div>
      </div>
      {failure ? <div className={styles.inlineError} role="alert">{failure}</div> : null}
      <div className={styles.actionBar}>
        <small>Only Owners can download. The download is recorded in the activity log.</small>
        <Button size="xs" variant="secondary" disabled={busy} onClick={() => void download()}>
          {busy ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <Download aria-hidden="true" />}
          {busy ? "Preparing…" : "Download workspace data"}
        </Button>
      </div>
    </section>
  );
}

/** Settings → Security: recent sign-ins and security changes on this account. */
export function RecentAccountActivity() {
  const [entries, setEntries] = useState<ActivityEntry[] | null>(null);
  useEffect(() => {
    authApi.accountActivity().then(setEntries).catch(() => setEntries([]));
  }, []);

  return (
    <section className={styles.panel} style={{ marginTop: 16 }}>
      <div className={styles.panelHeader}>
        <div>
          <h2>Recent account activity</h2>
          <p>Sign-ins and security changes on your account. If something here wasn&apos;t you, change your password and sign out everywhere.</p>
        </div>
      </div>
      {entries === null ? (
        <p className={styles.memberEmpty}><LoaderCircle className="animate-spin" size={15} aria-hidden="true" /> Loading…</p>
      ) : entries.length === 0 ? (
        <p className={styles.memberEmpty}>No activity recorded yet.</p>
      ) : (
        <ul className={styles.memberList}>
          {entries.slice(0, 8).map((entry) => (
            <li key={entry.id} className={styles.activityRow}>
              <span className={styles.memberIdentity}><strong>{entry.summary}</strong></span>
              <time dateTime={entry.at}>{timeFormat.format(new Date(entry.at))}</time>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/**
 * Settings → Security: delete the account. Anyone may. Workspaces where the person is the only
 * Owner close with it, and the dialog lists them so that is a deliberate choice.
 */
export function DeleteAccountPanel() {
  const { session, logout } = useAuth();
  const hasPassword = session?.account.hasPassword ?? true;
  const [open, setOpen] = useState(false);
  const [impact, setImpact] = useState<SoleOwnedWorkspace[] | null>(null);
  const [password, setPassword] = useState("");
  const [phrase, setPhrase] = useState("");
  const [closeConfirmed, setCloseConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");

  function start() {
    setOpen(true);
    setFailure("");
    setImpact(null);
    setCloseConfirmed(false);
    authApi.deletionImpact().then(setImpact).catch(() => setImpact([]));
  }

  async function deleteAccount() {
    setBusy(true);
    setFailure("");
    try {
      await authApi.deleteAccount(password, phrase, closeConfirmed);
      await logout();
      window.location.assign("/");
    } catch (caught) {
      setFailure(caught instanceof Error ? caught.message : "Unable to delete the account.");
      setBusy(false);
    }
  }

  const closes = impact ?? [];
  const ready = (hasPassword ? password.length > 0 : phrase.trim().toLowerCase() === CONFIRMATION_PHRASE)
    && (closes.length === 0 || closeConfirmed) && impact !== null;

  return (
    <section className={styles.panel} style={{ marginTop: 16 }}>
      <div className={styles.dangerZone}>
        <div>
          <span>Account</span>
          <h3>Delete your account</h3>
          <p>Removes you from every workspace and signs you out everywhere. Workspaces you alone own are closed.</p>
        </div>
        <Button size="xs" variant="danger-soft" onClick={start}>
          <Trash2 aria-hidden="true" />
          Delete account
        </Button>
      </div>

      <Dialog open={open} onOpenChange={(next) => !busy && setOpen(next)}>
        <DialogContent title="Delete your account?" description="Your email, password and preferences are removed. This can't be undone.">
          <div className="grid gap-4">
            {impact === null ? (
              <p className="m-0 flex items-center gap-2 text-sm text-fg-soft"><LoaderCircle className="size-4 animate-spin" aria-hidden="true" /> Checking your workspaces…</p>
            ) : closes.length > 0 ? (
              <div className="grid gap-2 rounded-[var(--radius-control)] border border-danger/30 bg-danger/5 p-3">
                <p className="m-0 text-sm font-medium text-ink">These workspaces will close, because you are their only Owner:</p>
                <ul className="m-0 grid gap-1 pl-4 text-sm text-fg-soft">
                  {closes.map((workspace) => (
                    <li key={workspace.workspaceId}>
                      {workspace.name}
                      {workspace.otherMembers > 0 ? ` (${workspace.otherMembers} other ${workspace.otherMembers === 1 ? "member loses" : "members lose"} access)` : ""}
                    </li>
                  ))}
                </ul>
                <p className="m-0 text-[0.8rem] text-fg-muted">To keep a workspace running, make someone else its Owner first in Settings → Members.</p>
                <label className="mt-1 flex items-start gap-2 text-sm text-ink">
                  <Checkbox checked={closeConfirmed} onCheckedChange={(value) => setCloseConfirmed(value === true)} aria-label="Close these workspaces" />
                  <span>Close {closes.length === 1 ? "this workspace" : "these workspaces"} and end the service</span>
                </label>
              </div>
            ) : null}

            {hasPassword ? (
              <label className={styles.inputField} style={{ marginBottom: 0 }}>
                <span>Your password</span>
                <input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} />
              </label>
            ) : (
              <label className={styles.inputField} style={{ marginBottom: 0 }}>
                <span>Type &quot;{CONFIRMATION_PHRASE}&quot; to confirm</span>
                <input value={phrase} onChange={(event) => setPhrase(event.target.value)} autoComplete="off" />
              </label>
            )}

            {failure ? <div className={styles.inlineError} style={{ margin: 0 }} role="alert">{failure}</div> : null}

            <div className="flex justify-end gap-2">
              <Button size="sm" variant="secondary" disabled={busy} onClick={() => setOpen(false)}>Keep my account</Button>
              <Button size="sm" variant="danger" disabled={busy || !ready} onClick={() => void deleteAccount()}>
                {busy ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : null}
                Delete account
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </section>
  );
}

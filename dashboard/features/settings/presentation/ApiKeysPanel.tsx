"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Check, Copy, KeyRound, LoaderCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent } from "@/components/ui/overlays";
import { Select } from "@/components/ui/select";
import { toast } from "@/components/ui/sonner";
import { useAuth } from "@/hooks/useAuth";
import { workspaceAdminApi } from "@/lib/api/auth";
import { canManageMembers, type Role } from "@/features/workspaces/domain/members";
import type { CreatedApiKey, WorkspaceApiKey } from "@/types/api";
import styles from "./WorkspaceSettings.module.css";

const ACCESS_OPTIONS = [
  { value: "read_only", label: "Read only", description: "See calls, bookings, agents and analytics." },
  { value: "full", label: "Full access", description: "Also change agents, numbers and integrations. Never members or billing." },
];

const dateFormat = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });

/** Settings → Developer: keys for software that calls Sauti on this workspace's behalf. */
export function ApiKeysPanel() {
  const { session } = useAuth();
  const workspaceId = session?.tenant?.id ?? "";
  const manages = canManageMembers((session?.role ?? null) as Role | null);
  const [keys, setKeys] = useState<WorkspaceApiKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [name, setName] = useState("");
  const [access, setAccess] = useState<"read_only" | "full">("read_only");
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<CreatedApiKey | null>(null);
  const [copied, setCopied] = useState(false);
  const [failure, setFailure] = useState("");
  const [revoking, setRevoking] = useState<WorkspaceApiKey | null>(null);

  const load = useCallback(async () => {
    if (!workspaceId || !manages) return;
    try {
      setKeys(await workspaceAdminApi.apiKeys(workspaceId));
    } catch (caught) {
      setFailure(caught instanceof Error ? caught.message : "Unable to load API keys.");
    } finally {
      setLoading(false);
    }
  }, [workspaceId, manages]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!manages) return null;

  async function create(event: FormEvent) {
    event.preventDefault();
    setCreating(true);
    setFailure("");
    try {
      setCreated(await workspaceAdminApi.createApiKey(workspaceId, name.trim(), access));
      setName("");
      setCopied(false);
      await load();
    } catch (caught) {
      setFailure(caught instanceof Error ? caught.message : "Unable to create the key.");
    } finally {
      setCreating(false);
    }
  }

  async function revoke(key: WorkspaceApiKey) {
    try {
      await workspaceAdminApi.revokeApiKey(workspaceId, key.id);
      toast.success("Key revoked", { description: `Anything using "${key.name}" stops working now.` });
      setRevoking(null);
      await load();
    } catch (caught) {
      setFailure(caught instanceof Error ? caught.message : "Unable to revoke the key.");
      setRevoking(null);
    }
  }

  async function copySecret() {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created.secret);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  const active = keys.filter((key) => !key.revokedAt);

  return (
    <section className={styles.panel} style={{ marginTop: 16 }}>
      <div className={styles.panelHeader}>
        <div>
          <h2>API keys</h2>
          <p>For your own software calling Sauti. A key works only in this workspace and is never allowed to manage members or billing.</p>
        </div>
        {!loading ? <span className={styles.statusNeutral}>{active.length} active</span> : null}
      </div>
      {failure ? <div className={styles.inlineError} role="alert">{failure}</div> : null}

      <form className={styles.inviteForm} onSubmit={create}>
        <label className={styles.inputField}>
          <span>Key name</span>
          <input maxLength={80} placeholder="CRM sync" value={name} onChange={(event) => setName(event.target.value)} />
        </label>
        <label className={styles.inputField}>
          <span>Access</span>
          <Select size="sm" ariaLabel="Key access" value={access} onValueChange={(value) => setAccess(value as "read_only" | "full")} options={ACCESS_OPTIONS} />
        </label>
        <Button size="xs" type="submit" disabled={creating || !name.trim()}>
          {creating ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <KeyRound aria-hidden="true" />}
          {creating ? "Creating…" : "Create key"}
        </Button>
      </form>

      {loading ? (
        <p className={styles.memberEmpty}><LoaderCircle className="animate-spin" size={15} aria-hidden="true" /> Loading keys…</p>
      ) : keys.length === 0 ? (
        <p className={styles.memberEmpty}>No keys yet.</p>
      ) : (
        <ul className={styles.memberList}>
          {keys.map((key) => (
            <li key={key.id} className={styles.memberRow}>
              <span className={styles.avatar} aria-hidden="true"><KeyRound size={14} /></span>
              <span className={styles.memberIdentity}>
                <strong>{key.name}</strong>
                <small>
                  <code>{key.hint}</code> · created {dateFormat.format(new Date(key.createdAt))} ·{" "}
                  {key.revokedAt ? `revoked ${dateFormat.format(new Date(key.revokedAt))}` : key.lastUsedAt ? `last used ${dateFormat.format(new Date(key.lastUsedAt))}` : "never used"}
                </small>
              </span>
              <span className={styles.memberRole}><b>{key.access === "FULL" ? "Full access" : "Read only"}</b></span>
              <span className={styles.memberActions}>
                {!key.revokedAt ? <Button size="xs" variant="ghost" onClick={() => setRevoking(key)}>Revoke</Button> : null}
              </span>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={created !== null} onOpenChange={(open) => !open && setCreated(null)}>
        {created ? (
          <DialogContent title="Copy your new key" description="This is the only time Sauti shows it. Store it somewhere safe, such as your password manager or your server's secrets.">
            <div className="grid gap-3">
              <code className="block break-all rounded-[var(--radius-control)] border border-line bg-surface-2 px-3 py-2.5 font-mono text-[0.82rem] text-ink">{created.secret}</code>
              <p className="m-0 text-[0.8rem] text-fg-muted">Send it as <code>Authorization: Bearer {"<key>"}</code> to <code>/api/v1/workspaces/{workspaceId}/…</code></p>
              <div className="flex justify-end gap-2">
                <Button size="sm" variant="secondary" onClick={() => void copySecret()}>
                  {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
                  {copied ? "Copied" : "Copy key"}
                </Button>
                <Button size="sm" onClick={() => setCreated(null)}>Done</Button>
              </div>
            </div>
          </DialogContent>
        ) : null}
      </Dialog>

      <Dialog open={revoking !== null} onOpenChange={(open) => !open && setRevoking(null)}>
        {revoking ? (
          <DialogContent title={`Revoke "${revoking.name}"?`} description="Anything using this key stops working straight away. This can't be undone; create a new key instead.">
            <div className="flex justify-end gap-2">
              <Button size="sm" variant="secondary" onClick={() => setRevoking(null)}>Keep key</Button>
              <Button size="sm" variant="danger" onClick={() => void revoke(revoking)}>Revoke key</Button>
            </div>
          </DialogContent>
        ) : null}
      </Dialog>
    </section>
  );
}

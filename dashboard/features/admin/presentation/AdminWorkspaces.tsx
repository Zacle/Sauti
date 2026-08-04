"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { Bot, CalendarCheck, LoaderCircle, PhoneCall, Save, Search, ShieldCheck, Users, X } from "lucide-react";
import { configureAdminPilotPolicy, getAdminWorkspace, getAdminWorkspaces } from "@/lib/api/admin";
import type { AdminWorkspace } from "@/types/api";
import styles from "./AdminDirectory.module.css";

export function AdminWorkspaces() {
  const [workspaces, setWorkspaces] = useState<AdminWorkspace[]>([]);
  const [query, setQuery] = useState("");
  const [total, setTotal] = useState(0);
  const [selected, setSelected] = useState<AdminWorkspace | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async (search = "") => {
    setLoading(true); setError("");
    try {
      const result = await getAdminWorkspaces(search);
      setWorkspaces(result.workspaces); setTotal(result.total);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to load workspaces.");
    } finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  function search(event: FormEvent) { event.preventDefault(); void load(query); }
  async function open(workspace: AdminWorkspace) {
    setError("");
    try { setSelected(await getAdminWorkspace(workspace.id)); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Unable to load workspace details."); }
  }

  return <div className={styles.page}>
    <div className={styles.heading}><div><span>PLATFORM DIRECTORY</span><h1>Workspaces</h1><p>Inspect account activity and pilot usage without entering or changing tenant data.</p></div></div>
    <form className={styles.searchBar} onSubmit={search}><Search size={17}/><input aria-label="Search workspaces" onChange={(event) => setQuery(event.target.value)} placeholder="Search business, owner email, or country" value={query}/><button type="submit">Search</button></form>
    {error && <div className={styles.error} role="alert">{error}</div>}
    <div className={styles.listHeader}><strong>{total} workspaces</strong><span>Newest first</span></div>
    {loading ? <div className={styles.loadingState}><LoaderCircle className={styles.spin} size={22}/>Loading workspaces…</div> : <section className={styles.dataList}>
      {workspaces.map((workspace) => <button key={workspace.id} onClick={() => void open(workspace)} type="button">
        <span className={styles.avatar}>{workspace.businessName.slice(0, 1).toUpperCase()}</span>
        <span className={styles.primary}><strong>{workspace.businessName}</strong><small>{workspace.email} · {workspace.countryCode}</small></span>
        <span><small>PLAN</small><strong>{workspace.plan}</strong></span><span><small>CALLS</small><strong>{workspace.calls}</strong></span><span className={`${styles.status} ${styles[workspace.status] ?? ""}`}>{workspace.status}</span>
      </button>)}
      {!workspaces.length && <div className={styles.empty}>No workspaces match this search.</div>}
    </section>}
    {selected && <WorkspaceDetail
      workspace={selected}
      onClose={() => setSelected(null)}
      onSaved={(updated) => {
        setSelected(updated);
        setWorkspaces((items) => items.map((item) => item.id === updated.id ? updated : item));
      }}
    />}
  </div>;
}

function WorkspaceDetail({ workspace, onClose, onSaved }: { workspace: AdminWorkspace; onClose: () => void; onSaved: (value: AdminWorkspace) => void }) {
  const stats = [["Agents", workspace.agents, Bot], ["Customers", workspace.customers, Users], ["Calls", workspace.calls, PhoneCall], ["Bookings", workspace.bookings, CalendarCheck]] as const;
  const policy = workspace.pilotPolicy;
  const [status, setStatus] = useState(policy?.status ?? "pending");
  const [currency, setCurrency] = useState(policy?.currency ?? "USD");
  const [budget, setBudget] = useState(String(policy?.monthlyBudget ?? 0));
  const [phoneNumbers, setPhoneNumbers] = useState(policy?.phoneNumbersApproved ?? false);
  const [liveCalling, setLiveCalling] = useState(policy?.liveCallingApproved ?? false);
  const [sms, setSms] = useState(policy?.smsApproved ?? false);
  const [whatsapp, setWhatsapp] = useState(policy?.whatsappApproved ?? false);
  const [notes, setNotes] = useState(policy?.notes ?? "");
  const [saving, setSaving] = useState(false);
  const [policyError, setPolicyError] = useState("");
  async function savePolicy() {
    setSaving(true); setPolicyError("");
    try { onSaved(await configureAdminPilotPolicy(workspace.id, { status, currency, monthlyBudget: Number(budget), phoneNumbersApproved: phoneNumbers, liveCallingApproved: liveCalling, smsApproved: sms, whatsappApproved: whatsapp, notes })); }
    catch (caught) { setPolicyError(caught instanceof Error ? caught.message : "Unable to save pilot policy."); }
    finally { setSaving(false); }
  }
  return <div className={styles.detailBackdrop} onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <aside className={styles.detailPanel} aria-label={`${workspace.businessName} details`}>
      <button className={styles.closeDetail} onClick={onClose} type="button"><X size={18}/><span className={styles.srOnly}>Close</span></button>
      <span className={styles.detailKicker}>WORKSPACE DETAIL</span><h2>{workspace.businessName}</h2><p>{workspace.email} · {workspace.countryCode}</p>
      <div className={styles.detailBadges}><span>{workspace.status}</span><span>{workspace.plan} plan</span></div>
      <section className={styles.detailStats}>{stats.map(([label, value, Icon]) => <article key={label}><Icon size={17}/><small>{label}</small><strong>{value}</strong></article>)}</section>
      <dl className={styles.detailList}><div><dt>Minutes this cycle</dt><dd>{workspace.minutesUsed} / {workspace.minutesLimit}</dd></div><div><dt>Created</dt><dd>{new Date(workspace.createdAt).toLocaleString()}</dd></div><div><dt>Workspace ID</dt><dd>{workspace.id}</dd></div></dl>
      <section className={styles.policyEditor}><h3><ShieldCheck size={17}/>Pilot provider approvals</h3><p>Paid provider operations stay blocked until this policy explicitly approves them.</p>{policyError && <div className={styles.error} role="alert">{policyError}</div>}<div className={styles.policyGrid}><label>Status<select value={status} onChange={(event) => setStatus(event.target.value)}><option value="pending">Pending</option><option value="approved">Approved</option><option value="suspended">Suspended</option></select></label><label>Monthly budget<div className={styles.moneyInput}><input aria-label="Pilot budget currency" maxLength={3} value={currency} onChange={(event) => setCurrency(event.target.value.toUpperCase())}/><input aria-label="Monthly pilot budget" min="0" step="0.01" type="number" value={budget} onChange={(event) => setBudget(event.target.value)}/></div></label></div><div className={styles.capabilities}>{[["Phone numbers", phoneNumbers, setPhoneNumbers], ["Live calling", liveCalling, setLiveCalling], ["SMS", sms, setSms], ["WhatsApp", whatsapp, setWhatsapp]].map(([label, checked, setter]) => <label key={String(label)}><input checked={Boolean(checked)} onChange={(event) => (setter as (value: boolean) => void)(event.target.checked)} type="checkbox"/>{String(label)}</label>)}</div><label className={styles.policyNotes}>Internal approval notes<textarea value={notes} onChange={(event) => setNotes(event.target.value)}/></label><button disabled={saving || !currency.match(/^[A-Z]{3}$/) || !Number.isFinite(Number(budget)) || Number(budget) < 0} onClick={() => void savePolicy()} type="button">{saving ? <LoaderCircle className={styles.spin} size={16}/> : <Save size={16}/>}Save approval policy</button>{policy?.approvedBy && <small>Last approved by {policy.approvedBy}{policy.approvedAt ? ` · ${new Date(policy.approvedAt).toLocaleString()}` : ""}</small>}</section>
    </aside>
  </div>;
}

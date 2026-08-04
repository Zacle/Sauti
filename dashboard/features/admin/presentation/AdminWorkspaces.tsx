"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { Bot, CalendarCheck, LoaderCircle, PhoneCall, Search, Users, X } from "lucide-react";
import { getAdminWorkspace, getAdminWorkspaces } from "@/lib/api/admin";
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
    {selected && <WorkspaceDetail workspace={selected} onClose={() => setSelected(null)}/>} 
  </div>;
}

function WorkspaceDetail({ workspace, onClose }: { workspace: AdminWorkspace; onClose: () => void }) {
  const stats = [["Agents", workspace.agents, Bot], ["Customers", workspace.customers, Users], ["Calls", workspace.calls, PhoneCall], ["Bookings", workspace.bookings, CalendarCheck]] as const;
  return <div className={styles.detailBackdrop} onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <aside className={styles.detailPanel} aria-label={`${workspace.businessName} details`}>
      <button className={styles.closeDetail} onClick={onClose} type="button"><X size={18}/><span className={styles.srOnly}>Close</span></button>
      <span className={styles.detailKicker}>WORKSPACE DETAIL</span><h2>{workspace.businessName}</h2><p>{workspace.email} · {workspace.countryCode}</p>
      <div className={styles.detailBadges}><span>{workspace.status}</span><span>{workspace.plan} plan</span></div>
      <section className={styles.detailStats}>{stats.map(([label, value, Icon]) => <article key={label}><Icon size={17}/><small>{label}</small><strong>{value}</strong></article>)}</section>
      <dl className={styles.detailList}><div><dt>Minutes this cycle</dt><dd>{workspace.minutesUsed} / {workspace.minutesLimit}</dd></div><div><dt>Created</dt><dd>{new Date(workspace.createdAt).toLocaleString()}</dd></div><div><dt>Workspace ID</dt><dd>{workspace.id}</dd></div></dl>
      <p className={styles.readOnlyNote}>Read-only platform view. Tenant data cannot be changed here.</p>
    </aside>
  </div>;
}

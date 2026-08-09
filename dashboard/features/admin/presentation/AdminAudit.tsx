"use client";

import { useCallback, useEffect, useState } from "react";
import { RefreshCw, ShieldCheck } from "lucide-react";
import { getAdminAudit } from "@/lib/api/admin";
import type { AdminAuditEvent } from "@/types/api";
import styles from "./AdminAudit.module.css";
import polish from "./AdminPolish.module.css";

export function AdminAudit() {
  const [events, setEvents] = useState<AdminAuditEvent[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState("");
  const load = useCallback(() => getAdminAudit().then((result) => { setEvents(result.events); setTotal(result.total); setError(""); }).catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load audit history.")), []);
  useEffect(() => { void load(); }, [load]);

  return <div className={`${styles.page} ${polish.auditPage}`}>
    <div className={`${styles.heading} ${polish.auditHeading}`}><div><span>GOVERNANCE</span><h1>Audit history</h1><p>Immutable records of privileged platform operations.</p></div><button onClick={() => void load()} type="button"><RefreshCw size={16}/>Refresh</button></div>
    {error && <div className={styles.error} role="alert">{error}</div>}
    <div className={`${styles.count} ${polish.auditCount}`}>{total} recorded events</div>
    <section className={`${styles.timeline} ${polish.auditTimeline}`}>{events.map((event) => <article key={event.id}><span className={styles.icon}><ShieldCheck size={17}/></span><div><strong>{label(event.action)}</strong><p>{event.summary}</p><small>{event.actorEmail} · {event.resourceType} {event.resourceId}</small></div><time>{new Date(event.createdAt).toLocaleString()}</time></article>)}{!events.length && !error && <div className={styles.empty}>No privileged changes have been recorded yet.</div>}</section>
  </div>;
}

function label(action: string) { return action.split(".").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" · "); }

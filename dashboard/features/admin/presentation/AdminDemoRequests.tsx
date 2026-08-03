"use client";

import { useCallback, useEffect, useState } from "react";
import { Check, LoaderCircle, Mail, Phone, RefreshCw } from "lucide-react";
import { getAdminDemoRequests, inviteDemoRequest } from "@/lib/api/admin";
import type { AdminDemoRequest } from "@/types/api";
import styles from "./AdminViews.module.css";

export function AdminDemoRequests() {
  const [requests, setRequests] = useState<AdminDemoRequest[]>([]);
  const [total, setTotal] = useState(0);
  const [busyId, setBusyId] = useState("");
  const [error, setError] = useState("");
  const load = useCallback(() => getAdminDemoRequests().then((result) => { setRequests(result.requests); setTotal(result.total); }).catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load demo requests.")), []);
  useEffect(() => { void load(); }, [load]);

  async function invite(request: AdminDemoRequest) {
    setBusyId(request.id); setError("");
    try { await inviteDemoRequest(request.id); await load(); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Unable to send invitation."); }
    finally { setBusyId(""); }
  }

  return <div className={styles.page}>
    <div className={styles.heading}><div><span>ACQUISITION</span><h1>Demo requests</h1><p>Review qualified businesses before giving them access to a Sauti pilot.</p></div><button onClick={() => void load()} type="button"><RefreshCw size={16}/>Refresh</button></div>
    {error && <div className={styles.error} role="alert">{error}</div>}
    <div className={styles.listHeader}><strong>{total} requests</strong><span>Newest first</span></div>
    <section className={styles.requestList}>
      {requests.map((request) => <article key={request.id}>
        <div className={styles.requestTop}><div><span className={`${styles.status} ${styles[request.status] ?? ""}`}>{request.status}</span><h2>{request.businessName}</h2><p>{request.contactName} · {request.industry} · {request.countryCode}</p></div><time>{new Date(request.createdAt).toLocaleDateString()}</time></div>
        <div className={styles.contact}><a href={`mailto:${request.email}`}><Mail size={15}/>{request.email}</a>{request.phone && <a href={`tel:${request.phone}`}><Phone size={15}/>{request.phone}</a>}<span>{request.monthlyCallVolume} monthly conversations</span></div>
        <p className={styles.useCase}>{request.primaryUseCase}</p>
        <div className={styles.requestBottom}><span>{request.channels.split(",").join(" · ")}</span>{request.status === "new" ? <button disabled={busyId === request.id} onClick={() => void invite(request)} type="button">{busyId === request.id ? <LoaderCircle className={styles.spin} size={16}/> : <Mail size={16}/>}Approve & invite</button> : <span className={styles.complete}><Check size={15}/>{request.status === "activated" ? "Workspace activated" : "Invitation sent"}</span>}</div>
      </article>)}
      {!requests.length && !error && <div className={styles.empty}>No demo requests yet.</div>}
    </section>
  </div>;
}

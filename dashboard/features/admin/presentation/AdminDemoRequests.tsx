"use client";

import { useCallback, useEffect, useState } from "react";
import { Check, LoaderCircle, Mail, Phone, RefreshCw, RotateCw, Save, UserRound, XCircle } from "lucide-react";
import { getAdminDemoRequests, inviteDemoRequest, rejectDemoRequest, resendDemoInvitation, revokeDemoInvitation, updateDemoRequestOperations } from "@/lib/api/admin";
import type { AdminDemoRequest } from "@/types/api";
import styles from "./AdminViews.module.css";
import ops from "./AdminDemoRequests.module.css";

type Draft = { assignedTo: string; internalNotes: string; rejectionReason: string };

export function AdminDemoRequests() {
  const [requests, setRequests] = useState<AdminDemoRequest[]>([]);
  const [total, setTotal] = useState(0);
  const [busyId, setBusyId] = useState("");
  const [error, setError] = useState("");
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const load = useCallback(() => getAdminDemoRequests().then((result) => {
    setRequests(result.requests); setTotal(result.total);
    setDrafts((current) => Object.fromEntries(result.requests.map((request) => [request.id, current[request.id] ?? { assignedTo: request.assignedTo ?? "", internalNotes: request.internalNotes ?? "", rejectionReason: "" }])));
  }).catch((caught) => setError(message(caught, "Unable to load demo requests."))), []);
  useEffect(() => { void load(); }, [load]);

  async function run(id: string, action: () => Promise<unknown>, fallback: string) {
    setBusyId(id); setError("");
    try { await action(); await load(); } catch (caught) { setError(message(caught, fallback)); } finally { setBusyId(""); }
  }
  function updateDraft(id: string, field: keyof Draft, value: string) {
    setDrafts((current) => ({ ...current, [id]: { ...(current[id] ?? { assignedTo: "", internalNotes: "", rejectionReason: "" }), [field]: value } }));
  }

  return <div className={styles.page}>
    <div className={styles.heading}><div><span>ACQUISITION</span><h1>Demo requests</h1><p>Qualify leads, control pilot access, and leave an accountable operations trail.</p></div><button onClick={() => void load()} type="button"><RefreshCw size={16}/>Refresh</button></div>
    {error && <div className={styles.error} role="alert">{error}</div>}
    <div className={styles.listHeader}><strong>{total} requests</strong><span>Newest first</span></div>
    <section className={styles.requestList}>
      {requests.map((request) => {
        const draft = drafts[request.id] ?? { assignedTo: "", internalNotes: "", rejectionReason: "" };
        const busy = busyId === request.id;
        return <article key={request.id}>
          <div className={styles.requestTop}><div><span className={`${styles.status} ${styles[request.status] ?? ""}`}>{request.status}</span><h2>{request.businessName}</h2><p>{request.contactName} · {request.industry} · {request.countryCode}</p></div><time>{new Date(request.createdAt).toLocaleDateString()}</time></div>
          <div className={styles.contact}><a href={`mailto:${request.email}`}><Mail size={15}/>{request.email}</a>{request.phone && <a href={`tel:${request.phone}`}><Phone size={15}/>{request.phone}</a>}<span>{request.monthlyCallVolume} monthly conversations</span></div>
          <p className={styles.useCase}>{request.primaryUseCase}</p>
          {request.invitation && <div className={ops.delivery}><strong>Email: {request.invitation.deliveryStatus}</strong><span>{request.invitation.deliveryAttempts} provider attempt{request.invitation.deliveryAttempts === 1 ? "" : "s"}</span>{request.invitation.sentAt && <span>Accepted by email provider {new Date(request.invitation.sentAt).toLocaleString()}</span>}{request.invitation.lastDeliveryError && <span className={ops.deliveryError}>{request.invitation.lastDeliveryError}</span>}</div>}
          {request.rejectedReason && <div className={ops.rejection}><strong>Rejection reason</strong><span>{request.rejectedReason}</span></div>}
          <details className={ops.operations}><summary><UserRound size={15}/>Assignment and internal notes</summary><div className={ops.operationFields}><label>Assigned to<input value={draft.assignedTo} onChange={(event) => updateDraft(request.id, "assignedTo", event.target.value)} placeholder="operator@sauti.uk"/></label><label>Internal notes<textarea value={draft.internalNotes} onChange={(event) => updateDraft(request.id, "internalNotes", event.target.value)} placeholder="Private context for the platform team"/></label></div><button disabled={busy} onClick={() => void run(request.id, () => updateDemoRequestOperations(request.id, draft.assignedTo, draft.internalNotes), "Unable to save operations details.")} type="button"><Save size={15}/>Save operations</button></details>
          <div className={styles.requestBottom}><span>{request.channels.split(",").join(" · ")}</span><div className={ops.actions}>
            {request.status === "new" && <button disabled={busy} onClick={() => void run(request.id, () => inviteDemoRequest(request.id), "Unable to send invitation.")} type="button">{busy ? <LoaderCircle className={styles.spin} size={16}/> : <Mail size={16}/>}Approve & invite</button>}
            {(request.status === "invited" || request.status === "approved") && <button disabled={busy} onClick={() => void run(request.id, () => resendDemoInvitation(request.id), "Unable to resend invitation.")} type="button"><RotateCw size={16}/>{request.status === "approved" ? "Reactivate & resend" : "Rotate & resend"}</button>}
            {request.status === "invited" && <button className={ops.dangerButton} disabled={busy} onClick={() => void run(request.id, () => revokeDemoInvitation(request.id), "Unable to revoke invitation.")} type="button"><XCircle size={16}/>Revoke</button>}
            {request.status === "activated" && <span className={styles.complete}><Check size={15}/>Workspace activated</span>}
          </div></div>
          {request.status !== "activated" && request.status !== "rejected" && <div className={ops.rejectRow}><input value={draft.rejectionReason} onChange={(event) => updateDraft(request.id, "rejectionReason", event.target.value)} placeholder="Customer-visible reason required before rejecting"/><button disabled={busy || !draft.rejectionReason.trim()} onClick={() => void run(request.id, () => rejectDemoRequest(request.id, draft.rejectionReason), "Unable to reject request.")} type="button">Reject request</button></div>}
        </article>;
      })}
      {!requests.length && !error && <div className={styles.empty}>No demo requests yet.</div>}
    </section>
  </div>;
}

function message(caught: unknown, fallback: string) { return caught instanceof Error ? caught.message : fallback; }

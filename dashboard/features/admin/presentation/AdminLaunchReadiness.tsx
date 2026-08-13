"use client";

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Check, CircleDashed, RefreshCw, Rocket, Save, ShieldCheck } from "lucide-react";
import { getAdminLaunchReadiness, updateAdminLaunchReadiness } from "@/lib/api/admin";
import type { AdminLaunchReadiness as Readiness } from "@/types/api";
import styles from "./AdminLaunchReadiness.module.css";

type ManualKey = "securityReviewCompleted" | "privacyLegalReviewCompleted" |
  "googleVerificationCompleted" | "liveAcceptanceCompleted";

const manualChecks: Array<{ key: ManualKey; label: string; detail: string }> = [
  { key: "securityReviewCompleted", label: "Security and tenant-isolation review", detail: "Review authentication, authorization, secrets, webhooks, tenant-scoped queries, abuse controls, and remediation evidence." },
  { key: "privacyLegalReviewCompleted", label: "Privacy and legal review", detail: "Approve the published privacy, retention, recording, AI disclosure, terms, cancellation, and refund commitments for launch countries." },
  { key: "googleVerificationCompleted", label: "Google OAuth verification", detail: "Confirm the production consent screen and requested Calendar and Sheets scopes have been accepted by Google." },
  { key: "liveAcceptanceCompleted", label: "Controlled live acceptance", detail: "Complete one consented end-to-end production journey across voice, email, calendar, billing, and each enabled messaging channel." },
];

export function AdminLaunchReadiness() {
  const [data, setData] = useState<Readiness | null>(null);
  const [form, setForm] = useState<Record<ManualKey, boolean>>({
    securityReviewCompleted: false,
    privacyLegalReviewCompleted: false,
    googleVerificationCompleted: false,
    liveAcceptanceCompleted: false,
  });
  const [notes, setNotes] = useState("");
  const [googleReference, setGoogleReference] = useState("");
  const [liveEvidence, setLiveEvidence] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const result = await getAdminLaunchReadiness();
      setData(result);
      setForm({
        securityReviewCompleted: result.manualReview.securityReviewCompleted,
        privacyLegalReviewCompleted: result.manualReview.privacyLegalReviewCompleted,
        googleVerificationCompleted: result.manualReview.googleVerificationCompleted,
        liveAcceptanceCompleted: result.manualReview.liveAcceptanceCompleted,
      });
      setNotes(result.manualReview.notes ?? "");
      setGoogleReference(result.manualReview.googleVerificationReference ?? "");
      setLiveEvidence(result.manualReview.liveAcceptanceEvidence ?? "");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to load launch readiness.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function save(approve = false) {
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const result = await updateAdminLaunchReadiness({
        ...form,
        googleVerificationReference: googleReference,
        liveAcceptanceEvidence: liveEvidence,
        notes,
        generalAvailabilityApproved: approve,
        confirmation: approve ? "APPROVE GENERAL AVAILABILITY" : "",
      });
      setData(result);
      setMessage(approve ? "General availability approved and recorded." : "Launch review saved.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to save launch readiness.");
    } finally {
      setSaving(false);
    }
  }

  const evidenceComplete = googleReference.trim().length > 0 && liveEvidence.trim().length > 0;
  const canApprove = data != null && data.automatedBlockingChecks === 0 &&
    Object.values(form).every(Boolean) && evidenceComplete && !data.manualReview.generalAvailabilityApproved;

  return <div className={styles.page}>
    <header className={styles.heading}>
      <div><span>PHASE 4</span><h1>Launch readiness</h1><p>General availability requires runtime evidence and explicit human review. This page never returns secrets or approves itself.</p></div>
      <button disabled={loading} onClick={() => void load()} type="button"><RefreshCw className={loading ? styles.spin : ""} size={16}/>Refresh</button>
    </header>

    {error && <div className={styles.error} role="alert"><AlertTriangle size={17}/>{error}</div>}
    {message && <div className={styles.success} role="status"><Check size={17}/>{message}</div>}
    {loading && !data && <div className={styles.loading}><CircleDashed className={styles.spin}/>Loading launch evidence…</div>}

    {data && <>
      <section className={`${styles.summary} ${styles[data.status] ?? ""}`}>
        <span><Rocket size={23}/></span>
        <div><small>GENERAL AVAILABILITY</small><strong>{label(data.status)}</strong><p>{summary(data)}</p></div>
        <b>{data.automatedBlockingChecks + data.manualBlockingChecks} checks remaining</b>
      </section>

      <section className={styles.panel}>
        <header><div><span>AUTOMATED EVIDENCE</span><h2>Production controls</h2></div><p>Derived from runtime configuration and stored operational evidence. Values and secrets are never displayed.</p></header>
        <div className={styles.checks}>{data.automatedChecks.map((check) => <article key={check.key}>
          <i className={check.passed ? styles.ready : styles.blocked}>{check.passed ? <Check size={16}/> : <AlertTriangle size={16}/>}</i>
          <div><strong>{check.label}</strong><small>{check.passed ? "Verified" : check.action}</small></div>
          <em>{check.passed ? "Ready" : "Blocked"}</em>
        </article>)}</div>
      </section>

      <section className={styles.panel}>
        <header><div><span>HUMAN ATTESTATIONS</span><h2>Reviewed launch decisions</h2></div><p>Check a decision only after retaining its supporting evidence. Every save records the administrator and time.</p></header>
        <div className={styles.manualChecks}>{manualChecks.map((check) => <div className={styles.manualCheck} key={check.key}>
          <label>
            <input checked={form[check.key]} onChange={(event) => setForm((current) => ({ ...current, [check.key]: event.target.checked }))} type="checkbox"/>
            <span><strong>{check.label}</strong><small>{check.detail}</small></span>
          </label>
          {check.key === "googleVerificationCompleted" && form.googleVerificationCompleted && <label className={styles.evidenceField}>
            <span>Approval evidence reference</span>
            <input maxLength={500} onChange={(event) => setGoogleReference(event.target.value)} placeholder="Example: Google approval email date and Cloud project name (no secrets)" required value={googleReference}/>
            {data.manualReview.googleVerifiedAt && <small>Recorded {when(data.manualReview.googleVerifiedAt)}</small>}
          </label>}
          {check.key === "liveAcceptanceCompleted" && form.liveAcceptanceCompleted && <label className={styles.evidenceField}>
            <span>Live acceptance evidence</span>
            <textarea maxLength={2000} onChange={(event) => setLiveEvidence(event.target.value)} placeholder="Record date and evidence location for voice, Calendar, Sheets, email, billing, and every enabled messaging channel. Mark disabled channels not applicable." required value={liveEvidence}/>
            {data.manualReview.liveAcceptedAt && <small>Recorded {when(data.manualReview.liveAcceptedAt)}</small>}
          </label>}
        </div>)}</div>
        <label className={styles.notes}><span>Evidence notes</span><textarea maxLength={2000} onChange={(event) => setNotes(event.target.value)} placeholder="Reference review date, evidence location, remaining limitations, and launch countries." value={notes}/></label>
        <div className={styles.actions}>
          <button disabled={saving} onClick={() => void save(false)} type="button"><Save size={16}/>{saving ? "Saving…" : data.manualReview.generalAvailabilityApproved ? "Reopen and save review" : "Save review"}</button>
          <button className={styles.approve} disabled={saving || !canApprove} onClick={() => void save(true)} type="button"><ShieldCheck size={16}/>{data.manualReview.generalAvailabilityApproved ? "General availability approved" : "Approve general availability"}</button>
        </div>
        <footer>Last reviewed {when(data.manualReview.reviewedAt)}{data.manualReview.reviewedBy ? ` by ${data.manualReview.reviewedBy}` : ""}.</footer>
      </section>
    </>}
  </div>;
}

function label(status: string) {
  return status === "approved" ? "Approved" : status === "review_pending" ? "Human review pending" : "Launch blocked";
}
function summary(data: Readiness) {
  if (data.status === "approved") return "All automated and reviewed gates passed. Approval is retained in the platform audit history.";
  if (data.automatedBlockingChecks > 0) return `${data.automatedBlockingChecks} automated production control${data.automatedBlockingChecks === 1 ? " is" : "s are"} still blocking launch.`;
  return `${data.manualBlockingChecks} reviewed decision${data.manualBlockingChecks === 1 ? " remains" : "s remain"} before approval.`;
}
function when(value: string | null) { return value ? new Date(value).toLocaleString() : "not yet"; }

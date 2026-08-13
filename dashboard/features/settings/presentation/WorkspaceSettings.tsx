"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Clock3, FileText, LoaderCircle, Save, ShieldCheck, Trash2 } from "lucide-react";
import { loadPrivacyRetention, savePrivacyRetention } from "@/lib/api/tenant";
import styles from "./WorkspaceSettings.module.css";

export function WorkspaceSettings() {
  const [conversationDays, setConversationDays] = useState(90);
  const [recordingDays, setRecordingDays] = useState(30);
  const [recordingEnabled, setRecordingEnabled] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    loadPrivacyRetention()
      .then((settings) => {
        setConversationDays(settings.conversationRetentionDays);
        setRecordingDays(settings.recordingRetentionDays);
        setRecordingEnabled(settings.recordingEnabledForAnyAgent);
      })
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load privacy settings."))
      .finally(() => setLoading(false));
  }, []);

  async function save() {
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const settings = await savePrivacyRetention({
        conversationRetentionDays: conversationDays,
        recordingRetentionDays: recordingDays,
        recordingComplianceAcknowledged: acknowledged,
      });
      setConversationDays(settings.conversationRetentionDays);
      setRecordingDays(settings.recordingRetentionDays);
      setRecordingEnabled(settings.recordingEnabledForAnyAgent);
      setAcknowledged(false);
      setMessage("Retention settings saved. Expired data is removed by the daily retention job.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to save privacy settings.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <div className={styles.loading}><LoaderCircle size={24} /> Loading workspace settings…</div>;

  const recordingOptions = [7, 30, 90].filter((days) => days <= conversationDays);
  return <div className={styles.page}>
    <header className={styles.header}>
      <span>Workspace controls</span>
      <h1>Settings</h1>
      <p>Choose how long Sauti keeps identifiable conversation content and call recordings.</p>
    </header>

    <section className={styles.notice}>
      <ShieldCheck size={21} />
      <div><h2>Privacy is an operational setting</h2><p>These limits are enforced automatically. Shorter retention reduces exposure while keeping aggregate call duration, outcome, and latency metrics available.</p></div>
    </section>

    <section className={styles.card}>
      <div className={styles.cardHeader}><div className={styles.cardIcon}><Clock3 size={21} /></div><div><h2>Conversation retention</h2><p>Caller numbers, transcripts, summaries, inferred intent and sentiment, archived conversation state, and transfer details are redacted after this period.</p></div></div>
      <label className={styles.field}>Keep identifiable conversation content for
        <select value={conversationDays} onChange={(event) => {
          const next = Number(event.target.value);
          setConversationDays(next);
          if (recordingDays > next) setRecordingDays(Math.max(...[7, 30, 90].filter((days) => days <= next)));
        }}>
          {[30, 90, 180, 365].map((days) => <option key={days} value={days}>{days} days</option>)}
        </select>
      </label>
    </section>

    <section className={styles.card}>
      <div className={styles.cardHeader}><div className={styles.cardIcon}><Trash2 size={21} /></div><div><h2>Recording retention</h2><p>Local audio is deleted and Telnyx-hosted recordings are permanently deleted through the provider API. Failed provider deletions remain queued for the next daily attempt.</p></div></div>
      <label className={styles.field}>Keep call recordings for
        <select value={recordingDays} onChange={(event) => setRecordingDays(Number(event.target.value))}>
          {recordingOptions.map((days) => <option key={days} value={days}>{days} days</option>)}
        </select>
      </label>
      {recordingEnabled && <label className={styles.acknowledgement}>
        <input type="checkbox" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} />
        <span>I confirm our enabled recording agents give the AI/recording notice and obtain consent required in every jurisdiction where we operate.</span>
      </label>}
    </section>

    <section className={styles.boundary}>
      <FileText size={20} /><div><h2>What this does not delete</h2><p>Bookings, customer records written to connected systems, billing evidence, security audit records, and provider data outside Sauti follow their own legal or provider lifecycle. Disconnect or delete those records at their source when required.</p><p>For a verified access, export, restriction, or full workspace deletion request, contact <a href="mailto:support@sauti.uk">support@sauti.uk</a>.</p></div>
    </section>

    {error && <p className={styles.error} role="alert">{error}</p>}
    {message && <p className={styles.success} role="status">{message}</p>}
    <div className={styles.actions}><div><Link href="/privacy">Privacy policy</Link><Link href="/terms">Terms</Link></div><button type="button" disabled={saving || (recordingEnabled && !acknowledged)} onClick={() => void save()}>{saving ? <LoaderCircle className={styles.spin} size={17} /> : <Save size={17} />}{saving ? "Saving…" : "Save retention"}</button></div>
  </div>;
}

"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import {
  BadgeCheck,
  Building2,
  Check,
  ChevronRight,
  Clock3,
  Code2,
  CreditCard,
  FileText,
  Globe2,
  KeyRound,
  Link2,
  LoaderCircle,
  LockKeyhole,
  Mail,
  Plug,
  Save,
  ShieldCheck,
  Trash2,
  UserRound,
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import type { Tenant } from "@/types/api";
import {
  loadPrivacyRetention,
  loadWorkspaceWebhook,
  savePrivacyRetention,
  saveWorkspaceWebhook,
} from "@/lib/api/tenant";
import styles from "./WorkspaceSettings.module.css";

type Section = "workspace" | "privacy" | "developer" | "security";

const sections: Array<{ id: Section; label: string; detail: string; icon: typeof Building2 }> = [
  { id: "workspace", label: "Workspace", detail: "Business and plan", icon: Building2 },
  { id: "privacy", label: "Privacy & data", detail: "Retention and consent", icon: ShieldCheck },
  { id: "developer", label: "Developer", detail: "Outbound webhook", icon: Code2 },
  { id: "security", label: "Security", detail: "Password and account", icon: LockKeyhole },
];

export function WorkspaceSettings() {
  const { session } = useAuth();
  const [active, setActive] = useState<Section>("workspace");
  const [conversationDays, setConversationDays] = useState(90);
  const [recordingDays, setRecordingDays] = useState(30);
  const [recordingEnabled, setRecordingEnabled] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);
  const [webhookUrl, setWebhookUrl] = useState("");
  const [webhookSecret, setWebhookSecret] = useState("");
  const [secretConfigured, setSecretConfigured] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<"privacy" | "webhook" | "">("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([loadPrivacyRetention(), loadWorkspaceWebhook()])
      .then(([privacy, webhook]) => {
        setConversationDays(privacy.conversationRetentionDays);
        setRecordingDays(privacy.recordingRetentionDays);
        setRecordingEnabled(privacy.recordingEnabledForAnyAgent);
        setWebhookUrl(webhook.webhookUrl ?? "");
        setSecretConfigured(webhook.secretConfigured);
      })
      .catch((caught) => setError(messageFrom(caught, "Unable to load workspace settings.")))
      .finally(() => setLoading(false));
  }, []);

  function clearFeedback() {
    setError("");
    setMessage("");
  }

  async function savePrivacy() {
    setSaving("privacy");
    clearFeedback();
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
      setMessage("Privacy settings saved. The daily retention policy will apply these limits automatically.");
    } catch (caught) {
      setError(messageFrom(caught, "Unable to save privacy settings."));
    } finally {
      setSaving("");
    }
  }

  async function saveWebhook() {
    setSaving("webhook");
    clearFeedback();
    try {
      const settings = await saveWorkspaceWebhook({
        webhookUrl: webhookUrl.trim(),
        webhookSecret,
      });
      setWebhookUrl(settings.webhookUrl ?? "");
      setSecretConfigured(settings.secretConfigured);
      setWebhookSecret("");
      setMessage(settings.webhookUrl
        ? "Webhook settings saved. New qualifying events will use this destination."
        : "Workspace webhook removed.");
    } catch (caught) {
      setError(messageFrom(caught, "Unable to save webhook settings."));
    } finally {
      setSaving("");
    }
  }

  if (loading) return <div className={styles.loading}><LoaderCircle size={24} /> Loading workspace settings…</div>;

  const tenant = session?.tenant;
  const recordingOptions = [7, 30, 90].filter((days) => days <= conversationDays);

  return <div className={styles.page}>
    <header className={styles.header}>
      <div><span>Workspace controls</span><h1>Settings</h1><p>Manage how your workspace is identified, protected, and connected.</p></div>
      <div className={styles.workspacePill}><i>{tenant?.businessName?.slice(0, 1).toUpperCase() ?? "S"}</i><span><strong>{tenant?.businessName ?? "Sauti workspace"}</strong><small>{displayPlan(tenant?.plan)} plan</small></span></div>
    </header>

    <div className={styles.settingsLayout}>
      <aside className={styles.settingsNav} aria-label="Settings sections">
        {sections.map(({ id, label, detail, icon: Icon }) => <button aria-current={active === id ? "page" : undefined} className={active === id ? styles.active : ""} key={id} onClick={() => { setActive(id); clearFeedback(); }} type="button">
          <Icon size={18}/><span><strong>{label}</strong><small>{detail}</small></span><ChevronRight size={16}/>
        </button>)}
        <div className={styles.helpCard}><ShieldCheck size={19}/><strong>Need help?</strong><p>Support can help with workspace access, privacy requests, and account closure.</p><a href="mailto:support@sauti.uk">Contact support</a></div>
      </aside>

      <main className={styles.content}>
        {error && <div className={styles.error} role="alert">{error}</div>}
        {message && <div className={styles.success} role="status"><Check size={16}/>{message}</div>}
        {active === "workspace" && (
          <WorkspaceSection role={session?.role} tenant={tenant}/>
        )}
        {active === "privacy" && (
          <PrivacySection acknowledged={acknowledged} conversationDays={conversationDays} recordingDays={recordingDays} recordingEnabled={recordingEnabled} recordingOptions={recordingOptions} saving={saving === "privacy"} setAcknowledged={setAcknowledged} setConversationDays={(next) => { setConversationDays(next); if (recordingDays > next) setRecordingDays(Math.max(...[7, 30, 90].filter((days) => days <= next))); }} setRecordingDays={setRecordingDays} save={savePrivacy}/>
        )}
        {active === "developer" && (
          <DeveloperSection secretConfigured={secretConfigured} saving={saving === "webhook"} webhookSecret={webhookSecret} webhookUrl={webhookUrl} setWebhookSecret={setWebhookSecret} setWebhookUrl={setWebhookUrl} save={saveWebhook}/>
        )}
        {active === "security" && (
          <SecuritySection email={tenant?.email ?? ""}/>
        )}
      </main>
    </div>
  </div>;
}

function WorkspaceSection({ tenant, role }: { tenant?: Tenant; role?: string }) {
  return <section className={styles.section}>
    <SectionHeading icon={<Building2 size={21}/>} eyebrow="Workspace" title="Business profile" detail="The identity used across your Sauti console, billing, and provider connections."/>
    <div className={styles.profileGrid}>
      <ProfileItem icon={<Building2 size={17}/>} label="Business name" value={tenant?.businessName ?? "Not available"}/>
      <ProfileItem icon={<Mail size={17}/>} label="Owner email" value={tenant?.email ?? "Not available"}/>
      <ProfileItem icon={<Globe2 size={17}/>} label="Business country" value={tenant?.countryCode || "Not set"}/>
      <ProfileItem icon={<UserRound size={17}/>} label="Your access" value={displayRole(role)}/>
    </div>
    <div className={styles.infoNote}><BadgeCheck size={19}/><div><strong>Workspace identity is protected</strong><p>Contact support to change your legal business name, owner email, or country. This avoids breaking billing, phone-number, and compliance records.</p></div></div>
    <div className={styles.linkCards}>
      <SettingsLink href="/billing" icon={<CreditCard size={20}/>} title="Plan and usage" detail={`${displayPlan(tenant?.plan)} plan · ${tenant?.minutesUsedThisCycle ?? 0} of ${tenant?.monthlyMinutesLimit ?? 0} minutes used`}/>
      <SettingsLink href="/dashboard/integrations" icon={<Plug size={20}/>} title="Connected services" detail="Manage Calendar, Sheets, WhatsApp, CRM, and notification connections."/>
    </div>
  </section>;
}

function PrivacySection(props: {
  conversationDays: number; recordingDays: number; recordingEnabled: boolean; acknowledged: boolean;
  recordingOptions: number[]; saving: boolean; setConversationDays: (value: number) => void;
  setRecordingDays: (value: number) => void; setAcknowledged: (value: boolean) => void; save: () => Promise<void>;
}) {
  return <section className={styles.section}>
    <SectionHeading icon={<ShieldCheck size={21}/>} eyebrow="Privacy & data" title="Retention controls" detail="Choose how long identifiable call content remains available in Sauti."/>
    <div className={styles.notice}><ShieldCheck size={20}/><div><strong>Automatic enforcement</strong><p>Shorter retention reduces exposure while aggregate duration, outcome, and latency metrics remain available.</p></div></div>
    <div className={styles.formCard}>
      <SettingRow icon={<Clock3 size={20}/>} title="Conversation data" detail="Caller numbers, transcripts, summaries, intent, sentiment, archived state, and transfer details.">
        <select aria-label="Conversation retention" value={props.conversationDays} onChange={(event) => props.setConversationDays(Number(event.target.value))}>{[30, 90, 180, 365].map((days) => <option key={days} value={days}>{days} days</option>)}</select>
      </SettingRow>
      <SettingRow icon={<Trash2 size={20}/>} title="Call recordings" detail="Local audio and Telnyx-hosted recordings are permanently deleted after this period.">
        <select aria-label="Recording retention" value={props.recordingDays} onChange={(event) => props.setRecordingDays(Number(event.target.value))}>{props.recordingOptions.map((days) => <option key={days} value={days}>{days} days</option>)}</select>
      </SettingRow>
      {props.recordingEnabled && <label className={styles.acknowledgement}><input checked={props.acknowledged} onChange={(event) => props.setAcknowledged(event.target.checked)} type="checkbox"/><span><strong>Recording compliance confirmation</strong><small>I confirm our enabled recording agents provide the AI/recording notice and obtain consent required in every jurisdiction where we operate.</small></span></label>}
    </div>
    <div className={styles.boundary}><FileText size={20}/><div><strong>Deletion boundaries</strong><p>Bookings, billing evidence, audit records, and customer data written to connected providers follow separate lifecycles. Remove those records at their source when required.</p><span><Link href="/privacy">Privacy policy</Link><Link href="/terms">Terms</Link></span></div></div>
    <ActionBar note="Changes apply workspace-wide on the next retention run."><button disabled={props.saving || (props.recordingEnabled && !props.acknowledged)} onClick={() => void props.save()} type="button">{props.saving ? <LoaderCircle className={styles.spin} size={17}/> : <Save size={17}/>} {props.saving ? "Saving…" : "Save privacy settings"}</button></ActionBar>
  </section>;
}

function DeveloperSection(props: { webhookUrl: string; webhookSecret: string; secretConfigured: boolean; saving: boolean; setWebhookUrl: (value: string) => void; setWebhookSecret: (value: string) => void; save: () => Promise<void> }) {
  return <section className={styles.section}>
    <SectionHeading icon={<Code2 size={21}/>} eyebrow="Developer" title="Workspace webhook" detail="Send qualifying Sauti events to one HTTPS endpoint owned by your business."/>
    <div className={styles.formCard}>
      <label className={styles.inputField}><span>Destination URL</span><input autoComplete="url" onChange={(event) => props.setWebhookUrl(event.target.value)} placeholder="https://example.com/webhooks/sauti" type="url" value={props.webhookUrl}/><small>Leave empty and save to disable delivery.</small></label>
      <label className={styles.inputField}><span>Signing secret</span><input autoComplete="new-password" onChange={(event) => props.setWebhookSecret(event.target.value)} placeholder={props.secretConfigured ? "Secret configured — enter a value only to replace it" : "Create a strong signing secret"} type="password" value={props.webhookSecret}/><small>{props.secretConfigured ? "The stored secret is never returned to the browser." : "Use this to verify that events were sent by Sauti."}</small></label>
      <div className={styles.webhookStatus}><i className={props.webhookUrl ? styles.connected : ""}><Link2 size={16}/></i><span><strong>{props.webhookUrl ? "Webhook configured" : "No webhook configured"}</strong><small>{props.webhookUrl ? "Delivery uses your saved HTTPS destination." : "Sauti will not send workspace events to a custom endpoint."}</small></span></div>
    </div>
    <ActionBar note="Secrets are encrypted at rest and are never displayed after saving."><button disabled={props.saving} onClick={() => void props.save()} type="button">{props.saving ? <LoaderCircle className={styles.spin} size={17}/> : <Save size={17}/>} {props.saving ? "Saving…" : "Save webhook"}</button></ActionBar>
  </section>;
}

function SecuritySection({ email }: { email: string }) {
  const deletionSubject = encodeURIComponent("Sauti workspace deletion request");
  const deletionBody = encodeURIComponent(`Please help me permanently delete the Sauti workspace associated with ${email || "my account"}.`);
  return <section className={styles.section}>
    <SectionHeading icon={<LockKeyhole size={21}/>} eyebrow="Security" title="Account access" detail="Protect your sign-in and request sensitive workspace changes through verified channels."/>
    <div className={styles.securityCards}>
      <article><i><KeyRound size={20}/></i><div><strong>Password</strong><p>Use a one-time email link to choose a new password. Existing sessions may be invalidated after the change.</p><Link href="/forgot-password">Reset password <ChevronRight size={15}/></Link></div></article>
      <article><i><Mail size={20}/></i><div><strong>Account email</strong><p>{email || "Your authenticated email"} receives account and billing notices. Contact support to change ownership safely.</p><a href="mailto:support@sauti.uk">Contact support <ChevronRight size={15}/></a></div></article>
    </div>
    <div className={styles.dangerZone}><div><span>Danger zone</span><h2>Delete this workspace</h2><p>Workspace deletion is permanent and requires identity and billing verification. Support will explain connected-provider data that must be removed separately.</p></div><a href={`mailto:support@sauti.uk?subject=${deletionSubject}&body=${deletionBody}`}><Trash2 size={16}/> Request deletion</a></div>
  </section>;
}

function SectionHeading({ icon, eyebrow, title, detail }: { icon: ReactNode; eyebrow: string; title: string; detail: string }) {
  return <header className={styles.sectionHeading}><i>{icon}</i><div><span>{eyebrow}</span><h2>{title}</h2><p>{detail}</p></div></header>;
}
function ProfileItem({ icon, label, value }: { icon: ReactNode; label: string; value: string }) { return <div className={styles.profileItem}><i>{icon}</i><span><small>{label}</small><strong>{value}</strong></span></div>; }
function SettingsLink({ href, icon, title, detail }: { href: string; icon: ReactNode; title: string; detail: string }) { return <Link className={styles.settingsLink} href={href}><i>{icon}</i><span><strong>{title}</strong><small>{detail}</small></span><ChevronRight size={17}/></Link>; }
function SettingRow({ icon, title, detail, children }: { icon: ReactNode; title: string; detail: string; children: ReactNode }) { return <div className={styles.settingRow}><i>{icon}</i><span><strong>{title}</strong><small>{detail}</small></span>{children}</div>; }
function ActionBar({ note, children }: { note: string; children: ReactNode }) { return <div className={styles.actionBar}><small>{note}</small>{children}</div>; }
function messageFrom(value: unknown, fallback: string) { return value instanceof Error ? value.message : fallback; }
function displayPlan(value?: string) { const plan = value?.trim() || "trial"; return plan.charAt(0).toUpperCase() + plan.slice(1); }
function displayRole(value?: string) { const role = value?.trim().toLowerCase().replaceAll("_", " ") || "workspace owner"; return role.charAt(0).toUpperCase() + role.slice(1); }

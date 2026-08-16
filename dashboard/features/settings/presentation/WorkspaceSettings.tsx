"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import * as Select from "@radix-ui/react-select";
import { BadgeCheck, Bell, Bot, Building2, Check, ChevronDown, ChevronRight, Clock3, Code2, CreditCard, FileText, Globe2, Headphones, KeyRound, Link2, LoaderCircle, LockKeyhole, Mail, MessageSquareText, Plug, Save, ShieldCheck, Trash2, UserRound, UsersRound } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { listAgents } from "@/lib/api/agents";
import { getIntegrationConnections } from "@/lib/api/integrations";
import { loadPrivacyRetention, loadWorkspaceProfile, loadWorkspaceWebhook, savePrivacyRetention, saveWorkspaceProfile, saveWorkspaceWebhook } from "@/lib/api/tenant";
import type { Tenant } from "@/types/api";
import styles from "./WorkspaceSettings.module.css";

type Section = "general" | "calls" | "notifications" | "privacy" | "security" | "developer";
const sections: Array<{ id: Section; label: string; icon: typeof Building2 }> = [
  { id: "general", label: "General", icon: Building2 }, { id: "calls", label: "Calls & AI", icon: Headphones },
  { id: "notifications", label: "Notifications", icon: Bell }, { id: "privacy", label: "Data & privacy", icon: ShieldCheck },
  { id: "security", label: "Security", icon: LockKeyhole }, { id: "developer", label: "Developer", icon: Code2 },
];

export function WorkspaceSettings() {
  const { session, updateSession } = useAuth();
  const [active, setActive] = useState<Section>("general");
  const [conversationDays, setConversationDays] = useState(90), [recordingDays, setRecordingDays] = useState(30);
  const [recordingEnabled, setRecordingEnabled] = useState(false), [acknowledged, setAcknowledged] = useState(false);
  const [webhookUrl, setWebhookUrl] = useState(""), [webhookSecret, setWebhookSecret] = useState("");
  const [secretConfigured, setSecretConfigured] = useState(false), [agentCount, setAgentCount] = useState<number | null>(null);
  const [connectionCount, setConnectionCount] = useState<number | null>(null), [loading, setLoading] = useState(true);
  const [businessName, setBusinessName] = useState(""), [workspaceTimezone, setWorkspaceTimezone] = useState("UTC");
  const [bookingDuration, setBookingDuration] = useState(60), [savedProfile, setSavedProfile] = useState("");
  const [saving, setSaving] = useState<"profile" | "privacy" | "webhook" | "">(""), [message, setMessage] = useState(""), [error, setError] = useState("");

  useEffect(() => {
    Promise.all([loadPrivacyRetention(), loadWorkspaceWebhook(), loadWorkspaceProfile()]).then(([privacy, webhook, profile]) => {
      setConversationDays(privacy.conversationRetentionDays); setRecordingDays(privacy.recordingRetentionDays);
      setRecordingEnabled(privacy.recordingEnabledForAnyAgent); setWebhookUrl(webhook.webhookUrl ?? ""); setSecretConfigured(webhook.secretConfigured);
      setBusinessName(profile.businessName); setWorkspaceTimezone(profile.timezone); setBookingDuration(profile.defaultBookingDurationMinutes);
      setSavedProfile(profileKey(profile.businessName, profile.timezone, profile.defaultBookingDurationMinutes));
    }).catch((caught) => setError(messageFrom(caught, "Unable to load workspace settings."))).finally(() => setLoading(false));
    void Promise.allSettled([listAgents(), getIntegrationConnections()]).then(([agents, connections]) => {
      if (agents.status === "fulfilled") setAgentCount(agents.value.length);
      if (connections.status === "fulfilled") setConnectionCount(connections.value.filter((connection) => connection.status === "connected").length);
    });
  }, []);

  function selectSection(section: Section) { setActive(section); setError(""); setMessage(""); }
  async function saveProfile() {
    setSaving("profile"); setError(""); setMessage("");
    try {
      const profile = await saveWorkspaceProfile({ businessName: businessName.trim(), timezone: workspaceTimezone, defaultBookingDurationMinutes: bookingDuration });
      setBusinessName(profile.businessName); setWorkspaceTimezone(profile.timezone); setBookingDuration(profile.defaultBookingDurationMinutes);
      setSavedProfile(profileKey(profile.businessName, profile.timezone, profile.defaultBookingDurationMinutes));
      if (session) updateSession({ ...session, tenant: { ...session.tenant, businessName: profile.businessName } });
      setMessage("Workspace settings saved. These defaults will apply when you create new agents.");
    } catch (caught) { setError(messageFrom(caught, "Unable to save workspace settings.")); } finally { setSaving(""); }
  }
  async function savePrivacy() {
    setSaving("privacy"); setError(""); setMessage("");
    try {
      const settings = await savePrivacyRetention({ conversationRetentionDays: conversationDays, recordingRetentionDays: recordingDays, recordingComplianceAcknowledged: acknowledged });
      setConversationDays(settings.conversationRetentionDays); setRecordingDays(settings.recordingRetentionDays); setRecordingEnabled(settings.recordingEnabledForAnyAgent); setAcknowledged(false);
      setMessage("Privacy settings saved. The daily retention policy will apply these limits automatically.");
    } catch (caught) { setError(messageFrom(caught, "Unable to save privacy settings.")); } finally { setSaving(""); }
  }
  async function saveWebhook() {
    setSaving("webhook"); setError(""); setMessage("");
    try {
      const settings = await saveWorkspaceWebhook({ webhookUrl: webhookUrl.trim(), webhookSecret });
      setWebhookUrl(settings.webhookUrl ?? ""); setSecretConfigured(settings.secretConfigured); setWebhookSecret("");
      setMessage(settings.webhookUrl ? "Webhook settings saved. New qualifying events will use this destination." : "Workspace webhook removed.");
    } catch (caught) { setError(messageFrom(caught, "Unable to save webhook settings.")); } finally { setSaving(""); }
  }
  if (loading) return <div className={styles.loading}><LoaderCircle size={24}/> Loading workspace settings…</div>;
  const tenant = session?.tenant, recordingOptions = [7, 30, 90].filter((days) => days <= conversationDays);
  const profileDirty = savedProfile !== profileKey(businessName, workspaceTimezone, bookingDuration);
  return <div className={styles.page}>
    <header className={styles.header}><div><span>Workspace controls</span><h1>Settings</h1><p>Manage your workspace, preferences, data, and connected services.</p></div><div className={styles.workspacePill}><i>{businessName.slice(0, 1).toUpperCase() || "S"}</i><span><strong>{businessName || tenant?.businessName || "Sauti workspace"}</strong><small>{displayPlan(tenant?.plan)} plan</small></span></div></header>
    <WorkspaceHealth tenant={tenant} connectionCount={connectionCount}/>
    <nav className={styles.tabs} aria-label="Settings sections" role="tablist">{sections.map(({ id, label, icon: Icon }) => <button aria-controls={`settings-panel-${id}`} aria-selected={active === id} className={active === id ? styles.activeTab : ""} id={`settings-tab-${id}`} key={id} onClick={() => selectSection(id)} role="tab" type="button"><Icon size={17}/><span>{label}</span></button>)}</nav>
    <main aria-labelledby={`settings-tab-${active}`} className={styles.content} id={`settings-panel-${active}`} role="tabpanel">
      {error && <div className={styles.error} role="alert">{error}</div>}{message && <div className={styles.success} role="status"><Check size={16}/>{message}</div>}
      {active === "general" && <GeneralSection bookingDuration={bookingDuration} businessName={businessName} dirty={profileDirty} role={session?.role} saving={saving === "profile"} tenant={tenant} timezone={workspaceTimezone} setBookingDuration={setBookingDuration} setBusinessName={setBusinessName} setTimezone={setWorkspaceTimezone} save={saveProfile}/>}
      {active === "calls" && <CallsSection agentCount={agentCount}/>}
      {active === "notifications" && <NotificationsSection connectionCount={connectionCount}/>}
      {active === "privacy" && <PrivacySection acknowledged={acknowledged} conversationDays={conversationDays} recordingDays={recordingDays} recordingEnabled={recordingEnabled} recordingOptions={recordingOptions} saving={saving === "privacy"} setAcknowledged={setAcknowledged} setConversationDays={(next) => { setConversationDays(next); if (recordingDays > next) setRecordingDays(Math.max(...[7, 30, 90].filter((days) => days <= next))); }} setRecordingDays={setRecordingDays} save={savePrivacy}/>}
      {active === "security" && <SecuritySection email={tenant?.email ?? ""}/>}
      {active === "developer" && <DeveloperSection secretConfigured={secretConfigured} saving={saving === "webhook"} webhookSecret={webhookSecret} webhookUrl={webhookUrl} setWebhookSecret={setWebhookSecret} setWebhookUrl={setWebhookUrl} save={saveWebhook}/>}
    </main>
    <footer className={styles.supportBar}><span>Need help choosing or changing a workspace setting?</span><a href="mailto:support@sauti.uk">Contact support <ChevronRight size={15}/></a></footer>
  </div>;
}

function WorkspaceHealth({ tenant, connectionCount }: { tenant?: Tenant; connectionCount: number | null }) {
  const values = [tenant?.businessName, tenant?.email, tenant?.countryCode, tenant?.plan], percent = Math.round((values.filter(Boolean).length / values.length) * 100);
  return <section className={styles.healthStrip} aria-label="Workspace status"><StatusItem icon={<BadgeCheck size={20}/>} label="Workspace setup" value={`${percent}% complete`} detail={percent === 100 ? "Your business identity is configured." : "Complete your business information."}/><StatusItem icon={<Plug size={20}/>} label="Integrations" value={connectionCount === null ? "Status unavailable" : `${connectionCount} connected`} detail="Manage the services used by your agents."/><StatusItem icon={<ShieldCheck size={20}/>} label="Account security" value="Protected" detail="Sensitive changes use verified channels."/></section>;
}
function GeneralSection(props: {
  tenant?: Tenant; role?: string; businessName: string; timezone: string; bookingDuration: number;
  dirty: boolean; saving: boolean; setBusinessName: (value: string) => void; setTimezone: (value: string) => void;
  setBookingDuration: (value: number) => void; save: () => Promise<void>;
}) {
  return <section className={styles.panel}>
    <div className={styles.panelHeader}><div><h2>General</h2><p>Update workspace-owned defaults. New agents inherit these values unless you choose different ones.</p></div></div>
    <div className={styles.twoColumns}>
      <div><GroupTitle title="Business identity"/>
        <EditableRow icon={<Building2 size={18}/>} label="Business name" detail="Displayed across your Sauti workspace."><input aria-label="Business name" maxLength={120} onChange={(event) => props.setBusinessName(event.target.value)} value={props.businessName}/></EditableRow>
        <ReadOnlyRow icon={<Globe2 size={18}/>} label="Registered country" detail="Contact support if the legal country changes." value={props.tenant?.countryCode || "Not set"}/>
        <ReadOnlyRow icon={<Mail size={18}/>} label="Owner email" detail="Receives account and billing notices." value={props.tenant?.email ?? "Not available"}/>
      </div>
      <div><GroupTitle title="Workspace defaults"/>
        <EditableRow icon={<Clock3 size={18}/>} label="Timezone" detail="Default timezone for newly created agents."><SettingsStringSelect ariaLabel="Workspace timezone" options={timezoneOptions(props.timezone)} value={props.timezone} onValueChange={props.setTimezone}/></EditableRow>
        <EditableRow icon={<Clock3 size={18}/>} label="Booking duration" detail="Default appointment length for new agents."><SettingsStringSelect ariaLabel="Default booking duration" options={[15, 30, 45, 60, 90, 120].map((minutes) => ({ value: String(minutes), label: `${minutes} minutes` }))} value={String(props.bookingDuration)} onValueChange={(value) => props.setBookingDuration(Number(value))}/></EditableRow>
        <ReadOnlyRow icon={<UserRound size={18}/>} label="Your access" detail="Your permission level in this workspace." value={displayRole(props.role)}/>
      </div>
    </div>
    <div className={styles.summaryLinks}><SettingsLink href="/billing" icon={<CreditCard size={19}/>} title="Plan and usage" detail={`${displayPlan(props.tenant?.plan)} plan · ${props.tenant?.minutesUsedThisCycle ?? 0} of ${props.tenant?.monthlyMinutesLimit ?? 0} minutes used`}/><SettingsLink href="/dashboard/integrations" icon={<Plug size={19}/>} title="Connected services" detail="Manage Calendar, Sheets, WhatsApp, CRM, and notifications."/></div>
    <div className={styles.protectedNote}><BadgeCheck size={18}/><p><strong>Protected account fields.</strong> Owner email and registered country are changed through support to protect billing, number provisioning, and compliance records.</p></div>
    <ActionBar note={props.dirty ? "You have unsaved workspace changes." : "Workspace defaults are up to date."}><button disabled={props.saving || !props.dirty || props.businessName.trim().length < 2} onClick={() => void props.save()} type="button">{props.saving ? <LoaderCircle className={styles.spin} size={17}/> : <Save size={17}/>} {props.saving ? "Saving…" : "Save changes"}</button></ActionBar>
  </section>;
}
function CallsSection({ agentCount }: { agentCount: number | null }) { return <section className={styles.panel}><div className={styles.panelHeader}><div><h2>Calls & AI</h2><p>Voice, language, booking, and call behavior are configured per agent.</p></div><Link className={styles.primaryButton} href="/agents">Manage agents</Link></div><div className={styles.featureRows}><FeatureRow icon={<Bot size={20}/>} title="Agent behavior" detail="Greeting, prompt, model, voice, languages, and interruption handling." value={agentCount === null ? "View agents" : `${agentCount} agent${agentCount === 1 ? "" : "s"}`} href="/agents"/><FeatureRow icon={<Clock3 size={20}/>} title="Availability & bookings" detail="Business hours, booking duration, calendar rules, and after-hours behavior." value="Per agent" href="/agents"/><FeatureRow icon={<Headphones size={20}/>} title="Call handling" detail="Transfers, voicemail, silence handling, recording, and keypad input." value="Per agent" href="/agents"/></div><div className={styles.explainer}><ShieldCheck size={18}/><p>Keeping these controls on each agent prevents a workspace change from unexpectedly altering every live phone line.</p></div></section>; }
function NotificationsSection({ connectionCount }: { connectionCount: number | null }) { return <section className={styles.panel}><div className={styles.panelHeader}><div><h2>Notifications</h2><p>Choose delivery providers and configure customer messaging where it is used.</p></div><Link className={styles.primaryButton} href="/dashboard/integrations">Manage integrations</Link></div><div className={styles.featureRows}><FeatureRow icon={<MessageSquareText size={20}/>} title="Customer confirmations" detail="WhatsApp and SMS confirmations are enabled through the agent and its connected provider." value="Configure" href="/dashboard/integrations"/><FeatureRow icon={<Mail size={20}/>} title="Business notifications" detail="Email, Sheets, Slack, and CRM delivery are managed from Integrations." value={connectionCount === null ? "View status" : `${connectionCount} connected`} href="/dashboard/integrations"/><FeatureRow icon={<Bell size={20}/>} title="Console notifications" detail="Booking and follow-up alerts appear in your Sauti notification centre." value="Always on" href="/bookings"/></div></section>; }

function PrivacySection(props: { conversationDays: number; recordingDays: number; recordingEnabled: boolean; acknowledged: boolean; recordingOptions: number[]; saving: boolean; setConversationDays: (value: number) => void; setRecordingDays: (value: number) => void; setAcknowledged: (value: boolean) => void; save: () => Promise<void> }) {
  return <section className={styles.panel}><div className={styles.panelHeader}><div><h2>Data & privacy</h2><p>Control how long identifiable call content remains available in Sauti.</p></div></div><div className={styles.settingRows}><SettingRow icon={<Clock3 size={19}/>} title="Conversation data" detail="Caller numbers, transcripts, summaries, intent, sentiment, archive state, and transfer details."><SettingsSelect ariaLabel="Conversation retention" options={[30, 90, 180, 365]} value={props.conversationDays} onValueChange={props.setConversationDays}/></SettingRow><SettingRow icon={<Trash2 size={19}/>} title="Call recordings" detail="Local audio and Telnyx-hosted recordings are permanently deleted after this period."><SettingsSelect ariaLabel="Recording retention" options={props.recordingOptions} value={props.recordingDays} onValueChange={props.setRecordingDays}/></SettingRow>{props.recordingEnabled && <label className={styles.acknowledgement}><input checked={props.acknowledged} onChange={(event) => props.setAcknowledged(event.target.checked)} type="checkbox"/><i aria-hidden="true">{props.acknowledged && <Check size={14}/>}</i><span><strong>Recording compliance confirmation</strong><small>I confirm our recording-enabled agents provide the AI and recording notice and obtain the consent required where we operate.</small></span></label>}</div><div className={styles.boundary}><FileText size={19}/><div><strong>Deletion boundaries</strong><p>Bookings, billing evidence, audit records, and customer data written to connected providers follow separate lifecycles. Remove those records at their source when required.</p><span><Link href="/privacy">Privacy policy</Link><Link href="/terms">Terms</Link></span></div></div><ActionBar note="Changes apply workspace-wide on the next retention run."><button disabled={props.saving || (props.recordingEnabled && !props.acknowledged)} onClick={() => void props.save()} type="button">{props.saving ? <LoaderCircle className={styles.spin} size={17}/> : <Save size={17}/>} {props.saving ? "Saving…" : "Save changes"}</button></ActionBar></section>;
}
function DeveloperSection(props: { webhookUrl: string; webhookSecret: string; secretConfigured: boolean; saving: boolean; setWebhookUrl: (value: string) => void; setWebhookSecret: (value: string) => void; save: () => Promise<void> }) { return <section className={styles.panel}><div className={styles.panelHeader}><div><h2>Developer</h2><p>Send qualifying Sauti events to one HTTPS endpoint owned by your business.</p></div><span className={props.webhookUrl ? styles.statusGood : styles.statusNeutral}>{props.webhookUrl ? "Configured" : "Not configured"}</span></div><div className={styles.developerForm}><label className={styles.inputField}><span>Destination URL</span><input autoComplete="url" onChange={(event) => props.setWebhookUrl(event.target.value)} placeholder="https://example.com/webhooks/sauti" type="url" value={props.webhookUrl}/><small>Leave empty and save to disable delivery.</small></label><label className={styles.inputField}><span>Signing secret</span><input autoComplete="new-password" onChange={(event) => props.setWebhookSecret(event.target.value)} placeholder={props.secretConfigured ? "Secret configured — enter a value only to replace it" : "Create a strong signing secret"} type="password" value={props.webhookSecret}/><small>{props.secretConfigured ? "The stored secret is never returned to the browser." : "Use this to verify that events were sent by Sauti."}</small></label><div className={styles.webhookStatus}><i className={props.webhookUrl ? styles.connected : ""}><Link2 size={16}/></i><span><strong>{props.webhookUrl ? "Webhook configured" : "No webhook configured"}</strong><small>{props.webhookUrl ? "Delivery uses your saved HTTPS destination." : "Sauti will not send workspace events to a custom endpoint."}</small></span></div></div><ActionBar note="Secrets are encrypted at rest and are never displayed after saving."><button disabled={props.saving} onClick={() => void props.save()} type="button">{props.saving ? <LoaderCircle className={styles.spin} size={17}/> : <Save size={17}/>} {props.saving ? "Saving…" : "Save webhook"}</button></ActionBar></section>; }
function SecuritySection({ email }: { email: string }) { const subject = encodeURIComponent("Sauti workspace deletion request"), body = encodeURIComponent(`Please help me permanently delete the Sauti workspace associated with ${email || "my account"}.`); return <section className={styles.panel}><div className={styles.panelHeader}><div><h2>Security</h2><p>Protect your sign-in and request sensitive changes through verified channels.</p></div><span className={styles.statusGood}>Protected</span></div><div className={styles.featureRows}><FeatureRow icon={<KeyRound size={20}/>} title="Password" detail="Choose a new password through a one-time link sent to your email." value="Reset" href="/forgot-password"/><FeatureRow icon={<Mail size={20}/>} title="Account email" detail={email || "Your authenticated email receives account and billing notices."} value="Contact support" href="mailto:support@sauti.uk" external/><FeatureRow icon={<UsersRound size={20}/>} title="Workspace access" detail="Your access is tied to your verified account and workspace role." value="Protected" href="mailto:support@sauti.uk" external/></div><div className={styles.dangerZone}><div><span>Danger zone</span><h3>Delete this workspace</h3><p>Deletion is permanent and requires identity and billing verification.</p></div><a href={`mailto:support@sauti.uk?subject=${subject}&body=${body}`}><Trash2 size={16}/> Request deletion</a></div></section>; }

function SettingsSelect({ ariaLabel, options, value, onValueChange }: { ariaLabel: string; options: number[]; value: number; onValueChange: (value: number) => void }) { return <SettingsStringSelect ariaLabel={ariaLabel} options={options.map((days) => ({ value: String(days), label: `${days} days` }))} value={String(value)} onValueChange={(next) => onValueChange(Number(next))}/>; }
function SettingsStringSelect({ ariaLabel, options, value, onValueChange }: { ariaLabel: string; options: Array<{ value: string; label: string }>; value: string; onValueChange: (value: string) => void }) { return <Select.Root value={value} onValueChange={onValueChange}><Select.Trigger aria-label={ariaLabel} className={styles.selectTrigger}><Select.Value/><Select.Icon><ChevronDown size={16}/></Select.Icon></Select.Trigger><Select.Portal><Select.Content align="end" className={styles.selectContent} position="popper" sideOffset={7}><Select.ScrollUpButton className={styles.selectScroll}><ChevronDown size={15}/></Select.ScrollUpButton><Select.Viewport className={styles.selectViewport}>{options.map((option) => <Select.Item className={styles.selectItem} key={option.value} value={option.value}><Select.ItemIndicator><Check size={14}/></Select.ItemIndicator><Select.ItemText>{option.label}</Select.ItemText></Select.Item>)}</Select.Viewport><Select.ScrollDownButton className={styles.selectScroll}><ChevronDown size={15}/></Select.ScrollDownButton></Select.Content></Select.Portal></Select.Root>; }
function StatusItem({ icon, label, value, detail }: { icon: ReactNode; label: string; value: string; detail: string }) { return <div className={styles.statusItem}><i>{icon}</i><span><small>{label}</small><strong>{value}</strong><p>{detail}</p></span></div>; }
function GroupTitle({ title }: { title: string }) { return <h3 className={styles.groupTitle}>{title}</h3>; }
function ReadOnlyRow({ icon, label, detail, value }: { icon: ReactNode; label: string; detail: string; value: string }) { return <div className={styles.readOnlyRow}><i>{icon}</i><span><strong>{label}</strong><small>{detail}</small></span><b>{value}</b></div>; }
function EditableRow({ icon, label, detail, children }: { icon: ReactNode; label: string; detail: string; children: ReactNode }) { return <div className={styles.editableRow}><i>{icon}</i><span><strong>{label}</strong><small>{detail}</small></span><div>{children}</div></div>; }
function SettingsLink({ href, icon, title, detail }: { href: string; icon: ReactNode; title: string; detail: string }) { return <Link className={styles.settingsLink} href={href}><i>{icon}</i><span><strong>{title}</strong><small>{detail}</small></span><ChevronRight size={17}/></Link>; }
function FeatureRow({ icon, title, detail, value, href, external = false }: { icon: ReactNode; title: string; detail: string; value: string; href: string; external?: boolean }) { const content = <><i>{icon}</i><span><strong>{title}</strong><small>{detail}</small></span><b>{value}</b><ChevronRight size={17}/></>; return external ? <a className={styles.featureRow} href={href}>{content}</a> : <Link className={styles.featureRow} href={href}>{content}</Link>; }
function SettingRow({ icon, title, detail, children }: { icon: ReactNode; title: string; detail: string; children: ReactNode }) { return <div className={styles.settingRow}><i>{icon}</i><span><strong>{title}</strong><small>{detail}</small></span>{children}</div>; }
function ActionBar({ note, children }: { note: string; children: ReactNode }) { return <div className={styles.actionBar}><small>{note}</small>{children}</div>; }
function messageFrom(value: unknown, fallback: string) { return value instanceof Error ? value.message : fallback; }
function displayPlan(value?: string) { const plan = value?.trim() || "trial"; return plan.charAt(0).toUpperCase() + plan.slice(1); }
function displayRole(value?: string) { const role = value?.trim().toLowerCase().replaceAll("_", " ") || "workspace owner"; return role.charAt(0).toUpperCase() + role.slice(1); }
function profileKey(businessName: string, timezone: string, bookingDuration: number) { return JSON.stringify([businessName.trim(), timezone, bookingDuration]); }
function timezoneOptions(current: string) {
  const api = Intl as typeof Intl & { supportedValuesOf?: (key: "timeZone") => string[] };
  const values = api.supportedValuesOf?.("timeZone") ?? ["UTC", "Africa/Cairo", "Africa/Dakar", "Africa/Johannesburg", "Africa/Kinshasa", "Africa/Lagos", "Africa/Nairobi", "America/New_York", "Asia/Dubai", "Europe/London"];
  return Array.from(new Set([current, "UTC", ...values])).sort().map((timezone) => ({ value: timezone, label: timezone.replaceAll("_", " ") }));
}

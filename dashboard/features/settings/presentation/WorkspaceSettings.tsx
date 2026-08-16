"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import * as Select from "@radix-ui/react-select";
import { BadgeCheck, Bell, Building2, Check, ChevronDown, ChevronRight, Clock3, Code2, CreditCard, FileText, Globe2, Headphones, KeyRound, Link2, LoaderCircle, LockKeyhole, Mail, Plug, Save, ShieldCheck, Trash2, UserRound } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { listAgents } from "@/lib/api/agents";
import { getIntegrationConnections } from "@/lib/api/integrations";
import { authApi } from "@/lib/api/auth";
import { loadPrivacyRetention, loadWorkspaceCallDefaults, loadWorkspaceNotificationPreferences, loadWorkspaceProfile, loadWorkspaceWebhook, savePrivacyRetention, saveWorkspaceCallDefaults, saveWorkspaceNotificationPreferences, saveWorkspaceProfile, saveWorkspaceWebhook } from "@/lib/api/tenant";
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
  const [saveTranscript, setSaveTranscript] = useState(true), [recordCalls, setRecordCalls] = useState(false), [bargeInSensitivity, setBargeInSensitivity] = useState(.7);
  const [consoleBookingNotifications, setConsoleBookingNotifications] = useState(true), [emailBookingNotifications, setEmailBookingNotifications] = useState(true);
  const [saving, setSaving] = useState<"profile" | "calls" | "notifications" | "privacy" | "webhook" | "">(""), [message, setMessage] = useState(""), [error, setError] = useState("");

  useEffect(() => {
    Promise.all([loadPrivacyRetention(), loadWorkspaceWebhook(), loadWorkspaceProfile(), loadWorkspaceCallDefaults(), loadWorkspaceNotificationPreferences()]).then(([privacy, webhook, profile, calls, notifications]) => {
      setConversationDays(privacy.conversationRetentionDays); setRecordingDays(privacy.recordingRetentionDays);
      setRecordingEnabled(privacy.recordingEnabledForAnyAgent); setWebhookUrl(webhook.webhookUrl ?? ""); setSecretConfigured(webhook.secretConfigured);
      setBusinessName(profile.businessName); setWorkspaceTimezone(profile.timezone); setBookingDuration(profile.defaultBookingDurationMinutes);
      setSavedProfile(profileKey(profile.businessName, profile.timezone, profile.defaultBookingDurationMinutes));
      setSaveTranscript(calls.saveTranscript); setRecordCalls(calls.recordCalls); setBargeInSensitivity(calls.bargeInSensitivity);
      setConsoleBookingNotifications(notifications.consoleBookingNotificationsEnabled); setEmailBookingNotifications(notifications.emailBookingNotificationsEnabled);
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
  async function saveCalls() {
    setSaving("calls"); setError(""); setMessage("");
    try {
      const settings = await saveWorkspaceCallDefaults({ saveTranscript, recordCalls, bargeInSensitivity });
      setSaveTranscript(settings.saveTranscript); setRecordCalls(settings.recordCalls); setBargeInSensitivity(settings.bargeInSensitivity);
      setMessage("Call defaults saved. New agents will start with these choices.");
    } catch (caught) { setError(messageFrom(caught, "Unable to save call defaults.")); } finally { setSaving(""); }
  }
  async function saveNotifications() {
    setSaving("notifications"); setError(""); setMessage("");
    try {
      const settings = await saveWorkspaceNotificationPreferences({ consoleBookingNotificationsEnabled: consoleBookingNotifications, emailBookingNotificationsEnabled: emailBookingNotifications });
      setConsoleBookingNotifications(settings.consoleBookingNotificationsEnabled); setEmailBookingNotifications(settings.emailBookingNotificationsEnabled);
      setMessage("Notification preferences saved and will apply to new booking events.");
    } catch (caught) { setError(messageFrom(caught, "Unable to save notification preferences.")); } finally { setSaving(""); }
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
      {active === "calls" && <CallsSection agentCount={agentCount} bargeInSensitivity={bargeInSensitivity} recordCalls={recordCalls} saveTranscript={saveTranscript} saving={saving === "calls"} setBargeInSensitivity={setBargeInSensitivity} setRecordCalls={setRecordCalls} setSaveTranscript={setSaveTranscript} save={saveCalls}/>}
      {active === "notifications" && <NotificationsSection connectionCount={connectionCount} consoleEnabled={consoleBookingNotifications} emailEnabled={emailBookingNotifications} saving={saving === "notifications"} setConsoleEnabled={setConsoleBookingNotifications} setEmailEnabled={setEmailBookingNotifications} save={saveNotifications}/>}
      {active === "privacy" && <PrivacySection acknowledged={acknowledged} conversationDays={conversationDays} recordingDays={recordingDays} recordingEnabled={recordingEnabled} recordingOptions={recordingOptions} saving={saving === "privacy"} setAcknowledged={setAcknowledged} setConversationDays={(next) => { setConversationDays(next); if (recordingDays > next) setRecordingDays(Math.max(...[7, 30, 90].filter((days) => days <= next))); }} setRecordingDays={setRecordingDays} save={savePrivacy}/>}
      {active === "security" && <SecuritySection email={tenant?.email ?? ""}/>}
      {active === "developer" && <DeveloperSection secretConfigured={secretConfigured} saving={saving === "webhook"} webhookSecret={webhookSecret} webhookUrl={webhookUrl} setWebhookSecret={setWebhookSecret} setWebhookUrl={setWebhookUrl} save={saveWebhook}/>}
    </main>
    <footer className={styles.supportBar}><span>Need help choosing or changing a workspace setting?</span><Link href="/help">Open the Sauti help centre <ChevronRight size={15}/></Link></footer>
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
function CallsSection(props: { agentCount: number | null; saveTranscript: boolean; recordCalls: boolean; bargeInSensitivity: number; saving: boolean; setSaveTranscript: (value: boolean) => void; setRecordCalls: (value: boolean) => void; setBargeInSensitivity: (value: number) => void; save: () => Promise<void> }) { return <section className={styles.panel}><div className={styles.panelHeader}><div><h2>Calls & AI defaults</h2><p>Choose the starting behavior used whenever someone creates a new agent.</p></div><span className={styles.statusNeutral}>{props.agentCount === null ? "Workspace defaults" : `${props.agentCount} existing agent${props.agentCount === 1 ? "" : "s"} unchanged`}</span></div><div className={styles.settingRows}><SettingRow icon={<FileText size={19}/>} title="Save call transcripts" detail="Keep transcripts for review, analytics, and customer follow-up on newly created agents."><ModernSwitch checked={props.saveTranscript} label="Save call transcripts" onChange={props.setSaveTranscript}/></SettingRow><SettingRow icon={<Headphones size={19}/>} title="Record calls" detail="Enable recording by default for new agents. Caller notice and local consent rules still apply."><ModernSwitch checked={props.recordCalls} label="Record calls by default" onChange={props.setRecordCalls}/></SettingRow><SettingRow icon={<Bell size={19}/>} title="Interruption sensitivity" detail="How quickly a new agent yields when the caller starts speaking."><SettingsStringSelect ariaLabel="Default interruption sensitivity" options={[{value:"0.45",label:"Patient"},{value:"0.70",label:"Balanced"},{value:"0.90",label:"Responsive"}]} value={props.bargeInSensitivity.toFixed(2)} onValueChange={(value) => props.setBargeInSensitivity(Number(value))}/></SettingRow></div><div className={styles.explainer}><ShieldCheck size={18}/><p>These are creation defaults. Existing live agents keep their current behavior so a workspace edit cannot unexpectedly change active calls.</p></div><ActionBar note="Saved values are loaded automatically in the new-agent setup."><button disabled={props.saving} onClick={() => void props.save()} type="button">{props.saving ? <LoaderCircle className={styles.spin} size={17}/> : <Save size={17}/>} {props.saving ? "Saving…" : "Save call defaults"}</button></ActionBar></section>; }
function NotificationsSection(props: { connectionCount: number | null; consoleEnabled: boolean; emailEnabled: boolean; saving: boolean; setConsoleEnabled: (value: boolean) => void; setEmailEnabled: (value: boolean) => void; save: () => Promise<void> }) { return <section className={styles.panel}><div className={styles.panelHeader}><div><h2>Notifications</h2><p>Control which owner alerts Sauti creates when booking activity happens.</p></div><span className={styles.statusNeutral}>{props.connectionCount === null ? "Provider status unavailable" : `${props.connectionCount} connected provider${props.connectionCount === 1 ? "" : "s"}`}</span></div><div className={styles.settingRows}><SettingRow icon={<Bell size={19}/>} title="Console booking alerts" detail="Create notifications in the Sauti notification centre for booking confirmations and follow-up."><ModernSwitch checked={props.consoleEnabled} label="Console booking notifications" onChange={props.setConsoleEnabled}/></SettingRow><SettingRow icon={<Mail size={19}/>} title="Booking emails" detail="Email the configured agent recipient, or the workspace owner when no recipient is set."><ModernSwitch checked={props.emailEnabled} label="Booking email notifications" onChange={props.setEmailEnabled}/></SettingRow></div><div className={styles.explainer}><ShieldCheck size={18}/><p>Calendar synchronization failures always create a console alert because they require owner action. Customer SMS and WhatsApp confirmations remain controlled by the agent and connected provider.</p></div><ActionBar note="These workspace switches gate notification channels selected on each agent."><button disabled={props.saving} onClick={() => void props.save()} type="button">{props.saving ? <LoaderCircle className={styles.spin} size={17}/> : <Save size={17}/>} {props.saving ? "Saving…" : "Save notifications"}</button></ActionBar></section>; }

function PrivacySection(props: { conversationDays: number; recordingDays: number; recordingEnabled: boolean; acknowledged: boolean; recordingOptions: number[]; saving: boolean; setConversationDays: (value: number) => void; setRecordingDays: (value: number) => void; setAcknowledged: (value: boolean) => void; save: () => Promise<void> }) {
  return <section className={styles.panel}><div className={styles.panelHeader}><div><h2>Data & privacy</h2><p>Control how long identifiable call content remains available in Sauti.</p></div></div><div className={styles.settingRows}><SettingRow icon={<Clock3 size={19}/>} title="Conversation data" detail="Caller numbers, transcripts, summaries, intent, sentiment, archive state, and transfer details."><SettingsSelect ariaLabel="Conversation retention" options={[30, 90, 180, 365]} value={props.conversationDays} onValueChange={props.setConversationDays}/></SettingRow><SettingRow icon={<Trash2 size={19}/>} title="Call recordings" detail="Local audio and Telnyx-hosted recordings are permanently deleted after this period."><SettingsSelect ariaLabel="Recording retention" options={props.recordingOptions} value={props.recordingDays} onValueChange={props.setRecordingDays}/></SettingRow>{props.recordingEnabled && <label className={styles.acknowledgement}><input checked={props.acknowledged} onChange={(event) => props.setAcknowledged(event.target.checked)} type="checkbox"/><i aria-hidden="true">{props.acknowledged && <Check size={14}/>}</i><span><strong>Recording compliance confirmation</strong><small>I confirm our recording-enabled agents provide the AI and recording notice and obtain the consent required where we operate.</small></span></label>}</div><div className={styles.boundary}><FileText size={19}/><div><strong>Deletion boundaries</strong><p>Bookings, billing evidence, audit records, and customer data written to connected providers follow separate lifecycles. Remove those records at their source when required.</p><span><Link href="/privacy">Privacy policy</Link><Link href="/terms">Terms</Link></span></div></div><ActionBar note="Changes apply workspace-wide on the next retention run."><button disabled={props.saving || (props.recordingEnabled && !props.acknowledged)} onClick={() => void props.save()} type="button">{props.saving ? <LoaderCircle className={styles.spin} size={17}/> : <Save size={17}/>} {props.saving ? "Saving…" : "Save changes"}</button></ActionBar></section>;
}
function DeveloperSection(props: { webhookUrl: string; webhookSecret: string; secretConfigured: boolean; saving: boolean; setWebhookUrl: (value: string) => void; setWebhookSecret: (value: string) => void; save: () => Promise<void> }) { return <section className={styles.panel}><div className={styles.panelHeader}><div><h2>Developer</h2><p>Send qualifying Sauti events to one HTTPS endpoint owned by your business.</p></div><span className={props.webhookUrl ? styles.statusGood : styles.statusNeutral}>{props.webhookUrl ? "Configured" : "Not configured"}</span></div><div className={styles.developerForm}><label className={styles.inputField}><span>Destination URL</span><input autoComplete="url" onChange={(event) => props.setWebhookUrl(event.target.value)} placeholder="https://example.com/webhooks/sauti" type="url" value={props.webhookUrl}/><small>Leave empty and save to disable delivery.</small></label><label className={styles.inputField}><span>Signing secret</span><input autoComplete="new-password" onChange={(event) => props.setWebhookSecret(event.target.value)} placeholder={props.secretConfigured ? "Secret configured — enter a value only to replace it" : "Create a strong signing secret"} type="password" value={props.webhookSecret}/><small>{props.secretConfigured ? "The stored secret is never returned to the browser." : "Use this to verify that events were sent by Sauti."}</small></label><div className={styles.webhookStatus}><i className={props.webhookUrl ? styles.connected : ""}><Link2 size={16}/></i><span><strong>{props.webhookUrl ? "Webhook configured" : "No webhook configured"}</strong><small>{props.webhookUrl ? "Delivery uses your saved HTTPS destination." : "Sauti will not send workspace events to a custom endpoint."}</small></span></div></div><ActionBar note="Secrets are encrypted at rest and are never displayed after saving."><button disabled={props.saving} onClick={() => void props.save()} type="button">{props.saving ? <LoaderCircle className={styles.spin} size={17}/> : <Save size={17}/>} {props.saving ? "Saving…" : "Save webhook"}</button></ActionBar></section>; }
function SecuritySection({ email }: { email: string }) { const [currentPassword,setCurrentPassword]=useState(""),[newPassword,setNewPassword]=useState(""),[confirmPassword,setConfirmPassword]=useState(""),[saving,setSaving]=useState(false),[notice,setNotice]=useState(""),[failure,setFailure]=useState(""); async function changePassword(){setFailure("");setNotice("");if(newPassword!==confirmPassword){setFailure("The new passwords do not match.");return;}setSaving(true);try{const response=await authApi.changePassword(currentPassword,newPassword);setCurrentPassword("");setNewPassword("");setConfirmPassword("");setNotice(response.message);}catch(caught){setFailure(messageFrom(caught,"Unable to change your password."));}finally{setSaving(false);}} return <section className={styles.panel}><div className={styles.panelHeader}><div><h2>Security</h2><p>Change sign-in credentials from inside Sauti. No email application is opened.</p></div><span className={styles.statusGood}>Protected</span></div>{failure&&<div className={styles.inlineError} role="alert">{failure}</div>}{notice&&<div className={styles.inlineSuccess} role="status"><Check size={16}/>{notice}</div>}<div className={styles.securityGrid}><div><GroupTitle title="Change password"/><div className={styles.securityForm}><label className={styles.inputField}><span>Current password</span><input autoComplete="current-password" onChange={(event)=>setCurrentPassword(event.target.value)} type="password" value={currentPassword}/></label><label className={styles.inputField}><span>New password</span><input autoComplete="new-password" minLength={8} onChange={(event)=>setNewPassword(event.target.value)} type="password" value={newPassword}/><small>Use at least 8 characters.</small></label><label className={styles.inputField}><span>Confirm new password</span><input autoComplete="new-password" minLength={8} onChange={(event)=>setConfirmPassword(event.target.value)} type="password" value={confirmPassword}/></label></div></div><div><GroupTitle title="Account protection"/><ReadOnlyRow icon={<Mail size={18}/>} label="Verified account email" detail="Used for sign-in, billing, and recovery notices." value={email || "Not available"}/><ReadOnlyRow icon={<KeyRound size={18}/>} label="Other sessions" detail="Changing your password revokes active refresh sessions on other devices." value="Revoked on save"/><div className={styles.explainer}><ShieldCheck size={18}/><p>If this account was created with Google and you do not know a Sauti password, continue using Google sign-in or use the in-app recovery flow from the login screen.</p></div></div></div><ActionBar note="You will remain signed in on this device until the current access token expires."><button disabled={saving||!currentPassword||newPassword.length<8||!confirmPassword} onClick={()=>void changePassword()} type="button">{saving?<LoaderCircle className={styles.spin} size={17}/>:<Save size={17}/>} {saving?"Changing…":"Change password"}</button></ActionBar></section>; }

function SettingsSelect({ ariaLabel, options, value, onValueChange }: { ariaLabel: string; options: number[]; value: number; onValueChange: (value: number) => void }) { return <SettingsStringSelect ariaLabel={ariaLabel} options={options.map((days) => ({ value: String(days), label: `${days} days` }))} value={String(value)} onValueChange={(next) => onValueChange(Number(next))}/>; }
function ModernSwitch({ checked, label, onChange }: { checked: boolean; label: string; onChange: (value: boolean) => void }) { return <button aria-checked={checked} aria-label={label} className={styles.modernSwitch} data-checked={checked} onClick={() => onChange(!checked)} role="switch" type="button"><span/></button>; }
function SettingsStringSelect({ ariaLabel, options, value, onValueChange }: { ariaLabel: string; options: Array<{ value: string; label: string }>; value: string; onValueChange: (value: string) => void }) { return <Select.Root value={value} onValueChange={onValueChange}><Select.Trigger aria-label={ariaLabel} className={styles.selectTrigger}><Select.Value/><Select.Icon><ChevronDown size={16}/></Select.Icon></Select.Trigger><Select.Portal><Select.Content align="end" className={styles.selectContent} position="popper" sideOffset={7}><Select.ScrollUpButton className={styles.selectScroll}><ChevronDown size={15}/></Select.ScrollUpButton><Select.Viewport className={styles.selectViewport}>{options.map((option) => <Select.Item className={styles.selectItem} key={option.value} value={option.value}><Select.ItemIndicator><Check size={14}/></Select.ItemIndicator><Select.ItemText>{option.label}</Select.ItemText></Select.Item>)}</Select.Viewport><Select.ScrollDownButton className={styles.selectScroll}><ChevronDown size={15}/></Select.ScrollDownButton></Select.Content></Select.Portal></Select.Root>; }
function StatusItem({ icon, label, value, detail }: { icon: ReactNode; label: string; value: string; detail: string }) { return <div className={styles.statusItem}><i>{icon}</i><span><small>{label}</small><strong>{value}</strong><p>{detail}</p></span></div>; }
function GroupTitle({ title }: { title: string }) { return <h3 className={styles.groupTitle}>{title}</h3>; }
function ReadOnlyRow({ icon, label, detail, value }: { icon: ReactNode; label: string; detail: string; value: string }) { return <div className={styles.readOnlyRow}><i>{icon}</i><span><strong>{label}</strong><small>{detail}</small></span><b>{value}</b></div>; }
function EditableRow({ icon, label, detail, children }: { icon: ReactNode; label: string; detail: string; children: ReactNode }) { return <div className={styles.editableRow}><i>{icon}</i><span><strong>{label}</strong><small>{detail}</small></span><div>{children}</div></div>; }
function SettingsLink({ href, icon, title, detail }: { href: string; icon: ReactNode; title: string; detail: string }) { return <Link className={styles.settingsLink} href={href}><i>{icon}</i><span><strong>{title}</strong><small>{detail}</small></span><ChevronRight size={17}/></Link>; }
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

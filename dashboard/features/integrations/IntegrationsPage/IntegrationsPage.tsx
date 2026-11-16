"use client";

import { Select } from "@/components/ui/select";
import Image from "next/image";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Bot, CalendarDays, Check, CircleAlert, Database, LoaderCircle, MessageSquare,
  Plug, Search, Settings2, ShieldCheck, TableProperties, TestTube2, Trash2, WalletCards, X,
} from "lucide-react";
import { listAgents } from "@/lib/api/agents";
import {
  authorizeGoogleCalendar,
  authorizeProvider,
  completeWhatsAppSignup,
  createIntegrationConnection,
  deleteIntegrationConnection,
  getAgentIntegrations,
  getGoogleCalendarStatus,
  getIntegrationCatalog,
  getIntegrationConnections,
  getWhatsAppSignupConfiguration,
  getWhatsAppTemplates,
  initializeGoogleSheets,
  putAgentIntegration,
  selectGoogleCalendar,
  testGoogleCalendar,
  testIntegrationConnection,
  updateIntegrationConnection,
  type AgentIntegration,
  type IntegrationCatalogEntry,
  type IntegrationConnection,
  type GoogleCalendarStatus,
  type WhatsAppSignupConfiguration,
  type WhatsAppTemplate,
} from "@/lib/api/integrations";
import type { Agent } from "@/types/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { Card, EmptyState, Skeleton, Switch, Tooltip } from "@/components/ui/primitives";
import { PageHeader } from "@/components/ui/console";
import { cn } from "@/lib/utils";
import { readSession } from "@/lib/session";
import styles from "./IntegrationsPage.module.css";

type Filter = "all" | "calendar" | "messaging" | "crm" | "data" | "notifications" | "payments" | "developer" | "during" | "post" | "connected";
const oauthProviders = ["google_sheets", "hubspot", "salesforce", "calendly"];

const disconnectImpact: Record<string, { resource: string; intact: string; stops: string }> = {
  google_calendar: {
    resource: "Calendar access",
    intact: "Existing bookings and calendar events stay intact.",
    stops: "Agents will stop creating new Calendar events until you reconnect it.",
  },
  google_sheets: {
    resource: "Google Sheets access",
    intact: "Existing spreadsheet data stays intact.",
    stops: "Agents will stop reading from or writing to connected Sheets until you reconnect it.",
  },
  hubspot: {
    resource: "HubSpot access",
    intact: "Existing HubSpot records stay intact.",
    stops: "Agents will stop reading from or updating HubSpot records until you reconnect it.",
  },
  salesforce: {
    resource: "Salesforce access",
    intact: "Existing Salesforce records stay intact.",
    stops: "Agents will stop reading from or updating Salesforce records until you reconnect it.",
  },
  calendly: {
    resource: "Calendly access",
    intact: "Existing Calendly event types and bookings stay intact.",
    stops: "Agents will stop using Calendly scheduling data until you reconnect it.",
  },
};

const logos: Record<string, string> = {
  google_calendar: "/logos/google-calendar.svg",
  calendly: "/logos/calendly.svg",
  telnyx_sms: "/logos/telnyx.svg",
  custom_webhook: "/logos/webhook.svg",
  whatsapp: "/logos/whatsapp.svg",
  email: "/logos/email.svg",
  slack: "/logos/slack.svg",
  google_sheets: "/logos/google-sheets.svg",
  hubspot: "/logos/hubspot.svg",
  salesforce: "/logos/salesforce.svg",
  mpesa: "/logos/mpesa.svg",
};

const labels: Record<string, string> = {
  webhookUrl: "Webhook URL", authType: "Authentication type", authToken: "Bearer token",
  apiKey: "API key", hmacSecret: "HMAC secret", wabaId: "WABA ID",
  phoneNumberId: "Phone-number ID", templateName: "Approved template name",
  templateLanguage: "Template language", accessToken: "Long-lived system-user token",
  recipients: "Recipients (comma-separated)", spreadsheetId: "Spreadsheet ID",
  range: "Customer lookup range", lookupColumn: "Phone column", customerNameColumn: "Name column",
  customerEmailColumn: "Email column", returnColumns: "Returned customer columns",
  appendRange: "Post-call append range", appendColumns: "Append columns", shortcode: "Shortcode", environment: "Environment",
  minimumAmount: "Minimum amount", maximumAmount: "Maximum amount",
  consumerKey: "Consumer key", consumerSecret: "Consumer secret", passkey: "Passkey",
  eventTypeUri: "Event type URI",
  bookingTitle: "Booking title",
};

const filterOptions: Array<{ value: Filter; label: string }> = [
  { value: "all", label: "All" },
  { value: "calendar", label: "Calendar" },
  { value: "messaging", label: "Messaging" },
  { value: "crm", label: "CRM" },
  { value: "data", label: "Data" },
  { value: "notifications", label: "Notifications" },
  { value: "payments", label: "Payments" },
  { value: "developer", label: "Developer" },
  { value: "during", label: "During call" },
  { value: "post", label: "Post call" },
  { value: "connected", label: "Connected" },
];

export function IntegrationsPage() {
  const searchParams = useSearchParams();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [agentId, setAgentId] = useState(searchParams.get("agentId") ?? "");
  const [catalog, setCatalog] = useState<IntegrationCatalogEntry[]>([]);
  const [connections, setConnections] = useState<IntegrationConnection[]>([]);
  const [bindings, setBindings] = useState<AgentIntegration[]>([]);
  const [filter, setFilter] = useState<Filter>("all");
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<IntegrationCatalogEntry | null>(null);
  const [whatsappEditing, setWhatsappEditing] = useState(false);
  const [calendarEditing, setCalendarEditing] = useState(false);
  const [googleAuthorizing, setGoogleAuthorizing] = useState<IntegrationCatalogEntry | null>(null);
  const [disconnecting, setDisconnecting] = useState<IntegrationConnection | null>(null);
  const [busy, setBusy] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const openedOAuthProvider = useRef<string | null>(null);

  const refresh = useCallback(async (selected = agentId) => {
    const [nextCatalog, nextConnections] = await Promise.all([
      getIntegrationCatalog(), getIntegrationConnections(),
    ]);
    setCatalog(nextCatalog);
    setConnections(nextConnections);
    setBindings(selected ? await getAgentIntegrations(selected) : []);
  }, [agentId]);

  useEffect(() => {
    listAgents().then(async (loaded) => {
      setAgents(loaded);
      const selected = loaded.some((agent) => agent.id === agentId) ? agentId : loaded[0]?.id ?? "";
      setAgentId(selected);
      await refresh(selected);
    }).catch(showError).finally(() => setLoading(false));
  // The initial selection is intentionally resolved once.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!agentId || loading) return;
    getAgentIntegrations(agentId).then(setBindings).catch(showError);
  }, [agentId, loading]);

  useEffect(() => {
    const provider = searchParams.get("provider");
    if (!loading && agentId && provider === "google_calendar"
        && openedOAuthProvider.current !== provider) {
      openedOAuthProvider.current = provider;
      setCalendarEditing(true);
    }
    if (!loading && agentId && provider === "google_sheets"
        && openedOAuthProvider.current !== provider) {
      openedOAuthProvider.current = provider;
      const sheets = catalog.find((entry) => entry.provider === "google_sheets");
      if (sheets) setEditing(sheets);
    }
  }, [agentId, catalog, loading, searchParams]);

  function showError(caught: unknown) {
    setError(caught instanceof Error ? caught.message : "Unable to load integrations.");
  }

  const visible = useMemo(() => catalog.filter((entry) => {
    const binding = bindings.find((item) => item.provider === entry.provider);
    const normalized = query.trim().toLowerCase();
    if (normalized && ![
      entry.name,
      entry.category,
      entry.description,
      entry.provider,
    ].some((value) => value.toLowerCase().includes(normalized))) return false;
    if (filter === "during") return entry.duringCall;
    if (filter === "post") return entry.postCall;
    if (filter === "connected") return binding?.connectionStatus === "connected" || binding?.connectionStatus === "built_in";
    if (filter !== "all") return categoryKey(entry.category) === filter;
    return true;
  }), [bindings, catalog, filter, query]);

  const grouped = useMemo(() => {
    const groups = new Map<string, IntegrationCatalogEntry[]>();
    visible.forEach((entry) => {
      const group = displayCategory(entry.category);
      groups.set(group, [...(groups.get(group) ?? []), entry]);
    });
    return Array.from(groups.entries());
  }, [visible]);

  async function toggle(entry: IntegrationCatalogEntry, enabled: boolean) {
    if (!agentId) return;
    const connection = connections.find((item) => item.provider === entry.provider);
    if (enabled && entry.requiresConnection && !connection) {
      if (entry.provider === "whatsapp") {
        setWhatsappEditing(true);
        return;
      }
      if (entry.provider === "google_calendar" || oauthProviders.includes(entry.provider)) {
        if (entry.provider.startsWith("google_")) setGoogleAuthorizing(entry);
        else await startOAuth(entry);
        return;
      }
      setEditing(entry);
      return;
    }
    setBusy(entry.provider);
    try {
      const currentBinding = bindings.find((item) => item.provider === entry.provider);
      await putAgentIntegration(agentId, {
        provider: entry.provider, enabled, connectionId: connection?.id ?? null,
        configuration: currentBinding?.configuration,
      });
      await refresh(agentId);
    } catch (caught) { showError(caught); } finally { setBusy(""); }
  }

  async function startOAuth(entry: IntegrationCatalogEntry) {
    if (!agentId) return;
    setBusy(entry.provider);
    setError("");
    try {
      const result = entry.provider === "google_calendar"
        ? await authorizeGoogleCalendar(agentId)
        : await authorizeProvider(entry.provider, agentId);
      window.location.assign(result.authorizationUrl);
    } catch (caught) {
      showError(caught);
      setBusy("");
    }
  }

  async function testConnection(connection: IntegrationConnection) {
    setBusy(connection.provider);
    try {
      const tested = await testIntegrationConnection(connection.id, agentId);
      await refresh(agentId);
      if (tested.status === "error") throw new Error(tested.lastError || `${connection.displayName} test failed.`);
    }
    catch (caught) { showError(caught); } finally { setBusy(""); }
  }

  async function disconnect(connection: IntegrationConnection): Promise<boolean> {
    setBusy(connection.provider);
    try {
      const result = await deleteIntegrationConnection(connection.id);
      await refresh(agentId);
      if (result.providerRevocationAttempted && !result.providerRevocationConfirmed) {
        setError(`${connection.displayName} was disconnected from Sauti, but Google did not confirm revocation. Remove Sauti from your Google Account permissions to finish revoking access.`);
      }
      return true;
    }
    catch (caught) { showError(caught); return false; } finally { setBusy(""); }
  }

  const counts = useMemo(() => {
    const result = {} as Record<Filter, number>;
    filterOptions.forEach(({ value }) => {
      result[value] = catalog.filter((entry) => matchesFilter(entry, value, bindings.find((item) => item.provider === entry.provider))).length;
    });
    return result;
  }, [bindings, catalog]);
  const connectedCount = counts.connected ?? 0;
  const selectedAgent = agents.find((agent) => agent.id === agentId);

  return (
    <div className="grid min-w-0 grid-cols-1 gap-6 [&>*]:min-w-0">
      <PageHeader
        title="Integrations"
        description="Connect an account once, then choose which tools each agent can use."
        actions={
          <div className="flex items-center gap-2">
            <span className="hidden text-[0.8rem] text-fg-muted sm:inline">Settings for</span>
            <div className="w-52">
              <Select
                size="sm"
                ariaLabel="Configure integrations for agent"
                icon={<Bot />}
                value={agentId || "none"}
                onValueChange={(value) => value !== "none" && setAgentId(value)}
                options={agents.length ? agents.map((agent) => ({ value: agent.id, label: agent.name })) : [{ value: "none", label: "No agents yet" }]}
              />
            </div>
          </div>
        }
      />

      {searchParams.get("calendar") === "connected" ? <Notice tone="success">Google Calendar connected.</Notice> : null}
      {searchParams.get("oauth") === "connected" ? (
        <Notice tone="success">{searchParams.get("provider") === "google_sheets" ? "Google account connected. Choose the spreadsheet to finish setup." : "Account connected."}</Notice>
      ) : null}
      {searchParams.get("oauth") === "cancelled" ? <Notice tone="error">Authorization was cancelled.</Notice> : null}
      {searchParams.get("oauth") === "failed" ? <Notice tone="error">Google authorization couldn&apos;t finish. Check the OAuth client, callback URL, enabled APIs and test-user access.</Notice> : null}
      {error ? <Notice tone="error" onDismiss={() => setError("")}>{error}</Notice> : null}

      <div className="grid grid-cols-1 gap-6 [&>*]:min-w-0 lg:grid-cols-[200px_minmax(0,1fr)]">
        <aside className="lg:sticky lg:top-20 lg:self-start">
          <nav aria-label="Integration filters" className="flex gap-1 overflow-x-auto pb-1 lg:flex-col lg:gap-px lg:overflow-visible lg:pb-0">
            {filterOptions.map(({ value, label }, index) => (
              <Fragment key={value}>
                {index === 1 || index === 8 ? <span className="hidden h-px bg-line lg:my-2 lg:block" aria-hidden="true" /> : null}
                <button
                  type="button"
                  aria-current={filter === value ? "true" : undefined}
                  onClick={() => setFilter(value)}
                  className={cn(
                    "flex h-8 shrink-0 cursor-pointer items-center justify-between gap-3 rounded-[7px] border px-2.5 text-left font-sans text-[0.84rem] transition-colors",
                    filter === value ? "border-line bg-surface font-medium text-ink shadow-[var(--shadow-card)]" : "border-transparent bg-transparent text-fg-soft hover:bg-tint/[0.05] hover:text-fg",
                  )}
                >
                  {label}
                  <span className="text-[0.75rem] tabular-nums text-fg-muted">{counts[value] ?? 0}</span>
                </button>
              </Fragment>
            ))}
          </nav>
        </aside>

        <section className="grid content-start gap-4">
          <div className="flex flex-wrap items-center gap-3">
            <label className="relative w-full sm:max-w-sm">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-fg-muted" />
              <Input aria-label="Search integrations" className="h-9 pl-9 text-[0.85rem]" onChange={(event) => setQuery(event.target.value)} placeholder="Search tools" value={query} />
            </label>
            <p className="m-0 text-[0.8rem] text-fg-muted sm:ml-auto">
              {connectedCount} connected{selectedAgent ? <> · showing settings for <span className="text-fg">{selectedAgent.name}</span></> : null}
            </p>
          </div>

          {loading ? (
            <Card className="grid gap-3 p-4">{[1, 2, 3, 4].map((row) => <Skeleton key={row} className="h-14" />)}</Card>
          ) : grouped.length === 0 ? (
            <EmptyState icon={<Plug />} title="Nothing matches" description="Try another search or filter." />
          ) : (
            <div className="grid gap-6">
              {grouped.map(([category, entries]) => (
                <section key={category} className="grid gap-2">
                  <h2 className="m-0 flex items-center gap-2 text-[0.8rem] font-medium text-fg-muted [&_svg]:size-3.5">
                    {categoryIcon(category)} {category}
                  </h2>
                  <Card>
                    <ul className="m-0 grid list-none divide-y divide-line p-0">
                      {entries.map((entry) => {
                        const binding = bindings.find((item) => item.provider === entry.provider);
                        const connection = connections.find((item) => item.provider === entry.provider);
                        const connected = binding?.connectionStatus === "connected" || binding?.connectionStatus === "built_in" || Boolean(connection);
                        const builtIn = binding?.connectionStatus === "built_in";
                        const working = busy === entry.provider;
                        const needsConnection = entry.requiresConnection && !connection;
                        const configure = () => {
                          if (entry.provider === "google_calendar") {
                            if (connection) setCalendarEditing(true);
                            else setGoogleAuthorizing(entry);
                          } else if (entry.provider === "whatsapp") {
                            setWhatsappEditing(true);
                          } else if (oauthProviders.includes(entry.provider) && !connection) {
                            if (entry.provider.startsWith("google_")) setGoogleAuthorizing(entry);
                            else void startOAuth(entry);
                          } else {
                            setEditing(entry);
                          }
                        };
                        return (
                          <li key={entry.provider} className="grid grid-cols-[36px_minmax(0,1fr)_auto] items-start gap-x-3 gap-y-2 px-4 py-3.5 sm:px-5 md:grid-cols-[36px_minmax(0,1fr)_8.5rem_auto]">
                            <span className="grid size-9 place-items-center rounded-[8px] border border-line bg-surface">
                              {logos[entry.provider]
                                ? <Image alt="" height={22} src={logos[entry.provider]} width={22} />
                                : <span className="text-fg-soft [&_svg]:size-4">{categoryIcon(entry.category)}</span>}
                            </span>
                            <div className="min-w-0">
                              <p className="m-0 flex flex-wrap items-center gap-x-2 gap-y-1 text-[0.9rem] font-medium text-ink">
                                {entry.name}
                                {entry.duringCall ? <span className="text-[0.72rem] font-normal text-fg-muted">During call</span> : null}
                                {entry.duringCall && entry.postCall ? <span className="text-[0.72rem] text-fg-muted" aria-hidden="true">·</span> : null}
                                {entry.postCall ? <span className="text-[0.72rem] font-normal text-fg-muted">After call</span> : null}
                              </p>
                              <p className="m-0 mt-0.5 text-[0.8rem] leading-snug text-fg-soft">{entry.description}</p>
                              {connection?.externalAccountId ? <p className="m-0 mt-1 text-[0.75rem] text-fg-muted">Connected as {connection.externalAccountId}</p> : null}
                              {!entry.authorizationConfigured ? <p className="m-0 mt-1 text-[0.75rem] text-warning">OAuth app credentials aren&apos;t configured on the server.</p> : null}
                              {binding?.lastDelivery ? (
                                <p className={cn("m-0 mt-1 text-[0.75rem]", binding.lastDelivery.status === "delivered" ? "text-fg-muted" : "text-danger")}>
                                  Last delivery {binding.lastDelivery.status}{binding.lastDelivery.lastError ? ` — ${binding.lastDelivery.lastError}` : ""}
                                </p>
                              ) : null}
                            </div>
                            <p className="m-0 hidden items-center gap-2 pt-1 text-[0.8rem] text-fg md:flex">
                              <span className={cn("size-1.5 rounded-full", connected ? "bg-success" : "bg-line-strong")} aria-hidden="true" />
                              {builtIn ? "Built in" : connected ? "Connected" : "Not connected"}
                            </p>
                            <div className="flex items-center justify-end gap-1">
                              {needsConnection && (entry.provider === "google_calendar" || entry.provider === "whatsapp" || oauthProviders.includes(entry.provider)) ? (
                                <Button variant="secondary" size="xs" disabled={!entry.authorizationConfigured || working || !agentId} onClick={configure}>
                                  {working ? <LoaderCircle className="animate-spin" /> : null} Connect
                                </Button>
                              ) : (
                                <Tooltip content={agentId ? `Use with ${selectedAgent?.name ?? "this agent"}` : "Pick an agent first"}>
                                  <span className="inline-flex">
                                    <Switch
                                      aria-label={`Use ${entry.name} with the selected agent`}
                                      checked={binding?.enabled ?? false}
                                      disabled={working || !agentId || (!(binding?.enabled ?? false) && !entry.authorizationConfigured)}
                                      onCheckedChange={(checked) => void toggle(entry, checked)}
                                    />
                                  </span>
                                </Tooltip>
                              )}
                              {entry.requiresConnection && !(needsConnection && (entry.provider === "google_calendar" || entry.provider === "whatsapp" || oauthProviders.includes(entry.provider))) ? (
                                <IconAction label={entry.authorizationConfigured ? "Configure" : "OAuth app not configured"} disabled={!entry.authorizationConfigured} onClick={configure}><Settings2 /></IconAction>
                              ) : null}
                              {connection ? <IconAction label="Test connection" disabled={working} onClick={() => void testConnection(connection)}><TestTube2 /></IconAction> : null}
                              {connection ? <IconAction label="Disconnect" danger onClick={() => setDisconnecting(connection)}><Trash2 /></IconAction> : null}
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  </Card>
                </section>
              ))}
            </div>
          )}
        </section>
      </div>

      {editing && <ConnectionDialog agentId={agentId} connection={connections.find((item) => item.provider === editing.provider)}
        entry={editing} onClose={() => setEditing(null)} onSaved={async () => {
        setEditing(null); await refresh(agentId);
      }} />}
      {whatsappEditing && <WhatsAppSetupDialog
        agentId={agentId}
        connection={connections.find((item) => item.provider === "whatsapp")}
        onClose={() => setWhatsappEditing(false)}
        onSaved={async () => {
          setWhatsappEditing(false);
          await refresh(agentId);
        }}
      />}
      {calendarEditing && <GoogleCalendarDialog agentId={agentId} onClose={() => setCalendarEditing(false)}
        onSaved={async () => { setCalendarEditing(false); await refresh(agentId); }} />}
      {googleAuthorizing && <GoogleAuthorizationDialog
        busy={busy === googleAuthorizing.provider}
        entry={googleAuthorizing}
        onClose={() => setGoogleAuthorizing(null)}
        onContinue={async () => { await startOAuth(googleAuthorizing); }}
      />}
      {disconnecting && <DisconnectDialog
        connection={disconnecting}
        busy={busy === disconnecting.provider}
        onClose={() => setDisconnecting(null)}
        onConfirm={async () => { if (await disconnect(disconnecting)) setDisconnecting(null); }}
      />}
    </div>
  );
}

function matchesFilter(entry: IntegrationCatalogEntry, filter: Filter, binding: AgentIntegration | undefined) {
  if (filter === "all") return true;
  if (filter === "during") return entry.duringCall;
  if (filter === "post") return entry.postCall;
  if (filter === "connected") return binding?.connectionStatus === "connected" || binding?.connectionStatus === "built_in";
  return categoryKey(entry.category) === filter;
}

function Notice({ tone, children, onDismiss }: { tone: "success" | "error"; children: React.ReactNode; onDismiss?: () => void }) {
  return (
    <div role={tone === "error" ? "alert" : "status"} className={cn(
      "flex items-start gap-2.5 rounded-[8px] border px-3.5 py-2.5 text-[0.85rem]",
      tone === "success" ? "border-success/30 bg-success/[0.06] text-fg" : "border-danger/30 bg-danger/[0.06] text-fg",
    )}>
      {tone === "success" ? <Check className="mt-0.5 size-4 shrink-0 text-success" /> : <CircleAlert className="mt-0.5 size-4 shrink-0 text-danger" />}
      <span className="flex-1">{children}</span>
      {onDismiss ? <button type="button" aria-label="Dismiss" onClick={onDismiss} className="grid size-6 cursor-pointer place-items-center rounded-[5px] border-0 bg-transparent p-0 text-fg-muted hover:text-fg"><X className="size-3.5" /></button> : null}
    </div>
  );
}

function IconAction({ label, onClick, disabled, danger = false, children }: { label: string; onClick: () => void; disabled?: boolean; danger?: boolean; children: React.ReactNode }) {
  return (
    <Tooltip content={label}>
      <Button
        variant="ghost"
        size="icon-xs"
        aria-label={label}
        disabled={disabled}
        onClick={onClick}
        className={cn("text-fg-muted [&_svg]:size-4", danger && "hover:bg-danger/[0.08] hover:text-danger")}
      >
        {children}
      </Button>
    </Tooltip>
  );
}

function GoogleAuthorizationDialog({ entry, busy, onClose, onContinue }: {
  entry: IntegrationCatalogEntry;
  busy: boolean;
  onClose: () => void;
  onContinue: () => Promise<void>;
}) {
  const calendar = entry.provider === "google_calendar";
  return <div className={styles.backdrop} onMouseDown={onClose}>
    <section className={`${styles.dialog} ${styles.googleAuthorizationDialog}`} onMouseDown={(event) => event.stopPropagation()} role="dialog" aria-modal="true" aria-labelledby="google-authorization-title">
      <header><div><span className={styles.dialogEyebrow}>Before connecting</span><h2 id="google-authorization-title">Connect {entry.name}</h2><p>Google will ask you to approve only the access needed for this integration.</p></div><Button variant="ghost" size="icon-sm" aria-label="Close Google authorization dialog" onClick={onClose}><X /></Button></header>
      <div className={styles.googleDataNotice}>
        <ShieldCheck size={20} />
        <div><h3>{calendar ? "Calendar events and availability" : "Spreadsheet content"}</h3><p>{calendar
          ? "Sauti reads busy time ranges and creates, updates, or cancels booking events in the calendar you select. It does not request calendar sharing or full-calendar administration."
          : "Sauti can read and update spreadsheets available to the connected Google account. In Sauti, access is limited to the spreadsheet and ranges you configure for the agent."}</p></div>
      </div>
      <ul className={styles.googleAuthorizationList}>
        <li>OAuth tokens are encrypted and scoped to this workspace.</li>
        <li>Google data is used only for the integration features you enable, not advertising or generalized model training.</li>
        <li>You can disconnect later; Sauti deletes its token copy and asks Google to revoke the grant.</li>
      </ul>
      <p className={styles.googlePolicyLinks}>See the <Link href="/privacy" target="_blank">Privacy Policy</Link> and <Link href="/terms" target="_blank">Terms</Link>.</p>
      <footer><Button variant="secondary" size="sm" onClick={onClose}>Cancel</Button><Button variant="primary" size="sm" disabled={busy} type="button" onClick={() => void onContinue()}>{busy && <LoaderCircle className="spin" />}{busy ? "Opening Google..." : "Continue to Google"}</Button></footer>
    </section>
  </div>;
}

function GoogleCalendarDialog({ agentId, onClose, onSaved }: {
  agentId: string; onClose: () => void; onSaved: () => Promise<void>;
}) {
  const [status, setStatus] = useState<GoogleCalendarStatus | null>(null);
  const [calendarId, setCalendarId] = useState("primary");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [testState, setTestState] = useState<"idle" | "testing" | "success" | "error">("idle");
  const [testMessage, setTestMessage] = useState("");

  useEffect(() => {
    getGoogleCalendarStatus(agentId).then((loaded) => {
      setStatus(loaded);
      setCalendarId(loaded.calendarId || "primary");
    }).catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load Calendar settings."));
  }, [agentId]);

  async function save(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(""); setTestState("idle"); setTestMessage("");
    try { await selectGoogleCalendar(agentId, calendarId); await onSaved(); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Unable to save Calendar settings."); setBusy(false); }
  }

  async function test() {
    setBusy(true); setError(""); setTestState("testing"); setTestMessage("");
    try {
      await testGoogleCalendar(agentId);
      setTestState("success");
      setTestMessage("Connection verified. Sauti can access the selected calendar.");
    } catch (caught) {
      setTestState("error");
      setTestMessage(caught instanceof Error ? caught.message : "Calendar test failed.");
    }
    finally { setBusy(false); }
  }

  return <div className={styles.backdrop} onMouseDown={onClose}>
    <form className={`${styles.dialog} ${styles.calendarDialog}`} onSubmit={(event) => void save(event)} onMouseDown={(event) => event.stopPropagation()}>
      <header><div><h2>Configure Google Calendar</h2><p>Choose the writable calendar used by this workspace connection.</p></div>
        <Button variant="ghost" size="icon-sm" aria-label="Close" onClick={onClose}><X /></Button></header>
      <div className={styles.formFields}><label><span>Calendar ID</span>
        <input required value={calendarId} onChange={(event) => setCalendarId(event.target.value)}
          placeholder="primary or calendar-id@group.calendar.google.com" />
        <small>Use “primary” for the connected account’s main calendar.</small>
      </label></div>
      {error && <div className={styles.formError}>{error}</div>}
      {testState !== "idle" && <div className={testState === "success" ? styles.formSuccess : styles.formError} role="status">
        {testState === "success" && <Check size={15} />}
        {testState === "testing" ? "Checking calendar access..." : testMessage}
      </div>}
      <footer><Button variant="secondary" size="sm" disabled={busy || !status?.connected} onClick={() => void test()}>
        {testState === "testing" && <LoaderCircle className="spin" />}
        {testState === "testing" ? "Testing..." : "Test live connection"}
      </Button>
        <Button variant="primary" size="sm" type="submit" disabled={busy || !status?.connected}>{busy ? "Saving…" : "Save calendar"}</Button></footer>
    </form>
  </div>;
}

function DisconnectDialog({ connection, busy, onClose, onConfirm }: {
  connection: IntegrationConnection;
  busy: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void>;
}) {
  const impact = disconnectImpact[connection.provider] ?? {
    resource: `${connection.displayName} access`,
    intact: `Existing ${connection.displayName} data stays intact.`,
    stops: `Agents will stop using ${connection.displayName} until you reconnect it.`,
  };

  return <div className={styles.backdrop} onMouseDown={onClose}>
    <section
      aria-describedby="disconnect-description"
      aria-labelledby="disconnect-title"
      aria-modal="true"
      className={`${styles.dialog} ${styles.disconnectDialog}`}
      onMouseDown={(event) => event.stopPropagation()}
      role="dialog"
    >
      <header>
        <div className={styles.dialogIcon}><Trash2 size={18} /></div>
        <Button variant="ghost" size="icon-sm" aria-label="Close disconnect dialog" onClick={onClose}><X /></Button>
      </header>
      <div className={styles.disconnectCopy}>
        <span className={styles.dialogEyebrow}>Connection settings</span>
        <h2 id="disconnect-title">Disconnect {connection.displayName}?</h2>
    <p id="disconnect-description">This removes the workspace connection and turns off {impact.resource} for agents using it.{connection.provider.startsWith("google_") ? " Sauti deletes its encrypted token copy. You can remove all Sauti access from Google Account permissions." : ""}</p>
    <div className={styles.impactList}>
     <div><Check size={15} /><span>{impact.intact}</span></div>
     <div><CircleAlert size={15} /><span>{impact.stops}</span></div>
        </div>
      </div>
      <footer>
        <Button variant="secondary" size="sm" onClick={onClose}>Keep connected</Button>
        <Button variant="danger" size="sm" disabled={busy} type="button" onClick={() => void onConfirm()}>
          {busy && <LoaderCircle className="spin" />}
          {busy ? "Disconnecting..." : "Disconnect"}
        </Button>
      </footer>
    </section>
  </div>;
}

type MetaSignupSession = { wabaId: string; phoneNumberId: string };
type FacebookSdk = {
  init: (options: Record<string, unknown>) => void;
  login: (
    callback: (response: { authResponse?: { code?: string }; status?: string }) => void,
    options: Record<string, unknown>,
  ) => void;
};

declare global {
  interface Window {
    FB?: FacebookSdk;
    fbAsyncInit?: () => void;
  }
}

let facebookSdkPromise: Promise<FacebookSdk> | null = null;

function loadFacebookSdk(configuration: WhatsAppSignupConfiguration) {
  if (facebookSdkPromise) return facebookSdkPromise;
  facebookSdkPromise = new Promise<FacebookSdk>((resolve, reject) => {
    const initialize = () => {
      if (!window.FB) {
        reject(new Error("Meta SDK did not initialize."));
        return;
      }
      window.FB.init({
        appId: configuration.appId,
        cookie: true,
        xfbml: false,
        version: configuration.graphVersion,
      });
      resolve(window.FB);
    };
    if (window.FB) {
      initialize();
      return;
    }
    window.fbAsyncInit = initialize;
    const existing = document.getElementById("facebook-jssdk");
    if (existing) return;
    const script = document.createElement("script");
    script.id = "facebook-jssdk";
    script.async = true;
    script.defer = true;
    script.crossOrigin = "anonymous";
    script.src = "https://connect.facebook.net/en_US/sdk.js";
    script.onerror = () => reject(new Error("Unable to load Meta Embedded Signup."));
    document.body.appendChild(script);
  });
  return facebookSdkPromise;
}

function WhatsAppSetupDialog({ agentId, connection, onClose, onSaved }: {
  agentId: string;
  connection?: IntegrationConnection;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [configuration, setConfiguration] = useState<WhatsAppSignupConfiguration | null>(null);
  const [templates, setTemplates] = useState<WhatsAppTemplate[]>([]);
  const [selection, setSelection] = useState(
    `${String(connection?.configuration.templateName ?? "")}::${String(connection?.configuration.templateLanguage ?? "")}`,
  );
  const [parameterMappings, setParameterMappings] = useState<Record<string, string>>(() => {
    const saved = connection?.configuration.templateParameterMappings;
    return saved && typeof saved === "object" && !Array.isArray(saved)
      ? Object.fromEntries(Object.entries(saved).map(([key, value]) => [key, String(value)]))
      : {};
  });
  const [connecting, setConnecting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [businessCountryCode, setBusinessCountryCode] = useState(
    () => readSession()?.tenant.countryCode || "KE",
  );
  const [businessPhoneNumber, setBusinessPhoneNumber] = useState("");

  useEffect(() => {
    Promise.all([
      getWhatsAppSignupConfiguration(),
      connection ? getWhatsAppTemplates(connection.id) : Promise.resolve([]),
    ]).then(([loadedConfiguration, loadedTemplates]) => {
      setConfiguration(loadedConfiguration);
      setTemplates(loadedTemplates);
      const savedNumber = String(connection?.configuration.businessPhoneNumber
        || connection?.configuration.displayPhoneNumber || "").replace(/\D/g, "");
      if (savedNumber) {
        const savedCountry = String(connection?.configuration.businessCountryCode || "");
        const preferred = loadedConfiguration.countries.find(
          (country) => country.region === (savedCountry || readSession()?.tenant.countryCode || ""),
        );
        const matchedCountry = preferred && savedNumber.startsWith(preferred.dialingCode.slice(1))
          ? preferred
          : [...loadedConfiguration.countries]
            .sort((left, right) => right.dialingCode.length - left.dialingCode.length)
            .find((country) => savedNumber.startsWith(country.dialingCode.slice(1)));
        if (matchedCountry) {
          setBusinessCountryCode(matchedCountry.region);
          setBusinessPhoneNumber(savedNumber.slice(matchedCountry.dialingCode.length - 1));
        }
      } else {
        const tenantCountry = readSession()?.tenant.countryCode || "";
        setBusinessCountryCode(loadedConfiguration.countries.find(
          (country) => country.region === tenantCountry,
        )?.region || loadedConfiguration.countries[0]?.region || "");
      }
      if (loadedConfiguration.configured) void loadFacebookSdk(loadedConfiguration).catch(() => undefined);
    }).catch((caught) => {
      setError(caught instanceof Error ? caught.message : "Unable to load WhatsApp configuration.");
    }).finally(() => setLoading(false));
  }, [connection]);

  async function connect() {
    if (!configuration?.configured) return;
    if (!businessCountryCode || !businessPhoneNumber.trim()) {
      setError("Select the business country code and enter the WhatsApp number.");
      return;
    }
    setConnecting(true);
    setError("");
    try {
      const sdk = await loadFacebookSdk(configuration);
      const sessionPromise = waitForMetaSignupSession();
      const codePromise = new Promise<string>((resolve, reject) => {
        sdk.login((response) => {
          const code = response.authResponse?.code;
          if (code) resolve(code);
          else reject(new Error("WhatsApp authorization was cancelled."));
        }, {
          config_id: configuration.configurationId,
          response_type: "code",
          override_default_response_type: true,
          extras: {
            feature: "whatsapp_embedded_signup",
            sessionInfoVersion: "3",
          },
        });
      });
      const [session, code] = await Promise.all([sessionPromise, codePromise]);
      await completeWhatsAppSignup({
        agentId,
        code,
        wabaId: session.wabaId,
        phoneNumberId: session.phoneNumberId,
        businessCountryCode,
        businessPhoneNumber,
      });
      await onSaved();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to connect WhatsApp.");
      setConnecting(false);
    }
  }

  async function saveTemplate() {
    if (!connection || !selection) return;
    const [templateName, templateLanguage] = selection.split("::", 2);
    const selectedTemplate = templates.find(
      (template) => template.name === templateName && template.language === templateLanguage,
    );
    if (!selectedTemplate) {
      setError("The selected Meta template is no longer available.");
      return;
    }
    const missing = selectedTemplate.parameters.find((parameter) => !parameterMappings[parameter.key]);
    if (missing) {
      setError(`Choose a Sauti booking field for ${missing.component} placeholder ${missing.placeholder}.`);
      return;
    }
    setConnecting(true);
    setError("");
    try {
      await updateIntegrationConnection(connection.id, {
        configuration: {
          ...connection.configuration,
          templateName,
          templateLanguage,
          templateParameterFormat: selectedTemplate.parameterFormat,
          templateParameters: selectedTemplate.parameters,
          templateParameterMappings: Object.fromEntries(
            selectedTemplate.parameters.map((parameter) => [parameter.key, parameterMappings[parameter.key]]),
          ),
        },
      });
      await onSaved();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to save the WhatsApp template.");
      setConnecting(false);
    }
  }

  const selectedTemplate = templates.find(
    (template) => `${template.name}::${template.language}` === selection,
  );

  return <div className={styles.backdrop} onMouseDown={onClose}>
    <section className={`${styles.dialog} ${styles.whatsappDialog}`} onMouseDown={(event) => event.stopPropagation()}>
      <header><div><h2>{connection ? "Configure" : "Connect"} WhatsApp</h2>
        <p>Connect securely with Meta. Sauti captures account and phone IDs automatically.</p></div>
        <Button variant="ghost" size="icon-sm" aria-label="Close" onClick={onClose}><X /></Button></header>

      {loading ? <div className={styles.dialogLoading}><LoaderCircle className="spin" size={18} /> Loading Meta configuration…</div> : <>
        {!configuration?.configured && <div className={styles.formError}>
          Meta Embedded Signup is not configured on the server. Add the app ID, app secret, and configuration ID.
        </div>}
        {connection && <div className={styles.connectionSummary}>
          <div><span>Business</span><strong>{String(connection.configuration.verifiedName || connection.displayName)}</strong></div>
          <div><span>Phone number</span><strong>{String(connection.configuration.displayPhoneNumber || "Connected")}</strong></div>
          <span className={styles.secureBadge}><ShieldCheck size={14} /> Connected through Meta</span>
        </div>}
        <div className={styles.businessPhoneSection}>
          <label htmlFor="whatsapp-business-country">Business WhatsApp number</label>
          <div className={styles.businessPhoneEntry}>
            <Select
              id="whatsapp-business-country"
              ariaLabel="Business country calling code"
              value={businessCountryCode}
              onValueChange={setBusinessCountryCode}
              searchPlaceholder="Search country or code…"
              options={(configuration?.countries ?? []).map((country) => ({
                value: country.region,
                label: `${country.name} (${country.dialingCode})`,
                keywords: [country.region, country.dialingCode],
              }))}
            />
            <input aria-label="Business WhatsApp national number" inputMode="tel" required
              value={businessPhoneNumber}
              onChange={(event) => setBusinessPhoneNumber(event.target.value.replace(/[^\d\s()-]/g, ""))}
              placeholder="712 345 678" />
          </div>
          <small>Choose the country prefix, then enter the business number. It must match the number selected in Meta.</small>
        </div>
        <button className={styles.metaConnect}
          disabled={connecting || !configuration?.configured || !businessCountryCode || !businessPhoneNumber.trim()}
          onClick={() => void connect()} type="button">
          {connecting ? <LoaderCircle className="spin" size={17} /> : <span className={styles.metaLogo}>f</span>}
          {connecting ? "Connecting…" : connection ? "Reconnect with Meta" : "Continue with Facebook"}
        </button>
        {connection && <div className={styles.templateSection}>
          <label><span>Outbound message template</span>
            {templates.length ? <Select
              ariaLabel="Outbound message template"
              placeholder="Select an approved template"
              value={selection}
              onValueChange={(value) => {
                setSelection(value);
                setParameterMappings({});
                setError("");
              }}
              options={templates.map((template) => ({
                value: `${template.name}::${template.language}`,
                label: template.name,
                description: `${template.language} · ${template.category}`,
              }))}
            /> : <p>No approved templates found. Incoming WhatsApp conversations can still receive free-form replies.</p>}
          </label>
          {selectedTemplate && selectedTemplate.parameters.length > 0 && <div className={styles.parameterMappings}>
            <div className={styles.mappingIntro}>
              <strong>Populate template placeholders</strong>
              <span>Map every Meta placeholder to trusted data from the booking created in this conversation.</span>
            </div>
            {selectedTemplate.parameters.map((parameter) => <label key={parameter.key}>
              <span>{parameter.component === "header" ? "Header" : "Message"} {parameter.placeholder}</span>
              <Select
                placeholder="Select booking field"
                value={parameterMappings[parameter.key] || ""}
                onValueChange={(value) => setParameterMappings((current) => ({ ...current, [parameter.key]: value }))}
                options={WHATSAPP_TEMPLATE_FIELDS.map((field) => ({ value: field.value, label: field.label }))}
              />
            </label>)}
          </div>}
        </div>}
      </>}
      {error && <div className={styles.formError}>{error}</div>}
      <footer><Button variant="secondary" size="sm" onClick={onClose}>Cancel</Button>
        {connection && templates.length > 0 && <Button variant="primary" size="sm"
          disabled={!selection || connecting} onClick={() => void saveTemplate()} type="button">Save template</Button>}
      </footer>
    </section>
  </div>;
}

const WHATSAPP_TEMPLATE_FIELDS = [
  { value: "customer_name", label: "Customer name" },
  { value: "customer_phone", label: "Customer phone" },
  { value: "customer_email", label: "Customer email" },
  { value: "service", label: "Booked service" },
  { value: "appointment_date", label: "Appointment date" },
  { value: "appointment_time", label: "Appointment time" },
  { value: "appointment_datetime", label: "Appointment date and time" },
  { value: "booking_reference", label: "Booking confirmation reference" },
  { value: "business_name", label: "Business name" },
  { value: "agent_name", label: "Agent name" },
  { value: "duration_minutes", label: "Duration in minutes" },
] as const;

function waitForMetaSignupSession() {
  return new Promise<MetaSignupSession>((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      window.removeEventListener("message", listener);
      reject(new Error("Meta Embedded Signup timed out."));
    }, 120_000);
    const listener = (event: MessageEvent) => {
      let hostname = "";
      try { hostname = new URL(event.origin).hostname; } catch { return; }
      if (hostname !== "facebook.com" && !hostname.endsWith(".facebook.com")) return;
      let payload: unknown = event.data;
      if (typeof payload === "string") {
        try { payload = JSON.parse(payload); } catch { return; }
      }
      const message = payload as { type?: string; event?: string; data?: Record<string, string> };
      if (message.type !== "WA_EMBEDDED_SIGNUP") return;
      if (message.event === "CANCEL" || message.event === "ERROR") {
        window.clearTimeout(timeout);
        window.removeEventListener("message", listener);
        reject(new Error("WhatsApp Embedded Signup was cancelled."));
        return;
      }
      if (message.event !== "FINISH") return;
      const wabaId = message.data?.waba_id ?? "";
      const phoneNumberId = message.data?.phone_number_id ?? "";
      if (!wabaId) return;
      window.clearTimeout(timeout);
      window.removeEventListener("message", listener);
      resolve({ wabaId, phoneNumberId });
    };
    window.addEventListener("message", listener);
  });
}

function ConnectionDialog({ entry, agentId, connection, onClose, onSaved }: {
  entry: IntegrationCatalogEntry; agentId: string; connection?: IntegrationConnection;
  onClose: () => void; onSaved: () => Promise<void>;
}) {
  const fields = [...entry.configurationFields, ...entry.credentialFields];
  const [values, setValues] = useState<Record<string, string>>(() => {
    const initial: Record<string, string> = {
      templateLanguage: "en_US", environment: "sandbox", authType: "none",
    };
    if (entry.provider === "google_sheets") {
      initial.range = "Customers!A:C";
      initial.lookupColumn = "0";
      initial.customerNameColumn = "1";
      initial.customerEmailColumn = "2";
      initial.returnColumns = "0, 1, 2";
      initial.appendRange = "Calls!A:F";
      initial.appendColumns = "callId, startedAt, callerPhone, outcome, summary, sentiment";
    }
    Object.entries(connection?.configuration ?? {}).forEach(([key, value]) => {
      initial[key] = Array.isArray(value) ? value.join(", ") : String(value ?? "");
    });
    return initial;
  });
  const visibleFields = fields.filter((field) => {
    if (entry.provider !== "custom_webhook") return true;
    if (field === "authToken") return values.authType === "bearer";
    if (field === "apiKey") return values.authType === "api_key";
    if (field === "hmacSecret") return values.authType === "hmac";
    return true;
  });
  const [saving, setSaving] = useState(false);
  const [initializing, setInitializing] = useState(false);
  const [initializationMessage, setInitializationMessage] = useState("");
  const [error, setError] = useState("");

  function configurationValues() {
    const configuration: Record<string, unknown> = {};
    entry.configurationFields.forEach((field) => {
      configuration[field] = field === "recipients"
        ? (values[field] ?? "").split(",").map((item) => item.trim()).filter(Boolean)
        : values[field] ?? "";
    });
    return configuration;
  }

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true); setError("");
    const configuration = configurationValues();
    const credentials: Record<string, unknown> = {};
    entry.credentialFields.forEach((field) => { credentials[field] = values[field] ?? ""; });
    try {
      const oauth = oauthProviders.includes(entry.provider);
      if (oauth && connection) {
        await putAgentIntegration(agentId, {
          provider: entry.provider, enabled: true, connectionId: connection.id, configuration,
        });
        if (entry.provider !== "google_sheets") {
          const tested = await testIntegrationConnection(connection.id, agentId);
          if (tested.status === "error") {
            throw new Error(tested.lastError || `${entry.name} could not access the configured resource.`);
          }
        }
      } else if (connection) {
        await updateIntegrationConnection(connection.id, {
          configuration,
          credentials: Object.values(credentials).some(Boolean) ? credentials : undefined,
        });
      } else {
        await createIntegrationConnection({ provider: entry.provider, configuration, credentials });
      }
      await onSaved();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to save connection.");
      setSaving(false);
    }
  }

  async function initializeSheets() {
    if (!connection) {
      setError("Connect your Google account before creating the spreadsheet tabs.");
      return;
    }
    setInitializing(true); setError(""); setInitializationMessage("");
    try {
      await putAgentIntegration(agentId, {
        provider: entry.provider,
        enabled: true,
        connectionId: connection.id,
        configuration: configurationValues(),
      });
      const result = await initializeGoogleSheets(agentId);
      const tested = await testIntegrationConnection(connection.id, agentId);
      if (tested.status === "error") {
        throw new Error(tested.lastError || "Google Sheets could not access the initialized tabs.");
      }
      const created = result.createdTabs.length
        ? `Created ${result.createdTabs.join(" and ")}. `
        : "Both tabs already existed. ";
      const headers = result.initializedHeaders.length
        ? `Added headers to ${result.initializedHeaders.join(" and ")}.`
        : "Existing headers were preserved.";
      setInitializationMessage(created + headers);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to initialize the Google Sheets tabs.");
    } finally {
      setInitializing(false);
    }
  }

  return <div className={styles.backdrop} onMouseDown={onClose}>
    <form className={styles.dialog} onSubmit={(event) => void save(event)} onMouseDown={(event) => event.stopPropagation()}>
      <header><div><h2>{connection ? "Configure" : "Connect"} {entry.name}</h2><p>
        {entry.provider === "google_sheets"
          ? "Use Sheets as an optional lightweight CRM and call log. Sauti remains the source of truth for calls and bookings."
          : "Secrets are encrypted before being stored."}
      </p></div>
        <Button variant="ghost" size="icon-sm" aria-label="Close" onClick={onClose}><X /></Button></header>
      {fields.length === 0 && <p className={styles.oauthNote}>This provider requires OAuth. Use its authorization flow when application credentials are configured.</p>}
      {entry.provider === "google_sheets" && <section className={styles.sheetsGuide}>
        <div><TableProperties size={18} /><div><strong>Customers</strong>
          <p>Automatically adds confirmed booking customers by phone, safely fills missing name or email details, and supports caller-confirmed updates.</p>
          <code>Phone · Name · Email</code>
        </div></div>
        <div><TableProperties size={18} /><div><strong>Calls</strong>
          <p>Receives an automatic post-call record for reporting, follow-up, and lightweight CRM workflows.</p>
          <code>Call ID · Started At · Caller Phone · Outcome · Summary · Sentiment</code>
        </div></div>
        <p className={styles.sheetsSafety}>Choose an empty spreadsheet or one that already has these tabs. Sauti creates only missing tabs, adds headers only to an empty first row, and never replaces existing headers.</p>
      </section>}
      <div className={styles.formFields}>
      {visibleFields.map((field) => <label key={field}><span>{labels[field] ?? field}</span>
        {field === "environment" || field === "authType" ? <Select
          value={values[field] ?? ""}
          onValueChange={(value) => setValues({ ...values, [field]: value })}
          options={(field === "environment"
            ? [["sandbox", "Sandbox"], ["production", "Production"]]
            : [["none", "None"], ["bearer", "Bearer token"], ["api_key", "API key"], ["hmac", "HMAC-SHA256"]]
          ).map(([value, text]) => ({ value, label: text }))}
        /> : <input required={
          (entry.provider === "google_sheets" && ["spreadsheetId", "range"].includes(field))
          || (!connection && entry.credentialFields.includes(field)
            && (entry.provider !== "custom_webhook" || !["authToken", "apiKey", "hmacSecret"].includes(field)))
        }
          type={entry.credentialFields.includes(field) ? "password" : "text"} value={values[field] ?? ""}
          placeholder={placeholderFor(entry.provider, field)}
          onChange={(event) => setValues({ ...values, [field]: event.target.value })} />}
      </label>)}
      </div>
      {entry.provider === "google_sheets" && <div className={styles.sheetsInitializer}>
        <Button variant="soft" size="sm" className="w-fit" onClick={() => void initializeSheets()}
          disabled={initializing || saving || !connection || !(values.spreadsheetId ?? "").trim()}>
          {initializing ? <><LoaderCircle className="spin" /> Creating tabs…</> : <><TableProperties /> Create tabs and headers</>}
        </Button>
        <p>{connection
          ? "This saves the settings above, creates the missing tabs, adds safe default headers, and verifies access."
          : "Connect your Google account first, then enter the spreadsheet ID."}</p>
      </div>}
      {initializationMessage && <div className={styles.formSuccess}><Check size={15} /> {initializationMessage}</div>}
      {error && <div className={styles.formError}>{error}</div>}
      <footer><Button variant="secondary" size="sm" onClick={onClose}>Cancel</Button>
        <Button variant="primary" size="sm" type="submit" disabled={saving || initializing || fields.length === 0}>
          {saving ? "Saving…" : entry.provider === "google_sheets" ? "Save" : "Save connection"}
        </Button></footer>
    </form>
  </div>;
}

function categoryKey(category: string): Filter {
  const normalized = category.toLowerCase();
  if (normalized.includes("calendar")) return "calendar";
  if (normalized.includes("messaging")) return "messaging";
  if (normalized.includes("crm")) return "crm";
  if (normalized.includes("data")) return "data";
  if (normalized.includes("notification")) return "notifications";
  if (normalized.includes("payment")) return "payments";
  if (normalized.includes("developer")) return "developer";
  return "all";
}

function displayCategory(category: string) {
  return category.toLowerCase().includes("calendar") ? "Calendar" : category;
}

function categoryIcon(category: string) {
  const key = categoryKey(category);
  if (key === "calendar") return <CalendarDays size={18} />;
  if (key === "messaging") return <MessageSquare size={18} />;
  if (key === "crm") return <Bot size={18} />;
  if (key === "data") return <Database size={18} />;
  if (key === "payments") return <WalletCards size={18} />;
  return <Plug size={18} />;
}

function placeholderFor(provider: string, field: string) {
  if (provider === "google_sheets" && field === "spreadsheetId") return "1AbCdEf… from the Google Sheets URL";
  if (provider === "google_sheets" && field === "range") return "Customers!A:C";
  if (provider === "google_sheets" && field === "lookupColumn") return "0";
  if (provider === "google_sheets" && field === "customerNameColumn") return "1";
  if (provider === "google_sheets" && field === "customerEmailColumn") return "2";
  if (provider === "google_sheets" && field === "returnColumns") return "0, 1, 2";
  if (provider === "google_sheets" && field === "appendRange") return "Calls!A:F";
  if (provider === "google_sheets" && field === "appendColumns") return "callId, startedAt, callerPhone, outcome, summary, sentiment";
  if (provider === "calendly" && field === "eventTypeUri") return "https://api.calendly.com/event_types/...";
  if (provider === "calendly" && field === "bookingTitle") return "Appointment with {{caller_name}}";
  return "";
}

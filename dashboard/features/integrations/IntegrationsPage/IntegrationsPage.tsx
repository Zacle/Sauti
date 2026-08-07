"use client";

import Image from "next/image";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Bot, CalendarDays, Check, ChevronDown, CircleAlert, Database, LoaderCircle, MessageSquare,
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
import { DarkSelect } from "@/components/DarkSelect/DarkSelect";
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
  const [disconnecting, setDisconnecting] = useState<IntegrationConnection | null>(null);
  const [busy, setBusy] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

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
    if (!loading && agentId && searchParams.get("provider") === "google_calendar") {
      setCalendarEditing(true);
    }
    if (!loading && agentId && searchParams.get("provider") === "google_sheets") {
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
        await startOAuth(entry);
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
    try { await deleteIntegrationConnection(connection.id); await refresh(agentId); return true; }
    catch (caught) { showError(caught); return false; } finally { setBusy(""); }
  }

  return (
    <div className={styles.page}>
      <header className={styles.heading}>
        <div><span><Plug size={15} /> Integrations</span><h1>Integration marketplace</h1>
          <p>Connect an account once, then control what each agent can use.</p></div>
        <label><span>Configure for agent</span>
          <DarkSelect ariaLabel="Configure integrations for agent" icon={<Bot size={16} />} value={agentId || "none"}
            onValueChange={(value) => value !== "none" && setAgentId(value)}
            options={agents.length ? agents.map((agent) => ({ value: agent.id, label: agent.name })) : [{ value: "none", label: "No agents available" }]} />
        </label>
      </header>

      {searchParams.get("calendar") === "connected" && <div className={styles.success}><Check size={17} /> Google Calendar connected.</div>}
      {searchParams.get("oauth") === "connected" && <div className={styles.success}><Check size={17} />
        {searchParams.get("provider") === "google_sheets"
          ? "Google account connected. Configure the spreadsheet to finish setup."
          : "Provider account connected."}
      </div>}
      {searchParams.get("oauth") === "cancelled" && <div className={styles.error}><CircleAlert size={17} /> Provider authorization was cancelled.</div>}
      {searchParams.get("oauth") === "failed" && <div className={styles.error}><CircleAlert size={17} />
        Google authorization could not be completed. Check the OAuth client, callback URL, API enablement, and test-user access.
      </div>}
      {error && <div className={styles.error}><CircleAlert size={17} /> {error}<button onClick={() => setError("")}><X size={14} /></button></div>}

      <section className={styles.controls}>
        <label className={styles.search}>
          <Search size={17} />
          <input
            aria-label="Search integrations"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search providers, categories, or workflows..."
            value={query}
          />
        </label>
        <nav className={styles.filters} aria-label="Integration filters">
          {filterOptions.map(({ value, label }) => (
            <button className={filter === value ? styles.activeFilter : ""} key={value} onClick={() => setFilter(value)}>
              {label}
            </button>
          ))}
        </nav>
      </section>

      {loading ? <div className={styles.loading}><LoaderCircle className="spin" /> Loading integrations…</div> : (
        grouped.length === 0 ? <div className={styles.empty}>No integrations match this filter.</div> : <section className={styles.groups}>
          {grouped.map(([category, entries]) => <section className={styles.group} key={category}>
            <header className={styles.groupHeader}>
              <span>{categoryIcon(category)}</span>
              <div><h2>{category}</h2><p>{entries.length} provider{entries.length === 1 ? "" : "s"}</p></div>
            </header>
            <div className={styles.grid}>
          {entries.map((entry) => {
            const binding = bindings.find((item) => item.provider === entry.provider);
            const connection = connections.find((item) => item.provider === entry.provider);
            const connected = binding?.connectionStatus === "connected" || binding?.connectionStatus === "built_in";
            return <article className={styles.card} key={entry.provider}>
              <div className={styles.cardTop}>
                <span className={styles.logo}><Image alt={`${entry.name} logo`} height={28} src={logos[entry.provider]} width={28} /></span>
                <div><h2>{entry.name}</h2><p>{entry.category}</p></div>
                <span className={`${styles.status} ${connected ? styles.connected : ""}`}>
                  {connected && <Check size={12} />}{connected ? binding?.connectionStatus === "built_in" ? "Built in" : "Connected" : "Not connected"}
                </span>
              </div>
              <p className={styles.description}>{entry.description}</p>
              {!entry.authorizationConfigured && <p className={styles.configurationWarning}>
                OAuth app credentials are not configured on the server.
              </p>}
              <div className={styles.capabilities}>
                {entry.duringCall && <span>During call</span>}{entry.postCall && <span>Post call</span>}
              </div>
              {binding?.lastDelivery && <p className={binding.lastDelivery.status === "delivered" ? styles.lastSuccess : styles.lastError}>
                Last delivery: {binding.lastDelivery.status}{binding.lastDelivery.lastError ? ` — ${binding.lastDelivery.lastError}` : ""}
              </p>}
              <footer>
                {entry.provider === "google_calendar" && !connection
                  ? <span className={styles.connectionRequired}>Connect before enabling</span>
                  : <label className={styles.switch}><input type="checkbox" checked={binding?.enabled ?? false}
                    disabled={busy === entry.provider || !agentId
                      || (!(binding?.enabled ?? false) && !entry.authorizationConfigured)}
                    onChange={(event) => void toggle(entry, event.target.checked)} /><span /> Agent enabled</label>}
                <div className={styles.actions}>
                  {entry.provider === "google_calendar" && !connection && <button
                    className={styles.googleConnect}
                    disabled={!entry.authorizationConfigured || busy === entry.provider || !agentId}
                    onClick={() => void startOAuth(entry)}
                    type="button"
                  >
                    <Image alt="" height={17} src={logos.google_calendar} width={17} />
                    Connect Google Calendar
                  </button>}
                  {entry.provider === "google_calendar" && connection && <button
                    onClick={() => setCalendarEditing(true)} title="Configure calendar"><Settings2 size={15} /></button>}
                  {entry.requiresConnection && entry.provider !== "google_calendar" && <button
                    disabled={!entry.authorizationConfigured}
                    onClick={() => {
                    if (entry.provider === "whatsapp") {
                      setWhatsappEditing(true);
                    } else if (oauthProviders.includes(entry.provider) && !connection) {
                      void startOAuth(entry);
                    } else {
                      setEditing(entry);
                    }
                  }} title={entry.authorizationConfigured ? "Configure" : "OAuth app not configured"}><Settings2 size={15} /></button>}
                  {connection && <button onClick={() => void testConnection(connection)} title="Test"><TestTube2 size={15} /></button>}
                  {connection && <button onClick={() => setDisconnecting(connection)} title="Disconnect"><Trash2 size={15} /></button>}
                </div>
              </footer>
            </article>;
          })}
            </div>
          </section>)}
        </section>
      )}
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
      {disconnecting && <DisconnectDialog
        connection={disconnecting}
        busy={busy === disconnecting.provider}
        onClose={() => setDisconnecting(null)}
        onConfirm={async () => { if (await disconnect(disconnecting)) setDisconnecting(null); }}
      />}
    </div>
  );
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
        <button type="button" onClick={onClose}><X size={18} /></button></header>
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
      <footer><button type="button" disabled={busy || !status?.connected} onClick={() => void test()}>
        {testState === "testing" && <LoaderCircle className="spin" size={15} />}
        {testState === "testing" ? "Testing..." : "Test live connection"}
      </button>
        <button className={styles.primary} disabled={busy || !status?.connected}>{busy ? "Saving…" : "Save calendar"}</button></footer>
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
        <button aria-label="Close disconnect dialog" type="button" onClick={onClose}><X size={18} /></button>
      </header>
      <div className={styles.disconnectCopy}>
        <span className={styles.dialogEyebrow}>Connection settings</span>
        <h2 id="disconnect-title">Disconnect {connection.displayName}?</h2>
    <p id="disconnect-description">This removes the workspace connection and turns off {impact.resource} for agents using it.</p>
    <div className={styles.impactList}>
     <div><Check size={15} /><span>{impact.intact}</span></div>
     <div><CircleAlert size={15} /><span>{impact.stops}</span></div>
        </div>
      </div>
      <footer>
        <button type="button" onClick={onClose}>Keep connected</button>
        <button className={styles.danger} disabled={busy} type="button" onClick={() => void onConfirm()}>
          {busy && <LoaderCircle className="spin" size={15} />}
          {busy ? "Disconnecting..." : "Disconnect"}
        </button>
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
        <button type="button" onClick={onClose}><X size={18} /></button></header>

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
            <div className={styles.selectControl}>
              <select id="whatsapp-business-country" aria-label="Business country calling code"
                value={businessCountryCode}
                onChange={(event) => setBusinessCountryCode(event.target.value)}>
                {configuration?.countries.map((country) => <option key={country.region} value={country.region}>
                  {country.name} ({country.dialingCode})
                </option>)}
              </select>
              <ChevronDown aria-hidden="true" size={17} />
            </div>
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
            {templates.length ? <div className={styles.selectControl}>
              <select value={selection} onChange={(event) => {
                setSelection(event.target.value);
                setParameterMappings({});
                setError("");
              }}>
                <option value="">Select an approved template</option>
                {templates.map((template) => <option key={`${template.id}-${template.language}`}
                  value={`${template.name}::${template.language}`}>
                  {template.name} · {template.language} · {template.category}
                </option>)}
              </select><ChevronDown aria-hidden="true" size={17} />
            </div> : <p>No approved templates found. Incoming WhatsApp conversations can still receive free-form replies.</p>}
          </label>
          {selectedTemplate && selectedTemplate.parameters.length > 0 && <div className={styles.parameterMappings}>
            <div className={styles.mappingIntro}>
              <strong>Populate template placeholders</strong>
              <span>Map every Meta placeholder to trusted data from the booking created in this conversation.</span>
            </div>
            {selectedTemplate.parameters.map((parameter) => <label key={parameter.key}>
              <span>{parameter.component === "header" ? "Header" : "Message"} {parameter.placeholder}</span>
              <div className={styles.selectControl}>
                <select value={parameterMappings[parameter.key] || ""}
                  onChange={(event) => setParameterMappings((current) => ({
                    ...current,
                    [parameter.key]: event.target.value,
                  }))}>
                  <option value="">Select booking field</option>
                  {WHATSAPP_TEMPLATE_FIELDS.map((field) => <option key={field.value} value={field.value}>
                    {field.label}
                  </option>)}
                </select>
                <ChevronDown aria-hidden="true" size={17} />
              </div>
            </label>)}
          </div>}
        </div>}
      </>}
      {error && <div className={styles.formError}>{error}</div>}
      <footer><button type="button" onClick={onClose}>Cancel</button>
        {connection && templates.length > 0 && <button className={styles.primary}
          disabled={!selection || connecting} onClick={() => void saveTemplate()} type="button">Save template</button>}
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
        <button type="button" onClick={onClose}><X size={18} /></button></header>
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
        {field === "environment" || field === "authType" ? <div className={styles.selectControl}>
          <select value={values[field] ?? ""} onChange={(event) => setValues({ ...values, [field]: event.target.value })}>
            {(field === "environment"
              ? [["sandbox", "Sandbox"], ["production", "Production"]]
              : [["none", "None"], ["bearer", "Bearer token"], ["api_key", "API key"], ["hmac", "HMAC-SHA256"]]
            ).map(([value, text]) => <option key={value} value={value}>{text}</option>)}
          </select>
          <ChevronDown aria-hidden="true" size={17} />
        </div> : <input required={
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
        <button type="button" className={styles.initializeSheets} onClick={() => void initializeSheets()}
          disabled={initializing || saving || !connection || !(values.spreadsheetId ?? "").trim()}>
          {initializing ? <><LoaderCircle className="spin" size={16} /> Creating tabs…</> : <><TableProperties size={16} /> Create tabs and headers</>}
        </button>
        <p>{connection
          ? "This saves the settings above, creates the missing tabs, adds safe default headers, and verifies access."
          : "Connect your Google account first, then enter the spreadsheet ID."}</p>
      </div>}
      {initializationMessage && <div className={styles.formSuccess}><Check size={15} /> {initializationMessage}</div>}
      {error && <div className={styles.formError}>{error}</div>}
      <footer><button type="button" onClick={onClose}>Cancel</button>
        <button className={styles.primary} disabled={saving || initializing || fields.length === 0}>
          {saving ? "Saving…" : entry.provider === "google_sheets" ? "Save" : "Save connection"}
        </button></footer>
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

"use client";

import Image from "next/image";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Bot, CalendarDays, Check, ChevronDown, CircleAlert, Database, LoaderCircle, MessageSquare,
  Plug, Search, Settings2, ShieldCheck, TestTube2, Trash2, WalletCards, X,
} from "lucide-react";
import { listAgents } from "@/lib/api/agents";
import {
  authorizeGoogleCalendar,
  authorizeProvider,
  completeWhatsAppSignup,
  createIntegrationConnection,
  deleteIntegrationConnection,
  getAgentIntegrations,
  getIntegrationCatalog,
  getIntegrationConnections,
  getWhatsAppSignupConfiguration,
  getWhatsAppTemplates,
  putAgentIntegration,
  testIntegrationConnection,
  updateIntegrationConnection,
  type AgentIntegration,
  type IntegrationCatalogEntry,
  type IntegrationConnection,
  type WhatsAppSignupConfiguration,
  type WhatsAppTemplate,
} from "@/lib/api/integrations";
import type { Agent } from "@/types/api";
import styles from "./IntegrationsPage.module.css";

type Filter = "all" | "calendar" | "messaging" | "crm" | "data" | "notifications" | "payments" | "developer" | "during" | "post" | "connected";
const oauthProviders = ["google_sheets", "hubspot", "salesforce", "calendly"];

const logos: Record<string, string> = {
  google_calendar: "/logos/google-calendar.svg",
  slack: "/logos/slack.svg",
  hubspot: "/logos/hubspot.svg",
  salesforce: "/logos/salesforce.svg",
};

const labels: Record<string, string> = {
  webhookUrl: "Webhook URL", authType: "Authentication type", authToken: "Bearer token",
  apiKey: "API key", hmacSecret: "HMAC secret", wabaId: "WABA ID",
  phoneNumberId: "Phone-number ID", templateName: "Approved template name",
  templateLanguage: "Template language", accessToken: "Long-lived system-user token",
  recipients: "Recipients (comma-separated)", spreadsheetId: "Spreadsheet ID",
  range: "Sheet / range", lookupColumn: "Lookup column", returnColumns: "Return columns",
  appendColumns: "Append columns", shortcode: "Shortcode", environment: "Environment",
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
    try { await testIntegrationConnection(connection.id); await refresh(agentId); }
    catch (caught) { showError(caught); } finally { setBusy(""); }
  }

  async function disconnect(connection: IntegrationConnection) {
    if (!window.confirm(`Disconnect ${connection.displayName}? Agents using it will be disabled.`)) return;
    setBusy(connection.provider);
    try { await deleteIntegrationConnection(connection.id); await refresh(agentId); }
    catch (caught) { showError(caught); } finally { setBusy(""); }
  }

  return (
    <div className={styles.page}>
      <header className={styles.heading}>
        <div><span><Plug size={15} /> Integrations</span><h1>Integration marketplace</h1>
          <p>Connect an account once, then control what each agent can use.</p></div>
        <label><span>Configure for agent</span><div><Bot size={16} />
          <select value={agentId} onChange={(event) => setAgentId(event.target.value)} disabled={!agents.length}>
            {!agents.length && <option value="">No agents available</option>}
            {agents.map((agent) => <option key={agent.id} value={agent.id}>{agent.name}</option>)}
          </select><ChevronDown size={15} /></div></label>
      </header>

      {searchParams.get("calendar") === "connected" && <div className={styles.success}><Check size={17} /> Google Calendar connected.</div>}
      {searchParams.get("oauth") === "connected" && <div className={styles.success}><Check size={17} /> Provider account connected.</div>}
      {searchParams.get("oauth") === "cancelled" && <div className={styles.error}><CircleAlert size={17} /> Provider authorization was cancelled.</div>}
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
                <span className={styles.logo}>{logos[entry.provider]
                  ? <Image alt="" height={28} src={logos[entry.provider]} width={28} />
                  : <Plug size={21} />}</span>
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
                <label className={styles.switch}><input type="checkbox" checked={binding?.enabled ?? false}
                  disabled={busy === entry.provider || !agentId
                    || (!(binding?.enabled ?? false) && !entry.authorizationConfigured)}
                  onChange={(event) => void toggle(entry, event.target.checked)} /><span /> Agent enabled</label>
                <div className={styles.actions}>
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
                  {connection && <button onClick={() => void disconnect(connection)} title="Disconnect"><Trash2 size={15} /></button>}
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
    </div>
  );
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
  const [connecting, setConnecting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([
      getWhatsAppSignupConfiguration(),
      connection ? getWhatsAppTemplates(connection.id) : Promise.resolve([]),
    ]).then(([loadedConfiguration, loadedTemplates]) => {
      setConfiguration(loadedConfiguration);
      setTemplates(loadedTemplates);
      if (loadedConfiguration.configured) void loadFacebookSdk(loadedConfiguration).catch(() => undefined);
    }).catch((caught) => {
      setError(caught instanceof Error ? caught.message : "Unable to load WhatsApp configuration.");
    }).finally(() => setLoading(false));
  }, [connection]);

  async function connect() {
    if (!configuration?.configured) return;
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
    setConnecting(true);
    try {
      await updateIntegrationConnection(connection.id, {
        configuration: {
          ...connection.configuration,
          templateName,
          templateLanguage,
        },
      });
      await onSaved();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to save the WhatsApp template.");
      setConnecting(false);
    }
  }

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
        <button className={styles.metaConnect} disabled={connecting || !configuration?.configured}
          onClick={() => void connect()} type="button">
          {connecting ? <LoaderCircle className="spin" size={17} /> : <span className={styles.metaLogo}>f</span>}
          {connecting ? "Connecting…" : connection ? "Reconnect with Meta" : "Continue with Facebook"}
        </button>
        {connection && <div className={styles.templateSection}>
          <label><span>Outbound message template</span>
            {templates.length ? <div className={styles.selectControl}>
              <select value={selection} onChange={(event) => setSelection(event.target.value)}>
                <option value="">Select an approved template</option>
                {templates.map((template) => <option key={`${template.id}-${template.language}`}
                  value={`${template.name}::${template.language}`}>
                  {template.name} · {template.language} · {template.category}
                </option>)}
              </select><ChevronDown aria-hidden="true" size={17} />
            </div> : <p>No approved templates found. Incoming WhatsApp conversations can still receive free-form replies.</p>}
          </label>
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
  const [error, setError] = useState("");

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true); setError("");
    const configuration: Record<string, unknown> = {};
    const credentials: Record<string, unknown> = {};
    entry.configurationFields.forEach((field) => {
      configuration[field] = field === "recipients"
        ? (values[field] ?? "").split(",").map((item) => item.trim()).filter(Boolean)
        : values[field] ?? "";
    });
    entry.credentialFields.forEach((field) => { credentials[field] = values[field] ?? ""; });
    try {
      const oauth = oauthProviders.includes(entry.provider);
      if (oauth && connection) {
        await putAgentIntegration(agentId, {
          provider: entry.provider, enabled: true, connectionId: connection.id, configuration,
        });
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

  return <div className={styles.backdrop} onMouseDown={onClose}>
    <form className={styles.dialog} onSubmit={(event) => void save(event)} onMouseDown={(event) => event.stopPropagation()}>
      <header><div><h2>{connection ? "Configure" : "Connect"} {entry.name}</h2><p>Secrets are encrypted before being stored.</p></div>
        <button type="button" onClick={onClose}><X size={18} /></button></header>
      {fields.length === 0 && <p className={styles.oauthNote}>This provider requires OAuth. Use its authorization flow when application credentials are configured.</p>}
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
      {error && <div className={styles.formError}>{error}</div>}
      <footer><button type="button" onClick={onClose}>Cancel</button>
        <button className={styles.primary} disabled={saving || fields.length === 0}>{saving ? "Saving…" : "Save connection"}</button></footer>
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
  if (provider === "calendly" && field === "eventTypeUri") return "https://api.calendly.com/event_types/...";
  if (provider === "calendly" && field === "bookingTitle") return "Appointment with {{caller_name}}";
  return "";
}

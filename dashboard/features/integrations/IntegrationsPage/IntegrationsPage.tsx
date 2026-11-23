"use client";

import * as DialogPrimitive from "@radix-ui/react-dialog";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Bot, CalendarDays, Check, CircleAlert, Database, LoaderCircle, MessageSquare,
  Plug, Plus, Search, Settings2, ShieldCheck, TableProperties, TestTube2, Unplug, WalletCards, X,
} from "lucide-react";
import { toast } from "sonner";
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
import { Alert, Input } from "@/components/ui/field";
import { Badge, Card, EmptyState, Skeleton, Switch, Tabs, TabsCount, TabsList, TabsTrigger, Tooltip } from "@/components/ui/primitives";
import { PageHeader } from "@/components/ui/console";
import { Select } from "@/components/ui/select";
import { IntegrationLogo } from "@/features/integrations/presentation/IntegrationLogo";
import { cn } from "@/lib/utils";
import { readSession } from "@/lib/session";

type Filter = "all" | "connected" | "calendar" | "messaging" | "crm" | "data" | "notifications" | "payments" | "developer";
type Timing = "any" | "during" | "post";
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

const FILTERS: Array<{ value: Filter; label: string }> = [
  { value: "all", label: "All" },
  { value: "connected", label: "Connected" },
  { value: "calendar", label: "Calendar" },
  { value: "messaging", label: "Messaging" },
  { value: "crm", label: "CRM" },
  { value: "data", label: "Data" },
  { value: "notifications", label: "Notifications" },
  { value: "payments", label: "Payments" },
  { value: "developer", label: "Developer" },
];

type Row = {
  entry: IntegrationCatalogEntry;
  binding?: AgentIntegration;
  connection?: IntegrationConnection;
  /** An account is connected (or the tool is built in and needs no account). */
  ready: boolean;
  builtIn: boolean;
  /** Turned on for the selected agent. */
  on: boolean;
};

export function IntegrationsPage() {
  const searchParams = useSearchParams();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [agentId, setAgentId] = useState(searchParams.get("agentId") ?? "");
  const [catalog, setCatalog] = useState<IntegrationCatalogEntry[]>([]);
  const [connections, setConnections] = useState<IntegrationConnection[]>([]);
  const [bindings, setBindings] = useState<AgentIntegration[]>([]);
  const [filter, setFilter] = useState<Filter>("all");
  const [timing, setTiming] = useState<Timing>("any");
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

  const rows = useMemo<Row[]>(() => catalog.map((entry) => {
    const binding = bindings.find((item) => item.provider === entry.provider);
    const connection = connections.find((item) => item.provider === entry.provider);
    const builtIn = binding?.connectionStatus === "built_in" || !entry.requiresConnection;
    const ready = builtIn || binding?.connectionStatus === "connected" || Boolean(connection);
    return { entry, binding, connection, ready, builtIn, on: Boolean(binding?.enabled) && ready };
  }), [bindings, catalog, connections]);

  const matches = useCallback((row: Row, byFilter: Filter) => {
    const normalized = query.trim().toLowerCase();
    if (normalized && ![row.entry.name, row.entry.category, row.entry.description, row.entry.provider]
      .some((value) => value.toLowerCase().includes(normalized))) return false;
    if (timing === "during" && !row.entry.duringCall) return false;
    if (timing === "post" && !row.entry.postCall) return false;
    if (byFilter === "connected") return row.ready && !row.builtIn;
    if (byFilter !== "all") return categoryKey(row.entry.category) === byFilter;
    return true;
  }, [query, timing]);

  const counts = useMemo(() => Object.fromEntries(
    FILTERS.map(({ value }) => [value, rows.filter((row) => matches(row, value)).length]),
  ) as Record<Filter, number>, [matches, rows]);

  const visible = rows.filter((row) => matches(row, filter));
  const readyRows = visible.filter((row) => row.ready).sort((a, b) => Number(b.on) - Number(a.on) || a.entry.name.localeCompare(b.entry.name));
  const availableRows = visible.filter((row) => !row.ready).sort((a, b) => a.entry.name.localeCompare(b.entry.name));
  const onCount = rows.filter((row) => row.on).length;
  const connectedCount = rows.filter((row) => row.ready && !row.builtIn).length;
  const selectedAgent = agents.find((agent) => agent.id === agentId);

  async function toggle(entry: IntegrationCatalogEntry, enabled: boolean) {
    if (!agentId) return;
    const connection = connections.find((item) => item.provider === entry.provider);
    if (enabled && entry.requiresConnection && !connection) {
      configure(entry, connection);
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
      toast.success(`${entry.name} ${enabled ? "on" : "off"} for ${selectedAgent?.name ?? "this agent"}`);
    } catch (caught) { showError(caught); } finally { setBusy(""); }
  }

  function configure(entry: IntegrationCatalogEntry, connection?: IntegrationConnection) {
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
      toast.success(`${connection.displayName} is working`);
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
      } else {
        toast.success(`${connection.displayName} disconnected`);
      }
      return true;
    }
    catch (caught) { showError(caught); return false; } finally { setBusy(""); }
  }

  const renderCard = (row: Row) => (
    <IntegrationCard
      key={row.entry.provider}
      row={row}
      agentName={selectedAgent?.name}
      hasAgent={Boolean(agentId)}
      working={busy === row.entry.provider}
      onToggle={(enabled) => void toggle(row.entry, enabled)}
      onConnect={() => configure(row.entry, row.connection)}
      onConfigure={() => configure(row.entry, row.connection)}
      onTest={row.connection ? () => void testConnection(row.connection!) : undefined}
      onDisconnect={row.connection ? () => setDisconnecting(row.connection!) : undefined}
    />
  );

  return (
    <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-6">
      <PageHeader
        title="Integrations"
        description="Connect an account once for your workspace, then choose which agents can use it."
        actions={(
          <label className="relative w-full sm:w-64">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-fg-muted" />
            <Input aria-label="Search integrations" className="h-9 pl-9 text-[0.85rem]" onChange={(event) => setQuery(event.target.value)} placeholder="Search tools" value={query} />
          </label>
        )}
      />

      {searchParams.get("calendar") === "connected" ? <Notice tone="success">Google Calendar connected.</Notice> : null}
      {searchParams.get("oauth") === "connected" ? (
        <Notice tone="success">{searchParams.get("provider") === "google_sheets" ? "Google account connected. Choose the spreadsheet to finish setup." : "Account connected."}</Notice>
      ) : null}
      {searchParams.get("oauth") === "cancelled" ? <Notice tone="error">Authorization was cancelled.</Notice> : null}
      {searchParams.get("oauth") === "failed" ? <Notice tone="error">Google authorization couldn&apos;t finish. Check the OAuth client, callback URL, enabled APIs and test-user access.</Notice> : null}
      {error ? <Notice tone="error" onDismiss={() => setError("")}>{error}</Notice> : null}

      <Card className="flex flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3.5 sm:px-5">
        <div className="flex min-w-0 flex-1 items-center gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-[var(--radius-control)] bg-signal/10 text-signal"><Bot className="size-[18px]" /></span>
          <div className="min-w-0">
            <p className="m-0 text-[0.86rem] font-medium text-ink">Choosing tools for</p>
            <p className="m-0 text-[0.76rem] leading-snug text-fg-muted">Switches apply to this agent only. Connected accounts are shared by every agent.</p>
          </div>
        </div>
        <div className="w-full sm:w-56">
          <Select
            size="sm"
            ariaLabel="Agent"
            icon={<Bot />}
            value={agentId || "none"}
            onValueChange={(value) => value !== "none" && setAgentId(value)}
            options={agents.length ? agents.map((agent) => ({ value: agent.id, label: agent.name })) : [{ value: "none", label: "No agents yet" }]}
          />
        </div>
        <div className="flex gap-5 text-[0.78rem] text-fg-muted max-sm:w-full">
          <span><strong className="font-semibold tabular-nums text-ink">{onCount}</strong> on for this agent</span>
          <span><strong className="font-semibold tabular-nums text-ink">{connectedCount}</strong> accounts connected</span>
        </div>
      </Card>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <Tabs value={filter} onValueChange={(value) => setFilter(value as Filter)} className="max-w-full overflow-x-auto [scrollbar-width:none]">
          <TabsList aria-label="Category">
            {FILTERS.map(({ value, label }) => (
              <TabsTrigger key={value} value={value}>
                {label}
                {value !== "all" ? <TabsCount>{counts[value] ?? 0}</TabsCount> : null}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        <div className="w-40 max-sm:w-full">
          <Select
            size="sm"
            ariaLabel="When it runs"
            value={timing}
            onValueChange={(value) => setTiming(value as Timing)}
            options={[
              { value: "any", label: "Any time" },
              { value: "during", label: "During calls" },
              { value: "post", label: "After calls" },
            ]}
          />
        </div>
      </div>

      {loading ? (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{[1, 2, 3, 4, 5, 6].map((item) => <Skeleton key={item} className="h-52" />)}</div>
      ) : visible.length === 0 ? (
        <EmptyState
          icon={<Plug />}
          title="Nothing matches"
          description="Try another search, category or timing."
          action={<Button variant="secondary" size="xs" onClick={() => { setQuery(""); setFilter("all"); setTiming("any"); }}>Clear filters</Button>}
        />
      ) : (
        <div className="grid gap-8">
          {readyRows.length ? (
            <section className="grid gap-3">
              <SectionLabel title="Ready to use" detail="Connected accounts and built-in tools. Turn each one on or off for the selected agent." count={readyRows.length} />
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{readyRows.map(renderCard)}</div>
            </section>
          ) : null}
          {availableRows.length ? (
            <section className="grid gap-3">
              <SectionLabel title={readyRows.length ? "Available" : "Integrations"} detail="Connect an account to make it available to your agents." count={availableRows.length} />
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{availableRows.map(renderCard)}</div>
            </section>
          ) : null}
        </div>
      )}

      {editing && <ConnectionDialog agentId={agentId} connection={connections.find((item) => item.provider === editing.provider)}
        entry={editing} onClose={() => setEditing(null)} onSaved={async () => {
        setEditing(null); await refresh(agentId); toast.success(`${editing.name} saved`);
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
        onSaved={async () => { setCalendarEditing(false); await refresh(agentId); toast.success("Calendar saved"); }} />}
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

function SectionLabel({ title, detail, count }: { title: string; detail: string; count: number }) {
  return (
    <div className="flex items-end justify-between gap-4">
      <div>
        <h2 className="m-0 text-[0.95rem] font-semibold tracking-[-0.01em] text-ink">{title}</h2>
        <p className="m-0 mt-0.5 text-[0.8rem] text-fg-muted">{detail}</p>
      </div>
      <span className="text-[0.76rem] tabular-nums text-fg-muted">{count}</span>
    </div>
  );
}

function IntegrationCard({
  row,
  agentName,
  hasAgent,
  working,
  onToggle,
  onConnect,
  onConfigure,
  onTest,
  onDisconnect,
}: {
  row: Row;
  agentName?: string;
  hasAgent: boolean;
  working: boolean;
  onToggle: (enabled: boolean) => void;
  onConnect: () => void;
  onConfigure: () => void;
  onTest?: () => void;
  onDisconnect?: () => void;
}) {
  const { entry, binding, connection, ready, builtIn, on } = row;
  const delivery = binding?.lastDelivery;
  const deliverySkipped = delivery?.status === "skipped";
  const deliveryFailed = delivery && delivery.status !== "delivered" && !deliverySkipped;
  const timing = [entry.duringCall ? "During calls" : null, entry.postCall ? "After calls" : null].filter(Boolean).join(" · ");
  const switchId = `use-${entry.provider}`;

  return (
    <article
      className={cn(
        "flex min-w-0 flex-col rounded-[var(--radius-card)] border bg-surface shadow-[var(--shadow-card)] transition-colors",
        on ? "border-signal/30" : "border-line",
      )}
    >
      <div className="flex flex-1 flex-col p-4 sm:p-5">
        <div className="flex items-start gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-[var(--radius-control)] border border-line bg-surface">
            <IntegrationLogo provider={entry.provider} fallback={<span className="text-fg-soft [&_svg]:size-[18px]">{categoryIcon(entry.category)}</span>} />
          </span>
          <div className="min-w-0 flex-1">
            <h3 className="m-0 truncate text-[0.95rem] font-semibold tracking-[-0.01em] text-ink">{entry.name}</h3>
            <p className="m-0 mt-0.5 truncate text-[0.74rem] text-fg-muted">{displayCategory(entry.category)}{timing ? ` · ${timing}` : ""}</p>
          </div>
          {builtIn ? (
            <Badge tone="neutral">Built in</Badge>
          ) : ready ? (
            <Badge tone="success" dot>Connected</Badge>
          ) : !entry.authorizationConfigured ? (
            <Tooltip content="The OAuth app isn't configured on the server yet.">
              <span><Badge tone="warning">Unavailable</Badge></span>
            </Tooltip>
          ) : null}
        </div>

        <p className="m-0 mt-3 line-clamp-3 text-[0.82rem] leading-relaxed text-fg-soft">{entry.description}</p>

        {connection?.externalAccountId || delivery ? (
          <div className="mt-3 grid gap-1 text-[0.74rem] text-fg-muted">
            {connection?.externalAccountId ? <p className="m-0 truncate">Connected as <span className="text-fg-soft">{connection.externalAccountId}</span></p> : null}
            {delivery ? (
              <p className={cn("m-0 flex min-w-0 items-center gap-1.5", deliveryFailed && "text-danger")}>
                <span aria-hidden="true" className={cn("size-1.5 shrink-0 rounded-full", deliveryFailed ? "bg-danger" : deliverySkipped ? "bg-warning" : "bg-success")} />
                <span className="truncate">
                  {deliverySkipped ? `Last run skipped${delivery.lastError ? `: ${delivery.lastError}` : ""}` : deliveryFailed ? `Last run failed${delivery.lastError ? `: ${delivery.lastError}` : ""}` : `Last ran ${formatRelative(delivery.deliveredAt ?? delivery.createdAt)}`}
                </span>
              </p>
            ) : null}
          </div>
        ) : null}
      </div>

      <div className="flex min-h-[3.25rem] items-center gap-2 border-t border-line px-4 py-2.5 sm:px-5">
        {ready ? (
          <>
            <label htmlFor={switchId} title={hasAgent ? undefined : "Create an agent first"} className={cn("flex min-w-0 flex-1 items-center gap-2.5", hasAgent ? "cursor-pointer" : "cursor-not-allowed")}>
                <Switch
                  id={switchId}
                  checked={on}
                  disabled={working || !hasAgent}
                  onCheckedChange={onToggle}
                />
                <span className="min-w-0 truncate text-[0.8rem] text-fg-soft">
                  {working ? "Saving…" : on ? <>On for <span className="font-medium text-ink">{agentName ?? "this agent"}</span></> : `Off for ${agentName ?? "this agent"}`}
                </span>
              </label>
            {!builtIn && entry.requiresConnection ? <IconAction label="Settings" onClick={onConfigure} disabled={!entry.authorizationConfigured}><Settings2 /></IconAction> : null}
            {onTest ? <IconAction label="Test connection" disabled={working} onClick={onTest}><TestTube2 /></IconAction> : null}
            {onDisconnect ? <IconAction label="Disconnect" danger onClick={onDisconnect}><Unplug /></IconAction> : null}
          </>
        ) : (
          <>
            <span className="min-w-0 flex-1 truncate text-[0.78rem] text-fg-muted">
              {oauthProviders.includes(entry.provider) || entry.provider.startsWith("google_") ? "Sign in to connect" : entry.provider === "whatsapp" ? "Connect through Meta" : "Add your details"}
            </span>
            <Button variant="secondary" size="xs" disabled={!entry.authorizationConfigured || working || !hasAgent} onClick={onConnect}>
              {working ? <LoaderCircle className="spin" /> : <Plus />} Connect
            </Button>
          </>
        )}
      </div>
    </article>
  );
}

function formatRelative(value?: string | null) {
  if (!value) return "recently";
  const minutes = Math.round((Date.now() - new Date(value).getTime()) / 60_000);
  if (!Number.isFinite(minutes)) return "recently";
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return new Date(value).toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

function Notice({ tone, children, onDismiss }: { tone: "success" | "error"; children: React.ReactNode; onDismiss?: () => void }) {
  return (
    <div role={tone === "error" ? "alert" : "status"} className={cn(
      "flex items-start gap-2.5 rounded-[var(--radius-control)] border px-3.5 py-2.5 text-[0.85rem]",
      tone === "success" ? "border-success/30 bg-success/[0.06] text-fg" : "border-danger/30 bg-danger/[0.06] text-fg",
    )}>
      {tone === "success" ? <Check className="mt-0.5 size-4 shrink-0 text-success" /> : <CircleAlert className="mt-0.5 size-4 shrink-0 text-danger" />}
      <span className="flex-1">{children}</span>
      {onDismiss ? <Button variant="ghost" size="icon-xs" aria-label="Dismiss" className="-my-1" onClick={onDismiss}><X /></Button> : null}
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

/* ---------- Dialogs ---------- */

function DialogShell({
  title,
  description,
  provider,
  onClose,
  busy = false,
  footer,
  children,
  as = "section",
  onSubmit,
  width = "34rem",
}: {
  title: string;
  description?: React.ReactNode;
  provider?: string;
  onClose: () => void;
  busy?: boolean;
  footer: React.ReactNode;
  children: React.ReactNode;
  as?: "section" | "form";
  onSubmit?: (event: React.FormEvent) => void;
  width?: string;
}) {
  const Body = as;
  return (
    <DialogPrimitive.Root open onOpenChange={(open) => { if (!open && !busy) onClose(); }}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-[150] bg-scrim data-[state=open]:animate-[fade-in_180ms_ease-out]" />
        <DialogPrimitive.Content
          aria-describedby={undefined}
          onInteractOutside={(event) => { if (busy) event.preventDefault(); }}
          style={{ width: `min(94vw, ${width})` }}
          className="fixed left-1/2 top-1/2 z-[151] flex max-h-[min(90dvh,780px)] -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded-[var(--radius-card)] border border-line-strong bg-popover font-sans text-fg shadow-[var(--shadow-pop)] outline-none data-[state=open]:animate-[dialog-in_220ms_cubic-bezier(0.16,1,0.3,1)] max-sm:inset-x-0 max-sm:bottom-0 max-sm:top-auto max-sm:max-h-[92dvh] max-sm:!w-full max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-b-none"
        >
          <Body className="flex min-h-0 flex-col" onSubmit={onSubmit}>
            <header className="flex items-start gap-3 px-6 pb-4 pt-5">
              {provider ? (
                <span className="grid size-10 shrink-0 place-items-center rounded-[var(--radius-control)] border border-line bg-surface">
                  <IntegrationLogo provider={provider} fallback={<Plug className="size-[18px] text-fg-muted" />} />
                </span>
              ) : null}
              <div className="min-w-0 flex-1">
                <DialogPrimitive.Title className="m-0 text-[1.05rem] font-semibold tracking-[-0.01em] text-ink">{title}</DialogPrimitive.Title>
                {description ? <div className="m-0 mt-1 text-[0.84rem] leading-relaxed text-fg-soft">{description}</div> : null}
              </div>
              <DialogPrimitive.Close asChild>
                <Button variant="ghost" size="icon-xs" aria-label="Close" disabled={busy} className="-mr-1.5"><X /></Button>
              </DialogPrimitive.Close>
            </header>
            <div className="grid min-h-0 grid-cols-[minmax(0,1fr)] gap-4 overflow-y-auto px-6 pb-5">{children}</div>
            <footer className="flex flex-wrap items-center justify-end gap-2 border-t border-line px-6 py-3.5">{footer}</footer>
          </Body>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

function DialogField({ label, hint, htmlFor, children }: { label: string; hint?: React.ReactNode; htmlFor?: string; children: React.ReactNode }) {
  return (
    <div className="grid min-w-0 gap-1.5">
      <label htmlFor={htmlFor} className="text-[0.84rem] font-medium text-ink">{label}</label>
      {children}
      {hint ? <p className="m-0 text-[0.76rem] leading-relaxed text-fg-muted">{hint}</p> : null}
    </div>
  );
}

function InfoBlock({ icon, title, children, tone = "neutral" }: { icon: React.ReactNode; title?: string; children: React.ReactNode; tone?: "neutral" | "success" | "danger" }) {
  return (
    <div className={cn(
      "flex items-start gap-3 rounded-[var(--radius-control)] border p-3.5",
      tone === "neutral" && "border-line bg-tint/[0.025]",
      tone === "success" && "border-success/30 bg-success/[0.06]",
      tone === "danger" && "border-danger/30 bg-danger/[0.06]",
    )}>
      <span className={cn("mt-px shrink-0 [&_svg]:size-4", tone === "success" ? "text-success" : tone === "danger" ? "text-danger" : "text-fg-muted")}>{icon}</span>
      <div className="min-w-0 text-[0.8rem] leading-relaxed text-fg-soft">
        {title ? <p className="m-0 mb-0.5 text-[0.84rem] font-medium text-ink">{title}</p> : null}
        {children}
      </div>
    </div>
  );
}

function GoogleAuthorizationDialog({ entry, busy, onClose, onContinue }: {
  entry: IntegrationCatalogEntry;
  busy: boolean;
  onClose: () => void;
  onContinue: () => Promise<void>;
}) {
  const calendar = entry.provider === "google_calendar";
  return (
    <DialogShell
      title={`Connect ${entry.name}`}
      description="Google asks you to approve only the access this integration needs."
      provider={entry.provider}
      busy={busy}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" size="sm" disabled={busy} onClick={onClose}>Cancel</Button>
          <Button size="sm" disabled={busy} onClick={() => void onContinue()}>
            {busy ? <LoaderCircle className="spin" /> : null}
            {busy ? "Opening Google…" : "Continue to Google"}
          </Button>
        </>
      )}
    >
      <InfoBlock icon={<ShieldCheck />} title={calendar ? "Calendar events and availability" : "Spreadsheet content"}>
        {calendar
          ? "Sauti reads busy times and creates, updates or cancels booking events in the calendar you choose. It never asks for sharing or admin rights."
          : "Sauti can read and update spreadsheets in the connected Google account, limited to the spreadsheet and ranges you set for the agent."}
      </InfoBlock>
      <ul className="m-0 grid list-none gap-2 p-0 text-[0.8rem] leading-relaxed text-fg-soft">
        {[
          "Access tokens are encrypted and belong to this workspace only.",
          "Google data is used only for the features you turn on, never for advertising or training models.",
          "Disconnect any time: Sauti deletes its copy and asks Google to revoke access.",
        ].map((item) => (
          <li key={item} className="flex gap-2"><Check className="mt-0.5 size-3.5 shrink-0 text-success" />{item}</li>
        ))}
      </ul>
      <p className="m-0 text-[0.76rem] text-fg-muted">
        Read the <Link href="/privacy" target="_blank" className="text-fg-soft underline underline-offset-2 hover:text-ink">Privacy Policy</Link> and <Link href="/terms" target="_blank" className="text-fg-soft underline underline-offset-2 hover:text-ink">Terms</Link>.
      </p>
    </DialogShell>
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

  return (
    <DialogShell
      as="form"
      onSubmit={(event) => void save(event)}
      title="Google Calendar settings"
      description="Choose which calendar the agent checks for free time and books into."
      provider="google_calendar"
      onClose={onClose}
      footer={(
        <>
          <Button variant="ghost" size="sm" className="mr-auto" disabled={busy || !status?.connected} onClick={() => void test()}>
            {testState === "testing" ? <LoaderCircle className="spin" /> : <TestTube2 />}
            {testState === "testing" ? "Testing…" : "Test connection"}
          </Button>
          <Button variant="secondary" size="sm" onClick={onClose}>Cancel</Button>
          <Button size="sm" type="submit" disabled={busy || !status?.connected}>{busy && testState !== "testing" ? "Saving…" : "Save calendar"}</Button>
        </>
      )}
    >
      {status?.connected === false ? <InfoBlock icon={<CircleAlert />} tone="danger">Google Calendar isn&apos;t connected for this workspace yet.</InfoBlock> : null}
      <DialogField label="Calendar ID" htmlFor="google-calendar-id" hint="Use “primary” for the account’s main calendar, or paste a calendar ID from Google Calendar settings.">
        <Input id="google-calendar-id" required className="h-10 font-mono text-[0.85rem]" value={calendarId} onChange={(event) => setCalendarId(event.target.value)} placeholder="primary or calendar-id@group.calendar.google.com" />
      </DialogField>
      {error ? <Alert>{error}</Alert> : null}
      {testState === "success" || testState === "error" ? (
        <InfoBlock icon={testState === "success" ? <Check /> : <CircleAlert />} tone={testState === "success" ? "success" : "danger"}>{testMessage}</InfoBlock>
      ) : null}
    </DialogShell>
  );
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

  return (
    <DialogShell
      title={`Disconnect ${connection.displayName}?`}
      description={<>This removes the connection for the whole workspace and turns off {impact.resource.toLowerCase()} for every agent using it.{connection.provider.startsWith("google_") ? " Sauti deletes its encrypted copy of the access token." : ""}</>}
      provider={connection.provider}
      busy={busy}
      onClose={onClose}
      width="30rem"
      footer={(
        <>
          <Button variant="secondary" size="sm" disabled={busy} onClick={onClose}>Keep connected</Button>
          <Button variant="danger" size="sm" disabled={busy} onClick={() => void onConfirm()}>
            {busy ? <LoaderCircle className="spin" /> : <Unplug />}
            {busy ? "Disconnecting…" : "Disconnect"}
          </Button>
        </>
      )}
    >
      <ul className="m-0 grid list-none gap-2 p-0 text-[0.82rem] leading-relaxed">
        <li className="flex gap-2.5 text-fg-soft"><Check className="mt-0.5 size-4 shrink-0 text-success" />{impact.intact}</li>
        <li className="flex gap-2.5 text-fg-soft"><CircleAlert className="mt-0.5 size-4 shrink-0 text-warning" />{impact.stops}</li>
      </ul>
    </DialogShell>
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
    () => readSession()?.tenant?.countryCode || "KE",
  );
  const [businessPhoneNumber, setBusinessPhoneNumber] = useState("");

  useEffect(() => {
    Promise.all([
      getWhatsAppSignupConfiguration(),
      connection ? getWhatsAppTemplates(connection.id) : Promise.resolve([]),
    ]).then(([rawConfiguration, loadedTemplates]) => {
      // Older servers (and the preview) may omit the country list.
      const loadedConfiguration = { ...rawConfiguration, countries: rawConfiguration.countries ?? [] };
      setConfiguration(loadedConfiguration);
      setTemplates(loadedTemplates);
      const savedNumber = String(connection?.configuration.businessPhoneNumber
        || connection?.configuration.displayPhoneNumber || "").replace(/\D/g, "");
      if (savedNumber) {
        const savedCountry = String(connection?.configuration.businessCountryCode || "");
        const preferred = loadedConfiguration.countries.find(
          (country) => country.region === (savedCountry || readSession()?.tenant?.countryCode || ""),
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
        const tenantCountry = readSession()?.tenant?.countryCode || "";
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

  return (
    <DialogShell
      title={connection ? "WhatsApp settings" : "Connect WhatsApp"}
      description="Sign in with Meta. Sauti picks up your business account and phone IDs automatically."
      provider="whatsapp"
      busy={connecting}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" size="sm" disabled={connecting} onClick={onClose}>{connection ? "Close" : "Cancel"}</Button>
          {connection && templates.length > 0 ? (
            <Button size="sm" disabled={!selection || connecting} onClick={() => void saveTemplate()}>Save template</Button>
          ) : null}
        </>
      )}
    >
      {loading ? (
        <div className="flex items-center justify-center gap-2 py-10 text-[0.84rem] text-fg-muted"><LoaderCircle className="spin size-4" /> Loading Meta settings…</div>
      ) : (
        <>
          {!configuration?.configured ? (
            <InfoBlock icon={<CircleAlert />} tone="danger" title="Meta sign-up isn’t set up on the server">
              Add the Meta app ID, app secret and configuration ID to the server settings, then try again.
            </InfoBlock>
          ) : null}
          {connection ? (
            <div className="grid gap-3 rounded-[var(--radius-control)] border border-line p-3.5 sm:grid-cols-2">
              <div className="min-w-0">
                <p className="m-0 text-[0.74rem] text-fg-muted">Business</p>
                <p className="m-0 truncate text-[0.86rem] font-medium text-ink">{String(connection.configuration.verifiedName || connection.displayName)}</p>
              </div>
              <div className="min-w-0">
                <p className="m-0 text-[0.74rem] text-fg-muted">Phone number</p>
                <p className="m-0 truncate font-mono text-[0.86rem] text-ink">{String(connection.configuration.displayPhoneNumber || "Connected")}</p>
              </div>
              <p className="m-0 flex items-center gap-1.5 text-[0.76rem] text-success sm:col-span-2"><ShieldCheck className="size-3.5" /> Connected through Meta</p>
            </div>
          ) : null}
          <DialogField label="Business WhatsApp number" htmlFor="whatsapp-business-country" hint="Must match the number you pick in the Meta window.">
            <div className="grid grid-cols-[minmax(0,13rem)_minmax(0,1fr)] gap-2 max-sm:grid-cols-1">
              <Select
                id="whatsapp-business-country"
                size="md"
                className="h-10 text-[0.86rem]"
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
              <Input aria-label="Business WhatsApp number" inputMode="tel" required className="h-10 font-mono text-[0.88rem]"
                value={businessPhoneNumber}
                onChange={(event) => setBusinessPhoneNumber(event.target.value.replace(/[^\d\s()-]/g, ""))}
                placeholder="712 345 678" />
            </div>
          </DialogField>
          <button
            type="button"
            disabled={connecting || !configuration?.configured || !businessCountryCode || !businessPhoneNumber.trim()}
            onClick={() => void connect()}
            className="inline-flex h-10 cursor-pointer items-center justify-center gap-2.5 rounded-[var(--radius-button)] border-0 bg-[#1877f2] px-4 font-sans text-[0.88rem] font-semibold text-white transition-colors hover:bg-[#166fe0] disabled:cursor-not-allowed disabled:opacity-45"
          >
            {connecting ? <LoaderCircle className="spin size-4" /> : <span aria-hidden="true" className="grid size-5 place-items-center rounded-full bg-white text-[0.8rem] font-bold leading-none text-[#1877f2]">f</span>}
            {connecting ? "Connecting…" : connection ? "Reconnect with Meta" : "Continue with Facebook"}
          </button>
          {connection ? (
            <div className="grid gap-4 border-t border-line pt-4">
              <DialogField label="Booking confirmation template" hint={templates.length ? "Sent to customers after a booking. Only templates Meta has approved are listed." : undefined}>
                {templates.length ? (
                  <Select
                    size="md"
                    className="h-10 text-[0.86rem]"
                    ariaLabel="Outbound message template"
                    placeholder="Choose an approved template"
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
                  />
                ) : (
                  <p className="m-0 rounded-[var(--radius-control)] border border-dashed border-line-strong px-3.5 py-3 text-[0.8rem] text-fg-muted">No approved templates yet. Customers who message you can still get free-form replies.</p>
                )}
              </DialogField>
              {selectedTemplate && selectedTemplate.parameters.length > 0 ? (
                <div className="grid gap-3 rounded-[var(--radius-control)] border border-line p-3.5">
                  <div>
                    <p className="m-0 text-[0.84rem] font-medium text-ink">Fill the template’s placeholders</p>
                    <p className="m-0 mt-0.5 text-[0.76rem] text-fg-muted">Choose which booking detail goes into each one.</p>
                  </div>
                  {selectedTemplate.parameters.map((parameter) => (
                    <div key={parameter.key} className="grid grid-cols-[minmax(0,9rem)_minmax(0,1fr)] items-center gap-3 max-sm:grid-cols-1">
                      <span className="text-[0.8rem] text-fg-soft">{parameter.component === "header" ? "Header" : "Message"} <code className="font-mono text-ink">{parameter.placeholder}</code></span>
                      <Select
                        size="sm"
                        ariaLabel={`Value for ${parameter.placeholder}`}
                        placeholder="Choose a booking detail"
                        value={parameterMappings[parameter.key] || ""}
                        onValueChange={(value) => setParameterMappings((current) => ({ ...current, [parameter.key]: value }))}
                        options={WHATSAPP_TEMPLATE_FIELDS.map((field) => ({ value: field.value, label: field.label }))}
                      />
                    </div>
                  ))}
                </div>
              ) : null}
            </div>
          ) : null}
        </>
      )}
      {error ? <Alert>{error}</Alert> : null}
    </DialogShell>
  );
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

  const sheets = entry.provider === "google_sheets";
  return (
    <DialogShell
      as="form"
      onSubmit={(event) => void save(event)}
      title={`${connection ? "" : "Connect "}${entry.name}${connection ? " settings" : ""}`}
      description={sheets
        ? "Use a spreadsheet as a simple customer list and call log. Sauti stays the source of truth for calls and bookings."
        : "Secrets are encrypted before they’re stored, and never shown again after saving."}
      provider={entry.provider}
      busy={saving || initializing}
      onClose={onClose}
      width={sheets ? "40rem" : "34rem"}
      footer={(
        <>
          <Button variant="secondary" size="sm" disabled={saving || initializing} onClick={onClose}>Cancel</Button>
          <Button size="sm" type="submit" disabled={saving || initializing || fields.length === 0}>
            {saving ? "Saving…" : connection ? "Save changes" : "Connect"}
          </Button>
        </>
      )}
    >
      {fields.length === 0 ? (
        <InfoBlock icon={<ShieldCheck />}>This provider connects by signing in. Use its sign-in flow once the app credentials are set up on the server.</InfoBlock>
      ) : null}
      {sheets ? (
        <div className="grid gap-2 sm:grid-cols-2">
          {[
            ["Customers", "Adds booked customers by phone and fills in missing names or emails.", "Phone · Name · Email"],
            ["Calls", "Gets a new row after every call for reporting and follow-up.", "Call ID · Started · Phone · Outcome · Summary · Sentiment"],
          ].map(([title, text, columns]) => (
            <div key={title} className="grid content-start gap-1.5 rounded-[var(--radius-control)] border border-line p-3.5">
              <p className="m-0 flex items-center gap-2 text-[0.84rem] font-medium text-ink"><TableProperties className="size-4 text-fg-muted" /> {title} tab</p>
              <p className="m-0 text-[0.78rem] leading-relaxed text-fg-soft">{text}</p>
              <code className="font-mono text-[0.72rem] text-fg-muted">{columns}</code>
            </div>
          ))}
          <p className="m-0 text-[0.76rem] leading-relaxed text-fg-muted sm:col-span-2">Use an empty spreadsheet or one that already has these tabs. Sauti only adds missing tabs and headers, and never overwrites existing ones.</p>
        </div>
      ) : null}
      {visibleFields.length ? (
        <div className={cn("grid gap-x-3 gap-y-4", sheets && "sm:grid-cols-2")}>
          {visibleFields.map((field) => (
            <DialogField key={field} label={labels[field] ?? field} htmlFor={`field-${field}`}>
              {field === "environment" || field === "authType" ? (
                <Select
                  id={`field-${field}`}
                  size="md"
                  className="h-10 text-[0.86rem]"
                  value={values[field] ?? ""}
                  onValueChange={(value) => setValues({ ...values, [field]: value })}
                  options={(field === "environment"
                    ? [["sandbox", "Sandbox"], ["production", "Production"]]
                    : [["none", "None"], ["bearer", "Bearer token"], ["api_key", "API key"], ["hmac", "HMAC-SHA256"]]
                  ).map(([value, text]) => ({ value, label: text }))}
                />
              ) : (
                <Input
                  id={`field-${field}`}
                  className={cn("h-10 text-[0.86rem]", (entry.credentialFields.includes(field) || /Range|Columns|Id$|Uri$|Url$/.test(field)) && "font-mono")}
                  required={
                    (sheets && ["spreadsheetId", "range"].includes(field))
                    || (!connection && entry.credentialFields.includes(field)
                      && (entry.provider !== "custom_webhook" || !["authToken", "apiKey", "hmacSecret"].includes(field)))
                  }
                  type={entry.credentialFields.includes(field) ? "password" : "text"}
                  autoComplete={entry.credentialFields.includes(field) ? "new-password" : "off"}
                  value={values[field] ?? ""}
                  placeholder={connection && entry.credentialFields.includes(field) ? "Leave blank to keep the saved value" : placeholderFor(entry.provider, field)}
                  onChange={(event) => setValues({ ...values, [field]: event.target.value })}
                />
              )}
            </DialogField>
          ))}
        </div>
      ) : null}
      {sheets ? (
        <div className="flex flex-wrap items-center gap-3 rounded-[var(--radius-control)] border border-line p-3.5">
          <p className="m-0 min-w-0 flex-1 text-[0.78rem] leading-relaxed text-fg-soft">
            {connection
              ? "Saves the settings above, adds any missing tabs and headers, and checks access."
              : "Connect your Google account first, then enter the spreadsheet ID."}
          </p>
          <Button variant="secondary" size="xs" onClick={() => void initializeSheets()}
            disabled={initializing || saving || !connection || !(values.spreadsheetId ?? "").trim()}>
            {initializing ? <LoaderCircle className="spin" /> : <TableProperties />}
            {initializing ? "Setting up…" : "Set up tabs"}
          </Button>
        </div>
      ) : null}
      {initializationMessage ? <InfoBlock icon={<Check />} tone="success">{initializationMessage}</InfoBlock> : null}
      {error ? <Alert>{error}</Alert> : null}
    </DialogShell>
  );
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

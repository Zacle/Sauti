"use client";


import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/primitives";
import { IntegrationLogo } from "@/features/integrations/presentation/IntegrationLogo";
import { getAgentIntegrations, getIntegrationCatalog, type AgentIntegration, type IntegrationCatalogEntry } from "@/lib/api/integrations";
import {
ArrowUpRight,
CalendarCheck,
CircleAlert,
Plug
} from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
Meta,
Note,
StudioGroup,
StudioSection
} from "./studio-kit";

import { EmptyBox } from "./KnowledgeSettings";

export const FALLBACK_AGENT_INTEGRATIONS: IntegrationCatalogEntry[] = [
  { provider: "google_calendar", name: "Google Calendar", category: "Calendar", description: "Check free slots during the call and create the event when the caller books.", duringCall: true, postCall: false, requiresConnection: true, configurationFields: [], credentialFields: [], authorizationConfigured: true },
  { provider: "custom_webhook", name: "Webhook", category: "Developer", description: "Send a signed JSON event to your own endpoint after each call.", duringCall: false, postCall: true, requiresConnection: true, configurationFields: [], credentialFields: [], authorizationConfigured: true },
];

export const CALENDAR_PROVIDERS = new Set(["google_calendar", "calendly"]);

export function IntegrationsSettings({ agentId, bookingEnabled }: { agentId?: string; bookingEnabled: boolean }) {
  const [catalog, setCatalog] = useState<IntegrationCatalogEntry[]>([]);
  const [bindings, setBindings] = useState<AgentIntegration[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    Promise.allSettled([getIntegrationCatalog(), agentId ? getAgentIntegrations(agentId) : Promise.resolve([] as AgentIntegration[])])
      .then(([catalogResult, bindingResult]) => {
        if (!active) return;
        setCatalog(catalogResult.status === "fulfilled" && catalogResult.value.length ? catalogResult.value : FALLBACK_AGENT_INTEGRATIONS);
        setBindings(bindingResult.status === "fulfilled" ? bindingResult.value : []);
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [agentId]);

  const stateOf = (provider: string) => {
    const binding = bindings.find((item) => item.provider === provider);
    const connected = Boolean(binding && ["connected", "built_in", "active"].includes(binding.connectionStatus));
    return { binding, connected, active: connected && binding?.enabled === true };
  };
  const rows = catalog.map((entry) => ({ entry, ...stateOf(entry.provider) }));
  const activeRows = rows.filter((row) => row.active);
  const otherRows = rows.filter((row) => !row.active)
    .sort((left, right) => Number(right.connected) - Number(left.connected) || left.entry.name.localeCompare(right.entry.name));
  const calendarReady = rows.some((row) => CALENDAR_PROVIDERS.has(row.entry.provider) && row.active);
  const manageHref = (provider: string) => `/dashboard/integrations?provider=${provider}${agentId ? `&agentId=${agentId}` : ""}`;

  const renderRow = ({ entry, binding, connected, active }: (typeof rows)[number]) => (
    <div key={entry.provider} className="flex items-start gap-3.5 px-4 py-3.5 max-sm:flex-wrap">
      <span className="grid size-10 shrink-0 place-items-center rounded-[var(--radius-control)] border border-line bg-surface">
        <IntegrationLogo provider={entry.provider} fallback={<Plug className="size-[18px] text-fg-muted" />} />
      </span>
      <div className="min-w-0 flex-1">
        <p className="m-0 flex flex-wrap items-center gap-2 text-[0.88rem] font-medium text-ink">
          {entry.name}
          {active ? <Badge tone="success" dot>On for this agent</Badge> : connected ? <Badge tone="neutral">Connected · off</Badge> : null}
        </p>
        <p className="m-0 mt-0.5 text-[0.78rem] leading-relaxed text-fg-muted">{entry.description}</p>
        <p className="m-0 mt-1.5 flex flex-wrap gap-x-3 gap-y-1 text-[0.72rem] text-fg-muted">
          <span>{entry.category}</span>
          {entry.duringCall ? <span>· Used during calls</span> : null}
          {entry.postCall ? <span>· Runs after calls</span> : null}
          {binding?.lastDelivery?.deliveredAt ? <span>· Last used {new Date(binding.lastDelivery.deliveredAt).toLocaleDateString(undefined, { day: "numeric", month: "short" })}</span> : null}
        </p>
      </div>
      <div className="shrink-0 self-center max-sm:ml-[3.375rem]">
        {agentId ? (
          <Button asChild variant={active || connected ? "ghost" : "secondary"} size="xs">
            <Link href={manageHref(entry.provider)}>{active || connected ? "Manage" : "Connect"} <ArrowUpRight /></Link>
          </Button>
        ) : (
          <Button variant="secondary" size="xs" disabled>Save agent first</Button>
        )}
      </div>
    </div>
  );

  return (
    <StudioSection
      eyebrow="Tools & integrations"
      title="Connect actions this agent can perform"
      description="Integrations give the agent live information and let it take actions during and after calls."
      actions={(
        <Button asChild variant="ghost" size="xs">
          <Link href="/dashboard/integrations">All integrations <ArrowUpRight /></Link>
        </Button>
      )}
    >
      {!agentId ? (
        <div className="pb-6">
          <Note tone="warning" icon={<CircleAlert />} title="Save the agent first">Integrations are linked to a saved agent so credentials stay secure.</Note>
        </div>
      ) : null}
      {bookingEnabled && !calendarReady && !loading ? (
        <div className="pb-6">
          <Note
            tone="warning"
            icon={<CalendarCheck />}
            title="Bookings need a calendar"
            action={agentId ? (
              <Button asChild variant="secondary" size="xs"><Link href={manageHref("google_calendar")}>Connect calendar</Link></Button>
            ) : undefined}
          >
            Appointment booking is on, but no calendar is connected for this agent yet.
          </Note>
        </div>
      ) : null}

      <StudioGroup title="On for this agent" description="Tools the agent uses right now." action={<Meta>{loading ? "…" : activeRows.length}</Meta>}>
        {loading ? (
          <div className="grid gap-2">{[0, 1].map((item) => <div key={item} className="h-[72px] animate-pulse rounded-[var(--radius-card)] bg-tint/[0.05]" />)}</div>
        ) : activeRows.length ? (
          <div className="divide-y divide-line overflow-hidden rounded-[var(--radius-card)] border border-line">{activeRows.map(renderRow)}</div>
        ) : (
          <EmptyBox icon={<Plug />} title="No tools on yet" text="Connect a tool below and the agent can use it on its next call." />
        )}
      </StudioGroup>

      {!loading && otherRows.length ? (
        <StudioGroup title="Available" description="Connect once for the workspace, then choose which agents use it." action={<Meta>{otherRows.length}</Meta>}>
          <div className="divide-y divide-line overflow-hidden rounded-[var(--radius-card)] border border-line">{otherRows.map(renderRow)}</div>
        </StudioGroup>
      ) : null}
    </StudioSection>
  );
}

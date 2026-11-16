"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Bot, MoreHorizontal, Plus, Search, Trash2 } from "lucide-react";
import { deleteAgent, listAgents, listAgentStats } from "@/lib/api/agents";
import type { Agent, AgentStats } from "@/types/api";
import { previewDashboardData } from "@/features/dashboard/data/preview-data";
import { useAuth } from "@/hooks/useAuth";
import { DeleteAgentDialog } from "@/features/agents/DeleteAgentDialog/DeleteAgentDialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { Card, EmptyState, Skeleton, Tabs, TabsCount, TabsList, TabsTrigger } from "@/components/ui/primitives";
import { PageHeader, StatStrip } from "@/components/ui/console";

type AgentFilter = "all" | "live" | "draft";

const numberFormat = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });
const COLUMNS = "lg:grid-cols-[minmax(0,2.2fr)_6rem_minmax(0,1.2fr)_5rem_6rem_2.5rem]";

export function AgentsPage() {
  const { session, ready } = useAuth();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [stats, setStats] = useState<Record<string, AgentStats>>({});
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<AgentFilter>("all");
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Agent | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!ready) return;
    if (!session) {
      const previewAgent = previewDashboardData.agents[0];
      setAgents(previewDashboardData.agents);
      setStats(previewAgent ? {
        [previewAgent.id]: {
          agentId: previewAgent.id,
          totalCalls: previewDashboardData.analytics.totalCalls,
          bookingCalls: previewDashboardData.analytics.bookingCalls,
          bookingRate: Math.round(previewDashboardData.analytics.bookingCalls / previewDashboardData.analytics.totalCalls * 1000) / 10,
        },
      } : {});
      setLoading(false);
      return;
    }
    Promise.all([listAgents(), listAgentStats()])
      .then(([agentList, agentStats]) => {
        setAgents(agentList);
        setStats(Object.fromEntries(agentStats.map((item) => [item.agentId, item])));
      })
      .catch(() => setAgents(previewDashboardData.agents))
      .finally(() => setLoading(false));
  }, [ready, session]);

  useEffect(() => {
    if (!openMenuId) return;
    const close = (event: MouseEvent | KeyboardEvent) => {
      if (event instanceof KeyboardEvent && event.key !== "Escape") return;
      if (event instanceof MouseEvent && (event.target as HTMLElement).closest("[data-agent-menu]")) return;
      setOpenMenuId(null);
    };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", close);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", close);
    };
  }, [openMenuId]);

  const visibleAgents = useMemo(
    () => agents.filter((agent) => {
      const needle = query.trim().toLowerCase();
      const matchesQuery = !needle
        || agent.name.toLowerCase().includes(needle)
        || agent.description.toLowerCase().includes(needle);
      const matchesFilter = filter === "all" || (filter === "live" ? agent.active : !agent.active);
      return matchesQuery && matchesFilter;
    }),
    [agents, filter, query],
  );

  const counts = useMemo(() => ({
    all: agents.length,
    live: agents.filter((agent) => agent.active).length,
    draft: agents.filter((agent) => !agent.active).length,
  }), [agents]);

  const totals = useMemo(() => {
    const values = Object.values(stats);
    const totalCalls = values.reduce((sum, item) => sum + item.totalCalls, 0);
    const bookingCalls = values.reduce((sum, item) => sum + item.bookingCalls, 0);
    return {
      totalCalls,
      bookingRate: totalCalls ? bookingCalls / totalCalls : 0,
      languages: new Set(agents.flatMap((agent) => agent.supportedLanguages.map(shortLanguage))).size,
    };
  }, [agents, stats]);

  async function confirmDelete() {
    if (!deleteTarget) return;
    setDeleteBusy(true);
    setDeleteError("");
    try {
      await deleteAgent(deleteTarget.id);
      setAgents((current) => current.filter((agent) => agent.id !== deleteTarget.id));
      setStats((current) => {
        const next = { ...current };
        delete next[deleteTarget.id];
        return next;
      });
      setDeleteTarget(null);
    } catch (caught) {
      setDeleteError(caught instanceof Error ? caught.message : "Unable to delete this agent.");
    } finally {
      setDeleteBusy(false);
    }
  }

  return (
    <div className="grid min-w-0 grid-cols-1 gap-6 [&>*]:min-w-0">
      <PageHeader
        title="Agents"
        description="Each agent answers a line, follows its instructions and books into your calendar."
        actions={
          <Button asChild size="xs">
            <Link href="/agents/new"><Plus /> New agent</Link>
          </Button>
        }
      />

      <StatStrip
        items={[
          { label: "Agents", value: counts.all, hint: `${counts.live} live · ${counts.draft} draft` },
          { label: "Calls handled", value: totals.totalCalls, hint: "All time" },
          { label: "Booking rate", value: totals.bookingRate, format: { style: "percent", maximumFractionDigits: 1 }, hint: "Calls that booked" },
          { label: "Languages", value: totals.languages, hint: "Across all agents" },
        ]}
      />

      <div className="flex flex-wrap items-center gap-2">
        <Tabs value={filter} onValueChange={(value) => setFilter(value as AgentFilter)}>
          <TabsList aria-label="Filter agents">
            {(["all", "live", "draft"] as const).map((value) => (
              <TabsTrigger key={value} value={value}>
                {value === "all" ? "All" : value === "live" ? "Live" : "Draft"}
                <TabsCount>{counts[value]}</TabsCount>
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        <label className="relative ml-auto w-full sm:w-64">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-fg-muted" />
          <Input aria-label="Find an agent" className="h-9 pl-9 text-[0.85rem]" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Find an agent" />
        </label>
      </div>

      {loading ? (
        <Card className="grid gap-3 p-4">{[1, 2, 3].map((item) => <Skeleton key={item} className="h-14" />)}</Card>
      ) : visibleAgents.length ? (
        <Card>
          <div className={`hidden gap-4 border-b border-line px-5 py-2.5 text-xs font-medium text-fg-muted lg:grid ${COLUMNS}`}>
            <span>Agent</span>
            <span>Status</span>
            <span>Channel</span>
            <span className="text-right">Calls</span>
            <span className="text-right">Booked</span>
            <span />
          </div>
          <ul className="m-0 list-none divide-y divide-line p-0">
            {visibleAgents.map((agent) => {
              const agentStats = stats[agent.id];
              return (
                <li key={agent.id} className={`relative grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-2 px-5 py-3.5 transition-colors first:rounded-t-[var(--radius-card)] last:rounded-b-[var(--radius-card)] hover:bg-surface-2/60 ${COLUMNS}`}>
                  <Link href={`/agents/${agent.id}`} className="flex min-w-0 items-center gap-3 text-inherit no-underline after:absolute after:inset-0">
                    <span className="grid size-9 shrink-0 place-items-center rounded-[8px] border border-line bg-surface-2 text-[0.85rem] font-semibold text-ink">
                      {agent.name.trim().slice(0, 1).toUpperCase()}
                    </span>
                    <span className="min-w-0">
                      <span className="block truncate text-[0.9rem] font-medium text-ink">{agent.name}</span>
                      <span className="block truncate text-[0.8rem] text-fg-muted">{agent.description || "No description yet"}</span>
                    </span>
                  </Link>
                  <span className="flex items-center gap-2 text-[0.82rem] text-fg max-lg:justify-self-end">
                    <span className={agent.active ? "size-1.5 rounded-full bg-success" : "size-1.5 rounded-full bg-line-strong"} aria-hidden="true" />
                    {agent.active ? "Live" : "Draft"}
                  </span>
                  <span className="min-w-0 text-[0.82rem] leading-tight max-lg:col-span-2 max-lg:pl-12">
                    <span className="block truncate tabular-nums text-fg">{agent.twilioPhoneNumber ?? (agent.webVoiceEnabled ? "Web voice" : "No channel yet")}</span>
                    <span className="block truncate text-[0.75rem] text-fg-muted">{agent.supportedLanguages.map(shortLanguage).join(" · ")}</span>
                  </span>
                  <span className="hidden text-right text-[0.85rem] tabular-nums text-fg lg:block">{numberFormat.format(agentStats?.totalCalls ?? 0)}</span>
                  <span className="hidden text-right text-[0.85rem] tabular-nums text-fg lg:block">{formatRate(agentStats?.bookingRate ?? 0)}</span>
                  <div className="relative z-10 hidden justify-self-end lg:block" data-agent-menu>
                    <button
                      aria-expanded={openMenuId === agent.id}
                      aria-label={`More actions for ${agent.name}`}
                      onClick={() => setOpenMenuId((current) => current === agent.id ? null : agent.id)}
                      type="button"
                      className="grid size-8 cursor-pointer place-items-center rounded-[6px] border-0 bg-transparent p-0 text-fg-muted transition-colors hover:bg-tint/[0.06] hover:text-fg"
                    >
                      <MoreHorizontal className="size-4" />
                    </button>
                    {openMenuId === agent.id ? (
                      <div className="absolute right-0 top-full z-20 mt-1 w-44 rounded-[var(--radius-popover)] border border-line-strong bg-popover p-1 shadow-[var(--shadow-pop)]">
                        <Link href={`/agents/${agent.id}`} className="flex h-8 items-center rounded-[6px] px-2.5 text-[0.84rem] text-fg no-underline hover:bg-surface-2">
                          {agent.active ? "Open agent" : "Continue setup"}
                        </Link>
                        <button
                          type="button"
                          onClick={() => {
                            setDeleteError("");
                            setDeleteTarget(agent);
                            setOpenMenuId(null);
                          }}
                          className="flex h-8 w-full cursor-pointer items-center gap-2 rounded-[6px] border-0 bg-transparent px-2.5 text-left font-sans text-[0.84rem] text-danger hover:bg-danger/[0.07]"
                        >
                          <Trash2 className="size-3.5" /> Delete agent
                        </button>
                      </div>
                    ) : null}
                  </div>
                </li>
              );
            })}
          </ul>
        </Card>
      ) : (
        <EmptyState
          icon={<Bot />}
          title={agents.length ? "No matching agents" : "No agents yet"}
          description={agents.length ? "Try another search or filter." : "Create an agent to start answering calls and taking bookings."}
          action={!agents.length ? <Button asChild size="xs"><Link href="/agents/new"><Plus /> New agent</Link></Button> : undefined}
        />
      )}

      {deleteTarget && (
        <DeleteAgentDialog
          agent={deleteTarget}
          busy={deleteBusy}
          error={deleteError}
          onCancel={() => !deleteBusy && setDeleteTarget(null)}
          onConfirm={() => void confirmDelete()}
        />
      )}
    </div>
  );
}

function shortLanguage(language: string) {
  const normalized = language.toLowerCase();
  const names: Record<string, string> = { english: "EN", french: "FR", swahili: "SW", arabic: "AR" };
  return names[normalized] ?? language.slice(0, 2).toUpperCase();
}

function formatRate(value: number) {
  return `${Math.round(value * 10) / 10}%`;
}

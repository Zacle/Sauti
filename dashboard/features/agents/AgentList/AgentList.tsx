"use client";

import styles from "./AgentList.module.css";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  Activity,
  ArrowRight,
  Bot,
  CalendarCheck2,
  CheckCircle2,
  Globe2,
  Grid2X2,
  Languages,
  List,
  Mic2,
  MoreHorizontal,
  PhoneCall,
  Plus,
  Radio,
  Search,
  Sparkles,
  Trash2,
  TrendingUp,
} from "lucide-react";
import { deleteAgent, listAgents, listAgentStats } from "@/lib/api/agents";
import type { Agent, AgentStats } from "@/types/api";
import { previewDashboardData } from "@/features/dashboard/data/preview-data";
import { useAuth } from "@/hooks/useAuth";
import { DeleteAgentDialog } from "@/features/agents/DeleteAgentDialog/DeleteAgentDialog";

type AgentFilter = "all" | "live" | "draft";

const numberFormat = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });

export function AgentsPage() {
  const { session, ready } = useAuth();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [stats, setStats] = useState<Record<string, AgentStats>>({});
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<AgentFilter>("all");
  const [gridView, setGridView] = useState(true);
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
      bookingRate: totalCalls ? Math.round((bookingCalls / totalCalls) * 1000) / 10 : 0,
      languages: new Set(agents.flatMap((agent) => agent.supportedLanguages)).size,
    };
  }, [agents, stats]);

  const rankedAgents = useMemo(() => [...agents]
    .sort((left, right) => (stats[right.id]?.totalCalls ?? 0) - (stats[left.id]?.totalCalls ?? 0)),
  [agents, stats]);

  const chartPoints = useMemo(() => {
    const values = rankedAgents.slice(0, 7).map((agent) => stats[agent.id]?.totalCalls ?? 0);
    if (!values.length) return "0,78 240,78";
    if (values.length === 1) return "0,42 240,42";
    const max = Math.max(...values, 1);
    return values.map((value, index) => `${index * (240 / (values.length - 1))},${84 - (value / max) * 62}`).join(" ");
  }, [rankedAgents, stats]);

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
    <div className={styles["agents-page"]}>
      <header className={styles["agents-hero"]}>
        <div>
          <span className={styles.eyebrow}><Sparkles size={13} /> Agent studio</span>
          <h1>AI voice agents</h1>
          <p>Design, test, and deploy voice agents that sound human and drive results.</p>
        </div>
        <div className={styles["voice-visual"]} aria-hidden="true">
          <span className={styles["wave-line"]} />
          <span className={styles["voice-ring"]} />
          <span className={styles["voice-ring"]} />
          <span className={styles["voice-core"]}><Mic2 size={25} /></span>
        </div>
        <Link className={styles["hero-create"]} href="/agents/new"><Plus size={17} /> Create agent</Link>
      </header>

      <div className={styles["studio-layout"]}>
        <main className={styles["agents-workspace"]}>
          <section className={styles["metric-grid"]} aria-label="Agent overview">
            <MetricCard icon={<Bot size={21} />} label="Total agents" value={counts.all} tone="teal" />
            <MetricCard icon={<Radio size={21} />} label="Live agents" value={counts.live} tone="violet" />
            <MetricCard icon={<PhoneCall size={21} />} label="Calls handled" value={numberFormat.format(totals.totalCalls)} tone="blue" />
            <MetricCard icon={<CalendarCheck2 size={21} />} label="Booking rate" value={`${totals.bookingRate}%`} tone="amber" />
          </section>

          <section className={styles["agents-toolbar"]}>
            <div className={styles.filters}>
              {(["all", "live", "draft"] as const).map((value) => (
                <button
                  aria-pressed={filter === value}
                  className={filter === value ? styles.active : ""}
                  key={value}
                  onClick={() => setFilter(value)}
                  type="button"
                >
                  {value === "all" ? "All agents" : value === "live" ? "Live" : "Draft"}
                  <span>{counts[value]}</span>
                </button>
              ))}
            </div>
            <label className={styles["agent-search"]}>
              <Search size={15} />
              <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Find an agent" />
            </label>
            <div className={styles["view-switcher"]}>
              <button className={gridView ? styles.active : ""} onClick={() => setGridView(true)} aria-label="Grid view" type="button"><Grid2X2 size={16} /></button>
              <button className={!gridView ? styles.active : ""} onClick={() => setGridView(false)} aria-label="List view" type="button"><List size={17} /></button>
            </div>
          </section>

          {loading ? (
            <div className={styles["agents-loading-grid"]}>{[1, 2, 3].map((item) => <span key={item} />)}</div>
          ) : visibleAgents.length ? (
            <section className={`${styles["agents-grid"]} ${!gridView ? styles["list-view"] : ""}`}>
              {visibleAgents.map((agent, index) => (
                <article className={`${styles["agent-list-card"]} ${styles[`accent-${index % 3}`]}`} key={agent.id}>
                  <div className={styles["agent-list-card-head"]}>
                    <i className={agent.active ? styles.live : styles.draft}>{agent.active ? "Live" : "Draft"}</i>
                    <div className={styles["agent-card-menu"]}>
                      <button
                        aria-expanded={openMenuId === agent.id}
                        aria-label={`More actions for ${agent.name}`}
                        onClick={() => setOpenMenuId((current) => current === agent.id ? null : agent.id)}
                        type="button"
                      ><MoreHorizontal size={18} /></button>
                      {openMenuId === agent.id && (
                        <div><button onClick={() => {
                          setDeleteError("");
                          setDeleteTarget(agent);
                          setOpenMenuId(null);
                        }} type="button"><Trash2 size={15} /> Delete agent</button></div>
                      )}
                    </div>
                  </div>
                  <div className={styles["agent-identity"]}>
                    <span className={styles["agent-list-avatar"]}>{agent.name.slice(0, 1).toUpperCase()}<small><Activity size={13} /></small></span>
                    <h2>{agent.name} {agent.active && <CheckCircle2 size={15} />}</h2>
                    <p>{agent.description || "Voice agent ready to be configured for your business."}</p>
                  </div>
                  <div className={styles["agent-list-meta"]}>
                    <span><Languages size={14} /> {agent.supportedLanguages.map(shortLanguage).join(" · ")}</span>
                    <span><Globe2 size={14} /> {agent.twilioPhoneNumber ?? (agent.webVoiceEnabled ? "Web voice enabled" : "Channel not assigned")}</span>
                  </div>
                  <div className={styles["agent-list-footer"]}>
                    <span><strong>{numberFormat.format(stats[agent.id]?.totalCalls ?? 0)}</strong><small>Calls</small></span>
                    <span><strong>{formatRate(stats[agent.id]?.bookingRate ?? 0)}</strong><small>Booking rate</small></span>
                    <span><strong>{agent.supportedLanguages.length}</strong><small>Languages</small></span>
                  </div>
                  <Link className={styles["open-agent"]} href={`/agents/${agent.id}`}>
                    {agent.active ? "Open agent" : "Continue setup"}<ArrowRight size={15} />
                  </Link>
                </article>
              ))}
              <Link className={styles["agent-create-card"]} href="/agents/new">
                <span><Plus size={22} /></span>
                <div><strong>Create new agent</strong><small>Build a new voice agent in minutes</small></div>
                <ArrowRight size={17} />
              </Link>
            </section>
          ) : (
            <section className={styles["agents-empty"]}>
              <span><Bot size={24} /></span>
              <h2>{agents.length ? "No matching agents" : "Create your first AI voice agent"}</h2>
              <p>{agents.length ? "Try another search or clear the current filters." : "No agent exists in this workspace yet. Create one to start handling calls, capturing bookings, and connecting your tools."}</p>
              {!agents.length && <Link className={styles["empty-create"]} href="/agents/new"><Plus size={16} /> Create an agent <ArrowRight size={15} /></Link>}
            </section>
          )}
        </main>

        <aside className={styles["insights-rail"]}>
          <section className={styles["performance-card"]}>
            <div><span>Performance overview</span><small>All time</small></div>
            <strong>{numberFormat.format(totals.totalCalls)}</strong>
            <p><TrendingUp size={14} /> Calls handled across your workspace</p>
            <svg viewBox="0 0 240 94" role="img" aria-label="Calls handled by agent">
              <defs><linearGradient id="agentChartFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#22e7dc" stopOpacity=".34" /><stop offset="1" stopColor="#22e7dc" stopOpacity="0" /></linearGradient></defs>
              <polygon points={`0,94 ${chartPoints} 240,94`} fill="url(#agentChartFill)" />
              <polyline points={chartPoints} fill="none" stroke="#21e5dc" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            <div className={styles["performance-foot"]}><span><Languages size={14} /> {totals.languages} languages</span><span><Radio size={14} /> {counts.live} live</span></div>
          </section>

          <section className={styles["activity-card"]}>
            <div className={styles["rail-heading"]}><span>Agent activity</span><Link href="/analytics">View analytics</Link></div>
            {rankedAgents.length ? rankedAgents.slice(0, 4).map((agent, index) => (
              <Link href={`/agents/${agent.id}`} className={styles["activity-row"]} key={agent.id}>
                <span className={styles[`activity-${index % 3}`]}><PhoneCall size={16} /></span>
                <div><strong>{agent.name}</strong><small>{numberFormat.format(stats[agent.id]?.totalCalls ?? 0)} calls handled</small></div>
                <em>{formatRate(stats[agent.id]?.bookingRate ?? 0)}</em>
              </Link>
            )) : <p className={styles["rail-empty"]}>Your agent activity will appear here after the first call.</p>}
          </section>

        </aside>
      </div>

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

function MetricCard({ icon, label, value, tone }: { icon: React.ReactNode; label: string; value: string | number; tone: string }) {
  return <article className={styles["metric-card"]}><span className={styles[`tone-${tone}`]}>{icon}</span><div><small>{label}</small><strong>{value}</strong></div></article>;
}

function shortLanguage(language: string) {
  const normalized = language.toLowerCase();
  const names: Record<string, string> = { english: "EN", french: "FR", swahili: "SW", arabic: "AR" };
  return names[normalized] ?? language.slice(0, 2).toUpperCase();
}

function formatRate(value: number) {
  return `${Math.round(value * 10) / 10}%`;
}

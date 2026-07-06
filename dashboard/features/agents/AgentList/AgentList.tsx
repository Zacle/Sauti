"use client";

import styles from "./AgentList.module.css";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  ArrowRight,
  Bot,
  Globe2,
  MoreHorizontal,
  PhoneCall,
  Plus,
  Search,
  Sparkles,
  Trash2,
} from "lucide-react";
import { deleteAgent, listAgents, listAgentStats } from "@/lib/api/agents";
import type { Agent, AgentStats } from "@/types/api";
import { previewDashboardData } from "@/features/dashboard/data/preview-data";
import { useAuth } from "@/hooks/useAuth";
import { DeleteAgentDialog } from "@/features/agents/DeleteAgentDialog/DeleteAgentDialog";

type AgentFilter = "all" | "live" | "draft";

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
      setAgents(previewDashboardData.agents);
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
      const matchesQuery = agent.name.toLowerCase().includes(query.trim().toLowerCase());
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
    <>
      <div className="dashboard-page-head">
        <div>
          <span className="page-eyebrow">Agent studio</span>
          <h1>AI voice agents</h1>
          <p>Create, test, and manage every agent in your workspace.</p>
        </div>
        <Link className="console-primary-button" href="/agents/new"><Plus size={16} /> Create agent</Link>
      </div>

      <section className={styles["agents-toolbar"]}>
        <label><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search agents..." /></label>
        <div>
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
      </section>

      {loading ? (
        <div className={styles["agents-loading-grid"]}>{[1, 2, 3].map((item) => <span key={item} />)}</div>
      ) : visibleAgents.length ? (
        <section className={styles["agents-grid"]}>
          {visibleAgents.map((agent) => (
            <article className={styles["agent-list-card"]} key={agent.id}>
              <div className={styles["agent-list-card-head"]}>
                <span className={styles["agent-list-avatar"]}>{agent.name.slice(0, 1).toUpperCase()}</span>
                <i className={agent.active ? styles.live : styles.draft}>{agent.active ? "Live" : "Draft"}</i>
                <div className={styles["agent-card-menu"]}>
                  <button
                    aria-expanded={openMenuId === agent.id}
                    aria-label={`More actions for ${agent.name}`}
                    onClick={() => setOpenMenuId((current) => current === agent.id ? null : agent.id)}
                    type="button"
                  >
                    <MoreHorizontal size={18} />
                  </button>
                  {openMenuId === agent.id && (
                    <div>
                      <button onClick={() => {
                        setDeleteError("");
                        setDeleteTarget(agent);
                        setOpenMenuId(null);
                      }} type="button"><Trash2 size={15} /> Delete agent</button>
                    </div>
                  )}
                </div>
              </div>
              <h2>{agent.name}</h2>
              <p>{agent.description}</p>
              <div className={styles["agent-list-meta"]}>
                <span><PhoneCall size={15} /> {agent.twilioPhoneNumber ?? "Number not assigned"}</span>
                <span><Globe2 size={15} /> {agent.supportedLanguages.join(", ")}</span>
              </div>
              <div className={styles["agent-list-footer"]}>
                <span><strong>{stats[agent.id]?.totalCalls ?? 0}</strong><small>Calls</small></span>
                <span><strong>{stats[agent.id]?.bookingRate ?? 0}%</strong><small>Booking rate</small></span>
                <div className={styles["agent-card-actions"]}>
                  <Link href={`/agents/${agent.id}`}>Configure <ArrowRight size={15} /></Link>
                </div>
              </div>
            </article>
          ))}
          <Link className={styles["agent-create-card"]} href="/agents/new">
            <span className={styles["agent-create-icon"]}><Sparkles size={13} /><Bot size={23} /></span>
            <strong>Create another agent</strong>
            <p>Start from your business setup or configure one manually.</p>
            <span className={styles["agent-create-action"]}><Plus size={15} /> Create agent</span>
          </Link>
        </section>
      ) : (
        <section className={`dashboard-error-state ${styles["agents-empty"]}`}>
          <span><Bot size={24} /></span><h1>No matching agents</h1><p>Try a different search or create a new agent.</p>
        </section>
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
    </>
  );
}

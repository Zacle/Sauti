"use client";

import Link from "next/link";
import { useMemo } from "react";
import {
  ArrowRight,
  Bot,
  CalendarCheck,
  Check,
  ChevronRight,
  CircleAlert,
  Clock3,
  Headphones,
  LoaderCircle,
  PhoneCall,
  RefreshCw,
  Sparkles,
  TrendingUp,
  UsersRound,
  WalletCards,
} from "lucide-react";
import type { Call, DashboardData } from "@/types/api";
import { useAuth } from "@/hooks/useAuth";
import { useDashboard } from "@/hooks/useDashboard";
import { formatDate, formatDuration } from "@/lib/utils";

function prettyOutcome(outcome: string) {
  return outcome?.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase()) || "In progress";
}

export function DashboardOverview() {
  const { data, error, loading, refresh } = useDashboard();
  const { session } = useAuth();

  const upcomingBookings = useMemo(
    () => data?.bookings
      .filter((booking) => new Date(booking.appointmentAt).getTime() > Date.now() && booking.status !== "cancelled")
      .sort((a, b) => a.appointmentAt.localeCompare(b.appointmentAt))
      .slice(0, 3) ?? [],
    [data],
  );

  if (loading && !data) {
    return <DashboardSkeleton />;
  }

  if (error && !data) {
    return (
      <section className="dashboard-error-state">
        <span><CircleAlert size={24} /></span>
        <h1>We couldn&apos;t load your workspace</h1>
        <p>{error}</p>
        <button className="console-primary-button" onClick={() => void refresh()}><RefreshCw size={17} /> Try again</button>
      </section>
    );
  }

  if (!data) return null;
  const tenant = session?.tenant;
  const initialAgent = data.agents.find((agent) => agent.active) ?? data.agents.at(-1);
  const activeAgent = data.agents.find((agent) => agent.active) ?? initialAgent;
  const initialReadiness = data.readiness.find((item) => item.agentId === initialAgent?.id);
  const setupItems = initialAgent && initialReadiness
    ? [
        { label: "Business details complete", done: initialReadiness.businessDetailsComplete },
        { label: "Calendar connected", done: initialReadiness.calendarConfigured },
        { label: "Live channel enabled", done: initialReadiness.channelConfigured },
        { label: "Agent activated", done: initialReadiness.active },
      ]
    : [
        { label: "Email verified", done: data.onboarding.emailVerified },
        { label: "Create your first agent", done: false },
      ];
  const setupComplete = data.onboarding.hasActiveAgent;
  const setupHref = initialAgent ? `/agents/${initialAgent.id}` : "/onboarding";

  return (
    <>
      <div className="dashboard-page-head">
        <div>
          <span className="page-eyebrow">Workspace overview</span>
          <h1>Good day, {tenant?.businessName ?? "Sauti"}.</h1>
          <p>Monitor your agents, calls, bookings, and usage from one place.</p>
        </div>
        <div className="page-head-actions">
          <button className="console-secondary-button" onClick={() => void refresh()} disabled={loading}>
            {loading ? <LoaderCircle className="spin" size={16} /> : <RefreshCw size={16} />} Refresh
          </button>
          <Link className="console-primary-button" href="/agents/new"><Sparkles size={16} /> Create agent</Link>
        </div>
      </div>

      {!setupComplete && (
        <section className="setup-progress-card">
          <div className="setup-progress-copy">
            <span><Sparkles size={17} /> {initialAgent ? `Finish setting up ${initialAgent.name}` : "Create your first agent"}</span>
            <h2>{initialAgent ? `${initialAgent.name} is` : "Your workspace is"} {Math.round((setupItems.filter((item) => item.done).length / setupItems.length) * 100)}% ready</h2>
            <p>{initialAgent ? "Complete this agent’s remaining requirements before activating it." : "Create the first agent for your workspace."}</p>
            <Link href={setupHref}>{initialAgent ? "Continue agent setup" : "Start onboarding"} <ArrowRight size={16} /></Link>
          </div>
          <div className="setup-progress-list">
            {setupItems.map((item) => <div className={item.done ? "done" : ""} key={item.label}><span>{item.done ? <Check size={14} /> : null}</span>{item.label}</div>)}
          </div>
        </section>
      )}

      <section className="overview-metrics">
        <MetricCard icon={PhoneCall} label="Total calls" value={String(data.analytics.totalCalls)} detail="All recorded calls" />
        <MetricCard icon={CalendarCheck} label="Bookings" value={String(data.analytics.bookingCalls)} detail="Created from calls" />
        <MetricCard icon={Clock3} label="Avg. duration" value={formatDuration(data.analytics.averageDurationSeconds)} detail="Across completed calls" />
        <MetricCard icon={WalletCards} label="Minutes remaining" value={String(data.usage.remainingMinutes)} detail={`${data.usage.usagePercent}% of plan used`} warning={data.usage.usagePercent >= 80} />
      </section>

      <section className="dashboard-primary-grid">
        <article className="console-card call-volume-card">
          <div className="console-card-head">
            <div><span>Call activity</span><h2>Last 14 days</h2></div>
            <Link href="/analytics">View analytics <ChevronRight size={15} /></Link>
          </div>
          <CallVolumeChart daily={data.daily} />
        </article>
        <article className="console-card agent-status-card">
          <div className="console-card-head">
            <div><span>Agent status</span><h2>{activeAgent?.name ?? "No agent yet"}</h2></div>
            {activeAgent && <i className={activeAgent.active ? "live" : "draft"}>{activeAgent.active ? "Live" : "Draft"}</i>}
          </div>
          {activeAgent ? (
            <>
              <div className="agent-avatar-large">{activeAgent.name.slice(0, 1).toUpperCase()}</div>
              <div className="agent-detail-row"><PhoneCall size={17} /><span>Phone number</span><strong>{activeAgent.twilioPhoneNumber ?? "Not assigned"}</strong></div>
              <div className="agent-detail-row"><Headphones size={17} /><span>Languages</span><strong>{activeAgent.supportedLanguages.join(", ")}</strong></div>
              <Link className="card-text-link" href={`/agents/${activeAgent.id}`}>Open agent studio <ArrowRight size={15} /></Link>
            </>
          ) : (
            <EmptyCompact icon={Bot} title="Create your first agent" detail="Build a multilingual agent for calls and bookings." href="/onboarding" action="Start setup" />
          )}
        </article>
      </section>

      <section className="dashboard-secondary-grid">
        <article className="console-card recent-calls-card">
          <div className="console-card-head">
            <div><span>Operations</span><h2>Recent calls</h2></div>
            <Link href="/calls">View all <ChevronRight size={15} /></Link>
          </div>
          {data.calls.length ? <RecentCalls calls={data.calls.slice(0, 5)} /> : <EmptyCompact icon={PhoneCall} title="No calls yet" detail="Calls will appear here once an agent receives traffic." />}
        </article>
        <article className="console-card upcoming-card">
          <div className="console-card-head">
            <div><span>Calendar</span><h2>Upcoming bookings</h2></div>
            <Link href="/bookings">View all <ChevronRight size={15} /></Link>
          </div>
          {upcomingBookings.length ? (
            <div className="booking-list">
              {upcomingBookings.map((booking) => (
                <div key={booking.id}>
                  <span className="booking-date"><strong>{new Date(booking.appointmentAt).getDate()}</strong><small>{new Intl.DateTimeFormat("en", { month: "short" }).format(new Date(booking.appointmentAt))}</small></span>
                  <div><strong>{booking.callerName}</strong><small>{booking.serviceType} · {formatDate(booking.appointmentAt)}</small></div>
                  <ChevronRight size={16} />
                </div>
              ))}
            </div>
          ) : <EmptyCompact icon={CalendarCheck} title="No upcoming bookings" detail="New appointments will be listed here." />}
        </article>
      </section>
    </>
  );
}

function MetricCard({ icon: Icon, label, value, detail, warning = false }: {
  icon: typeof PhoneCall;
  label: string;
  value: string;
  detail: string;
  warning?: boolean;
}) {
  return <article className={`metric-card ${warning ? "warning" : ""}`}><span><Icon size={19} /></span><div><small>{label}</small><strong>{value}</strong><p>{detail}</p></div></article>;
}

function CallVolumeChart({ daily }: { daily: DashboardData["daily"] }) {
  const values = daily.length ? daily.map((item) => item.callCount) : Array.from({ length: 14 }, () => 0);
  const maximum = Math.max(...values, 1);
  return (
    <div className="volume-chart" aria-label="Calls per day">
      {values.map((value, index) => (
        <div className="volume-column" key={`${daily[index]?.date ?? "empty"}-${index}`}>
          <span title={`${value} calls`} style={{ height: `${Math.max(6, (value / maximum) * 100)}%` }} />
          <small>{index % 3 === 0 ? new Intl.DateTimeFormat("en", { weekday: "short" }).format(new Date(daily[index]?.date ?? Date.now() - (13 - index) * 86400000)) : ""}</small>
        </div>
      ))}
    </div>
  );
}

function RecentCalls({ calls }: { calls: Call[] }) {
  return (
    <div className="recent-call-list">
      {calls.map((call) => (
        <Link href={`/calls/${call.id}`} key={call.id}>
          <span className="caller-avatar"><UsersRound size={16} /></span>
          <div><strong>{call.callerNumber || "Unknown caller"}</strong><small>{formatDate(call.startedAt)} · {call.languageDetected?.toUpperCase() ?? "Detecting"}</small></div>
          <span className={`outcome-pill ${call.outcome}`}>{prettyOutcome(call.outcome)}</span>
          <strong className="call-duration">{formatDuration(call.durationSeconds ?? 0)}</strong>
          <ChevronRight size={15} />
        </Link>
      ))}
    </div>
  );
}

function EmptyCompact({ icon: Icon, title, detail, href, action }: {
  icon: typeof Bot;
  title: string;
  detail: string;
  href?: string;
  action?: string;
}) {
  return <div className="empty-compact"><span><Icon size={21} /></span><strong>{title}</strong><p>{detail}</p>{href && action && <Link href={href}>{action} <ArrowRight size={14} /></Link>}</div>;
}

function DashboardSkeleton() {
  return <div className="dashboard-skeleton"><div /><div className="skeleton-row">{[1, 2, 3, 4].map((item) => <span key={item} />)}</div><section><div /><div /></section><section><div /><div /></section></div>;
}

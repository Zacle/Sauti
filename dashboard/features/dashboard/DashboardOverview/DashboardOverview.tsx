"use client";

import styles from "./DashboardOverview.module.css";
import Link from "next/link";
import { useMemo } from "react";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";
import {
  ArrowDown,
  ArrowRight,
  ArrowUp,
  CalendarCheck,
  Check,
  ChevronRight,
  CircleAlert,
  Clock3,
  CreditCard,
  Headphones,
  LoaderCircle,
  PhoneCall,
  RefreshCw,
  Sparkles,
  TrendingUp,
} from "lucide-react";
import type { Call, DashboardData } from "@/types/api";
import { useAuth } from "@/hooks/useAuth";
import { useDashboard } from "@/hooks/useDashboard";
import { formatDate, formatDuration } from "@/lib/utils";

const numberFormat = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });

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

  if (loading && !data) return <DashboardSkeleton />;

  if (error && !data) {
    return (
      <section className={styles["error-state"]}>
        <span><CircleAlert size={24} /></span><h1>We couldn&apos;t load your workspace</h1><p>{error}</p>
        <button onClick={() => void refresh()}><RefreshCw size={17} /> Try again</button>
      </section>
    );
  }

  if (!data) return null;
  const tenant = session?.tenant;
  const initialAgent = data.agents.find((agent) => agent.active) ?? data.agents.at(-1);
  const initialReadiness = data.readiness.find((item) => item.agentId === initialAgent?.id);
  const setupItems = initialAgent && initialReadiness
    ? [
        { label: "Business details completed", done: initialReadiness.businessDetailsComplete },
        { label: "Calendar connected", done: initialReadiness.calendarConfigured },
        { label: "Live channel enabled", done: initialReadiness.channelConfigured },
        { label: "Agent activated", done: initialReadiness.active },
      ]
    : [
        { label: "Email verified", done: data.onboarding.emailVerified },
        { label: "Create your first agent", done: false },
      ];
  const setupProgress = Math.round((setupItems.filter((item) => item.done).length / setupItems.length) * 100);
  const setupHref = initialAgent ? `/agents/${initialAgent.id}` : "/agents/new";

  return (
    <div className={styles["dashboard-page"]}>
      <header className={styles.hero}>
        <div><h1>Good day, {tenant?.businessName ?? "Sauti"} <span>👋</span></h1><p>Monitor your agents, calls, bookings, and usage from one place.</p></div>
        <div className={styles["hero-wave"]} aria-hidden="true"><BrandLogo className={styles["hero-logo"]} size={67} /></div>
        <button className={styles.refresh} onClick={() => void refresh()} disabled={loading} aria-label="Refresh dashboard">
          {loading ? <LoaderCircle className="spin" size={16} /> : <RefreshCw size={16} />}
        </button>
      </header>

      <section className={styles["setup-card"]}>
        <div className={styles["progress-ring"]} style={{ background: `conic-gradient(#20e1d1 ${setupProgress}%, rgba(112,145,173,.18) 0)` }}>
          <div><strong>{setupProgress}%</strong><small>Setup progress</small></div>
        </div>
        <div className={styles["setup-copy"]}>
          <span><Sparkles size={14} /> {setupProgress === 100 ? "Workspace ready" : "Complete your setup"}</span>
          <h2>{setupProgress === 100 ? "You’re ready to serve callers" : initialAgent ? "You’re almost ready to launch" : "Create your first voice agent"}</h2>
          <p>{setupProgress === 100 ? "Your active channels and agents are ready for customer conversations." : initialAgent ? "Finish the remaining steps to activate your agent and start delivering value." : "Start with a focused template or a blank agent, then configure the business, voice, tools, and call behavior."}</p>
          <Link href={setupHref}>{setupProgress === 100 ? "Open agent studio" : initialAgent ? "Continue setup" : "Create an agent"} <ArrowRight size={15} /></Link>
        </div>
        <div className={styles["setup-list"]}>
          {setupItems.map((item) => <div className={item.done ? styles.done : ""} key={item.label}><span>{item.done && <Check size={12} />}</span>{item.label}</div>)}
        </div>
        <div className={styles["setup-visual"]} aria-hidden="true">
          <span><CalendarCheck size={25} /></span><i /><span><Headphones size={25} /></span>
        </div>
      </section>

      <section className={styles.metrics} aria-label="Workspace metrics">
        <MetricCard tone="teal" icon={<PhoneCall size={20} />} label="Total calls" value={numberFormat.format(data.analytics.totalCalls)} delta={data.analytics.totalCallsDelta.percentChange} />
        <MetricCard tone="violet" icon={<CalendarCheck size={20} />} label="Bookings" value={numberFormat.format(data.analytics.bookingCalls)} delta={data.analytics.bookingCallsDelta.percentChange} />
        <MetricCard tone="blue" icon={<Clock3 size={20} />} label="Avg. call duration" value={formatDuration(data.analytics.averageDurationSeconds)} delta={data.analytics.averageDurationSecondsDelta.percentChange} inverse />
        <MetricCard tone="amber" icon={<TrendingUp size={20} />} label="Answer rate" value={`${data.analytics.connectRate}%`} delta={data.analytics.connectRateDelta.percentChange} />
        <MetricCard tone="cyan" icon={<CreditCard size={20} />} label="Minutes remaining" value={numberFormat.format(data.usage.remainingMinutes)} detail={`${data.usage.usagePercent}% of plan used`} />
      </section>

      <section className={styles["primary-grid"]}>
        <article className={`${styles.panel} ${styles["activity-chart"]}`}>
          <PanelHead title="Call activity" action="View analytics" href="/analytics" note="Last 14 days" />
          <CallActivityChart daily={data.daily} />
        </article>
        <article className={`${styles.panel} ${styles.operations}`}>
          <PanelHead title="Operations summary" action="View all" href="/calls" note="Recent activity" />
          {data.calls.length ? <RecentOperations calls={data.calls.slice(0, 5)} /> : <CompactEmpty icon={<PhoneCall size={21} />} title="No call activity yet" />}
        </article>
      </section>

      <section className={styles["secondary-grid"]}>
        <article className={`${styles.panel} ${styles.funnel}`}>
          <PanelHead title="Bookings funnel" note="All time" />
          <BookingFunnel data={data} />
        </article>
        <article className={`${styles.panel} ${styles.appointments}`}>
          <PanelHead title="Appointments by day" note="Current data" />
          <AppointmentBars data={data} />
        </article>
        <article className={`${styles.panel} ${styles.upcoming}`}>
          <PanelHead title="Upcoming bookings" action="View calendar" href="/bookings" />
          {upcomingBookings.length ? <div className={styles["booking-list"]}>{upcomingBookings.map((booking) => (
            <div key={booking.id}>
              <span><strong>{new Date(booking.appointmentAt).getDate()}</strong><small>{new Intl.DateTimeFormat("en", { month: "short" }).format(new Date(booking.appointmentAt))}</small></span>
              <div><strong>{booking.serviceType}</strong><small>{booking.callerName} · {formatDate(booking.appointmentAt)}</small></div>
              <ChevronRight size={14} />
            </div>
          ))}</div> : <CompactEmpty icon={<CalendarCheck size={21} />} title="No upcoming bookings" />}
        </article>
        <article className={`${styles.panel} ${styles.usage}`}>
          <PanelHead title="Plan usage" note={titleCase(data.usage.plan)} />
          <small>Minutes used</small><div><strong>{numberFormat.format(data.usage.minutesUsedThisCycle)}</strong><span>/ {numberFormat.format(data.usage.monthlyMinutesLimit)} mins</span><em>{data.usage.usagePercent}%</em></div>
          <progress max="100" value={data.usage.usagePercent} />
          <p><Clock3 size={13} /> Usage resets with your billing cycle</p>
          <Link href="/billing">Manage plan <ArrowRight size={13} /></Link>
          <footer><span>System status</span><strong><i /> All systems operational</strong></footer>
        </article>
      </section>
    </div>
  );
}

function MetricCard({ icon, label, value, delta, detail, tone, inverse = false }: { icon: React.ReactNode; label: string; value: string; delta?: number; detail?: string; tone: string; inverse?: boolean }) {
  const positive = (delta ?? 0) >= 0;
  const good = inverse ? !positive : positive;
  return <article className={styles["metric-card"]}><span className={styles[`tone-${tone}`]}>{icon}</span><div><small>{label}</small><strong>{value}</strong>{detail ? <p>{detail}</p> : <p className={good ? styles.good : styles.bad}>{positive ? <ArrowUp size={12} /> : <ArrowDown size={12} />}{Math.abs(delta ?? 0)}% <em>vs previous period</em></p>}</div></article>;
}

function PanelHead({ title, note, action, href }: { title: string; note?: string; action?: string; href?: string }) {
  return <div className={styles["panel-head"]}><div><strong>{title}</strong>{note && <small>{note}</small>}</div>{action && href && <Link href={href}>{action} <ArrowRight size={13} /></Link>}</div>;
}

function CallActivityChart({ daily }: { daily: DashboardData["daily"] }) {
  const values = daily.length ? daily : Array.from({ length: 14 }, (_, index) => ({ date: new Date(Date.now() - (13 - index) * 86400000).toISOString(), callCount: 0 }));
  const max = Math.max(...values.map((item) => item.callCount), 1);
  const points = values.map((item, index) => `${index * (680 / Math.max(1, values.length - 1))},${150 - (item.callCount / max) * 118}`).join(" ");
  return <div className={styles["line-chart"]}>
    <svg viewBox="0 0 680 174" preserveAspectRatio="none" role="img" aria-label="Daily calls">
      <defs><linearGradient id="dashboardCallFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#22e2d3" stopOpacity=".36"/><stop offset="1" stopColor="#22e2d3" stopOpacity="0"/></linearGradient></defs>
      <g className={styles.gridlines}><line x1="0" x2="680" y1="32" y2="32"/><line x1="0" x2="680" y1="71" y2="71"/><line x1="0" x2="680" y1="110" y2="110"/><line x1="0" x2="680" y1="150" y2="150"/></g>
      <polygon points={`0,160 ${points} 680,160`} fill="url(#dashboardCallFill)" />
      <polyline points={points} fill="none" stroke="#23e1d3" strokeWidth="2.5" vectorEffect="non-scaling-stroke" strokeLinecap="round" strokeLinejoin="round" />
      {values.map((item, index) => <circle key={item.date} cx={index * (680 / Math.max(1, values.length - 1))} cy={150 - (item.callCount / max) * 118} r="3.5" fill="#5ffff1"><title>{item.callCount} calls</title></circle>)}
    </svg>
    <div>{values.map((item, index) => <span key={item.date}>{index % 2 === 0 ? new Intl.DateTimeFormat("en", { month: "short", day: "2-digit" }).format(new Date(item.date)) : ""}</span>)}</div>
  </div>;
}

function RecentOperations({ calls }: { calls: Call[] }) {
  return <div className={styles["operations-list"]}>{calls.map((call) => {
    const completed = ["completed", "booking", "booked", "transferred", "faq_answered"].includes(call.outcome);
    return <Link href={`/calls/${call.id}`} key={call.id}>
      <span className={completed ? styles.success : styles.warning}><PhoneCall size={14} /></span>
      <div><strong>{call.outcome === "booking" || call.outcome === "booked" ? "Booking created" : prettyOutcome(call.outcome)}</strong><small>{call.callerNumber || "Unknown caller"}</small></div>
      <time>{formatDate(call.startedAt)}</time><em className={completed ? styles.completed : styles.missed}>{completed ? "Completed" : prettyOutcome(call.outcome)}</em>
    </Link>;
  })}</div>;
}

function BookingFunnel({ data }: { data: DashboardData }) {
  const rows = [
    ["Calls handled", data.analytics.attemptedCalls],
    ["Connected", data.analytics.connectedCalls],
    ["Bookings made", data.analytics.bookingCalls],
    ["Completed", data.analytics.completedCalls],
  ] as const;
  const max = Math.max(data.analytics.attemptedCalls, 1);
  return <div className={styles["funnel-list"]}>{rows.map(([label, value]) => <div key={label}><span>{label}</span><i><b style={{ width: `${Math.max(5, value / max * 100)}%` }} /></i><strong>{numberFormat.format(value)}</strong><small>{Math.round(value / max * 1000) / 10}%</small></div>)}<footer><span>Conversion rate</span><strong>{data.analytics.attemptedCalls ? Math.round(data.analytics.bookingCalls / data.analytics.attemptedCalls * 1000) / 10 : 0}%</strong></footer></div>;
}

function AppointmentBars({ data }: { data: DashboardData }) {
  const counts = Array.from({ length: 7 }, () => 0);
  data.bookings.forEach((booking) => { counts[(new Date(booking.appointmentAt).getDay() + 6) % 7] += 1; });
  const max = Math.max(...counts, 1);
  return <div className={styles["bar-chart"]}>{counts.map((value, index) => <div key={index}><span><i style={{ height: `${Math.max(4, value / max * 100)}%` }} title={`${value} bookings`} /></span><small>{["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][index]}</small></div>)}</div>;
}

function CompactEmpty({ icon, title }: { icon: React.ReactNode; title: string }) { return <div className={styles["compact-empty"]}><span>{icon}</span><p>{title}</p></div>; }
function titleCase(value: string) { return value ? value.charAt(0).toUpperCase() + value.slice(1) : "Plan"; }
function DashboardSkeleton() { return <div className={styles.skeleton}><div/><section>{[1,2,3,4,5].map((item) => <span key={item}/>)}</section><main><div/><div/></main></div>; }

"use client";

import { useEffect, useMemo, useState } from "react";
import {
  Activity,
  BarChart3,
  CalendarClock,
  CheckCircle2,
  Clock3,
  Globe2,
  LoaderCircle,
  PhoneCall,
  RefreshCw,
  Route,
  TrendingUp,
  UsersRound,
} from "lucide-react";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Funnel,
  FunnelChart,
  LabelList,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { listAgents } from "@/lib/api/agents";
import { loadAnalytics } from "@/lib/api/analytics";
import type {
  Agent,
  AnalyticsAgentSummary,
  AnalyticsData,
  AnalyticsDelta,
  AnalyticsOutcomeByDay,
} from "@/types/api";
import { formatDuration } from "@/lib/utils";
import { DarkSelect } from "@/components/DarkSelect/DarkSelect";
import { analyticsRange, analyticsRangeOptions, type AnalyticsRangeKey } from "../domain/date-ranges";
import styles from "./AnalyticsPage.module.css";

type Tab = "overview" | "outcomes" | "latency";

const COLORS = {
  blue: "#1688ff",
  sky: "#18c7f4",
  green: "#17d8c5",
  amber: "#f59e0b",
  red: "#ef4444",
  violet: "#8b5cf6",
  slate: "#64748b",
};

const PIE_COLORS = [COLORS.blue, COLORS.green, COLORS.violet, COLORS.amber, COLORS.sky, COLORS.red, COLORS.slate];

export function AnalyticsPage() {
  const [rangeKey, setRangeKey] = useState<AnalyticsRangeKey>("30d");
  const [agentId, setAgentId] = useState("");
  const [tab, setTab] = useState<Tab>("overview");
  const [agents, setAgents] = useState<Agent[]>([]);
  const [data, setData] = useState<AnalyticsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  const range = useMemo(() => analyticsRange(rangeKey), [rangeKey]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    Promise.all([loadAnalytics({ from: range.from, to: range.to, agentId: agentId || undefined }), listAgents()])
      .then(([analytics, agentItems]) => {
        if (cancelled) return;
        setData(analytics);
        setAgents(agentItems);
      })
      .catch((caught) => {
        if (!cancelled) setError(caught instanceof Error ? caught.message : "Unable to load analytics.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [range.from, range.to, agentId, refreshKey]);

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <span>Insights</span>
          <h1>Analytics</h1>
          <p>Track call volume, connect rate, outcomes, language demand, channels, and agent performance.</p>
        </div>
        <button className={styles.refresh} onClick={() => setRefreshKey((current) => current + 1)} disabled={loading} type="button">
          {loading ? <LoaderCircle className="spin" size={16} /> : <RefreshCw size={16} />}
          Refresh
        </button>
      </header>

      <section className={styles.filters}>
        <div className={styles.rangeButtons}>
          {analyticsRangeOptions.map((option) => (
            <button className={rangeKey === option.key ? styles.activeFilter : ""} key={option.key} onClick={() => setRangeKey(option.key)} type="button">
              {option.label}
            </button>
          ))}
        </div>
        <div className={styles.selectField}>
          <span className={styles.selectLabel}>Agent scope</span>
          <DarkSelect ariaLabel="Filter analytics by agent" icon={<UsersRound size={18} />} triggerClassName={styles.agentSelectTrigger} value={agentId || "all"}
            onValueChange={(value) => setAgentId(value === "all" ? "" : value)}
            options={[{ value: "all", label: "All agents" }, ...agents.map((agent) => ({ value: agent.id, label: agent.name }))]} />
          <small className={styles.selectHint}>{agentId ? "Focused agent view" : `${agents.length || "All"} agents included`}</small>
        </div>
      </section>

      {error && <div className={styles.error}>{error}</div>}
      {loading && !data ? (
        <div className={styles.loading}><LoaderCircle className="spin" size={22} /> Loading analytics...</div>
      ) : data ? (
        <>
          <KpiGrid data={data} />
          <nav className={styles.tabs} aria-label="Analytics sections">
            <button className={tab === "overview" ? styles.activeTab : ""} onClick={() => setTab("overview")} type="button">Overview</button>
            <button className={tab === "outcomes" ? styles.activeTab : ""} onClick={() => setTab("outcomes")} type="button">Outcomes</button>
            <button className={tab === "latency" ? styles.activeTab : ""} onClick={() => setTab("latency")} type="button">Latency</button>
          </nav>
          {tab === "overview" && <OverviewTab data={data} />}
          {tab === "outcomes" && <OutcomesTab data={data} />}
          {tab === "latency" && <LatencyTab data={data} />}
        </>
      ) : null}
    </main>
  );
}

function KpiGrid({ data }: { data: AnalyticsData }) {
  return (
    <section className={styles.kpis}>
      <Kpi icon={PhoneCall} label="Total calls" value={number(data.summary.totalCalls)} delta={data.summary.totalCallsDelta} />
      <Kpi icon={TrendingUp} label="Connect rate" value={`${formatNumber(data.summary.connectRate, 1)}%`} delta={data.summary.connectRateDelta} />
      <Kpi icon={Clock3} label="Total duration" value={formatDuration(data.summary.totalDurationSeconds)} delta={data.summary.totalDurationSecondsDelta} />
      <Kpi icon={Activity} label="Avg duration" value={formatDuration(data.summary.averageDurationSeconds)} delta={data.summary.averageDurationSecondsDelta} />
      <Kpi icon={CheckCircle2} label="Bookings" value={number(data.summary.bookingCalls)} delta={data.summary.bookingCallsDelta} />
      <Kpi icon={Route} label="Transfers" value={number(data.summary.transferredCalls)} delta={data.summary.transferredCallsDelta} />
    </section>
  );
}

function OverviewTab({ data }: { data: AnalyticsData }) {
  return (
    <section className={styles.grid}>
      <Card title="Call funnel" eyebrow="Attempted → connected → completed"><CallFunnel data={data} /></Card>
      <Card title="Connect rate over time" eyebrow="Daily connected calls"><ConnectRateChart data={data} /></Card>
      <Card title="Outcomes over time" eyebrow="Daily outcome mix" wide><OutcomeStackedChart items={data.outcomesByDay} /></Card>
      <Card title="Language breakdown" eyebrow="Detected caller language"><DonutChart items={data.languages.map((item) => ({ name: item.language.toUpperCase(), value: item.callCount }))} empty="No language data yet." /></Card>
      <Card title="Channel breakdown" eyebrow="Voice, web, WhatsApp"><HorizontalBarChart items={data.channels.map((item) => ({ name: humanize(item.channel), value: item.totalCalls }))} empty="No channel data yet." /></Card>
      <Card title="Sentiment trend" eyebrow="Positive +1, neutral 0, negative -1"><SentimentTrend data={data.sentimentByDay} /></Card>
      <Card title="Top intents" eyebrow="Most common caller needs"><HorizontalBarChart items={data.topIntents.map((item) => ({ name: humanize(item.intent), value: item.callCount }))} empty="No analysed intents yet." /></Card>
      <Card title="By agent" eyebrow="Sortable summary" wide><AgentTable agents={data.agents} /></Card>
    </section>
  );
}

function OutcomesTab({ data }: { data: AnalyticsData }) {
  return (
    <section className={styles.grid}>
      <Card title="After-hours volume" eyebrow="Calls outside configured hours"><AfterHoursChart data={data} /></Card>
      <Card title="After-hours behavior" eyebrow="Agent configured behavior">
        <HorizontalBarChart items={data.afterHours.behaviors.map((item) => ({ name: humanize(item.behavior), value: item.callCount }))} empty="No after-hours calls yet." />
      </Card>
      <Card title="Integration events" eyebrow="SMS, WhatsApp, CRM, M-Pesa" wide>
        <IntegrationEventsChart data={data} />
      </Card>
      <Card title="Outcome totals" eyebrow="Selected period" wide><OutcomeTotalsChart items={data.outcomesByDay} /></Card>
    </section>
  );
}

function LatencyTab({ data }: { data: AnalyticsData }) {
  const items = [
    { name: "STT", value: Math.round(data.summary.avgSttLatencyMs), fill: COLORS.sky },
    { name: "LLM", value: Math.round(data.summary.avgLlmLatencyMs), fill: COLORS.blue },
    { name: "TTS", value: Math.round(data.summary.avgTtsLatencyMs), fill: COLORS.green },
  ];
  return (
    <section className={styles.grid}>
      <Card title="Pipeline latency" eyebrow="Average per turn" wide>
        <div className={styles.latencyGrid}>
          <Kpi icon={Globe2} label="STT" value={`${items[0].value} ms`} />
          <Kpi icon={BarChart3} label="LLM" value={`${items[1].value} ms`} />
          <Kpi icon={CalendarClock} label="TTS" value={`${items[2].value} ms`} />
        </div>
        <div className={styles.chartTall}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={items} margin={{ top: 18, right: 18, bottom: 8, left: 8 }}>
              <CartesianGrid stroke="rgba(115, 159, 198, .14)" strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="name" axisLine={false} tickLine={false} />
              <YAxis axisLine={false} tickLine={false} width={46} />
              <Tooltip content={<ChartTooltip />} />
              <Bar dataKey="value" radius={[12, 12, 0, 0]}>
                {items.map((item) => <Cell key={item.name} fill={item.fill} />)}
              <LabelList dataKey="value" position="top" fill="#a9bdd0" formatter={(value: unknown) => `${value} ms`} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Card>
    </section>
  );
}

function Card({ title, eyebrow, children, wide = false }: { title: string; eyebrow: string; children: React.ReactNode; wide?: boolean }) {
  return <article className={`${styles.card} ${wide ? styles.wide : ""}`}><div className={styles.cardHead}><span>{eyebrow}</span><h2>{title}</h2></div>{children}</article>;
}

function Kpi({ icon: Icon, label, value, delta }: { icon: typeof PhoneCall; label: string; value: string; delta?: AnalyticsDelta }) {
  return <article className={styles.kpi}><span><Icon size={18} /></span><div><small>{label}</small><strong>{value}</strong>{delta && <em className={delta.percentChange >= 0 ? styles.good : styles.bad}>{delta.percentChange >= 0 ? "↑" : "↓"} {Math.abs(delta.percentChange).toFixed(0)}% vs previous</em>}</div></article>;
}

function CallFunnel({ data }: { data: AnalyticsData }) {
  const items = [
    { name: "Attempted", value: data.funnel.attempted, fill: COLORS.blue },
    { name: "Connected", value: data.funnel.connected, fill: COLORS.green },
    { name: "Completed", value: data.funnel.completed, fill: COLORS.violet },
  ];
  if (!items.some((item) => item.value > 0)) {
    return (
      <div className={styles.funnelEmpty}>
        <Route size={28} />
        <strong>No call funnel yet</strong>
        <p>The funnel will show how calls move from attempted to connected to completed once your agents start receiving calls.</p>
      </div>
    );
  }
  return (
    <>
      <div className={styles.chart}>
        <ResponsiveContainer width="100%" height="100%">
          <FunnelChart margin={{ top: 10, right: 20, bottom: 10, left: 20 }}>
            <Tooltip content={<ChartTooltip />} />
            <Funnel dataKey="value" data={items} isAnimationActive>
              <LabelList position="right" fill="#d8e8f6" stroke="none" dataKey="name" />
            </Funnel>
          </FunnelChart>
        </ResponsiveContainer>
      </div>
      <Legend items={items.map((item) => ({ label: item.name, value: item.value, color: item.fill }))} />
    </>
  );
}

function ConnectRateChart({ data }: { data: AnalyticsData }) {
  const items = data.connectRateByDay.map((item) => ({ ...item, label: dayLabel(item.date) }));
  if (!items.some((item) => item.attempts > 0)) return <Empty message="No connect-rate data yet." />;
  return (
    <div className={styles.chart}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={items} margin={{ top: 12, right: 12, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id="connectRate" x1="0" x2="0" y1="0" y2="1">
              <stop offset="5%" stopColor={COLORS.blue} stopOpacity={0.32} />
              <stop offset="95%" stopColor={COLORS.blue} stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="rgba(115, 159, 198, .14)" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" axisLine={false} tickLine={false} minTickGap={22} />
          <YAxis domain={[0, 100]} axisLine={false} tickLine={false} width={36} tickFormatter={(value) => `${value}%`} />
          <Tooltip content={<ChartTooltip suffix="%" />} />
          <Area type="monotone" dataKey="rate" stroke={COLORS.blue} fill="url(#connectRate)" strokeWidth={3} />
          <Line type="monotone" dataKey={() => 70} stroke={COLORS.green} strokeDasharray="5 5" dot={false} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

function OutcomeStackedChart({ items }: { items: AnalyticsOutcomeByDay[] }) {
  const data = items.map((item) => ({ ...item, label: dayLabel(item.date) }));
  if (!data.some((item) => item.completed + item.transferred + item.voicemail + item.noAnswer + item.busy + item.failed > 0)) return <Empty message="No outcome data yet." />;
  return (
    <>
      <div className={styles.chartTall}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 14, right: 18, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="rgba(115, 159, 198, .14)" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="label" axisLine={false} tickLine={false} minTickGap={18} />
            <YAxis axisLine={false} tickLine={false} width={36} allowDecimals={false} />
            <Tooltip content={<ChartTooltip />} />
            <Bar dataKey="completed" stackId="a" fill={COLORS.blue} radius={[0, 0, 6, 6]} />
            <Bar dataKey="transferred" stackId="a" fill={COLORS.violet} />
            <Bar dataKey="voicemail" stackId="a" fill={COLORS.amber} />
            <Bar dataKey="noAnswer" stackId="a" fill={COLORS.slate} />
            <Bar dataKey="busy" stackId="a" fill={COLORS.sky} />
            <Bar dataKey="failed" stackId="a" fill={COLORS.red} radius={[6, 6, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <Legend items={[
        { label: "Completed", value: sum(items, "completed"), color: COLORS.blue },
        { label: "Transferred", value: sum(items, "transferred"), color: COLORS.violet },
        { label: "Voicemail", value: sum(items, "voicemail"), color: COLORS.amber },
        { label: "Missed/failed", value: sum(items, "noAnswer") + sum(items, "busy") + sum(items, "failed"), color: COLORS.red },
      ]} />
    </>
  );
}

function DonutChart({ items, empty }: { items: Array<{ name: string; value: number }>; empty: string }) {
  if (!items.length) return <Empty message={empty} />;
  return (
    <div className={styles.donutWrap}>
      <div className={styles.chart}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Tooltip content={<ChartTooltip />} />
            <Pie data={items} dataKey="value" nameKey="name" innerRadius="58%" outerRadius="82%" paddingAngle={3}>
              {items.map((item, index) => <Cell key={item.name} fill={PIE_COLORS[index % PIE_COLORS.length]} />)}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
      </div>
      <Legend items={items.map((item, index) => ({ label: item.name, value: item.value, color: PIE_COLORS[index % PIE_COLORS.length] }))} />
    </div>
  );
}

function HorizontalBarChart({ items, empty }: { items: Array<{ name: string; value: number }>; empty: string }) {
  if (!items.length) return <Empty message={empty} />;
  const data = items.slice(0, 10);
  return (
    <div className={styles.chart}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 6, right: 22, bottom: 6, left: 8 }}>
          <CartesianGrid stroke="rgba(115, 159, 198, .14)" strokeDasharray="3 3" horizontal={false} />
          <XAxis type="number" hide />
          <YAxis type="category" dataKey="name" width={116} axisLine={false} tickLine={false} tick={{ fontSize: 12, fill: "#91a7bc" }} />
          <Tooltip content={<ChartTooltip />} />
          <Bar dataKey="value" fill={COLORS.blue} radius={[0, 10, 10, 0]}>
            <LabelList dataKey="value" position="right" fill="#d8e8f6" formatter={(value: unknown) => number(Number(value))} />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function SentimentTrend({ data }: { data: AnalyticsData["sentimentByDay"] }) {
  if (!data.some((item) => item.analysedCalls > 0)) return <Empty message="No sentiment analysis yet." />;
  const items = data.map((item) => ({ ...item, label: dayLabel(item.date) }));
  return (
    <div className={styles.chart}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={items} margin={{ top: 12, right: 16, bottom: 0, left: 0 }}>
          <CartesianGrid stroke="rgba(115, 159, 198, .14)" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" axisLine={false} tickLine={false} minTickGap={22} />
          <YAxis domain={[-1, 1]} axisLine={false} tickLine={false} width={32} />
          <Tooltip content={<ChartTooltip />} />
          <Line type="monotone" dataKey="averageScore" stroke={COLORS.green} strokeWidth={3} dot={false} />
          <Line type="monotone" dataKey="analysedCalls" stroke={COLORS.slate} strokeWidth={1.5} dot={false} yAxisId={0} opacity={0.25} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function AfterHoursChart({ data }: { data: AnalyticsData }) {
  const items = [
    { name: "After hours", value: data.afterHours.totalCalls, fill: COLORS.amber },
    { name: "Connected", value: data.afterHours.connectedCalls, fill: COLORS.blue },
    { name: "Completed", value: data.afterHours.completedCalls, fill: COLORS.green },
  ];
  if (!items[0].value) return <Empty message="No after-hours calls yet." />;
  return (
    <div className={styles.chart}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={items} margin={{ top: 18, right: 18, bottom: 8, left: 8 }}>
          <CartesianGrid stroke="rgba(115, 159, 198, .14)" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="name" axisLine={false} tickLine={false} />
          <YAxis axisLine={false} tickLine={false} width={36} allowDecimals={false} />
          <Tooltip content={<ChartTooltip />} />
          <Bar dataKey="value" radius={[12, 12, 0, 0]}>
            {items.map((item) => <Cell key={item.name} fill={item.fill} />)}
          <LabelList dataKey="value" position="top" fill="#a9bdd0" />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function IntegrationEventsChart({ data }: { data: AnalyticsData }) {
  if (!data.integrationEvents.length) return <Empty message="No integration delivery events yet." />;
  return (
    <div className={styles.chartTall}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data.integrationEvents.map((item) => ({ ...item, provider: humanize(item.provider) }))} margin={{ top: 14, right: 18, bottom: 0, left: 0 }}>
          <CartesianGrid stroke="rgba(115, 159, 198, .14)" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="provider" axisLine={false} tickLine={false} />
          <YAxis axisLine={false} tickLine={false} width={36} allowDecimals={false} />
          <Tooltip content={<ChartTooltip />} />
          <Bar dataKey="delivered" stackId="events" fill={COLORS.green} radius={[0, 0, 6, 6]} />
          <Bar dataKey="retrying" stackId="events" fill={COLORS.amber} />
          <Bar dataKey="failed" stackId="events" fill={COLORS.red} radius={[6, 6, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function AgentTable({ agents }: { agents: AnalyticsAgentSummary[] }) {
  if (!agents.length) return <Empty message="No agent calls yet." />;
  return <div className={styles.table}><table><thead><tr><th>Agent</th><th>Calls</th><th>Connect rate</th><th>Avg duration</th><th>Bookings</th></tr></thead><tbody>{agents.map((agent) => <tr key={agent.agentId}><td><UsersRound size={15} /> {agent.agentName}</td><td>{number(agent.totalCalls)}</td><td>{formatNumber(agent.connectRate, 1)}%</td><td>{formatDuration(agent.avgDurationSeconds)}</td><td>{number(agent.bookingCalls)}</td></tr>)}</tbody></table></div>;
}

function OutcomeTotalsChart({ items }: { items: AnalyticsOutcomeByDay[] }) {
  const totals = [
    { name: "Completed", value: sum(items, "completed") },
    { name: "Transferred", value: sum(items, "transferred") },
    { name: "Voicemail", value: sum(items, "voicemail") },
    { name: "No answer", value: sum(items, "noAnswer") },
    { name: "Busy", value: sum(items, "busy") },
    { name: "Failed", value: sum(items, "failed") },
    { name: "After hours", value: sum(items, "afterHours") },
  ].filter((item) => item.value > 0);
  return <HorizontalBarChart items={totals} empty="No outcomes yet." />;
}

function Legend({ items }: { items: Array<{ label: string; value: number; color: string }> }) {
  return (
    <div className={styles.legend}>
      {items.map((item) => (
        <span key={item.label}><i style={{ background: item.color }} /> {item.label} <strong>{number(item.value)}</strong></span>
      ))}
    </div>
  );
}

function Empty({ message }: { message: string }) {
  return <p className={styles.empty}>{message}</p>;
}

function ChartTooltip({ active, payload, label, suffix = "" }: {
  active?: boolean;
  payload?: Array<{ name?: string; value?: number | string; color?: string; payload?: { name?: string } }>;
  label?: string;
  suffix?: string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className={styles.tooltip}>
      {label && <strong>{label}</strong>}
      {payload.map((item) => (
        <span key={`${item.name}-${item.value}`}>
          <i style={{ background: item.color ?? COLORS.blue }} />
          {humanize(String(item.name ?? item.payload?.name ?? "Value"))}: {number(Number(item.value ?? 0))}{suffix}
        </span>
      ))}
    </div>
  );
}

function humanize(value: string) {
  return value.replaceAll("_", " ").replace(/([a-z])([A-Z])/g, "$1 $2").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function number(value: number) {
  return new Intl.NumberFormat("en").format(value);
}

function formatNumber(value: number, digits = 0) {
  return new Intl.NumberFormat("en", { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(value);
}

function dayLabel(value: string) {
  return new Intl.DateTimeFormat("en", { month: "short", day: "numeric" }).format(new Date(value));
}

function sum<T extends object>(items: T[], key: keyof T) {
  return items.reduce((total, item) => total + Number(item[key] ?? 0), 0);
}

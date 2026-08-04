"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Activity, AlertTriangle, Clock3, LoaderCircle, PhoneCall, RefreshCw, ServerCog } from "lucide-react";
import { Area, AreaChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { getAdminPlatformAnalytics } from "@/lib/api/admin";
import type { AdminPlatformAnalytics } from "@/types/api";
import styles from "./AdminAnalytics.module.css";

type Days = 7 | 30 | 90;

export function AdminAnalytics() {
  const [days, setDays] = useState<Days>(30);
  const [data, setData] = useState<AdminPlatformAnalytics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const load = useCallback(async (range: Days) => {
    setLoading(true); setError("");
    try { setData(await getAdminPlatformAnalytics(range)); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Unable to load platform analytics."); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(days); }, [days, load]);

  const totals = useMemo(() => ({
    calls: data?.activity.reduce((sum, day) => sum + day.calls, 0) ?? 0,
    duration: data?.activity.reduce((sum, day) => sum + day.durationSeconds, 0) ?? 0,
    failed: data?.activity.reduce((sum, day) => sum + day.failed, 0) ?? 0,
    attention: data?.providers.filter((provider) => provider.status === "attention").length ?? 0,
  }), [data]);

  return <div className={styles.page}>
    <header className={styles.heading}><div><span>PLATFORM INTELLIGENCE</span><h1>Analytics & provider health</h1><p>Stored operational evidence across Sauti. Refreshing this page never calls or charges an external provider.</p></div><button disabled={loading} onClick={() => void load(days)} type="button"><RefreshCw className={loading ? styles.spin : ""} size={16}/>Refresh</button></header>
    <nav className={styles.ranges} aria-label="Analytics range">{([7, 30, 90] as Days[]).map((range) => <button className={days === range ? styles.activeRange : ""} key={range} onClick={() => setDays(range)} type="button">{range} days</button>)}</nav>
    {error && <div className={styles.error} role="alert">{error}</div>}
    {loading && !data ? <div className={styles.loading}><LoaderCircle className={styles.spin} size={22}/>Loading platform evidence…</div> : data && <>
      <section className={styles.kpis}>
        <Kpi icon={PhoneCall} label="Calls" value={format(totals.calls)}/><Kpi icon={Clock3} label="Conversation time" value={duration(totals.duration)}/><Kpi icon={AlertTriangle} label="Failed calls" value={format(totals.failed)}/><Kpi icon={ServerCog} label="Providers needing attention" value={format(totals.attention)}/>
      </section>
      <section className={styles.grid}>
        <Card title="Platform activity" subtitle="Calls and completed outcomes by UTC day" wide><ActivityChart data={data}/></Card>
        <Card title="Provider cost" subtitle="Confirmed, estimated, and quoted ledger entries"><CostSummary data={data}/></Card>
        <Card title="Unpriced usage" subtitle="Usage awaiting a configured or confirmed cost"><UnpricedUsage data={data}/></Card>
        <Card title="Daily cost trend" subtitle="Net ledger amount; credits reduce the daily value" wide><CostChart data={data}/></Card>
        <Card title="Provider health" subtitle="Observed connection, delivery, and reconciliation evidence" wide><ProviderHealth data={data}/></Card>
      </section>
      <p className={styles.generated}>Generated {new Date(data.generatedAt).toLocaleString()} · This is operational evidence, not a live provider uptime check.</p>
    </>}
  </div>;
}

function Kpi({ icon: Icon, label, value }: { icon: typeof Activity; label: string; value: string }) {
  return <article className={styles.kpi}><span><Icon size={18}/></span><div><small>{label}</small><strong>{value}</strong></div></article>;
}
function Card({ title, subtitle, children, wide = false }: { title: string; subtitle: string; children: React.ReactNode; wide?: boolean }) {
  return <article className={`${styles.card} ${wide ? styles.wide : ""}`}><header><span>{subtitle}</span><h2>{title}</h2></header>{children}</article>;
}
function ActivityChart({ data }: { data: AdminPlatformAnalytics }) {
  const rows = data.activity.map((day) => ({ ...day, label: dateLabel(day.date) }));
  if (!rows.some((day) => day.calls)) return <Empty text="No calls were recorded in this period."/>;
  return <div className={styles.chart}><ResponsiveContainer width="100%" height="100%"><AreaChart data={rows}><defs><linearGradient id="adminCalls" x1="0" x2="0" y1="0" y2="1"><stop offset="5%" stopColor="#35ddd2" stopOpacity={.35}/><stop offset="95%" stopColor="#35ddd2" stopOpacity={.02}/></linearGradient></defs><CartesianGrid stroke="rgba(115,159,198,.13)" strokeDasharray="3 3" vertical={false}/><XAxis dataKey="label" axisLine={false} tickLine={false} minTickGap={24}/><YAxis axisLine={false} tickLine={false} allowDecimals={false}/><Tooltip contentStyle={tooltipStyle}/><Legend/><Area dataKey="calls" name="Calls" stroke="#35ddd2" fill="url(#adminCalls)" strokeWidth={2.5}/><Line dataKey="completed" name="Completed" stroke="#8d83ff" strokeWidth={2} dot={false}/><Line dataKey="failed" name="Failed" stroke="#ff766b" strokeWidth={2} dot={false}/></AreaChart></ResponsiveContainer></div>;
}
function CostSummary({ data }: { data: AdminPlatformAnalytics }) {
  if (!data.costTotals.length) return <Empty text="No priced provider usage was recorded in this period."/>;
  const currencies = data.costTotals.reduce<Map<string, AdminPlatformAnalytics["costTotals"]>>((groups, item) => {
    groups.set(item.currency, [...(groups.get(item.currency) ?? []), item]); return groups;
  }, new Map());
  return <div className={styles.costList}>{Array.from(currencies).map(([currency, entries]) => <section key={currency}><strong>{currency}</strong>{entries.map((entry) => <div key={`${entry.costBasis}:${entry.category}`}><span>{human(entry.category)} <small>{human(entry.costBasis)}</small></span><b>{money(entry.amount, currency)}</b></div>)}</section>)}</div>;
}
function UnpricedUsage({ data }: { data: AdminPlatformAnalytics }) {
  if (!data.unpricedUsage.length) return <Empty text="No unpriced usage is waiting for cost data."/>;
  return <div className={styles.unpriced}>{data.unpricedUsage.map((item) => <div key={`${item.category}:${item.unit}`}><span>{human(item.category)}<small>{human(item.unit)}</small></span><strong>{format(item.quantity)}</strong></div>)}</div>;
}
function CostChart({ data }: { data: AdminPlatformAnalytics }) {
  if (!data.dailyCosts.length) return <Empty text="No daily provider cost data is available yet."/>;
  const currencies = Array.from(new Set(data.dailyCosts.map((item) => item.currency)));
  const rows = data.activity.map((day) => ({ date: dateLabel(day.date), ...Object.fromEntries(currencies.map((currency) => [currency, data.dailyCosts.filter((cost) => cost.date === day.date && cost.currency === currency).reduce((sum, cost) => sum + Number(cost.amount), 0)])) }));
  const colors = ["#35ddd2", "#8d83ff", "#ffb45d", "#64a9ff"];
  return <div className={styles.chart}><ResponsiveContainer width="100%" height="100%"><LineChart data={rows}><CartesianGrid stroke="rgba(115,159,198,.13)" strokeDasharray="3 3" vertical={false}/><XAxis dataKey="date" axisLine={false} tickLine={false} minTickGap={24}/><YAxis axisLine={false} tickLine={false}/><Tooltip contentStyle={tooltipStyle}/><Legend/>{currencies.map((currency, index) => <Line dataKey={currency} key={currency} stroke={colors[index % colors.length]} strokeWidth={2.5} dot={false}/>)}</LineChart></ResponsiveContainer></div>;
}
function ProviderHealth({ data }: { data: AdminPlatformAnalytics }) {
  if (!data.providers.length) return <Empty text="No provider connection, delivery, or cost evidence exists in this period."/>;
  return <div className={styles.providers}>{data.providers.map((provider) => <article key={provider.provider}><div><span className={`${styles.healthDot} ${styles[provider.status] ?? ""}`}/><strong>{human(provider.provider)}</strong><em>{provider.status}</em></div><dl><div><dt>Connections</dt><dd>{provider.configuredConnections - provider.connectionErrors}/{provider.configuredConnections} healthy</dd></div><div><dt>Deliveries</dt><dd>{provider.delivered}/{provider.deliveryAttempts} delivered</dd></div><div><dt>Delivery issues</dt><dd>{provider.retryingDeliveries} retrying · {provider.failedDeliveries} failed</dd></div><div><dt>Cost evidence</dt><dd>{provider.reconciledCosts} confirmed · {provider.estimatedCosts} estimated</dd></div><div><dt>Cost issues</dt><dd>{provider.pendingCosts + provider.retryingCosts} waiting · {provider.unavailableCosts} unavailable</dd></div></dl>{provider.lastActivityAt && <time>Last recorded activity {new Date(provider.lastActivityAt).toLocaleString()}</time>}</article>)}</div>;
}
function Empty({ text }: { text: string }) { return <div className={styles.empty}>{text}</div>; }
function format(value: number) { return new Intl.NumberFormat("en", { maximumFractionDigits: 2 }).format(value); }
function money(value: number, currency: string) { return new Intl.NumberFormat("en", { style: "currency", currency }).format(value); }
function duration(seconds: number) { const hours = Math.floor(seconds / 3600); const minutes = Math.round((seconds % 3600) / 60); return hours ? `${hours}h ${minutes}m` : `${minutes}m`; }
function dateLabel(value: string) { return new Intl.DateTimeFormat("en", { month: "short", day: "numeric", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`)); }
function human(value: string) { return value.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase()); }
const tooltipStyle = { border: "1px solid #265166", borderRadius: 12, background: "#071925", color: "#dcebf2" };

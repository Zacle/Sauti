"use client";

import Link from "next/link";
import { useMemo } from "react";
import { ArrowRight, Check, CircleAlert, LoaderCircle, RefreshCw } from "lucide-react";
import type { Booking, Call, DashboardData } from "@/types/api";
import { useAuth } from "@/hooks/useAuth";
import { useDashboard } from "@/hooks/useDashboard";
import { cn, formatDuration } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, EmptyState, Skeleton, Table, TableBody, TableHead } from "@/components/ui/primitives";
import { PageHeader, SectionTitle, StatStrip } from "@/components/ui/console";

const dayFormat = new Intl.DateTimeFormat("en", { weekday: "short", month: "short", day: "numeric" });
const timeFormat = new Intl.DateTimeFormat("en", { hour: "numeric", minute: "2-digit" });

function greeting() {
  const hour = new Date().getHours();
  return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
}

export function outcomeMeta(outcome: string | null | undefined) {
  switch (outcome) {
    case "booking":
    case "booked":
      return { label: "Booked", dot: "bg-signal" };
    case "completed":
      return { label: "Completed", dot: "bg-fg-muted" };
    case "faq_answered":
      return { label: "Answered", dot: "bg-info" };
    case "transferred":
      return { label: "Transferred", dot: "bg-violet" };
    case "voicemail":
      return { label: "Voicemail", dot: "bg-warning" };
    case "no_answer":
      return { label: "No answer", dot: "bg-danger" };
    case "failed":
      return { label: "Failed", dot: "bg-danger" };
    default:
      return { label: outcome ? outcome.replaceAll("_", " ") : "In progress", dot: "bg-line-strong" };
  }
}

export function relativeTime(value: string) {
  const diff = Date.now() - new Date(value).getTime();
  const minutes = Math.round(diff / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 7) return `${days}d ago`;
  return dayFormat.format(new Date(value));
}

export function DashboardOverview() {
  const { data, error, loading, refresh } = useDashboard();
  const { session } = useAuth();

  const upcoming = useMemo(
    () => data?.bookings
      .filter((booking) => new Date(booking.appointmentAt).getTime() > Date.now() && booking.status !== "cancelled")
      .sort((a, b) => a.appointmentAt.localeCompare(b.appointmentAt))
      .slice(0, 6) ?? [],
    [data],
  );

  if (loading && !data) return <DashboardSkeleton />;

  if (error && !data) {
    return (
      <EmptyState
        icon={<CircleAlert />}
        title="We couldn't load your workspace"
        description={error}
        action={<Button size="xs" onClick={() => void refresh()}><RefreshCw /> Try again</Button>}
      />
    );
  }

  if (!data) return null;
  const { analytics } = data;
  const agentNames = new Map(data.agents.map((agent) => [agent.id, agent.name]));

  return (
    <div className="grid min-w-0 grid-cols-1 gap-8 [&>*]:min-w-0">
      <PageHeader
        title={greeting()}
        description={`Here's how ${session?.tenant?.businessName ?? "your workspace"} has been doing over the last two weeks.`}
        actions={
          <Button variant="secondary" size="xs" onClick={() => void refresh()} disabled={loading}>
            {loading ? <LoaderCircle className="animate-spin" /> : <RefreshCw />} Refresh
          </Button>
        }
      />

      <SetupChecklist data={data} />

      <StatStrip
        items={[
          { label: "Calls", value: analytics.totalCalls, change: analytics.totalCallsDelta.percentChange, hint: "vs last period" },
          { label: "Bookings", value: analytics.bookingCalls, change: analytics.bookingCallsDelta.percentChange, hint: "vs last period" },
          { label: "Answer rate", value: analytics.connectRate / 100, format: { style: "percent", maximumFractionDigits: 1 }, change: analytics.connectRateDelta.percentChange, hint: "vs last period" },
          { label: "Avg. call length", value: Math.round(analytics.averageDurationSeconds), suffix: "s", change: analytics.averageDurationSecondsDelta.percentChange, invertChange: true, hint: formatDuration(analytics.averageDurationSeconds) },
        ]}
      />

      <section className="grid grid-cols-1 gap-8 [&>*]:min-w-0 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="grid gap-3">
          <SectionTitle
            title="Calls per day"
            meta="Last 14 days"
            action={<TextLink href="/analytics">Analytics</TextLink>}
          />
          <Card className="px-4 pb-3 pt-5 sm:px-5">
            <DailyBars daily={data.daily} />
          </Card>
        </div>
        <div className="grid content-start gap-3">
          <SectionTitle title="Coming up" action={<TextLink href="/bookings">All bookings</TextLink>} />
          <Card className="overflow-hidden">
            {upcoming.length ? <UpcomingList bookings={upcoming} /> : <p className="m-0 px-4 py-10 text-center text-sm text-fg-muted">Nothing booked yet.</p>}
          </Card>
        </div>
      </section>

      <section className="grid grid-cols-1 gap-8 [&>*]:min-w-0 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="grid gap-3">
          <SectionTitle title="Latest calls" action={<TextLink href="/calls">All calls</TextLink>} />
          <Card className="overflow-hidden">
            {data.calls.length ? (
              <RecentCalls calls={data.calls.slice(0, 7)} agentNames={agentNames} />
            ) : (
              <p className="m-0 px-4 py-10 text-center text-sm text-fg-muted">Calls show up here once your agent answers one.</p>
            )}
          </Card>
        </div>
        <div className="grid content-start gap-3">
          <SectionTitle title="This cycle" />
          <Card className="divide-y divide-line">
            <PlanUsage data={data} />
            <Conversion data={data} />
          </Card>
        </div>
      </section>
    </div>
  );
}

function TextLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link href={href} className="group inline-flex items-center gap-1 text-[0.8rem] text-fg-soft no-underline transition-colors hover:text-ink">
      {children}
      <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" />
    </Link>
  );
}

function SetupChecklist({ data }: { data: DashboardData }) {
  const agent = data.agents.find((item) => item.active) ?? data.agents.at(-1);
  const readiness = data.readiness.find((item) => item.agentId === agent?.id);
  const items = agent && readiness
    ? [
        { label: "Business details", done: readiness.businessDetailsComplete },
        { label: "Calendar connected", done: readiness.calendarConfigured },
        { label: "Phone or web channel", done: readiness.channelConfigured },
        { label: "Agent switched on", done: readiness.active },
      ]
    : [
        { label: "Verify your email", done: data.onboarding.emailVerified },
        { label: "Create your first agent", done: false },
      ];
  const done = items.filter((item) => item.done).length;
  if (done === items.length) return null;

  return (
    <Card className="flex flex-col gap-4 px-5 py-4 md:flex-row md:items-center">
      <div className="min-w-0 flex-1">
        <p className="m-0 text-[0.9rem] font-medium text-ink">
          {agent ? `Finish setting up ${agent.name}` : "Set up your first agent"}
          <span className="ml-2 font-normal text-fg-muted">{done} of {items.length} done</span>
        </p>
        <ul className="m-0 mt-3 flex list-none flex-wrap gap-x-5 gap-y-2 p-0">
          {items.map((item) => (
            <li key={item.label} className={cn("flex items-center gap-2 text-[0.84rem]", item.done ? "text-fg-muted" : "text-fg")}>
              <span className={cn("grid size-4 place-items-center rounded-full border", item.done ? "border-signal bg-signal text-signal-fg" : "border-line-strong")}>
                {item.done ? <Check className="size-2.5" strokeWidth={3.5} /> : null}
              </span>
              {item.label}
            </li>
          ))}
        </ul>
      </div>
      <Button asChild size="xs">
        <Link href={agent ? `/agents/${agent.id}` : "/agents/new"}>{agent ? "Continue setup" : "Create agent"}</Link>
      </Button>
    </Card>
  );
}

function DailyBars({ daily }: { daily: DashboardData["daily"] }) {
  const values = daily.length
    ? daily
    : Array.from({ length: 14 }, (_, index) => ({ date: new Date(Date.now() - (13 - index) * 86_400_000).toISOString(), callCount: 0 }));
  const max = Math.max(...values.map((item) => item.callCount), 1);
  const step = max > 40 ? 20 : max > 20 ? 10 : 5;
  const top = Math.ceil(max / step) * step || step;
  const ticks = Array.from({ length: Math.floor(top / step) + 1 }, (_, index) => index * step).reverse();
  const total = values.reduce((sum, item) => sum + item.callCount, 0);
  const average = Math.round(total / values.length);

  return (
    <figure className="m-0">
      <figcaption className="sr-only">{total} calls over {values.length} days</figcaption>
      <div className="flex gap-3">
        <div className="flex h-48 flex-col justify-between pb-0 text-right text-[0.7rem] tabular-nums text-fg-muted" aria-hidden="true">
          {ticks.map((tick) => <span key={tick} className="-translate-y-1/2 leading-none first:translate-y-0 last:translate-y-0">{tick}</span>)}
        </div>
        <div className="relative h-48 flex-1">
          <div className="absolute inset-0 flex flex-col justify-between" aria-hidden="true">
            {ticks.map((tick) => <span key={tick} className="block border-t border-dashed border-line first:border-solid last:border-solid last:border-line-strong" />)}
          </div>
          <div
            className="absolute inset-x-0 border-t border-dashed border-signal/60"
            style={{ bottom: `${(average / top) * 100}%` }}
            aria-hidden="true"
          >
            <span className="absolute -top-[1.15rem] right-0 bg-surface pl-1 text-[0.68rem] text-signal">avg {average}</span>
          </div>
          <ol className="absolute inset-0 m-0 flex list-none items-end gap-[6%] p-0 px-[1.5%]">
            {values.map((item, index) => {
              const weekend = [0, 6].includes(new Date(item.date).getDay());
              return (
                <li key={item.date} className="group relative flex h-full flex-1 items-end">
                  <span
                    className={cn("block w-full rounded-t-[3px] transition-colors", weekend ? "bg-surface-3 group-hover:bg-line-strong" : "bg-fg/80 group-hover:bg-ink", index === values.length - 1 && "bg-signal group-hover:bg-signal")}
                    style={{ height: `${Math.max(1.5, (item.callCount / top) * 100)}%` }}
                  />
                  <span className="pointer-events-none absolute bottom-full left-1/2 mb-1.5 -translate-x-1/2 whitespace-nowrap rounded-[5px] bg-primary px-1.5 py-0.5 text-[0.7rem] text-primary-fg opacity-0 transition-opacity group-hover:opacity-100">
                    {item.callCount} · {dayFormat.format(new Date(item.date))}
                  </span>
                </li>
              );
            })}
          </ol>
        </div>
      </div>
      <div className="ml-8 mt-2 flex justify-between px-[1.5%] text-[0.7rem] text-fg-muted" aria-hidden="true">
        {values.map((item, index) => (
          <span key={item.date} className="flex-1 text-center">
            {index % 2 === 0 || index === values.length - 1 ? new Intl.DateTimeFormat("en", { day: "numeric", month: index === 0 ? "short" : undefined }).format(new Date(item.date)) : ""}
          </span>
        ))}
      </div>
    </figure>
  );
}

function UpcomingList({ bookings }: { bookings: Booking[] }) {
  const groups = new Map<string, Booking[]>();
  bookings.forEach((booking) => {
    const date = new Date(booking.appointmentAt);
    const today = new Date();
    const tomorrow = new Date(Date.now() + 86_400_000);
    const key = date.toDateString() === today.toDateString()
      ? "Today"
      : date.toDateString() === tomorrow.toDateString()
        ? "Tomorrow"
        : dayFormat.format(date);
    groups.set(key, [...(groups.get(key) ?? []), booking]);
  });

  return (
    <div className="divide-y divide-line">
      {[...groups].map(([day, items]) => (
        <div key={day} className="px-4 py-3">
          <p className="m-0 text-[0.75rem] font-medium text-fg-muted">{day}</p>
          <ul className="m-0 mt-1.5 grid list-none gap-0.5 p-0">
            {items.map((booking) => (
              <li key={booking.id}>
                <Link href="/bookings" className="-mx-2 flex items-baseline gap-3 rounded-[6px] px-2 py-1.5 text-inherit no-underline transition-colors hover:bg-surface-2">
                  <span className="w-[4.3rem] shrink-0 text-[0.8rem] tabular-nums text-fg-soft">{timeFormat.format(new Date(booking.appointmentAt))}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[0.84rem] text-ink">{booking.callerName}</span>
                    <span className="block truncate text-[0.76rem] text-fg-muted">{booking.serviceType}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}

function RecentCalls({ calls, agentNames }: { calls: Call[]; agentNames: Map<string, string> }) {
  return (
    <Table>
      <TableHead>
        <tr>
          <th>Caller</th>
          <th className="max-sm:hidden">Agent</th>
          <th>Result</th>
          <th className="text-right max-sm:hidden">Length</th>
          <th className="text-right">When</th>
        </tr>
      </TableHead>
      <TableBody className="[&_td]:py-2.5">
        {calls.map((call) => {
          const outcome = outcomeMeta(call.outcome);
          return (
            <tr key={call.id}>
              <td className="whitespace-nowrap">
                <Link href="/calls" className="text-ink no-underline tabular-nums hover:underline hover:underline-offset-4">{call.callerNumber || "Unknown caller"}</Link>
                {call.direction === "test" ? <span className="ml-2 text-[0.72rem] text-fg-muted">test</span> : null}
              </td>
              <td className="max-sm:hidden">{agentNames.get(call.agentId) ?? "—"}</td>
              <td className="whitespace-nowrap">
                <span className="inline-flex items-center gap-2">
                  <span className={cn("size-1.5 rounded-full", outcome.dot)} aria-hidden="true" />
                  <span className="text-fg">{outcome.label}</span>
                </span>
              </td>
              <td className="text-right tabular-nums max-sm:hidden">{call.durationSeconds ? formatDuration(call.durationSeconds) : "—"}</td>
              <td className="whitespace-nowrap text-right text-fg-muted">{relativeTime(call.startedAt)}</td>
            </tr>
          );
        })}
      </TableBody>
    </Table>
  );
}

function PlanUsage({ data }: { data: DashboardData }) {
  const { usage } = data;
  const percent = Math.min(100, Math.max(0, usage.usagePercent));
  return (
    <div className="px-4 py-4">
      <div className="flex items-baseline justify-between">
        <span className="text-[0.84rem] text-fg-soft">Minutes used</span>
        <span className="text-[0.75rem] capitalize text-fg-muted">{usage.plan} plan</span>
      </div>
      <p className="m-0 mt-1.5 text-ink">
        <span className="text-[1.35rem] font-medium tabular-nums tracking-tight">{usage.minutesUsedThisCycle.toLocaleString("en-US")}</span>
        <span className="ml-1 text-[0.84rem] text-fg-muted">of {usage.monthlyMinutesLimit.toLocaleString("en-US")}</span>
      </p>
      <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-surface-3">
        <div className={cn("h-full rounded-full", percent >= 90 ? "bg-danger" : percent >= 75 ? "bg-warning" : "bg-signal")} style={{ width: `${percent}%` }} />
      </div>
      <p className="m-0 mt-2 flex justify-between text-[0.75rem] text-fg-muted">
        <span>{usage.remainingMinutes.toLocaleString("en-US")} min left</span>
        <Link href="/billing" className="text-fg-soft no-underline hover:text-ink">Manage plan</Link>
      </p>
    </div>
  );
}

function Conversion({ data }: { data: DashboardData }) {
  const { analytics } = data;
  const rows = [
    ["Calls", analytics.attemptedCalls],
    ["Connected", analytics.connectedCalls],
    ["Booked", analytics.bookingCalls],
  ] as const;
  const max = Math.max(analytics.attemptedCalls, 1);
  const rate = analytics.attemptedCalls ? (analytics.bookingCalls / analytics.attemptedCalls) * 100 : 0;
  return (
    <div className="px-4 py-4">
      <div className="flex items-baseline justify-between">
        <span className="text-[0.84rem] text-fg-soft">Calls that became bookings</span>
        <span className="text-[0.84rem] font-medium tabular-nums text-ink">{rate.toFixed(1)}%</span>
      </div>
      <dl className="m-0 mt-3 grid gap-2">
        {rows.map(([label, value]) => (
          <div key={label} className="grid grid-cols-[4.5rem_1fr_2.5rem] items-center gap-2 text-[0.76rem]">
            <dt className="text-fg-muted">{label}</dt>
            <dd className="m-0 h-1.5 overflow-hidden rounded-full bg-surface-3">
              <span className="block h-full rounded-full bg-fg/70" style={{ width: `${(value / max) * 100}%` }} />
            </dd>
            <dd className="m-0 text-right tabular-nums text-fg">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="grid gap-8" aria-busy="true" aria-label="Loading dashboard">
      <div className="grid gap-2">
        <Skeleton className="h-7 w-56" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Skeleton className="h-24 rounded-[var(--radius-card)]" />
      <div className="grid grid-cols-1 gap-8 [&>*]:min-w-0 xl:grid-cols-[minmax(0,1fr)_320px]">
        <Skeleton className="h-64 rounded-[var(--radius-card)]" />
        <Skeleton className="h-64 rounded-[var(--radius-card)]" />
      </div>
    </div>
  );
}

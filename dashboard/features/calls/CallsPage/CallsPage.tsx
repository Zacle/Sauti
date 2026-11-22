"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import WaveSurfer from "wavesurfer.js";
import { Select as UiSelect } from "@/components/ui/select";
import { DateRangePicker, type DateRange, type DateRangePreset } from "@/components/ui/date-range-picker";
import { addDays, startOfDay, subDays } from "date-fns";
import {
  Activity,
  ArrowLeft,
  ArrowRight,
  Bot,
  CalendarCheck2,
  Clock3,
  FileText,
  Headphones,
  ListChecks,
  LoaderCircle,
  Pause,
  PhoneCall,
  PhoneIncoming,
  Play,
  Search,
  Sparkles,
  SlidersHorizontal,
} from "lucide-react";
import { listAgents } from "@/lib/api/agents";
import Link from "next/link";
import { getCall, getCallRecording, listCallActions, listCallTurns, searchCalls } from "@/lib/api/calls";
import type { Agent, Call, CallBookingSummary, CallSearchResponse, CallToolAction, CallTurn } from "@/types/api";
import { Button } from "@/components/ui/button";
import { Alert, Input } from "@/components/ui/field";
import { Card, EmptyState, Skeleton, Table, TableBody, TableHead, Tabs, TabsList, TabsTrigger } from "@/components/ui/primitives";
import { Drawer } from "@/components/ui/overlays";
import { PageHeader, StatStrip } from "@/components/ui/console";

type CallFilter = "all" | "phone" | "test";
const ROWS_PER_PAGE = 20;

export function CallsPage() {
  const [result, setResult] = useState<CallSearchResponse | null>(null);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState("");
  const [selectedCall, setSelectedCall] = useState<Call | null>(null);
  const [turns, setTurns] = useState<Record<string, CallTurn[]>>({});
  const [actions, setActions] = useState<Record<string, CallToolAction[] | null>>({});
  const [filter, setFilter] = useState<CallFilter>("all");
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");
  const [agentFilter, setAgentFilter] = useState("all");
  const [dateRange, setDateRange] = useState<DateRange | undefined>(() => ({ from: subDays(new Date(), 13), to: new Date() }));
  const [page, setPage] = useState(1);
  const searchText = useDebouncedValue(query.trim(), 300);

  useEffect(() => {
    listAgents().then(setAgents).catch(() => undefined);
    // A link from a booking opens its call: /calls?call=<id>.
    const linked = new URLSearchParams(window.location.search).get("call");
    if (linked) {
      getCall(linked)
        .then((call) => void openDetails(call))
        .catch(() => setError("That call could not be found."));
    }
    // Runs once, on arrival.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => setPage(1), [agentFilter, dateRange, filter, searchText, statusFilter]);

  useEffect(() => {
    const controller = new AbortController();
    setSearching(true);
    searchCalls({
      q: searchText,
      from: dateRange?.from ? startOfDay(dateRange.from).toISOString() : undefined,
      to: dateRange?.to ? addDays(startOfDay(dateRange.to), 1).toISOString() : undefined,
      type: filter,
      status: statusFilter,
      agentId: agentFilter,
      page: page - 1,
      size: ROWS_PER_PAGE,
    }, { signal: controller.signal })
      .then((found) => {
        setResult(found);
        setError("");
      })
      .catch((caught) => {
        if (!controller.signal.aborted) setError(caught instanceof Error ? caught.message : "Unable to load calls.");
      })
      .finally(() => {
        if (controller.signal.aborted) return;
        setLoading(false);
        setSearching(false);
      });
    return () => controller.abort();
  }, [agentFilter, dateRange, filter, page, searchText, statusFilter]);

  const agentNames = useMemo(
    () => new Map(agents.map((agent) => [agent.id, agent.name])),
    [agents],
  );

  const pageCalls = result?.items ?? [];
  const total = result?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / ROWS_PER_PAGE));
  const filtersActive = Boolean(searchText) || filter !== "all" || statusFilter !== "all" || agentFilter !== "all";
  const metrics = {
    answerRate: total ? Math.round((result?.answered ?? 0) / total * 1000) / 10 : 0,
    averageDuration: result?.averageDurationSeconds ?? 0,
    bookingCount: result?.bookings ?? 0,
  };

  const selectedId = selectedCall?.id ?? "";
  const selectedRecordingPending = Boolean(
    selectedCall?.endedAt
    && !selectedCall.recordingUrl
    && (
      selectedCall.recordingSid?.startsWith("TELNYX-CALL-CONTROL:")
      || selectedCall.recordingSid?.startsWith("TELNYX-CALL-LEG:")
    ),
  );

  const selectedBooking = selectedCall?.booking ?? null;
  // After a phone call ends, its full conversation and then its summary arrive within a few minutes.
  const selectedRecordStatus = selectedCall?.recordStatus ?? null;

  function replaceCall(updated: Call) {
    setSelectedCall((current) => current?.id === updated.id ? updated : current);
    setResult((current) => current && {
      ...current,
      items: current.items.map((call) => call.id === updated.id ? { ...updated, transcript: undefined } : call),
    });
  }

  useEffect(() => {
    if (!selectedId || (!selectedRecordingPending && !selectedRecordStatus)) return;
    let cancelled = false;
    const refresh = () => {
      void getCall(selectedId)
        .then((updated) => {
          if (cancelled) return;
          replaceCall(updated);
          if (selectedRecordStatus === "transcript" && updated.recordStatus !== "transcript") {
            void listCallTurns(selectedId)
              .then((items) => { if (!cancelled) setTurns((current) => ({ ...current, [selectedId]: items })); })
              .catch(() => undefined);
          }
        })
        .catch(() => undefined);
    };
    const interval = window.setInterval(refresh, 5_000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [selectedId, selectedRecordingPending, selectedRecordStatus]);

  async function openDetails(call: Call) {
    const callId = call.id;
    setSelectedCall(call);
    // The list leaves out the transcript and some details; the single call has them all.
    void getCall(callId).then(replaceCall).catch(() => undefined);
    if (actions[callId] === undefined) {
      setActions((current) => ({ ...current, [callId]: null }));
      listCallActions(callId)
        .then((items) => setActions((current) => ({ ...current, [callId]: items })))
        .catch(() => setActions((current) => ({ ...current, [callId]: [] })));
    }
    if (!turns[callId]) {
      try {
        setTurns((current) => ({ ...current, [callId]: [] }));
        const items = await listCallTurns(callId);
        setTurns((current) => ({ ...current, [callId]: items }));
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : "Unable to load the transcript.");
      }
    }
  }

  return (
    <div className="mk-v2 grid min-w-0 grid-cols-1 gap-6 [&>*]:min-w-0">
      <PageHeader
        eyebrow="Operations"
        title="Calls"
        description="Phone calls and browser tests, with routed events, recordings and transcripts."
              />

      <StatStrip
        items={[
          { label: "Conversations", value: total, hint: "In these dates and filters" },
          { label: "Answered", value: metrics.answerRate / 100, format: { style: "percent", maximumFractionDigits: 1 }, hint: "Reached a conclusion" },
          { label: "Avg. length", value: metrics.averageDuration, suffix: "s", hint: formatDurationSeconds(metrics.averageDuration) },
          { label: "Bookings", value: metrics.bookingCount, hint: "From calls in these dates" },
        ]}
      />

      <section className="flex flex-wrap items-center gap-2">
        <Tabs value={filter} onValueChange={(value) => setFilter(value as CallFilter)}>
          <TabsList aria-label="Call type filter">
            <TabsTrigger value="all">All</TabsTrigger>
            <TabsTrigger value="phone">Phone</TabsTrigger>
            <TabsTrigger value="test">Tests</TabsTrigger>
          </TabsList>
        </Tabs>
        <DateRangeFilter value={dateRange} onChange={setDateRange} />
        <FilterSelect icon={<Activity />} label="Status" value={statusFilter} onChange={setStatusFilter} options={[{ value: "all", label: "All status" }, { value: "answered", label: "Answered" }, { value: "missed", label: "Missed" }, { value: "active", label: "Active" }]} />
        <FilterSelect icon={<Bot />} label="Agent" value={agentFilter} onChange={setAgentFilter} options={[{ value: "all", label: "All agents" }, ...agents.map((agent) => ({ value: agent.id, label: agent.name }))]} />
        <label className="relative ml-auto min-w-[16rem] flex-1 sm:max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-fg-muted" />
          {searching ? <LoaderCircle className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 animate-spin text-fg-muted" /> : null}
          <Input
            aria-label="Search calls"
            className="h-9 pl-9 text-[0.85rem]"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search what was said, a number, a booking…"
            value={query}
          />
        </label>
      </section>

      {error ? <Alert tone="error">{error}</Alert> : null}

      {loading ? (
        <Card className="grid gap-3 p-5">
          {[1, 2, 3, 4, 5].map((row) => <Skeleton key={row} className="h-12" />)}
        </Card>
      ) : total === 0 && !filtersActive ? (
        <EmptyState
          icon={<PhoneCall />}
          title="No calls in these dates"
          description="Pick other dates, or start a browser test from an agent. The transcript and recording will appear here."
        />
      ) : (
        <Card className="overflow-hidden">
          <Table>
            <TableHead>
              <tr>
                <th>Call</th>
                <th>Date</th>
                <th>Caller</th>
                <th>Result</th>
                <th>Duration</th>
                <th>Status</th>
              </tr>
            </TableHead>
            <TableBody>
              {pageCalls.map((call) => {
                const booking = call.booking ?? null;
                const selected = selectedId === call.id;
                return (
                  <tr
                    key={call.id}
                    onClick={() => void openDetails(call)}
                    onKeyDown={(event) => { if (event.key === "Enter") void openDetails(call); }}
                    tabIndex={0}
                    aria-selected={selected}
                    className={selected ? "cursor-pointer bg-tint/[0.04]" : "cursor-pointer"}
                  >
                    <td className={call.matchedText ? "min-w-[14rem]" : undefined}>
                      <div className="flex items-center gap-3">
                        <span className="min-w-0 flex-1">
                          <span className="block truncate font-medium text-ink">{callTitle(call, agentNames.get(call.agentId))}</span>
                          <span className="block text-xs text-fg-muted">{call.direction === "test" ? "Browser test" : `${humanize(call.direction)} call`}</span>
                          {call.matchedText ? (
                            <span className="mt-1 line-clamp-2 w-0 min-w-full text-xs leading-snug text-fg-soft" title={call.matchedText}>
                              <Highlighted text={call.matchedText} term={searchText} />
                            </span>
                          ) : null}
                        </span>
                      </div>
                    </td>
                    <td><DateCell value={call.startedAt} /></td>
                    <td className="tabular-nums">{call.direction === "test" ? "—" : call.callerNumber || "—"}</td>
                    <td><RoutedEvent booking={booking} call={call} /></td>
                    <td className="tabular-nums">{formatDurationSeconds(call.durationSeconds)}</td>
                    <td><StatusBadge outcome={call.outcome} /></td>
                  </tr>
                );
              })}
            </TableBody>
          </Table>
          {total === 0 ? (
            <p className="m-0 border-t border-line px-5 py-10 text-center text-sm text-fg-muted">No calls match these filters.</p>
          ) : (
            <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3 text-xs text-fg-muted">
              <span>
                {(page - 1) * ROWS_PER_PAGE + 1}–{Math.min(page * ROWS_PER_PAGE, total)} of {total}
              </span>
              <div className="flex items-center gap-1">
                <Button variant="ghost" size="sm" className="size-8 px-0" disabled={page === 1} onClick={() => setPage((current) => Math.max(1, current - 1))} aria-label="Previous page"><ArrowLeft /></Button>
                {pageWindow(page, pageCount).map((number, index) => number === null ? (
                  <span key={`gap-${index}`} className="px-1" aria-hidden>…</span>
                ) : (
                  <Button
                    key={number}
                    size="sm"
                    variant={page === number ? "primary" : "ghost"}
                    className="size-8 px-0"
                    onClick={() => setPage(number)}
                    aria-current={page === number ? "page" : undefined}
                  >
                    {number}
                  </Button>
                ))}
                <Button variant="ghost" size="sm" className="size-8 px-0" disabled={page === pageCount} onClick={() => setPage((current) => Math.min(pageCount, current + 1))} aria-label="Next page"><ArrowRight /></Button>
              </div>
            </footer>
          )}
        </Card>
      )}

      <Drawer
        open={Boolean(selectedCall)}
        onOpenChange={(open) => { if (!open) setSelectedCall(null); }}
        title={selectedCall ? callTitle(selectedCall, agentNames.get(selectedCall.agentId)) : "Call"}
        description={selectedCall ? `${formatCompactDate(selectedCall.startedAt)} · ${formatDurationSeconds(selectedCall.durationSeconds)}` : undefined}
        actions={selectedCall ? <StatusBadge outcome={selectedCall.outcome} /> : null}
      >
        {selectedCall ? (
          <div className="grid gap-6">
            {selectedCall.outcome === "unanswered" ? <Alert>{unansweredReason(selectedCall.failureReason)}</Alert> : null}
            <dl className="m-0 grid grid-cols-2 gap-px overflow-hidden rounded-[var(--radius-card)] border border-line bg-line">
              <DetailLine icon={CalendarCheck2} label="Booked event" value={selectedBooking ? `${selectedBooking.serviceType} · ${formatDate(selectedBooking.appointmentAt)}` : "No booking"} />
              <DetailLine icon={PhoneIncoming} label="Direction" value={selectedCall.direction === "test" ? "Browser test" : humanize(selectedCall.direction)} />
              <DetailLine icon={Clock3} label="Duration" value={formatDurationSeconds(selectedCall.durationSeconds)} />
              <DetailLine icon={Headphones} label="Language" value={selectedCall.languageDetected ? selectedCall.languageDetected.toUpperCase() : "—"} />
              {selectedCall.intent ? <DetailLine icon={SlidersHorizontal} label="Intent" value={humanize(selectedCall.intent)} /> : null}
              {selectedCall.sentiment ? <DetailLine icon={Sparkles} label="Sentiment" value={humanize(selectedCall.sentiment)} /> : null}
            </dl>
            {selectedBooking ? (
              <Link
                href={`/bookings?booking=${selectedBooking.id}`}
                className="-mt-3 inline-flex w-fit items-center gap-1.5 text-sm font-medium text-ink underline-offset-4 hover:underline"
              >
                Open booking {selectedBooking.bookingReference} <ArrowRight className="size-3.5" />
              </Link>
            ) : null}

            {selectedRecordStatus && !selectedCall.callSummary ? (
              <section className="rounded-[var(--radius-card)] border border-line p-4">
                <p className="m-0 flex items-center gap-2 text-sm font-medium text-ink"><Sparkles className="size-4 text-signal" /> Summary</p>
                <p className="m-0 mt-2 flex items-center gap-2 text-sm text-fg-soft">
                  <LoaderCircle className="size-4 animate-spin" /> Writing the summary once the conversation is in…
                </p>
              </section>
            ) : (selectedCall.callSummary || selectedCall.intent || selectedCall.sentiment || selectedBooking) ? (
              <section className="rounded-[var(--radius-card)] border border-signal/20 bg-signal/[0.05] p-4">
                <p className="m-0 flex items-center gap-2 text-sm font-medium text-ink"><Sparkles className="size-4 text-signal" /> Summary</p>
                <p className="m-0 mt-2 text-sm leading-relaxed text-fg-soft">{selectedCall.callSummary || summaryFallback(selectedCall, selectedBooking)}</p>
              </section>
            ) : null}

            <section>
              <p className="m-0 mb-3 flex items-center gap-2 text-xs font-medium text-fg-muted"><Headphones className="size-3.5" /> Recording</p>
              {selectedCall.recordingUrl
                ? <RecordingPlayer callId={selectedCall.id} />
                : selectedRecordingPending
                  ? <p className="m-0 flex items-center gap-2 text-sm text-fg-soft"><LoaderCircle className="size-4 animate-spin" /> Finalizing recording…</p>
                  : <p className="m-0 text-sm text-fg-muted">No recording captured.</p>}
            </section>

            <section>
              <p className="m-0 mb-3 flex items-center gap-2 text-xs font-medium text-fg-muted"><ListChecks className="size-3.5" /> Actions</p>
              <ActionList actions={actions[selectedCall.id]} callActive={selectedCall.outcome === "active"} />
            </section>

            <section>
              <p className="m-0 mb-3 flex items-center gap-2 text-xs font-medium text-fg-muted"><FileText className="size-3.5" /> Transcript</p>
              {selectedRecordStatus === "transcript" ? (
                <p className="m-0 mb-3 flex items-center gap-2 text-sm text-fg-soft">
                  <LoaderCircle className="size-4 animate-spin" /> Getting the full conversation from the call…
                </p>
              ) : null}
              <Transcript turns={turns[selectedCall.id]} />
            </section>
          </div>
        ) : null}
      </Drawer>
    </div>
  );
}

const CALL_RANGE_PRESETS: DateRangePreset[] = [
  { label: "Today", range: () => ({ from: new Date(), to: new Date() }) },
  { label: "Last 7 days", range: () => ({ from: subDays(new Date(), 6), to: new Date() }) },
  { label: "Last 14 days", range: () => ({ from: subDays(new Date(), 13), to: new Date() }) },
  { label: "Last 30 days", range: () => ({ from: subDays(new Date(), 29), to: new Date() }) },
];

function DateRangeFilter({ value, onChange }: { value: DateRange | undefined; onChange: (range: DateRange | undefined) => void }) {
  return <DateRangePicker value={value} onChange={onChange} presets={CALL_RANGE_PRESETS} />;
}

function FilterSelect({ icon, label, value, onChange, options }: { icon: React.ReactNode; label: string; value: string; onChange: (value: string) => void; options: Array<{ value: string; label: string }> }) {
  return <div className="w-44 max-sm:w-full"><UiSelect size="sm" icon={icon} ariaLabel={label} value={value} onValueChange={onChange} options={options} /></div>;
}

function DateCell({ value }: { value: string }) {
  const date = new Date(value);
  return (
    <span className="block whitespace-nowrap">
      <span className="block text-ink">{new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(date)}</span>
      <span className="block text-xs text-fg-muted">{new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(date)}</span>
    </span>
  );
}

function RoutedEvent({ booking, call }: { booking: CallBookingSummary | null; call: Call }) {
  if (booking) return <span className="inline-flex items-center gap-1.5 text-ink"><CalendarCheck2 className="size-3.5 text-signal" /> {booking.serviceType}</span>;
  if (call.transferStatus === "completed") return <span className="text-fg">Transferred</span>;
  if (call.transferStatus === "requested" || call.transferStatus === "dialing") return <span className="text-fg">Transfer started</span>;
  if (call.transferStatus) return <span className="text-fg-muted" title={call.transferFailureReason ?? undefined}>Transfer failed</span>;
  return <span className="text-fg-muted">—</span>;
}

/** Why a call to the agent was turned away before anyone spoke. */
function unansweredReason(reason: string | null) {
  switch (reason) {
    case "billing_blocked":
      return "Not answered: calls are paused until the workspace’s billing is sorted out.";
    case "calling_not_allowed":
      return "Not answered: live calling isn’t enabled for this workspace yet.";
    case "not_handled":
      return "Not answered: Sauti was restarting while this call rang.";
    case "agent_unavailable":
      return "Not answered: the agent couldn’t start. Try a test call from the agent’s page.";
    default:
      return "Not answered: the caller was turned away before the agent spoke.";
  }
}

function StatusBadge({ outcome }: { outcome: string }) {
  const successful = ["completed", "booking", "faq_answered", "transferred"].includes(outcome);
  const label = outcome === "active" ? "Live now" : successful ? "Answered"
    : outcome === "unanswered" ? "Not answered" : outcome === "closed" ? "Closed" : humanize(outcome);
  const dot = outcome === "active" ? "bg-signal animate-pulse" : successful ? "bg-success" : "bg-warning";
  return <span className="inline-flex items-center gap-2 whitespace-nowrap text-fg"><span className={`size-1.5 rounded-full ${dot}`} aria-hidden="true" />{label}</span>;
}

function DetailLine({ icon: Icon, label, value }: { icon: typeof CalendarCheck2; label: string; value: string }) {
  return (
    <div className="bg-popover p-3.5">
      <dt className="flex items-center gap-1.5 text-xs text-fg-muted"><Icon className="size-3.5" /> {label}</dt>
      <dd className="m-0 mt-1 text-sm text-ink">{value}</dd>
    </div>
  );
}

const ACTION_STATUS: Record<CallToolAction["status"], { label: string; dot: string }> = {
  performed: { label: "Done", dot: "bg-success" },
  awaiting_confirmation: { label: "Waited for confirmation", dot: "bg-info" },
  not_performed: { label: "Not done", dot: "bg-warning" },
  failed: { label: "Failed", dot: "bg-danger" },
  started: { label: "Outcome unknown", dot: "bg-warning" },
};

/** Bookings, messages and transfers in the order they happened, each with what came of it. */
function ActionList({ actions, callActive }: { actions: CallToolAction[] | null | undefined; callActive: boolean }) {
  if (!actions) return <p className="m-0 flex items-center gap-2 text-sm text-fg-soft"><LoaderCircle className="size-4 animate-spin" /> Loading actions…</p>;
  if (actions.length === 0) return <p className="m-0 text-sm text-fg-muted">The agent didn’t book, send or change anything on this call.</p>;
  const time = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  return (
    <ol className="m-0 grid list-none gap-0 divide-y divide-line rounded-[var(--radius-card)] border border-line p-0">
      {actions.map((action, index) => {
        const status = action.status === "started" && callActive
          ? { label: "In progress", dot: "bg-signal animate-pulse" }
          : ACTION_STATUS[action.status];
        return (
          <li key={`${action.at}-${index}`} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 px-3.5 py-2.5 text-sm">
            <span className="min-w-0 text-ink">{action.summary ?? humanize(action.toolName)}</span>
            <span className="inline-flex items-center gap-2 whitespace-nowrap text-xs text-fg-muted">
              <span className={`size-1.5 rounded-full ${status.dot}`} aria-hidden="true" />
              {status.label} · <span className="tabular-nums">{time.format(new Date(action.at))}</span>
            </span>
          </li>
        );
      })}
    </ol>
  );
}

function Transcript({ turns }: { turns: CallTurn[] | undefined }) {
  if (!turns) return <p className="m-0 flex items-center gap-2 text-sm text-fg-soft"><LoaderCircle className="size-4 animate-spin" /> Loading transcript…</p>;
  if (turns.length === 0) return <p className="m-0 text-sm text-fg-muted">No transcript was captured.</p>;
  return (
    <ol className="m-0 grid list-none gap-3 p-0">
      {turns.map((turn) => (
        <li key={turn.turnIndex} className="grid gap-3">
          {turn.callerTranscript ? <TranscriptLine role="Caller" text={turn.callerTranscript} time={turn.turnIndex} /> : null}
          {turn.agentResponse ? <TranscriptLine role="Agent" text={turn.agentResponse} time={turn.turnIndex} /> : null}
        </li>
      ))}
    </ol>
  );
}

function TranscriptLine({ role, text, time }: { role: "Agent" | "Caller"; text: string; time: number }) {
  const agent = role === "Agent";
  return (
    <div className={agent ? "ml-auto max-w-[88%] text-right" : "max-w-[88%]"}>
      <p className="m-0 mb-1 text-xs font-medium text-fg-muted">{role} · {formatTurnTime(time)}</p>
      <p className={agent
        ? "m-0 inline-block rounded-[var(--radius-card)] rounded-tr-md bg-signal/[0.1] px-3.5 py-2 text-left text-sm leading-snug text-ink"
        : "m-0 inline-block rounded-[var(--radius-card)] rounded-tl-md bg-tint/[0.07] px-3.5 py-2 text-sm leading-snug text-fg"}
      >
        {text}
      </p>
    </div>
  );
}

function RecordingPlayer({ callId }: { callId: string }) {
  const waveformRef = useRef<HTMLDivElement | null>(null);
  const playerRef = useRef<WaveSurfer | null>(null);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  useEffect(() => {
    let cancelled = false;
    let currentUrl = "";
    setReady(false);
    setFailed(false);
    setPlaying(false);
    setCurrentTime(0);
    setDuration(0);
    getCallRecording(callId)
      .then((blob) => {
        if (cancelled || !waveformRef.current) return;
        currentUrl = URL.createObjectURL(blob);
        const tokens = getComputedStyle(document.documentElement);
        const token = (name: string, fallback: string) => tokens.getPropertyValue(name).trim() || fallback;
        const player = WaveSurfer.create({
          container: waveformRef.current,
          url: currentUrl,
          height: 44,
          waveColor: token("--color-line-strong", "rgba(255,255,255,0.18)"),
          progressColor: token("--color-signal", "#5eead4"),
          cursorColor: token("--color-ink", "#ffffff"),
          cursorWidth: 1,
          barWidth: 2,
          barGap: 2,
          barRadius: 2,
          normalize: true,
        });
        playerRef.current = player;
        player.on("ready", (length) => { setDuration(length); setReady(true); });
        player.on("timeupdate", setCurrentTime);
        player.on("play", () => setPlaying(true));
        player.on("pause", () => setPlaying(false));
        player.on("finish", () => setPlaying(false));
        player.on("error", () => setFailed(true));
      })
      .catch(() => setFailed(true));
    return () => {
      cancelled = true;
      playerRef.current?.destroy();
      playerRef.current = null;
      if (currentUrl) URL.revokeObjectURL(currentUrl);
    };
  }, [callId]);

  if (failed) return <p className="m-0 text-sm text-fg-muted">Could not load the recording.</p>;
  return (
    <div className="flex items-center gap-3 rounded-[var(--radius-card)] border border-line bg-tint/[0.02] p-3">
      <button
        type="button"
        disabled={!ready}
        onClick={() => void playerRef.current?.playPause()}
        aria-label={playing ? "Pause recording" : "Play recording"}
        className="grid size-10 shrink-0 cursor-pointer place-items-center rounded-full border-0 bg-primary text-primary-fg transition-transform active:scale-95 disabled:cursor-wait disabled:opacity-60"
      >
        {!ready ? <LoaderCircle className="size-4 animate-spin" /> : playing ? <Pause className="size-4" /> : <Play className="size-4 translate-x-px" />}
      </button>
      <span className="font-mono text-xs text-fg-muted tabular-nums">{formatAudioTime(currentTime)}</span>
      <div className="min-w-0 flex-1" ref={waveformRef} />
      <span className="font-mono text-xs text-fg-muted tabular-nums">{formatAudioTime(duration)}</span>
    </div>
  );
}

function callTitle(call: Call, agentName?: string) {
  if (call.intent) return humanize(call.intent);
  if (call.outcome === "booking") return "Appointment booking";
  if (call.outcome === "faq_answered") return "Question answered";
  if (call.outcome === "transferred") return "Human transfer";
  if (call.outcome === "voicemail") return "Voicemail";
  return agentName ? `${agentName} conversation` : "Agent conversation";
}

function formatDurationSeconds(seconds: number | null) {
  if (seconds === null) return "In progress";
  if (seconds < 60) return `${seconds}s`;
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
}

function formatCompactDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function formatTurnTime(turnIndex: number) {
  return `Turn ${Math.max(1, turnIndex)}`;
}

function humanize(value: string) {
  return value.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatAudioTime(value: number) {
  if (!Number.isFinite(value)) return "0:00";
  const seconds = Math.max(0, Math.floor(value));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
}

function summaryFallback(call: Call, booking: CallBookingSummary | null) {
  if (booking) return `The caller booked ${booking.serviceType} for ${formatDate(booking.appointmentAt)}.`;
  if (call.intent) return `The conversation was about ${humanize(call.intent).toLowerCase()} and ended as ${humanize(call.outcome).toLowerCase()}.`;
  return `The conversation ended with the status ${humanize(call.outcome).toLowerCase()}.`;
}

/** Page numbers to show: the first, the last, and those around the current page, with gaps as null. */
function pageWindow(page: number, pageCount: number): Array<number | null> {
  const shown = new Set([1, pageCount, page - 1, page, page + 1].filter((number) => number >= 1 && number <= pageCount));
  const sorted = [...shown].sort((a, b) => a - b);
  return sorted.flatMap((number, index) => (index > 0 && number - sorted[index - 1] > 1 ? [null, number] : [number]));
}

/** The value, once it has stopped changing for the given time. */
function useDebouncedValue<T>(value: T, delayMs: number) {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(value), delayMs);
    return () => window.clearTimeout(timer);
  }, [value, delayMs]);
  return settled;
}

/** The text with each occurrence of the search term marked. */
function Highlighted({ text, term }: { text: string; term: string }) {
  const needle = term.trim().toLowerCase();
  if (!needle) return <>{text}</>;
  const parts: React.ReactNode[] = [];
  const lower = text.toLowerCase();
  let from = 0;
  for (let at = lower.indexOf(needle); at >= 0; at = lower.indexOf(needle, from)) {
    if (at > from) parts.push(text.slice(from, at));
    parts.push(<mark key={at} className="rounded-[3px] bg-signal/15 px-0.5 text-ink">{text.slice(at, at + needle.length)}</mark>);
    from = at + needle.length;
  }
  parts.push(text.slice(from));
  return <>{parts}</>;
}

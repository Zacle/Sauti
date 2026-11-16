"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import WaveSurfer from "wavesurfer.js";
import { Select as UiSelect } from "@/components/ui/select";
import { DateRangePicker, type DateRange, type DateRangePreset } from "@/components/ui/date-range-picker";
import { endOfDay, isAfter, isBefore, startOfDay, subDays } from "date-fns";
import {
  Activity,
  ArrowLeft,
  ArrowRight,
  Bot,
  CalendarCheck2,
  Clock3,
  FileText,
  Headphones,
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
import { listBookings } from "@/lib/api/bookings";
import { getCall, getCallRecording, listCalls, listCallTurns } from "@/lib/api/calls";
import type { Agent, Booking, Call, CallTurn } from "@/types/api";
import { Button } from "@/components/ui/button";
import { Alert, Input } from "@/components/ui/field";
import { Card, EmptyState, Skeleton, Table, TableBody, TableHead, Tabs, TabsList, TabsTrigger } from "@/components/ui/primitives";
import { Drawer } from "@/components/ui/overlays";
import { PageHeader, StatStrip } from "@/components/ui/console";

type CallFilter = "all" | "phone" | "test";
const ROWS_PER_PAGE = 20;
const SUCCESSFUL_OUTCOMES = new Set(["completed", "booking", "booked", "faq_answered", "transferred"]);

export function CallsPage() {
  const [calls, setCalls] = useState<Call[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const [turns, setTurns] = useState<Record<string, CallTurn[]>>({});
  const [filter, setFilter] = useState<CallFilter>("all");
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");
  const [agentFilter, setAgentFilter] = useState("all");
  const [dateRange, setDateRange] = useState<DateRange>();
  const [page, setPage] = useState(1);

  useEffect(() => {
    Promise.all([listCalls(), listAgents(), listBookings()])
      .then(([callItems, agentItems, bookingItems]) => {
        setCalls(callItems);
        setAgents(agentItems);
        setBookings(bookingItems);
        if (callItems.length) {
          const latestCallDate = new Date(Math.max(...callItems.map((call) => new Date(call.startedAt).getTime())));
          setDateRange({ from: subDays(latestCallDate, 13), to: latestCallDate });
        } else {
          const today = new Date();
          setDateRange({ from: subDays(today, 13), to: today });
        }
      })
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load calls."))
      .finally(() => setLoading(false));
  }, []);

  const agentNames = useMemo(
    () => new Map(agents.map((agent) => [agent.id, agent.name])),
    [agents],
  );

  const bookingsByCall = useMemo(() => {
    const map = new Map<string, Booking>();
    bookings.forEach((booking) => {
      if (booking.callId) map.set(booking.callId, booking);
    });
    return map;
  }, [bookings]);

  const filteredCalls = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return calls.filter((call) => {
      const isTest = call.direction === "test";
      if (filter === "phone" && isTest) return false;
      if (filter === "test" && !isTest) return false;
      if (statusFilter === "answered" && !SUCCESSFUL_OUTCOMES.has(call.outcome)) return false;
      if (statusFilter === "missed" && SUCCESSFUL_OUTCOMES.has(call.outcome)) return false;
      if (statusFilter === "active" && call.outcome !== "active") return false;
      if (agentFilter !== "all" && call.agentId !== agentFilter) return false;
      const callDate = new Date(call.startedAt);
      if (dateRange?.from && isBefore(callDate, startOfDay(dateRange.from))) return false;
      if (dateRange?.to && isAfter(callDate, endOfDay(dateRange.to))) return false;
      if (!normalized) return true;
      const booking = bookingsByCall.get(call.id);
      return [
        agentNames.get(call.agentId) ?? "",
        call.callerNumber,
        call.direction,
        call.outcome,
        call.intent ?? "",
        call.sentiment ?? "",
        booking?.serviceType ?? "",
        booking?.callerName ?? "",
      ].some((value) => value.toLowerCase().includes(normalized));
    });
  }, [agentFilter, agentNames, bookingsByCall, calls, dateRange, filter, query, statusFilter]);

  useEffect(() => setPage(1), [agentFilter, dateRange, filter, query, statusFilter]);

  const pageCount = Math.max(1, Math.ceil(filteredCalls.length / ROWS_PER_PAGE));
  const pagedCalls = filteredCalls.slice((page - 1) * ROWS_PER_PAGE, page * ROWS_PER_PAGE);

  const metrics = useMemo(() => {
    const completedCalls = calls.filter((call) => call.durationSeconds !== null);
    const answered = calls.filter((call) => SUCCESSFUL_OUTCOMES.has(call.outcome)).length;
    const durations = completedCalls.map((call) => call.durationSeconds ?? 0);
    return {
      answerRate: calls.length ? Math.round(answered / calls.length * 1000) / 10 : 0,
      averageDuration: durations.length ? Math.round(durations.reduce((sum, value) => sum + value, 0) / durations.length) : 0,
      bookingCount: bookings.filter((booking) => booking.status !== "cancelled").length,
      sparkValues: durations.slice(0, 12).reverse(),
    };
  }, [bookings, calls]);

  const selectedCall = useMemo(
    () => calls.find((call) => call.id === selectedId) ?? null,
    [calls, selectedId],
  );
  const selectedRecordingPending = Boolean(
    selectedCall?.endedAt
    && !selectedCall.recordingUrl
    && (
      selectedCall.recordingSid?.startsWith("TELNYX-CALL-CONTROL:")
      || selectedCall.recordingSid?.startsWith("TELNYX-CALL-LEG:")
    ),
  );

  const selectedBooking = selectedCall ? bookingsByCall.get(selectedCall.id) ?? null : null;

  useEffect(() => {
    if (!selectedId || !selectedRecordingPending) return;
    let cancelled = false;
    const refresh = () => {
      void getCall(selectedId)
        .then((updated) => {
          if (cancelled) return;
          setCalls((current) => current.map((call) => call.id === updated.id ? updated : call));
        })
        .catch(() => undefined);
    };
    refresh();
    const interval = window.setInterval(refresh, 5_000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [selectedId, selectedRecordingPending]);

  async function openDetails(callId: string) {
    setSelectedId(callId);
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
          { label: "Conversations", value: calls.length, hint: "Phone and browser" },
          { label: "Answered", value: metrics.answerRate / 100, format: { style: "percent", maximumFractionDigits: 1 }, hint: "Reached a conclusion" },
          { label: "Avg. length", value: metrics.averageDuration, suffix: "s", hint: formatDurationSeconds(metrics.averageDuration) },
          { label: "Bookings", value: metrics.bookingCount, hint: "From these calls" },
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
          <Input
            aria-label="Search calls"
            className="h-9 pl-9 text-[0.85rem]"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search agent, number, intent, booking…"
            value={query}
          />
        </label>
      </section>

      {error ? <Alert tone="error">{error}</Alert> : null}

      {loading ? (
        <Card className="grid gap-3 p-5">
          {[1, 2, 3, 4, 5].map((row) => <Skeleton key={row} className="h-12" />)}
        </Card>
      ) : calls.length === 0 ? (
        <EmptyState
          icon={<PhoneCall />}
          title="No calls yet"
          description="Start a browser test from an agent. The transcript and recording will appear here."
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
              {pagedCalls.map((call) => {
                const booking = bookingsByCall.get(call.id) ?? null;
                const selected = selectedId === call.id;
                return (
                  <tr
                    key={call.id}
                    onClick={() => void openDetails(call.id)}
                    onKeyDown={(event) => { if (event.key === "Enter") void openDetails(call.id); }}
                    tabIndex={0}
                    aria-selected={selected}
                    className={selected ? "cursor-pointer bg-tint/[0.04]" : "cursor-pointer"}
                  >
                    <td>
                      <div className="flex items-center gap-3">
                        <span className="min-w-0">
                          <span className="block truncate font-medium text-ink">{callTitle(call, agentNames.get(call.agentId))}</span>
                          <span className="block text-xs text-fg-muted">{call.direction === "test" ? "Browser test" : `${humanize(call.direction)} call`}</span>
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
          {filteredCalls.length === 0 ? (
            <p className="m-0 border-t border-line px-5 py-10 text-center text-sm text-fg-muted">No calls match these filters.</p>
          ) : (
            <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3 text-xs text-fg-muted">
              <span>
                {(page - 1) * ROWS_PER_PAGE + 1}–{Math.min(page * ROWS_PER_PAGE, filteredCalls.length)} of {filteredCalls.length}
              </span>
              <div className="flex items-center gap-1">
                <Button variant="ghost" size="sm" className="size-8 px-0" disabled={page === 1} onClick={() => setPage((current) => Math.max(1, current - 1))} aria-label="Previous page"><ArrowLeft /></Button>
                {Array.from({ length: pageCount }, (_, index) => (
                  <Button
                    key={index}
                    size="sm"
                    variant={page === index + 1 ? "primary" : "ghost"}
                    className="size-8 px-0"
                    onClick={() => setPage(index + 1)}
                    aria-current={page === index + 1 ? "page" : undefined}
                  >
                    {index + 1}
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
        onOpenChange={(open) => { if (!open) setSelectedId(""); }}
        title={selectedCall ? callTitle(selectedCall, agentNames.get(selectedCall.agentId)) : "Call"}
        description={selectedCall ? `${formatCompactDate(selectedCall.startedAt)} · ${formatDurationSeconds(selectedCall.durationSeconds)}` : undefined}
        actions={selectedCall ? <StatusBadge outcome={selectedCall.outcome} /> : null}
      >
        {selectedCall ? (
          <div className="grid gap-6">
            <dl className="m-0 grid grid-cols-2 gap-px overflow-hidden rounded-[var(--radius-card)] border border-line bg-line">
              <DetailLine icon={CalendarCheck2} label="Booked event" value={selectedBooking ? `${selectedBooking.serviceType} · ${formatDate(selectedBooking.appointmentAt)}` : "No booking"} />
              <DetailLine icon={PhoneIncoming} label="Direction" value={selectedCall.direction === "test" ? "Browser test" : humanize(selectedCall.direction)} />
              <DetailLine icon={Clock3} label="Duration" value={formatDurationSeconds(selectedCall.durationSeconds)} />
              <DetailLine icon={Headphones} label="Language" value={selectedCall.languageDetected ? selectedCall.languageDetected.toUpperCase() : "—"} />
              {selectedCall.intent ? <DetailLine icon={SlidersHorizontal} label="Intent" value={humanize(selectedCall.intent)} /> : null}
              {selectedCall.sentiment ? <DetailLine icon={Sparkles} label="Sentiment" value={humanize(selectedCall.sentiment)} /> : null}
            </dl>

            {(selectedCall.callSummary || selectedCall.intent || selectedCall.sentiment || selectedBooking) ? (
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
              <p className="m-0 mb-3 flex items-center gap-2 text-xs font-medium text-fg-muted"><FileText className="size-3.5" /> Transcript</p>
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

function RoutedEvent({ booking, call }: { booking: Booking | null; call: Call }) {
  if (booking) return <span className="inline-flex items-center gap-1.5 text-ink"><CalendarCheck2 className="size-3.5 text-signal" /> {booking.serviceType}</span>;
  if (call.transferStatus) return <span className="text-fg">Transferred</span>;
  return <span className="text-fg-muted">—</span>;
}

function StatusBadge({ outcome }: { outcome: string }) {
  const successful = ["completed", "booking", "faq_answered", "transferred"].includes(outcome);
  const label = outcome === "active" ? "Live now" : successful ? "Answered" : humanize(outcome);
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

function summaryFallback(call: Call, booking: Booking | null) {
  if (booking) return `The caller booked ${booking.serviceType} for ${formatDate(booking.appointmentAt)}.`;
  if (call.intent) return `The conversation was about ${humanize(call.intent).toLowerCase()} and ended as ${humanize(call.outcome).toLowerCase()}.`;
  return `The conversation ended with the status ${humanize(call.outcome).toLowerCase()}.`;
}

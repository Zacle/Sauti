"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import WaveSurfer from "wavesurfer.js";
import {
  Activity,
  ArrowLeft,
  ArrowRight,
  Bot,
  CalendarDays,
  CalendarCheck2,
  CheckCircle2,
  ChevronDown,
  Clock3,
  FileText,
  Headphones,
  LoaderCircle,
  Pause,
  PhoneCall,
  PhoneIncoming,
  Play,
  PlayCircle,
  Search,
  Sparkles,
  SlidersHorizontal,
  X,
  XCircle,
} from "lucide-react";
import { listAgents } from "@/lib/api/agents";
import { listBookings } from "@/lib/api/bookings";
import { getCallRecording, listCalls, listCallTurns } from "@/lib/api/calls";
import type { Agent, Booking, Call, CallTurn } from "@/types/api";
import styles from "./CallsPage.module.css";

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
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [page, setPage] = useState(1);

  useEffect(() => {
    Promise.all([listCalls(), listAgents(), listBookings()])
      .then(([callItems, agentItems, bookingItems]) => {
        setCalls(callItems);
        setAgents(agentItems);
        setBookings(bookingItems);
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
      const callDate = localDateKey(call.startedAt);
      if (fromDate && callDate < fromDate) return false;
      if (toDate && callDate > toDate) return false;
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
  }, [agentFilter, agentNames, bookingsByCall, calls, filter, fromDate, query, statusFilter, toDate]);

  useEffect(() => setPage(1), [agentFilter, filter, fromDate, query, statusFilter, toDate]);

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

  const selectedBooking = selectedCall ? bookingsByCall.get(selectedCall.id) ?? null : null;

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

  function updateFromDate(value: string) {
    setFromDate(value);
    if (value && toDate && value > toDate) setToDate(value);
  }

  function updateToDate(value: string) {
    setToDate(value);
    if (value && fromDate && value < fromDate) setFromDate(value);
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <span>Operations</span>
          <h1>Calls</h1>
          <p>Inspect phone calls and browser tests, review routed events, and open the transcript without losing the call list.</p>
        </div>
        <div className={styles.summary}>
          <PhoneCall size={18} />
          <span><strong>{calls.length}</strong><small>Total conversations</small></span>
        </div>
        <div className={styles["header-wave"]} aria-hidden="true" />
      </header>

      <section className={styles.metrics} aria-label="Call metrics">
        <CallMetric tone="teal" icon={<PhoneCall size={21} />} label="Total conversations" value={String(calls.length)} detail="Phone and browser sessions" values={metrics.sparkValues} />
        <CallMetric tone="cyan" icon={<Activity size={21} />} label="Answered rate" value={`${metrics.answerRate}%`} detail="Successful conversations" ring={metrics.answerRate} values={metrics.sparkValues} />
        <CallMetric tone="blue" icon={<Clock3 size={21} />} label="Avg. duration" value={formatDurationSeconds(metrics.averageDuration)} detail="Across completed calls" values={metrics.sparkValues} />
        <CallMetric tone="violet" icon={<CalendarCheck2 size={21} />} label="Bookings made" value={String(metrics.bookingCount)} detail="Confirmed routed events" values={metrics.sparkValues} />
      </section>

      <section className={styles.toolbar}>
        <div className={styles.segmented} aria-label="Call type filter">
          <button className={filter === "all" ? styles.activeSegment : ""} onClick={() => setFilter("all")} type="button">
            <SlidersHorizontal size={16} /> All
          </button>
          <button className={filter === "phone" ? styles.activeSegment : ""} onClick={() => setFilter("phone")} type="button">
            <PhoneCall size={16} /> Calls
          </button>
          <button className={filter === "test" ? styles.activeSegment : ""} onClick={() => setFilter("test")} type="button">
            <PlayCircle size={16} /> Tests
          </button>
        </div>
        <div className={`${styles.dateRange} ${fromDate || toDate ? styles.activeDateRange : ""}`}><CalendarDays size={15} /><label><span>From</span><input aria-label="Calls from date" type="date" value={fromDate} onChange={(event) => updateFromDate(event.target.value)} /></label><i>–</i><label><span>To</span><input aria-label="Calls to date" type="date" value={toDate} onChange={(event) => updateToDate(event.target.value)} /></label>{(fromDate || toDate) && <button aria-label="Clear date range" onClick={() => { setFromDate(""); setToDate(""); }} type="button"><X size={13} /></button>}</div>
        <label className={styles.selectControl}><Activity size={15} /><select aria-label="Filter calls by status" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="all">All status</option><option value="answered">Answered</option><option value="missed">Missed</option><option value="active">Active</option></select><ChevronDown size={14} /></label>
        <label className={styles.selectControl}><Bot size={15} /><select aria-label="Filter calls by agent" value={agentFilter} onChange={(event) => setAgentFilter(event.target.value)}><option value="all">All agents</option>{agents.map((agent) => <option key={agent.id} value={agent.id}>{agent.name}</option>)}</select><ChevronDown size={14} /></label>
        <label className={styles.search}>
          <Search size={17} />
          <input
            aria-label="Search calls"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search agent, number, intent, booking..."
            value={query}
          />
        </label>
      </section>

      {error && <div className={styles.error}>{error}</div>}

      {loading ? (
        <div className={styles.loading}><LoaderCircle className="spin" size={22} /> Loading calls...</div>
      ) : calls.length === 0 ? (
        <section className={styles.empty}><span><PhoneCall size={24} /></span><h2>No calls yet</h2><p>Start a browser test from an agent configuration. The transcript and recording will appear here.</p></section>
      ) : (
        <section className={`${styles.workspace} ${selectedCall ? styles.withPanel : ""}`}>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Call type</th>
                  <th>Date</th>
                  <th>Incoming phone number</th>
                  <th>Routed event</th>
                  <th>Duration</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {pagedCalls.map((call) => {
                  const booking = bookingsByCall.get(call.id) ?? null;
                  const selected = selectedId === call.id;
                  return (
                    <tr className={selected ? styles.selectedRow : ""} key={call.id} onClick={() => void openDetails(call.id)}>
                      <td>
                        <div className={styles.typeCell}>
                          <span className={`${styles.typeIcon} ${call.direction === "test" ? styles.testIcon : styles.phoneIcon}`}>
                            {call.direction === "test" ? <Bot size={17} /> : <PhoneIncoming size={17} />}
                          </span>
                          <span>
                            <strong>{callTitle(call, agentNames.get(call.agentId))}</strong>
                            <small>{call.direction === "test" ? "Browser test" : `${humanize(call.direction)} phone call`}</small>
                          </span>
                        </div>
                      </td>
                      <td><DateCell value={call.startedAt} /></td>
                      <td className={styles.muted}>{call.direction === "test" ? "-" : call.callerNumber || "-"}</td>
                      <td><RoutedEvent booking={booking} call={call} /></td>
                      <td>{formatDurationSeconds(call.durationSeconds)}</td>
                      <td><StatusBadge outcome={call.outcome} /></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {filteredCalls.length === 0 && (
              <div className={styles.noResults}>No calls match this filter.</div>
            )}
            {filteredCalls.length > 0 && <footer className={styles.pagination}>
              <span>Showing {(page - 1) * ROWS_PER_PAGE + 1} to {Math.min(page * ROWS_PER_PAGE, filteredCalls.length)} of {filteredCalls.length} results</span>
              <div><button disabled={page === 1} onClick={() => setPage((current) => Math.max(1, current - 1))} type="button"><ArrowLeft size={14} /></button>{Array.from({ length: pageCount }, (_, index) => <button className={page === index + 1 ? styles.activePage : ""} onClick={() => setPage(index + 1)} type="button" key={index}>{index + 1}</button>)}<button disabled={page === pageCount} onClick={() => setPage((current) => Math.min(pageCount, current + 1))} type="button"><ArrowRight size={14} /></button></div>
              <small>{ROWS_PER_PAGE} rows per page</small>
            </footer>}
          </div>

          {selectedCall && (
            <aside className={styles.panel}>
              <div className={styles.panelHeader}>
                <div>
                  <strong><FileText size={18} /> Call transcript</strong>
                  <span>{callTitle(selectedCall, agentNames.get(selectedCall.agentId))}</span>
                </div>
                <button aria-label="Close transcript" onClick={() => setSelectedId("")} type="button"><X size={18} /></button>
              </div>

              <section className={styles.panelSection}>
                <button className={styles.sectionTitle} type="button">
                  <span><FileText size={17} /> Details</span>
                  <small>{formatCompactDate(selectedCall.startedAt)}</small>
                  <ChevronDown size={16} />
                </button>
                <div className={styles.detailCard}>
                  <DetailLine icon={CalendarCheck2} label="Booked event" value={selectedBooking ? `${selectedBooking.serviceType} - ${formatDate(selectedBooking.appointmentAt)}` : "No booking"} />
                  <DetailLine icon={PhoneIncoming} label="Direction" value={selectedCall.direction === "test" ? "Browser test" : humanize(selectedCall.direction)} />
                  <DetailLine icon={CheckCircle2} label="Status" value={humanize(selectedCall.outcome)} badge />
                  <DetailLine icon={Clock3} label="Duration" value={formatDurationSeconds(selectedCall.durationSeconds)} />
                  {selectedCall.languageDetected && <DetailLine icon={Headphones} label="Language" value={selectedCall.languageDetected.toUpperCase()} />}
                  {selectedCall.intent && <DetailLine icon={SlidersHorizontal} label="Intent" value={humanize(selectedCall.intent)} />}
                </div>
              </section>

              <section className={styles.panelSection}>
                <div className={styles.sectionStatic}><span><Headphones size={17} /> Recording</span></div>
                <div className={styles.recordingCard}>
                  {selectedCall.recordingUrl ? <RecordingPlayer callId={selectedCall.id} /> : <span className={styles.unavailable}>No recording captured</span>}
                </div>
              </section>

              <section className={styles.panelSection}>
                <div className={styles.sectionStatic}><span><FileText size={17} /> Transcription</span></div>
                <Transcript turns={turns[selectedCall.id]} />
              </section>

              {(selectedCall.callSummary || selectedCall.intent || selectedCall.sentiment || selectedBooking) && <section className={`${styles.panelSection} ${styles["summary-section"]}`}>
                <div className={styles.sectionStatic}><span><Sparkles size={17} /> AI summary</span></div>
                <p>{selectedCall.callSummary || summaryFallback(selectedCall, selectedBooking)}</p>
                <div className={styles["summary-grid"]}>
                  <span><small>Outcome</small><strong>{humanize(selectedCall.outcome)}</strong></span>
                  <span><small>Event</small><strong>{selectedBooking?.serviceType ?? "None"}</strong></span>
                  <span><small>Intent</small><strong>{selectedCall.intent ? humanize(selectedCall.intent) : "Not detected"}</strong></span>
                  <span><small>Sentiment</small><strong>{selectedCall.sentiment ? humanize(selectedCall.sentiment) : "Not analysed"}</strong></span>
                </div>
              </section>}
            </aside>
          )}
        </section>
      )}
    </main>
  );
}

function CallMetric({ tone, icon, label, value, detail, values, ring }: { tone: string; icon: React.ReactNode; label: string; value: string; detail: string; values: number[]; ring?: number }) {
  const max = Math.max(...values, 1);
  const points = values.length > 1 ? values.map((item, index) => `${index * (130 / (values.length - 1))},${26 - item / max * 20}`).join(" ") : "0,20 130,20";
  return <article className={styles.metricCard}>
    {ring === undefined ? <span className={styles[`tone-${tone}`]}>{icon}</span> : <span className={styles.ring} style={{ background: `conic-gradient(#20e0d2 ${ring}%, rgba(114,150,183,.17) 0)` }}><i>{icon}</i></span>}
    <div><small>{label}</small><strong>{value}</strong><p>{detail}</p></div>
    <svg viewBox="0 0 130 30" preserveAspectRatio="none" aria-hidden="true"><polyline points={points} fill="none" stroke={tone === "violet" ? "#a96cff" : "#21dfd1"} strokeWidth="1.6" vectorEffect="non-scaling-stroke" /></svg>
  </article>;
}

function DateCell({ value }: { value: string }) {
  const date = new Date(value);
  return <span className={styles.dateCell}><strong>{new Intl.DateTimeFormat(undefined, { month: "short", day: "2-digit", year: "numeric" }).format(date)}</strong><small>{new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(date)}</small></span>;
}

function RoutedEvent({ booking, call }: { booking: Booking | null; call: Call }) {
  if (booking) {
    return (
      <span className={styles.routedSuccess}>
        <CalendarCheck2 size={15} /> {booking.serviceType}
      </span>
    );
  }
  if (call.transferStatus) {
    return <span className={styles.routedNeutral}>Transfer {humanize(call.transferStatus)}</span>;
  }
  return <span className={styles.routedEmpty}><XCircle size={15} /> No booking</span>;
}

function StatusBadge({ outcome }: { outcome: string }) {
  const successful = ["completed", "booking", "faq_answered", "transferred"].includes(outcome);
  const active = outcome === "active";
  return (
    <span className={`${styles.status} ${active ? styles.statusActive : successful ? styles.statusGood : styles.statusNeutral}`}>
      {active ? "Active" : successful ? "Answered" : humanize(outcome)}
    </span>
  );
}

function DetailLine({ icon: Icon, label, value, badge = false }: { icon: typeof CalendarCheck2; label: string; value: string; badge?: boolean }) {
  return (
    <div className={styles.detailLine}>
      <Icon size={15} />
      <span>{label}</span>
      {badge ? <strong className={styles.inlineBadge}>{value}</strong> : <strong>{value}</strong>}
    </div>
  );
}

function Transcript({ turns }: { turns: CallTurn[] | undefined }) {
  if (!turns) return <div className={styles.transcriptLoading}><LoaderCircle className="spin" size={18} /> Loading transcript...</div>;
  if (turns.length === 0) return <p className={styles.noTranscript}>No transcript was captured.</p>;
  return (
    <div className={styles.transcript}>
      {turns.map((turn) => (
        <div className={styles.turnGroup} key={turn.turnIndex}>
          {turn.callerTranscript && <TranscriptLine role="Caller" text={turn.callerTranscript} time={turn.turnIndex} />}
          {turn.agentResponse && <TranscriptLine role="Agent" text={turn.agentResponse} time={turn.turnIndex} />}
        </div>
      ))}
    </div>
  );
}

function TranscriptLine({ role, text, time }: { role: "Agent" | "Caller"; text: string; time: number }) {
  return (
    <article className={styles.transcriptLine}>
      <span className={role === "Agent" ? styles.agentDot : styles.callerDot} />
      <div>
        <header><strong>{role}</strong><small>{formatTurnTime(time)}</small></header>
        <p>{text}</p>
      </div>
    </article>
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
        const player = WaveSurfer.create({
          container: waveformRef.current,
          url: currentUrl,
          height: 48,
          waveColor: "#3a657b",
          progressColor: "#22dfd1",
          cursorColor: "#75fff4",
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

  if (failed) return <span className={styles.unavailable}>Could not load recording</span>;
  return (
    <div className={styles.wavePlayer}>
      <button disabled={!ready} onClick={() => void playerRef.current?.playPause()} aria-label={playing ? "Pause recording" : "Play recording"} type="button">{!ready ? <LoaderCircle className="spin" size={16} /> : playing ? <Pause size={15} /> : <Play size={15} />}</button>
      <span>{formatAudioTime(currentTime)}</span>
      <div className={styles.waveform} ref={waveformRef} />
      <span>{formatAudioTime(duration)}</span>
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
  const seconds = Math.max(0, turnIndex * 8);
  return `00:${String(seconds).padStart(2, "0")}`;
}

function humanize(value: string) {
  return value.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function localDateKey(value: string) {
  const date = new Date(value);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
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

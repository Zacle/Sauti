"use client";

import { useEffect, useMemo, useState } from "react";
import {
  Bot,
  CalendarCheck2,
  CheckCircle2,
  ChevronDown,
  Clock3,
  FileText,
  Headphones,
  LoaderCircle,
  Mic2,
  PhoneCall,
  PhoneIncoming,
  PlayCircle,
  Search,
  SlidersHorizontal,
  UserRound,
  X,
  XCircle,
} from "lucide-react";
import { listAgents } from "@/lib/api/agents";
import { listBookings } from "@/lib/api/bookings";
import { getCallRecording, listCalls, listCallTurns } from "@/lib/api/calls";
import type { Agent, Booking, Call, CallTurn } from "@/types/api";
import styles from "./CallsPage.module.css";

type CallFilter = "all" | "phone" | "test";

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
  }, [agentNames, bookingsByCall, calls, filter, query]);

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
      </header>

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
                {filteredCalls.map((call) => {
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
                      <td>{formatCompactDate(call.startedAt)}</td>
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
            </aside>
          )}
        </section>
      )}
    </main>
  );
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
          {turn.agentResponse && <TranscriptLine role="Agent" text={turn.agentResponse} time={turn.turnIndex} />}
          {turn.callerTranscript && <TranscriptLine role="Caller" text={turn.callerTranscript} time={turn.turnIndex} />}
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
  const [url, setUrl] = useState("");
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let currentUrl = "";
    getCallRecording(callId)
      .then((blob) => {
        currentUrl = URL.createObjectURL(blob);
        setUrl(currentUrl);
      })
      .catch(() => setFailed(true));
    return () => {
      if (currentUrl) URL.revokeObjectURL(currentUrl);
    };
  }, [callId]);

  if (failed) return <span className={styles.unavailable}>Could not load recording</span>;
  if (!url) return <LoaderCircle className="spin" size={17} />;
  return (
    <div className={styles.audioShell}>
      <Mic2 size={17} />
      <audio controls preload="metadata" src={url}>Your browser does not support audio playback.</audio>
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
  return `${seconds} sec`;
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

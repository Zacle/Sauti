"use client";

import { useEffect, useMemo, useState } from "react";
import { Bot, ChevronDown, Clock3, LoaderCircle, Mic2, PhoneCall, UserRound } from "lucide-react";
import { listAgents } from "@/lib/api/agents";
import { getCallRecording, listCalls, listCallTurns } from "@/lib/api/calls";
import type { Agent, Call, CallTurn } from "@/types/api";
import styles from "./CallsPage.module.css";

export function CallsPage() {
  const [calls, setCalls] = useState<Call[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [expandedId, setExpandedId] = useState("");
  const [turns, setTurns] = useState<Record<string, CallTurn[]>>({});

  useEffect(() => {
    Promise.all([listCalls(), listAgents()])
      .then(([callItems, agentItems]) => {
        setCalls(callItems);
        setAgents(agentItems);
      })
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load calls."))
      .finally(() => setLoading(false));
  }, []);

  const agentNames = useMemo(
    () => Object.fromEntries(agents.map((agent) => [agent.id, agent.name])),
    [agents],
  );

  async function toggleDetails(callId: string) {
    if (expandedId === callId) {
      setExpandedId("");
      return;
    }
    setExpandedId(callId);
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
        <div><span>Operations</span><h1>Calls and recordings</h1><p>Review every test and phone conversation, its transcript, outcome, and complete recording.</p></div>
        <div className={styles.summary}><PhoneCall size={18} /><span><strong>{calls.length}</strong><small>Total calls</small></span></div>
      </header>

      {error && <div className={styles.error}>{error}</div>}
      {loading ? (
        <div className={styles.loading}><LoaderCircle className="spin" size={22} /> Loading calls...</div>
      ) : calls.length === 0 ? (
        <section className={styles.empty}><span><PhoneCall size={24} /></span><h2>No calls yet</h2><p>Start a browser test from an agent configuration. The transcript and recording will appear here.</p></section>
      ) : (
        <section className={styles.list}>
          {calls.map((call) => {
            const expanded = expandedId === call.id;
            return (
              <article className={styles.card} key={call.id}>
                <button className={styles.cardHead} onClick={() => void toggleDetails(call.id)} type="button">
                  <span className={`${styles.direction} ${call.direction === "test" ? styles.test : ""}`}>
                    {call.direction === "test" ? <Bot size={18} /> : <PhoneCall size={18} />}
                  </span>
                  <span className={styles.identity}>
                    <strong>{agentNames[call.agentId] ?? "Agent call"}</strong>
                    <small>{call.direction === "test" ? "Browser test call" : `${call.direction} call`}{call.afterHours ? " · After hours" : ""} · {formatDate(call.startedAt)}</small>
                  </span>
                  <span className={styles.metadata}><Clock3 size={14} /> {formatDuration(call.durationSeconds)}</span>
                  <span className={`${styles.outcome} ${call.outcome === "active" ? styles.active : ""}`}>{humanize(call.outcome)}</span>
                  <ChevronDown className={expanded ? styles.rotated : ""} size={17} />
                </button>
                <div className={styles.recording}>
                  <div><Mic2 size={16} /><span><strong>Full recording</strong><small>{call.recordingUrl ? "Caller and agent audio" : call.outcome === "active" ? "Recording in progress" : "No recording was captured"}</small></span></div>
                  {call.recordingUrl ? <RecordingPlayer callId={call.id} /> : <span className={styles.unavailable}>Unavailable</span>}
                </div>
                {expanded && (
                  <div className={styles.details}>
                    <div className={styles.transcript}>
                      <h3>Conversation</h3>
                      {!turns[call.id] ? <LoaderCircle className="spin" size={18} /> : turns[call.id].length ? turns[call.id].map((turn) => (
                        <div className={styles.turn} key={turn.turnIndex}>
                          {turn.callerTranscript && <div className={styles.caller}><span><UserRound size={13} /> Caller</span><p>{turn.callerTranscript}</p></div>}
                          {turn.agentResponse && <div className={styles.agent}><span><Bot size={13} /> Agent</span><p>{turn.agentResponse}</p></div>}
                        </div>
                      )) : <p className={styles.noTranscript}>No transcript was captured.</p>}
                    </div>
                    <dl>
                      <div><dt>Language</dt><dd>{call.languageDetected?.toUpperCase() ?? "—"}</dd></div>
                      <div><dt>Intent</dt><dd>{call.intent ? humanize(call.intent) : "—"}</dd></div>
                      <div><dt>Sentiment</dt><dd>{call.sentiment ? humanize(call.sentiment) : "—"}</dd></div>
                      {call.afterHours && <div><dt>Schedule</dt><dd>Outside operating hours</dd></div>}
                      {call.transferStatus && (
                        <div>
                          <dt>Human transfer</dt>
                          <dd>{humanize(call.transferStatus)}</dd>
                          {call.transferTargetNumber && <small>{call.transferTargetNumber}</small>}
                          {call.transferFailureReason && <small className={styles.transferFailure}>{call.transferFailureReason}</small>}
                        </div>
                      )}
                      <div><dt>Started</dt><dd>{formatDate(call.startedAt)}</dd></div>
                    </dl>
                  </div>
                )}
              </article>
            );
          })}
        </section>
      )}
    </main>
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

  if (failed) return <span className={styles.unavailable}>Could not load</span>;
  if (!url) return <LoaderCircle className="spin" size={17} />;
  return <audio controls preload="metadata" src={url}>Your browser does not support audio playback.</audio>;
}

function formatDuration(seconds: number | null) {
  if (seconds === null) return "In progress";
  const minutes = Math.floor(seconds / 60);
  const remaining = seconds % 60;
  return `${minutes}:${String(remaining).padStart(2, "0")}`;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function humanize(value: string) {
  return value.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

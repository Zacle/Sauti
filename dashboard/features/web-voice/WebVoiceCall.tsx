"use client";

import { useEffect, useRef, useState, type CSSProperties } from "react";
import { Languages, Mic, PhoneOff, ShieldCheck, Sparkles } from "lucide-react";
import {
  completePublicWebVoiceSession,
  getPublicWebVoiceAgent,
  recordPublicRealtimeTranscript,
  startPublicWebVoiceSession,
  type PublicWebVoiceAgent,
} from "@/lib/api/public-web-voice";
import {
  connectBrowserVoiceRuntime,
  type BrowserVoiceRuntimeConnection,
} from "@/features/voice-runtime/browserVoiceRuntime";
import {
  configuredLanguageHint,
  displayLanguage,
} from "@/features/voice-runtime/languagePreference";
import styles from "./WebVoiceCall.module.css";

type Status = "loading" | "idle" | "connecting" | "live" | "ended" | "error";
type Message = { id: string; role: "agent" | "visitor"; text: string };

export function WebVoiceCall({ publicId }: { publicId: string }) {
  const [agent, setAgent] = useState<PublicWebVoiceAgent | null>(null);
  const [status, setStatus] = useState<Status>("loading");
  const [languageHint, setLanguageHint] = useState("");
  const [consent, setConsent] = useState(false);
  const [speaking, setSpeaking] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [error, setError] = useState("");
  const connectionRef = useRef<BrowserVoiceRuntimeConnection | null>(null);
  const sessionIdRef = useRef("");
  const tokenRef = useRef("");
  const captionIdRef = useRef("");
  const endingRef = useRef(false);
  const accent = "#39d4c0";

  useEffect(() => {
    getPublicWebVoiceAgent(publicId)
      .then((loaded) => {
        setAgent(loaded);
        setLanguageHint(configuredLanguageHint(loaded.languages, loaded.defaultLanguage));
        setConsent(!loaded.consentRequired);
        setStatus("idle");
      })
      .catch((caught) => {
        setError(caught instanceof Error ? caught.message : "Unable to load this voice agent.");
        setStatus("error");
      });
    return () => {
      void connectionRef.current?.stop();
      connectionRef.current = null;
    };
  }, [publicId]);

  function append(role: Message["role"], text: string) {
    const normalized = text.trim();
    if (!normalized) return;
    setMessages((current) => {
      const latest = current.at(-1);
      if (latest?.role === role && latest.text === normalized) return current;
      return [...current, { id: crypto.randomUUID(), role, text: normalized }];
    });
  }

  function caption(text: string) {
    const normalized = text.trim();
    if (!normalized) return;
    setMessages((current) => {
      const id = captionIdRef.current || crypto.randomUUID();
      captionIdRef.current = id;
      const index = current.findIndex((message) => message.id === id);
      if (index < 0) return [...current, { id, role: "agent", text: normalized }];
      const next = [...current];
      next[index] = { ...next[index], text: normalized };
      return next;
    });
  }

  async function start() {
    if (!agent || status === "connecting") return;
    setStatus("connecting");
    setError("");
    setMessages([]);
    endingRef.current = false;
    try {
      const session = await startPublicWebVoiceSession(
        publicId,
        consent,
        window.location.origin,
        languageHint || agent.defaultLanguage,
      );
      if (!session.runtime || session.runtime.provider.toLowerCase() !== "telnyx") {
        throw new Error("The voice agent did not return a Telnyx session.");
      }
      sessionIdRef.current = session.sessionId;
      tokenRef.current = session.token;
      connectionRef.current = await connectBrowserVoiceRuntime(session.runtime, {
        onConnected() {
          setStatus("live");
        },
        onCallerSpeechStarted() {
          setSpeaking(false);
        },
        onCallerSpeechEnded() {
          // Telnyx owns endpointing and response generation.
        },
        onCallerTranscript(text) {
          append("visitor", text);
          void recordPublicRealtimeTranscript(
            session.sessionId,
            session.token,
            "caller",
            text,
          ).catch(() => undefined);
        },
        onAgentCaption(text) {
          caption(text);
        },
        onAgentTranscript(text, interrupted) {
          caption(text);
          captionIdRef.current = "";
          void recordPublicRealtimeTranscript(
            session.sessionId,
            session.token,
            "agent",
            text,
            interrupted,
          ).catch(() => undefined);
        },
        onAgentSpeaking(value) {
          setSpeaking(value);
        },
        onInterrupted() {
          captionIdRef.current = "";
          setSpeaking(false);
        },
        onError(message) {
          setError(message);
        },
        onEnded() {
          void end(false);
        },
      });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to start this conversation.");
      setStatus("error");
    }
  }

  async function end(stopProvider = true) {
    if (endingRef.current) return;
    endingRef.current = true;
    try {
      if (stopProvider) await connectionRef.current?.stop();
      if (sessionIdRef.current && tokenRef.current) {
        await completePublicWebVoiceSession(
          sessionIdRef.current,
          tokenRef.current,
          connectionRef.current?.providerCallControlId() ?? "",
        );
      }
    } catch {
      // The provider may already have ended the session.
    } finally {
      connectionRef.current = null;
      sessionIdRef.current = "";
      tokenRef.current = "";
      captionIdRef.current = "";
      setSpeaking(false);
      setStatus("ended");
    }
  }

  const pageStyle = { "--web-voice-accent": accent } as CSSProperties;
  return (
    <main className={styles.page} style={pageStyle}>
      <section className={styles.card}>
        <header>
          <span><Sparkles size={22} /></span>
          <div><small>WEB VOICE · TELNYX</small><h1>{agent?.name ?? "Voice assistant"}</h1></div>
          <i className={status === "live" ? styles.online : ""}>{status}</i>
        </header>
        <div className={`${styles.orb} ${speaking ? styles.speaking : ""}`}><Mic size={34} /></div>
        <h2>{status === "live" ? speaking ? `${agent?.name} is speaking` : "Listening to you" : status === "ended" ? "Conversation ended" : "Talk with our assistant"}</h2>
        <p>{agent?.description ?? "Loading the voice assistant…"}</p>
        {status === "idle" && agent && agent.languages.length > 1 && (
          <div className={styles.languageAuto}>
            <Languages size={18} />
            <span>
              <strong>Speak naturally in your language</strong>
              Automatic detection · greeting starts in {displayLanguage(languageHint)}
            </span>
          </div>
        )}
        {status === "idle" && agent?.consentRequired && (
          <label className={styles.consent}>
            <input type="checkbox" checked={consent} onChange={(event) => setConsent(event.target.checked)} />
            <ShieldCheck size={18} />
            <span>I agree to use my microphone for this conversation{agent.recordingEnabled ? " and understand that it will be recorded" : ""}.</span>
          </label>
        )}
        {status === "idle" && <button className={styles.start} disabled={!consent} onClick={() => void start()}><Mic size={18} /> Start conversation</button>}
        {status === "connecting" && <button className={styles.start} disabled><span className={styles.spinner} /> Connecting…</button>}
        {status === "live" && <button className={styles.end} onClick={() => void end()}><PhoneOff size={18} /> End conversation</button>}
        {status === "ended" && <button className={styles.start} onClick={() => setStatus("idle")}><Mic size={18} /> Start another conversation</button>}
        {error && <div className={styles.error}>{error}</div>}
        <div className={styles.transcript}>
          {messages.length === 0 && <span>The conversation transcript will appear here.</span>}
          {messages.map((message) => (
            <div className={message.role === "agent" ? styles.agent : styles.visitor} key={message.id}>
              <small>{message.role === "agent" ? agent?.name : "You"}</small>
              <p>{message.text}</p>
            </div>
          ))}
        </div>
        <footer><ShieldCheck size={13} /> Voice powered by Telnyx</footer>
      </section>
    </main>
  );
}

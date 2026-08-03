"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ArrowRight, Mic, PhoneOff, ShieldCheck, Sparkles, X } from "lucide-react";
import { AiVoiceAnimation, type VoiceAnimationActivity } from "@/features/agents/AgentCreator/AiVoiceAnimation";
import {
  connectBrowserVoiceRuntime,
  prepareBrowserMicrophone,
  preconnectBrowserVoiceRuntime,
  preloadBrowserVoiceRuntime,
  releasePreconnectedBrowserVoiceRuntime,
  type BrowserMicrophoneCapture,
  type BrowserVoiceRuntimeConnection,
} from "@/features/voice-runtime/browserVoiceRuntime";
import {
  completePublicDemoVoiceSession,
  getPublicDemoVoiceConfiguration,
  startPublicDemoVoiceSession,
  type PublicDemoVoiceConfiguration,
} from "@/lib/api/public-demo-voice";
import styles from "./PublicDemoVoice.module.css";

type Readiness = "preparing" | "ready" | "unavailable";
type CallState = "idle" | "connecting" | "listening" | "thinking" | "speaking" | "ending" | "ended";

function deviceId() {
  const key = "sauti-public-demo-device";
  const existing = window.localStorage.getItem(key);
  if (existing) return existing;
  const created = crypto.randomUUID();
  window.localStorage.setItem(key, created);
  return created;
}

function safeMessage(value: unknown) {
  return (value instanceof Error ? value.message : "The voice demo is temporarily unavailable.")
    .replaceAll(/Telnyx/gi, "voice service")
    .replaceAll(/agent id/gi, "configuration");
}

export function PublicDemoVoice() {
  const [configuration, setConfiguration] = useState<PublicDemoVoiceConfiguration | null>(null);
  const [readiness, setReadiness] = useState<Readiness>("preparing");
  const [callState, setCallState] = useState<CallState>("idle");
  const [visible, setVisible] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(60);
  const [error, setError] = useState("");
  const connectionRef = useRef<BrowserVoiceRuntimeConnection | null>(null);
  const sessionRef = useRef({ id: "", token: "" });
  const microphoneRef = useRef<BrowserMicrophoneCapture | null>(null);
  const timerRef = useRef<number | undefined>(undefined);
  const closingPromptRef = useRef(false);
  const endingRef = useRef(false);
  const mountedRef = useRef(true);

  const prepare = useCallback(async () => {
    setReadiness("preparing");
    setError("");
    try {
      await preloadBrowserVoiceRuntime();
      const loaded = await getPublicDemoVoiceConfiguration(window.location.origin);
      await preconnectBrowserVoiceRuntime(loaded.runtime);
      if (!mountedRef.current) {
        await releasePreconnectedBrowserVoiceRuntime();
        return;
      }
      setConfiguration(loaded);
      setSecondsLeft(loaded.maxDurationSeconds);
      setReadiness("ready");
    } catch (caught) {
      if (!mountedRef.current) return;
      setConfiguration(null);
      setError(safeMessage(caught));
      setReadiness("unavailable");
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    void prepare();
    return () => {
      mountedRef.current = false;
      if (timerRef.current !== undefined) window.clearInterval(timerRef.current);
      void connectionRef.current?.stop();
      void microphoneRef.current?.stop();
      void releasePreconnectedBrowserVoiceRuntime();
      if (sessionRef.current.id) {
        void completePublicDemoVoiceSession(sessionRef.current.id, sessionRef.current.token);
      }
    };
  }, [prepare]);

  function startTimer(maxDurationSeconds: number) {
    const startedAt = Date.now();
    setSecondsLeft(maxDurationSeconds);
    timerRef.current = window.setInterval(() => {
      const elapsed = Math.floor((Date.now() - startedAt) / 1000);
      const remaining = Math.max(0, maxDurationSeconds - elapsed);
      setSecondsLeft(remaining);
      if (remaining <= 8 && !closingPromptRef.current) {
        closingPromptRef.current = true;
        connectionRef.current?.sendUserText("Please close this short public demonstration with one brief final sentence.");
      }
      if (remaining === 0) void finish(true);
    }, 500);
  }

  async function start() {
    if (!configuration || readiness !== "ready" || callState !== "idle") return;
    setError("");
    setCallState("connecting");
    endingRef.current = false;
    closingPromptRef.current = false;
    let microphone: BrowserMicrophoneCapture | null = null;
    try {
      const [session, preparedMicrophone] = await Promise.all([
        startPublicDemoVoiceSession(deviceId(), window.location.origin),
        prepareBrowserMicrophone(),
      ]);
      microphone = preparedMicrophone;
      microphoneRef.current = preparedMicrophone;
      sessionRef.current = { id: session.sessionId, token: session.token };
      connectionRef.current = await connectBrowserVoiceRuntime(session.runtime, {
        onConnected() {
          setCallState("listening");
          startTimer(session.maxDurationSeconds);
        },
        onCallerSpeechStarted() {
          setCallState("listening");
        },
        onCallerSpeechEnded() {
          setCallState("thinking");
        },
        onCallerTranscript() {
          setCallState("thinking");
        },
        onAgentCaption(text) {
          if (text.trim()) setCallState("speaking");
        },
        onAgentTranscript() {},
        onAgentSpeaking(speaking) {
          if (speaking) setVisible(true);
          setCallState(speaking ? "speaking" : "listening");
        },
        onInterrupted() {
          setCallState("listening");
        },
        onError(message) {
          setError(safeMessage(new Error(message)));
        },
        onEnded() {
          void finish(false);
        },
      }, { microphone: preparedMicrophone });
    } catch (caught) {
      await microphone?.stop();
      microphoneRef.current = null;
      setCallState("idle");
      setError(safeMessage(caught));
      if (sessionRef.current.id) {
        await completePublicDemoVoiceSession(sessionRef.current.id, sessionRef.current.token).catch(() => undefined);
        sessionRef.current = { id: "", token: "" };
      }
    }
  }

  async function finish(stopProvider: boolean) {
    if (endingRef.current) return;
    endingRef.current = true;
    setCallState("ending");
    if (timerRef.current !== undefined) window.clearInterval(timerRef.current);
    timerRef.current = undefined;
    try {
      if (stopProvider) await connectionRef.current?.stop();
      if (sessionRef.current.id) {
        await completePublicDemoVoiceSession(sessionRef.current.id, sessionRef.current.token);
      }
    } catch {
      // Provider and quota leases expire independently if either side already closed.
    } finally {
      connectionRef.current = null;
      microphoneRef.current = null;
      sessionRef.current = { id: "", token: "" };
      setCallState("ended");
      setVisible(true);
    }
  }

  const activity: VoiceAnimationActivity = callState === "speaking"
    ? "speaking"
    : callState === "thinking"
      ? "thinking"
      : callState === "listening"
        ? "listening"
        : "calm";
  const status = callState === "speaking"
    ? "Sauti is speaking"
    : callState === "thinking"
      ? "Thinking"
      : callState === "ending"
        ? "Closing the conversation"
        : "Ready when you are";

  return (
    <>
      <button
        className={styles.trigger}
        disabled={readiness === "preparing" || callState !== "idle"}
        onClick={() => readiness === "unavailable" ? void prepare() : void start()}
        type="button"
      >
        <Mic size={15} /> {!visible && callState !== "idle" ? "Starting voice…" : readiness === "unavailable" ? "Retry voice demo" : "Talk to Sauti"}
      </button>
      {error && !visible ? <span className={styles.triggerError}>{error}</span> : null}
      {visible ? (
        <div className={styles.backdrop} role="dialog" aria-modal="true" aria-label="Talk to Sauti voice demo">
          <section className={styles.dialog}>
            <header>
              <div><Sparkles size={18} /><span><small>LIVE SAUTI DEMO</small><strong>Talk to Sauti</strong></span></div>
              <button aria-label="Close voice demo" onClick={() => callState === "ended" ? setVisible(false) : void finish(true)} type="button"><X size={19} /></button>
            </header>
            {callState === "ended" ? (
              <div className={styles.ended}>
                <span><ShieldCheck size={25} /></span>
                <h2>That was Sauti in action.</h2>
                <p>Tell us about your customer journey and we’ll prepare a tailored demonstration for your business.</p>
                <Link href="/request-demo">Request a tailored demo <ArrowRight size={16} /></Link>
              </div>
            ) : (
              <>
                <div className={styles.animation}><AiVoiceAnimation activity={activity} /></div>
                <div className={styles.liveStatus}><strong>{status}</strong><span>{secondsLeft}s remaining</span></div>
                <p className={styles.hint}>Ask about voice agents, supported channels, languages, integrations, or how Sauti could fit your business.</p>
                {error ? <div className={styles.error}>{error}</div> : null}
                <button className={styles.end} onClick={() => void finish(true)} type="button"><PhoneOff size={16} /> End conversation</button>
                <small className={styles.privacy}><ShieldCheck size={13} /> One-minute product demo. No customer tools or workspace data are available.</small>
              </>
            )}
          </section>
        </div>
      ) : null}
    </>
  );
}

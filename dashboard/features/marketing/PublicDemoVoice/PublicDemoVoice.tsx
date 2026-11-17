"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { ArrowRight, Check, LoaderCircle, Mic, PhoneOff, ShieldCheck, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
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
  recordPublicDemoStartupLatency,
  startPublicDemoVoiceSession,
  type PublicDemoVoiceConfiguration,
} from "@/lib/api/public-demo-voice";
import { trackPublicAnalyticsEvent } from "@/lib/api/public-analytics";

const DEMO_PROMPTS = [
  "Can you book appointments for a dental clinic?",
  "Which languages can you speak?",
  "How would you handle a caller after hours?",
];

type Readiness ="preparing" | "ready" | "unavailable";
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
  const startupLatencyRecordedRef = useRef(false);

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

  useEffect(() => {
    if (!visible) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [visible]);

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
    startupLatencyRecordedRef.current = false;
    trackPublicAnalyticsEvent("voice_demo_started");
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
        onLatencyMeasured(kind, latencyMs) {
          if (kind !== "greeting" || startupLatencyRecordedRef.current) return;
          startupLatencyRecordedRef.current = true;
          void recordPublicDemoStartupLatency(session.sessionId, session.token, latencyMs)
            .catch(() => undefined);
        },
        onInterrupted() {
          setCallState("listening");
        },
        onError(message) {
          if (endingRef.current) return;
          setError(safeMessage(new Error(message)));
        },
        onEnded() {
          void finish(false);
        },
      }, { microphone: preparedMicrophone });
    } catch (caught) {
      trackPublicAnalyticsEvent("voice_demo_failed");
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
      trackPublicAnalyticsEvent("voice_demo_completed");
    } catch {
      // Provider and quota leases expire independently if either side already closed.
    } finally {
      connectionRef.current = null;
      microphoneRef.current = null;
      sessionRef.current = { id: "", token: "" };
      setError("");
      setCallState("ended");
      setVisible(true);
    }
  }

  function closeDialog() {
    setVisible(false);
    if (callState !== "ended") {
      void finish(true);
      return;
    }
    endingRef.current = false;
    closingPromptRef.current = false;
    setSecondsLeft(configuration?.maxDurationSeconds ?? 60);
    setError("");
    setCallState("idle");
  }

  const activity: VoiceAnimationActivity = callState === "speaking"
    ? "speaking"
    : callState === "thinking"
      ? "thinking"
      : callState === "listening"
        ? "listening"
        : "calm";
  const status = callState === "speaking"
    ? { title: "Sauti is speaking", detail: "Interrupt any time. Just start talking." }
    : callState === "thinking"
      ? { title: "Thinking…", detail: "Working out the best answer." }
      : callState === "listening"
        ? { title: "Listening", detail: "Go ahead and ask a question." }
        : callState === "ending"
          ? { title: "Wrapping up", detail: "Closing the conversation." }
          : { title: "Connecting…", detail: "Getting the line ready." };
  const maxSeconds = configuration?.maxDurationSeconds ?? 60;
  const remaining = Math.max(0, Math.min(1, secondsLeft / maxSeconds));
  const clock = `${Math.floor(secondsLeft / 60)}:${String(secondsLeft % 60).padStart(2, "0")}`;
  const ended = callState === "ended";
  // Only offer the demo once it is confirmed available (or a call is already running);
  // while checking, or when the service is down, the button is simply not shown.
  const showTrigger = readiness === "ready" || callState !== "idle";

  return (
    <>
      {showTrigger ? (
      <span className="inline-flex flex-col items-start gap-1.5 animate-[fade-in_300ms_ease-out]">
        <Button
          variant="secondary"
          size="lg"
          disabled={callState !== "idle"}
          onClick={() => void start()}
        >
          {callState !== "idle" && !visible ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <Mic aria-hidden="true" />}
          {!visible && callState !== "idle" ? "Connecting…" : "Talk to Sauti"}
        </Button>
        {error && !visible ? <span role="alert" className="max-w-xs text-[0.78rem] leading-snug text-danger">{error}</span> : null}
      </span>
      ) : null}

      <DialogPrimitive.Root open={visible} onOpenChange={(open) => { if (!open) closeDialog(); }}>
        <DialogPrimitive.Portal>
          <DialogPrimitive.Overlay className="fixed inset-0 z-[150] bg-black/70 backdrop-blur-sm data-[state=open]:animate-[fade-in_200ms_ease-out]" />
          <DialogPrimitive.Content
            aria-describedby={undefined}
            onOpenAutoFocus={(event) => event.preventDefault()}
            className="fixed left-1/2 top-1/2 z-[151] w-[min(92vw,26rem)] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-[1.75rem] border border-line bg-surface font-sans text-fg shadow-[0_40px_120px_-30px_rgb(0_0_0/0.9)] outline-none data-[state=open]:animate-[dialog-in_260ms_cubic-bezier(0.16,1,0.3,1)]"
          >
            <header className="flex items-center justify-between gap-3 px-5 pt-5">
              <div className="flex items-center gap-2.5">
                {ended ? (
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-line px-2.5 py-1 font-mono text-[0.66rem] uppercase tracking-[0.14em] text-fg-muted">Call ended</span>
                ) : (
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-signal/25 bg-signal/[0.08] px-2.5 py-1 font-mono text-[0.66rem] uppercase tracking-[0.14em] text-signal">
                    <span aria-hidden="true" className="relative flex size-1.5">
                      <span className="absolute inline-flex size-full animate-ping rounded-full bg-signal opacity-60" />
                      <span className="relative inline-flex size-1.5 rounded-full bg-signal" />
                    </span>
                    Live
                  </span>
                )}
                <DialogPrimitive.Title className="m-0 text-[0.92rem] font-medium text-ink">Talk to Sauti</DialogPrimitive.Title>
              </div>
              <DialogPrimitive.Close asChild>
                <Button variant="ghost" size="icon-xs" aria-label="Close voice demo" className="-mr-1.5"><X /></Button>
              </DialogPrimitive.Close>
            </header>

            {ended ? (
              <div className="grid justify-items-center gap-3 px-7 pb-7 pt-10 text-center">
                <span className="grid size-12 place-items-center rounded-full border border-signal/30 bg-signal/10 text-signal"><Check className="size-5" aria-hidden="true" /></span>
                <h2 className="m-0 mt-2 text-[1.6rem] font-medium leading-tight tracking-[-0.02em] text-ink">That was Sauti.</h2>
                <p className="m-0 max-w-[19rem] text-[0.9rem] leading-relaxed text-fg-soft">
                  Imagine it answering your phone, in your customers’ languages, and booking straight into your calendar.
                </p>
                <div className="mt-4 grid w-full gap-2">
                  <Button asChild size="lg" className="w-full">
                    <Link href="/request-demo" onClick={() => setVisible(false)}>Book a tailored demo <ArrowRight aria-hidden="true" /></Link>
                  </Button>
                  <DialogPrimitive.Close asChild>
                    <Button variant="ghost" size="md" className="w-full">Close</Button>
                  </DialogPrimitive.Close>
                </div>
              </div>
            ) : (
              <div className="grid justify-items-center px-6 pb-6">
                <div className="relative mt-2 grid place-items-center">
                  <div aria-hidden="true" className="pointer-events-none absolute size-56 rounded-full bg-signal/10 blur-3xl" />
                  <AiVoiceAnimation activity={activity} size={208} />
                </div>

                <div className="mt-1 grid justify-items-center gap-1 text-center" aria-live="polite">
                  <p className="m-0 text-[1.05rem] font-medium text-ink">{status.title}</p>
                  <p className="m-0 text-[0.84rem] text-fg-muted">{status.detail}</p>
                </div>

                <div className="mt-5 w-full">
                  <div className="flex items-center justify-between text-[0.72rem] text-fg-muted">
                    <span>Demo time left</span>
                    <span className="font-mono tabular-nums text-fg-soft">{clock}</span>
                  </div>
                  <div className="mt-1.5 h-1 overflow-hidden rounded-full bg-tint/[0.08]">
                    <div
                      className={cn("h-full rounded-full transition-[width] duration-500 ease-linear", secondsLeft <= 10 ? "bg-warning" : "bg-signal")}
                      style={{ width: `${remaining * 100}%` }}
                    />
                  </div>
                </div>

                <div className="mt-5 w-full rounded-2xl border border-line bg-tint/[0.02] p-4">
                  <p className="m-0 font-mono text-[0.66rem] uppercase tracking-[0.14em] text-fg-muted">Try asking</p>
                  <ul className="m-0 mt-2.5 grid list-none gap-2 p-0 text-[0.86rem] text-fg-soft">
                    {DEMO_PROMPTS.map((prompt) => (
                      <li key={prompt} className="flex gap-2"><span aria-hidden="true" className="text-signal">“</span>{prompt}</li>
                    ))}
                  </ul>
                </div>

                {error ? <p role="alert" className="m-0 mt-4 w-full rounded-xl border border-danger/30 bg-danger/[0.08] px-3.5 py-2.5 text-[0.82rem] text-danger">{error}</p> : null}

                <Button
                  variant="danger-soft"
                  size="md"
                  className="mt-5 w-full rounded-full"
                  disabled={callState === "ending"}
                  onClick={() => void finish(true)}
                >
                  <PhoneOff aria-hidden="true" /> {callState === "ending" ? "Ending…" : "End call"}
                </Button>
                <p className="m-0 mt-3 flex items-center gap-1.5 text-[0.72rem] text-fg-muted">
                  <ShieldCheck className="size-3.5" aria-hidden="true" /> One-minute demo. No customer data is used.
                </p>
              </div>
            )}
          </DialogPrimitive.Content>
        </DialogPrimitive.Portal>
      </DialogPrimitive.Root>
    </>
  );
}

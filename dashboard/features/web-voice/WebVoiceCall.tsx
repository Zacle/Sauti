"use client";

import { useEffect, useRef, useState } from "react";
import { motion } from "motion/react";
import { Languages, LoaderCircle, Mic, PhoneOff, ShieldCheck, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/field";
import { toast } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";
import { PixelGlow, glowPalettes } from "@/features/marketing/VoiceHome/PixelGlow";
import {
  completePublicWebVoiceSession,
  getPublicWebVoiceAgent,
  recordPublicRealtimeTranscript,
  recordPublicWebVoiceStartupLatency,
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
  const startupLatencyRecordedRef = useRef(false);

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
    startupLatencyRecordedRef.current = false;
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
        onLatencyMeasured(kind, latencyMs) {
          if (kind !== "greeting" || startupLatencyRecordedRef.current) return;
          startupLatencyRecordedRef.current = true;
          void recordPublicWebVoiceStartupLatency(session.sessionId, session.token, latencyMs)
            .catch(() => undefined);
        },
        onInterrupted() {
          captionIdRef.current = "";
          setSpeaking(false);
        },
        onError(message) {
          setError(message);
          toast.error("Voice connection problem", { description: message });
        },
        onEnded() {
          void end(false);
        },
      });
    } catch (caught) {
      const text = caught instanceof Error ? caught.message : "Unable to start this conversation.";
      setError(text);
      setStatus("error");
      toast.error("Couldn't start the conversation", { description: text });
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
      toast("Conversation ended", { description: "Thanks for talking with us." });
    }
  }

  const live = status === "live";
  const headline = live
    ? speaking ? `${agent?.name ?? "The assistant"} is speaking` : "Listening to you"
    : status === "ended" ? "Conversation ended" : status === "connecting" ? "Connecting…" : status === "error" && !agent ? "Assistant unavailable" : "Talk with our assistant";

  return (
    <main className="mk-v2 relative isolate grid min-h-dvh place-items-center overflow-hidden bg-canvas px-4 py-10">
      <PixelGlow
        seed={7}
        palette={live ? glowPalettes.signal : glowPalettes.ocean}
        intensity={live ? 0.9 : 0.6}
        className="pointer-events-none absolute left-1/2 top-1/2 -z-10 h-[46rem] w-[64rem] max-w-[220%] -translate-x-1/2 -translate-y-1/2 [mask-image:radial-gradient(closest-side,black_20%,transparent)]"
      />
      <section className="w-full max-w-[460px] overflow-hidden rounded-[2rem] border border-line-strong bg-black/70 shadow-[0_40px_120px_-40px_rgba(0,0,0,0.95)] backdrop-blur-2xl">
        <header className="flex items-center gap-3 border-b border-line px-5 py-4">
          <span className="grid size-9 place-items-center rounded-xl bg-white/[0.06]">
            <Sparkles className="size-4 text-signal" aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="font-mono text-[0.6rem] uppercase tracking-[0.16em] text-fg-muted">Web voice</p>
            <h1 className="truncate text-[0.98rem] font-medium text-white">{agent?.name ?? "Voice assistant"}</h1>
          </div>
          <span
            className={cn(
              "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 font-mono text-[0.62rem] uppercase tracking-wider",
              live ? "bg-signal/15 text-signal" : status === "error" ? "bg-[#ff7a7a]/10 text-[#ff9a9a]" : "bg-white/[0.05] text-fg-muted",
            )}
          >
            <span className={cn("size-1.5 rounded-full", live ? "animate-pulse bg-signal" : "bg-current")} />
            {status}
          </span>
        </header>

        <div className="flex flex-col items-center px-6 pb-6 pt-10 text-center">
          <div className="relative grid size-32 place-items-center" aria-hidden="true">
            {[0, 1, 2].map((ring) => (
              <motion.span
                key={ring}
                className="absolute inset-0 rounded-full border border-signal/30"
                animate={live ? { scale: speaking ? [1, 1.5 + ring * 0.2] : [1, 1.15 + ring * 0.08], opacity: [0.6, 0] } : { scale: 1, opacity: 0 }}
                transition={live ? { duration: speaking ? 1.4 : 2.4, repeat: Infinity, delay: ring * 0.35, ease: "easeOut" } : { duration: 0.3 }}
              />
            ))}
            <motion.span
              className="grid size-24 place-items-center rounded-full bg-[radial-gradient(circle_at_30%_25%,#ffffff,rgb(94_234_212)_40%,rgb(59_130_246)_100%)] text-black shadow-[0_0_80px_rgb(94_234_212/0.45)]"
              animate={{ scale: speaking ? [1, 1.06, 1] : 1 }}
              transition={{ duration: 0.8, repeat: speaking ? Infinity : 0 }}
            >
              <Mic className="size-8" />
            </motion.span>
          </div>

          <h2 className="mt-8 text-2xl font-medium tracking-tight text-white">{headline}</h2>
          <p className="mt-2 max-w-sm text-sm leading-relaxed text-fg-soft">{agent?.description ?? (status === "error" ? "This conversation link isn't available right now. Please check the link or try again later." : "Loading the voice assistant…")}</p>

          {status === "idle" && agent && agent.languages.length > 1 && (
            <p className="mt-6 flex w-full items-start gap-3 rounded-2xl border border-line bg-white/[0.03] px-4 py-3 text-left text-sm">
              <Languages className="mt-0.5 size-4 shrink-0 text-signal" aria-hidden="true" />
              <span>
                <span className="block font-medium text-white">Speak naturally in your language</span>
                <span className="text-fg-soft">Automatic detection · greeting starts in {displayLanguage(languageHint)}</span>
              </span>
            </p>
          )}
          {status === "idle" && agent?.consentRequired && (
            <label className="mt-3 flex w-full cursor-pointer items-start gap-3 rounded-2xl border border-line bg-white/[0.03] px-4 py-3 text-left text-sm text-fg-soft has-[:checked]:border-signal/40 has-[:checked]:bg-signal/[0.06]">
              <input
                type="checkbox"
                checked={consent}
                onChange={(event) => setConsent(event.target.checked)}
                className="mt-0.5 size-4 shrink-0 accent-[#5eead4]"
              />
              <span>
                I agree to use my microphone for this conversation
                {agent.recordingEnabled ? " and understand that it will be recorded" : ""}.
              </span>
            </label>
          )}

          <div className="mt-6 w-full">
            {status === "idle" && (
              <Button size="lg" className="w-full rounded-xl" disabled={!consent} onClick={() => void start()}>
                <Mic aria-hidden="true" /> Start conversation
              </Button>
            )}
            {status === "connecting" && (
              <Button size="lg" className="w-full rounded-xl" disabled>
                <LoaderCircle className="animate-spin" aria-hidden="true" /> Connecting…
              </Button>
            )}
            {live && (
              <Button size="lg" className="w-full rounded-xl bg-[#ff5a5f] text-white hover:bg-[#ff5a5f]/85" onClick={() => void end()}>
                <PhoneOff aria-hidden="true" /> End conversation
              </Button>
            )}
            {status === "ended" && (
              <Button size="lg" variant="secondary" className="w-full rounded-xl" onClick={() => setStatus("idle")}>
                <Mic aria-hidden="true" /> Start another conversation
              </Button>
            )}
          </div>
          {error && <Alert tone="error" className="mt-4 w-full text-left">{error}</Alert>}
        </div>

        <div className="max-h-72 min-h-28 overflow-y-auto border-t border-line px-5 py-4" aria-live="polite">
          {messages.length === 0 ? (
            <p className="py-6 text-center text-sm text-fg-muted">The conversation transcript will appear here.</p>
          ) : (
            <ul className="grid gap-3">
              {messages.map((message) => (
                <li key={message.id} className={cn("max-w-[85%]", message.role === "agent" ? "" : "ml-auto text-right")}>
                  <p className="mb-1 font-mono text-[0.58rem] uppercase tracking-[0.14em] text-fg-muted">
                    {message.role === "agent" ? agent?.name : "You"}
                  </p>
                  <p
                    className={cn(
                      "inline-block rounded-2xl px-3.5 py-2 text-left text-sm leading-snug",
                      message.role === "agent" ? "rounded-tl-md bg-white/[0.07] text-fg" : "rounded-tr-md bg-white text-black",
                    )}
                  >
                    {message.text}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </div>

        <footer className="flex items-center justify-center gap-1.5 border-t border-line py-3 text-xs text-fg-muted">
          <ShieldCheck className="size-3.5" aria-hidden="true" /> Voice powered by Telnyx · Sauti
        </footer>
      </section>
    </main>
  );
}

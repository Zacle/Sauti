"use client";

import { useEffect, useRef, useState } from "react";
import { Download, LoaderCircle, Mic, Phone, PhoneOff, Send, Volume2 } from "lucide-react";
import {
  completeTestCall,
  correlateTestCall,
  recordTestRealtimeTranscript,
  startTestCall,
} from "@/lib/api/calls";
import {
  connectBrowserVoiceRuntime,
  preloadBrowserVoiceRuntime,
  type BrowserVoiceRuntimeConnection,
  warmBrowserMicrophone,
} from "@/features/voice-runtime/browserVoiceRuntime";
import type { VoiceDiagnosticEntry } from "@/features/voice-runtime/voiceDiagnostics";

type TestCallPanelProps = {
  agentId?: string;
  agentName: string;
  voiceId?: string;
};

type Message = {
  id: string;
  role: "caller" | "agent";
  text: string;
};

type CallStatus =
  | "idle"
  | "connecting"
  | "listening"
  | "capturing"
  | "thinking"
  | "working"
  | "speaking"
  | "ending";

export function TestCallPanel({ agentId, agentName, voiceId }: TestCallPanelProps) {
  const [callId, setCallId] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [status, setStatus] = useState<CallStatus>("idle");
  const [error, setError] = useState("");
  const [diagnosticCount, setDiagnosticCount] = useState(0);
  const connectionRef = useRef<BrowserVoiceRuntimeConnection | null>(null);
  const callIdRef = useRef("");
  const statusRef = useRef<CallStatus>("idle");
  const transcriptRef = useRef<HTMLDivElement | null>(null);
  const diagnosticsRef = useRef<VoiceDiagnosticEntry[]>([]);
  const diagnosticsStartedAtRef = useRef(0);
  const diagnosticSequenceRef = useRef(0);
  const agentCaptionIdRef = useRef("");
  const endingRef = useRef(false);
  const firstAgentAudioRef = useRef(false);

  useEffect(() => {
    transcriptRef.current?.scrollTo({
      top: transcriptRef.current.scrollHeight,
      behavior: "smooth",
    });
  }, [messages]);

  useEffect(() => () => {
    void connectionRef.current?.stop();
    connectionRef.current = null;
  }, []);

  useEffect(() => {
    void preloadBrowserVoiceRuntime().catch(() => undefined);
  }, []);

  function updateStatus(next: CallStatus) {
    if (statusRef.current !== next) {
      recordDiagnostic("status_changed", { from: statusRef.current, to: next });
    }
    statusRef.current = next;
    setStatus(next);
  }

  function recordDiagnostic(
    event: string,
    details?: Record<string, string | number | boolean | null>,
    level: "info" | "warn" | "error" = "info",
  ) {
    if (!diagnosticsStartedAtRef.current) return;
    diagnosticSequenceRef.current += 1;
    diagnosticsRef.current.push({
      sequence: diagnosticSequenceRef.current,
      occurredAt: new Date().toISOString(),
      elapsedMs: Date.now() - diagnosticsStartedAtRef.current,
      callId: callIdRef.current,
      runtime: "telnyx",
      status: statusRef.current,
      component: "telnyx",
      event,
      level,
      details,
    });
    setDiagnosticCount(diagnosticsRef.current.length);
  }

  function resetDiagnostics() {
    diagnosticsStartedAtRef.current = Date.now();
    diagnosticSequenceRef.current = 0;
    diagnosticsRef.current = [];
    setDiagnosticCount(0);
    recordDiagnostic("test_started");
  }

  function appendMessage(role: Message["role"], text: string) {
    const normalized = text.trim();
    if (!normalized) return;
    setMessages((current) => {
      const latest = current.at(-1);
      if (latest?.role === role && latest.text === normalized) return current;
      return [...current, {
        id: crypto.randomUUID(),
        role,
        text: normalized,
      }];
    });
  }

  function updateAgentCaption(text: string) {
    const normalized = text.trim();
    if (!normalized) return;
    setMessages((current) => {
      const id = agentCaptionIdRef.current || crypto.randomUUID();
      agentCaptionIdRef.current = id;
      const existing = current.findIndex((message) => message.id === id);
      if (existing < 0) {
        return [...current, { id, role: "agent", text: normalized }];
      }
      const next = [...current];
      next[existing] = { ...next[existing], text: normalized };
      return next;
    });
  }

  async function beginCall() {
    if (!agentId || statusRef.current === "connecting") return;
    if (!voiceId?.toLowerCase().startsWith("telnyx.")) {
      setError("Select and save a Telnyx voice before starting the test.");
      return;
    }
    resetDiagnostics();
    setMessages([]);
    setError("");
    endingRef.current = false;
    firstAgentAudioRef.current = false;
    agentCaptionIdRef.current = "";
    updateStatus("connecting");
    try {
      const callPromise = startTestCall(agentId, voiceId).then((started) => {
        recordDiagnostic("call_created", {
          provider: started.runtime?.provider ?? "unknown",
        });
        return started;
      });
      const microphonePromise = warmBrowserMicrophone()
        .then(() => recordDiagnostic("microphone_ready"))
        .catch(() => recordDiagnostic("microphone_warmup_failed", undefined, "warn"));
      const [started] = await Promise.all([
        callPromise,
        microphonePromise,
        preloadBrowserVoiceRuntime(),
      ]);
      if (!started.runtime || started.runtime.provider.toLowerCase() !== "telnyx") {
        throw new Error("The backend did not create a Telnyx test session.");
      }
      callIdRef.current = started.call.id;
      setCallId(started.call.id);
      connectionRef.current = await connectBrowserVoiceRuntime(started.runtime, {
        onStartupStage(stage, details) {
          recordDiagnostic(`startup_${stage}`, details);
        },
        onConnected() {
          recordDiagnostic("runtime_connected");
          updateStatus("listening");
        },
        onCallerSpeechStarted() {
          updateStatus("capturing");
        },
        onCallerSpeechEnded() {
          if (statusRef.current === "capturing") updateStatus("thinking");
        },
        onCallerTranscript(text) {
          appendMessage("caller", text);
          updateStatus("thinking");
          void recordTestRealtimeTranscript(started.call.id, "caller", text)
            .catch(() => recordDiagnostic("caller_transcript_write_failed", undefined, "warn"));
        },
        onAgentCaption(text) {
          updateAgentCaption(text);
        },
        onAgentTranscript(text, interrupted) {
          updateAgentCaption(text);
          agentCaptionIdRef.current = "";
          void recordTestRealtimeTranscript(started.call.id, "agent", text, interrupted)
            .catch(() => recordDiagnostic("agent_transcript_write_failed", undefined, "warn"));
        },
        onAgentSpeaking(speaking) {
          if (speaking && !firstAgentAudioRef.current) {
            firstAgentAudioRef.current = true;
            recordDiagnostic("first_agent_audio");
          }
          if (speaking) setError("");
          updateStatus(speaking ? "speaking" : "listening");
        },
        onLatencyMeasured(kind, latencyMs) {
          recordDiagnostic(`${kind}_latency`, { latencyMs });
        },
        onInterrupted() {
          agentCaptionIdRef.current = "";
          recordDiagnostic("agent_interrupted");
          updateStatus("listening");
        },
        onToolInvoked(toolName) {
          recordDiagnostic("tool_invoked", { toolName });
        },
        onToolCompleted(toolName, isError) {
          recordDiagnostic("tool_completed", { toolName, isError }, isError ? "warn" : "info");
        },
        onToolError(toolName, reason) {
          recordDiagnostic("tool_error", { toolName, reason }, "error");
        },
        onProviderCallControlId(callControlId) {
          recordDiagnostic("provider_call_correlated");
          void correlateTestCall(started.call.id, callControlId)
            .catch(() => recordDiagnostic("provider_call_correlation_failed", undefined, "warn"));
        },
        onProviderCallLegId(callLegId) {
          recordDiagnostic("provider_call_leg_correlated");
          void correlateTestCall(started.call.id, "", callLegId)
            .catch(() => recordDiagnostic("provider_call_leg_correlation_failed", undefined, "warn"));
        },
        onError(message) {
          recordDiagnostic("runtime_error", { message }, "error");
          setError(message);
          updateStatus("listening");
        },
        onEnded(outcome) {
          recordDiagnostic("runtime_ended", { outcome: outcome ?? "completed" });
          void finishCall(outcome ?? "completed", false);
        },
      });
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : "Unable to start the Telnyx test call.";
      recordDiagnostic("start_failed", { message }, "error");
      setError(message);
      callIdRef.current = "";
      setCallId("");
      updateStatus("idle");
    }
  }

  async function submitTranscript(value: string) {
    const normalized = value.trim();
    if (!normalized || !connectionRef.current || statusRef.current !== "listening") return;
    setInput("");
    appendMessage("caller", normalized);
    updateStatus("thinking");
    await recordTestRealtimeTranscript(callIdRef.current, "caller", normalized)
      .catch(() => recordDiagnostic("typed_transcript_write_failed", undefined, "warn"));
    connectionRef.current.sendUserText(normalized);
  }

  async function finishCall(outcome = "completed", stopProvider = true) {
    if (endingRef.current) return;
    endingRef.current = true;
    updateStatus("ending");
    const activeCallId = callIdRef.current;
    try {
      if (stopProvider) await connectionRef.current?.stop();
      if (activeCallId) {
        await completeTestCall(
          activeCallId,
          outcome,
          connectionRef.current?.providerCallControlId() ?? "",
          connectionRef.current?.providerCallLegId() ?? "",
        );
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to complete the test call.");
    } finally {
      connectionRef.current = null;
      callIdRef.current = "";
      agentCaptionIdRef.current = "";
      setCallId("");
      updateStatus("idle");
      endingRef.current = false;
    }
  }

  function downloadDiagnostics() {
    const blob = new Blob([JSON.stringify({
      generatedAt: new Date().toISOString(),
      runtime: "telnyx",
      entries: diagnosticsRef.current,
    }, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `sauti-telnyx-diagnostics-${Date.now()}.json`;
    link.click();
    URL.revokeObjectURL(url);
  }

  const active = Boolean(callId);
  return (
    <aside className={`agent-test-panel ${active ? "active" : ""}`}>
      {!active ? (
        <div className="agent-test-canvas">
          <span className="test-orb"><Mic size={28} /></span>
          <small>Telnyx browser test call</small>
          <h2>Talk to {agentName || "your agent"}</h2>
          <p>Test the selected voice, conversation behavior, and business tools before taking the agent live.</p>
          {!voiceId?.toLowerCase().startsWith("telnyx.") && (
            <p className="test-runtime-note">Select and save a Telnyx voice in Voice settings to run this test.</p>
          )}
          <button
            disabled={!agentId || status === "connecting" || !voiceId?.toLowerCase().startsWith("telnyx.")}
            onClick={() => void beginCall()}
            type="button"
          >
            {status === "connecting" ? <LoaderCircle className="spin" size={17} /> : <Phone size={17} />}
            {!agentId ? "Save agent to test" : status === "connecting" ? "Preparing Telnyx..." : "Start Telnyx test call"}
          </button>
          {diagnosticCount > 0 && (
            <button className="test-diagnostics-download" onClick={downloadDiagnostics} type="button">
              <Download size={15} /> Download last diagnostics ({diagnosticCount})
            </button>
          )}
          {error && <div className="test-call-error">{error}</div>}
        </div>
      ) : (
        <>
          <header className="test-call-header">
            <div><span className="test-call-live-dot" /><div><small>Hands-free test call · TELNYX</small><strong>{agentName}</strong></div></div>
            <button className="test-diagnostics-download" disabled={!diagnosticCount} onClick={downloadDiagnostics} type="button">
              <Download size={15} /> Logs
            </button>
            <button disabled={status === "ending"} onClick={() => void finishCall()} type="button">
              <PhoneOff size={15} /> {status === "ending" ? "Saving..." : "End"}
            </button>
          </header>
          <div className="test-call-transcript" ref={transcriptRef}>
            {messages.map((message) => (
              <div className={message.role} key={message.id}>
                <small>{message.role === "agent" ? agentName : "You"}</small>
                <p>{message.text}</p>
                {message.role === "agent" && <Volume2 size={12} />}
              </div>
            ))}
            {(status === "thinking" || status === "working" || status === "speaking") && (
              <div className="test-call-activity"><LoaderCircle className="spin" size={14} /> {
                status === "thinking" ? "Agent is thinking" : status === "working" ? "Agent is working" : "Agent is speaking"
              }</div>
            )}
          </div>
          <div className="test-call-auto-state">
            <span className={status === "capturing" ? "hearing" : status === "thinking" || status === "working" ? "thinking" : ""}><Mic size={17} /></span>
            <div>
              <strong>{status === "capturing" ? "Hearing you…" : status === "listening" ? "Listening" : status === "working" ? "Working on your request…" : status === "speaking" ? `${agentName} is speaking` : "Preparing a response…"}</strong>
              <small>{status === "speaking" ? "Speak at any time to interrupt." : "Hands-free mode is active. Just speak naturally."}</small>
            </div>
          </div>
          <div className="test-call-controls">
            <input
              disabled={status !== "listening"}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  void submitTranscript(input);
                }
              }}
              placeholder="Or type a message…"
              value={input}
            />
            <button disabled={!input.trim() || status !== "listening"} onClick={() => void submitTranscript(input)} type="button">
              <Send size={16} />
            </button>
          </div>
          {error && <div className="test-call-error inline">{error}</div>}
        </>
      )}
    </aside>
  );
}

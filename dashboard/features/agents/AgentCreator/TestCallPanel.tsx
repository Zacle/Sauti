"use client";

import { useEffect, useRef, useState } from "react";
import { Download, Languages, LoaderCircle, Phone, PhoneOff, ShieldCheck } from "lucide-react";
import {
  completeTestCall,
  correlateTestCall,
  prepareTestCallRuntime,
  recordTestRealtimeTranscript,
  recordTestStartupLatency,
  startTestCall,
} from "@/lib/api/calls";
import { ApiError } from "@/lib/api/client";
import {
  connectBrowserVoiceRuntime,
  prepareBrowserMicrophone,
  preconnectBrowserVoiceRuntime,
  preloadBrowserVoiceRuntime,
  releasePreconnectedBrowserVoiceRuntime,
  type BrowserVoiceRuntimeConnection,
  type BrowserMicrophoneCapture,
} from "@/features/voice-runtime/browserVoiceRuntime";
import type { VoiceDiagnosticEntry } from "@/features/voice-runtime/voiceDiagnostics";
import {
  configuredLanguageHint,
  displayLanguage,
} from "@/features/voice-runtime/languagePreference";
import type { BrowserVoiceRuntimeSession } from "@/types/api";
import { AiVoiceAnimation, type VoiceAnimationActivity } from "./AiVoiceAnimation";

type TestCallPanelProps = {
  agentId?: string;
  agentName: string;
  voiceId?: string;
  defaultLanguage: string;
  supportedLanguages: string[];
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
type PreparationStatus = "idle" | "preparing" | "ready" | "error";

function testErrorMessage(value: unknown, fallback: string) {
  const message = value instanceof Error ? value.message : fallback;
  return message
    .replaceAll(/TELNYX_API_KEY/gi, "voice service configuration")
    .replaceAll(/Telnyx/gi, "voice service");
}

function TestCallOrb({ status }: { status: CallStatus }) {
  const activity: VoiceAnimationActivity = status === "idle" || status === "connecting" || status === "ending"
    ? "calm"
    : status === "speaking"
      ? "speaking"
      : status === "thinking" || status === "working"
        ? "thinking"
        : "listening";

  return <AiVoiceAnimation activity={activity} />;
}

export function TestCallPanel({
  agentId,
  agentName,
  voiceId,
  defaultLanguage,
  supportedLanguages,
}: TestCallPanelProps) {
  const [callId, setCallId] = useState("");
  const [status, setStatus] = useState<CallStatus>("idle");
  const [error, setError] = useState("");
  const [diagnosticCount, setDiagnosticCount] = useState(0);
  const [preparationStatus, setPreparationStatus] = useState<PreparationStatus>("idle");
  const languageHint = configuredLanguageHint(supportedLanguages, defaultLanguage);
  const connectionRef = useRef<BrowserVoiceRuntimeConnection | null>(null);
  const callIdRef = useRef("");
  const statusRef = useRef<CallStatus>("idle");
  const diagnosticsRef = useRef<VoiceDiagnosticEntry[]>([]);
  const diagnosticsStartedAtRef = useRef(0);
  const diagnosticSequenceRef = useRef(0);
  const endingRef = useRef(false);
  const firstAgentAudioRef = useRef(false);
  const startupLatencyRecordedRef = useRef(false);
  const preparationRef = useRef<{
    key: string;
    promise: Promise<BrowserVoiceRuntimeSession>;
  } | null>(null);
  const preparationStatusRef = useRef<PreparationStatus>("idle");
  const preparationStartedAtRef = useRef(0);
  const preparationReadyAtRef = useRef(0);

  useEffect(() => () => {
    void connectionRef.current?.stop();
    connectionRef.current = null;
    void releasePreconnectedBrowserVoiceRuntime();
  }, []);

  useEffect(() => {
    void preloadBrowserVoiceRuntime().catch(() => undefined);
  }, []);

  function updatePreparationStatus(next: PreparationStatus) {
    preparationStatusRef.current = next;
    setPreparationStatus(next);
  }

  function prepareRuntime() {
    if (!agentId || !voiceId?.toLowerCase().startsWith("telnyx.")) {
      return Promise.reject(new Error(
        "Save the agent with a supported voice before preparing the test.",
      ));
    }
    const key = `${agentId}|${voiceId}|${languageHint}`;
    if (preparationRef.current?.key === key) return preparationRef.current.promise;
    updatePreparationStatus("preparing");
    preparationStartedAtRef.current = Date.now();
    preparationReadyAtRef.current = 0;
    recordDiagnostic("runtime_configuration_requested");
    const promise = prepareTestCallRuntime(agentId, voiceId, languageHint)
      .then(async (runtime) => {
        recordDiagnostic("runtime_configuration_ready");
        recordDiagnostic("runtime_preconnect_started");
        await preconnectBrowserVoiceRuntime(runtime);
        recordDiagnostic("runtime_preconnect_ready");
        if (preparationRef.current?.key === key) {
          preparationReadyAtRef.current = Date.now();
          updatePreparationStatus("ready");
        }
        return runtime;
      })
      .catch((caught) => {
        if (preparationRef.current?.key === key) updatePreparationStatus("error");
        throw caught;
      });
    preparationRef.current = { key, promise };
    return promise;
  }

  useEffect(() => {
    preparationRef.current = null;
    if (!agentId || !voiceId?.toLowerCase().startsWith("telnyx.")) {
      updatePreparationStatus("idle");
      return;
    }
    void prepareRuntime().catch(() => undefined);
    return () => {
      preparationRef.current = null;
      void releasePreconnectedBrowserVoiceRuntime();
    };
    // Preparation is keyed by the persisted agent, voice, and opening-language hint.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentId, voiceId, languageHint]);

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

  async function beginCall() {
    if (!agentId || statusRef.current !== "idle") return;
    if (!voiceId?.toLowerCase().startsWith("telnyx.")) {
      setError("Select and save a supported voice before starting the test.");
      return;
    }
    resetDiagnostics();
    setError("");
    endingRef.current = false;
    firstAgentAudioRef.current = false;
    startupLatencyRecordedRef.current = false;
    updateStatus("connecting");
    let microphoneCapture: BrowserMicrophoneCapture | undefined;
    try {
      const readyBeforeStart = preparationStatusRef.current === "ready";
      if (preparationStatusRef.current === "error") preparationRef.current = null;
      const preparedRuntimePromise = prepareRuntime();
      const microphonePromise = prepareBrowserMicrophone()
        .then((microphone) => {
          microphoneCapture = microphone;
          recordDiagnostic("microphone_ready", {
            label: microphone.snapshot.label,
            autoGainControl: microphone.snapshot.autoGainControl,
            echoCancellation: microphone.snapshot.echoCancellation,
            noiseSuppression: microphone.snapshot.noiseSuppression,
            channelCount: microphone.snapshot.channelCount,
            sampleRate: microphone.snapshot.sampleRate,
            appliedGainDb: microphone.snapshot.appliedGainDb,
          });
          return microphone;
        });
      await preparedRuntimePromise;
      recordDiagnostic(
        readyBeforeStart ? "startup_preconnection_reused" : "startup_preconnection_waited",
        {
          preparationMs: preparationReadyAtRef.current && preparationStartedAtRef.current
            ? preparationReadyAtRef.current - preparationStartedAtRef.current
            : 0,
          readyAgeMs: readyBeforeStart && preparationReadyAtRef.current
            ? Date.now() - preparationReadyAtRef.current
            : 0,
        },
      );
      const callPromise = startTestCall(agentId, voiceId, languageHint).then((started) => {
        recordDiagnostic("call_created", {
          provider: started.runtime?.provider ?? "unknown",
          voiceId: voiceId ?? "",
        });
        return started;
      });
      const [started, microphone] = await Promise.all([
        callPromise,
        microphonePromise,
        preloadBrowserVoiceRuntime(),
      ]);
      if (!started.runtime || started.runtime.provider.toLowerCase() !== "telnyx") {
        throw new Error("The test session could not be created. Please try again.");
      }
      callIdRef.current = started.call.id;
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
          updateStatus("thinking");
          void recordTestRealtimeTranscript(started.call.id, "caller", text)
            .catch(() => recordDiagnostic("caller_transcript_write_failed", undefined, "warn"));
        },
        onAgentCaption(text) {
          if (text.trim()) updateStatus("speaking");
        },
        onAgentTranscript(text, interrupted) {
          void recordTestRealtimeTranscript(started.call.id, "agent", text, interrupted)
            .catch(() => recordDiagnostic("agent_transcript_write_failed", undefined, "warn"));
        },
        onAgentSpeaking(speaking) {
          if (speaking && !firstAgentAudioRef.current) {
            firstAgentAudioRef.current = true;
            recordDiagnostic("first_agent_audio");
            // Do not reveal an empty live-call view while the voice runtime is
            // activating the conversation. The first real greeting audio is
            // the point at which the agent is visibly ready for the caller.
            setCallId(started.call.id);
          }
          if (speaking) setError("");
          updateStatus(speaking ? "speaking" : "listening");
        },
        onMicrophoneLevel(rmsDb, peakDb) {
          recordDiagnostic("microphone_level", {
            rmsDb,
            peakDb,
            lowInput: peakDb < -32,
          }, peakDb < -32 ? "warn" : "info");
        },
        onLatencyMeasured(kind, latencyMs) {
          recordDiagnostic(`${kind}_latency`, { latencyMs });
          if (kind === "greeting" && !startupLatencyRecordedRef.current) {
            startupLatencyRecordedRef.current = true;
            void recordTestStartupLatency(started.call.id, latencyMs)
              .catch(() => recordDiagnostic("startup_latency_write_failed", undefined, "warn"));
          }
        },
        onInterrupted() {
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
          setError(testErrorMessage(new Error(message), "The voice test encountered a problem."));
          updateStatus("listening");
        },
        onEnded(outcome) {
          recordDiagnostic("runtime_ended", { outcome: outcome ?? "completed" });
          void finishCall(outcome ?? "completed", false);
        },
      }, { microphone });
      preparationRef.current = null;
      updatePreparationStatus("idle");
    } catch (caught) {
      const message = testErrorMessage(caught, "Unable to start the test call.");
      recordDiagnostic("start_failed", {
        message,
        httpStatus: caught instanceof ApiError ? caught.status : null,
      }, "error");
      setError(message);
      callIdRef.current = "";
      setCallId("");
      updateStatus("idle");
      preparationRef.current = null;
      updatePreparationStatus("error");
      await microphoneCapture?.stop();
    }
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
      setError(testErrorMessage(caught, "Unable to complete the test call."));
    } finally {
      connectionRef.current = null;
      callIdRef.current = "";
      setCallId("");
      updateStatus("idle");
      endingRef.current = false;
      void prepareRuntime().catch(() => undefined);
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
    link.download = `sauti-voice-test-diagnostics-${Date.now()}.json`;
    link.click();
    URL.revokeObjectURL(url);
  }

  const active = Boolean(callId);
  return (
    <aside className={`agent-test-panel ${active ? "active" : ""}`}>
      {!active ? (
        <div className={`agent-test-canvas status-${status}`}>
          <small>Voice test</small>
          <h2>Test your agent</h2>
          <p>Test the selected voice, conversation behavior, and business tools before taking the agent live.</p>
          <TestCallOrb status={status} />
          {supportedLanguages.length > 1 && (
            <div className="test-call-language-auto">
              <Languages size={15} />
              <span>
                <strong>Automatic language detection</strong>
                Starts in {displayLanguage(languageHint)} and follows the caller
              </span>
            </div>
          )}
          {!voiceId?.toLowerCase().startsWith("telnyx.") && (
            <p className="test-runtime-note">Select and save a supported voice in Voice settings to run this test.</p>
          )}
          <button
            disabled={
              !agentId
              || status !== "idle"
              || preparationStatus === "preparing"
              || !voiceId?.toLowerCase().startsWith("telnyx.")
            }
            onClick={() => void beginCall()}
            type="button"
          >
            {status !== "idle" || preparationStatus === "preparing"
              ? <LoaderCircle className="spin" size={17} />
              : <Phone size={17} />}
            {!agentId
              ? "Save agent to test"
              : status !== "idle"
                ? "Starting agent..."
                : preparationStatus === "preparing"
                  ? "Preparing voice demo..."
                  : preparationStatus === "error"
                    ? "Retry voice preparation"
                    : "Start test call"}
          </button>
          {preparationStatus === "ready" && (
            <p className="test-runtime-note">Voice demo ready. Signaling is already connected.</p>
          )}
          <p className="test-call-privacy"><ShieldCheck size={13} /> Calls are private and used for testing only.</p>
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
            <div><span className="test-call-live-dot" /><div><small>Live voice test</small><strong>{agentName}</strong></div></div>
            <button className="test-diagnostics-download" disabled={!diagnosticCount} onClick={downloadDiagnostics} type="button">
              <Download size={15} /> Logs
            </button>
            <button disabled={status === "ending"} onClick={() => void finishCall()} type="button">
              <PhoneOff size={15} /> {status === "ending" ? "Saving..." : "End"}
            </button>
          </header>
          <div className="test-call-visual" role="status" aria-live="polite">
            <TestCallOrb status={status} />
            <div className="test-call-visual-status">
              <strong>{status === "capturing" ? "Listening to you" : status === "listening" ? "Ready when you are" : status === "working" ? "Working on your request" : status === "speaking" ? `${agentName} is speaking` : "Preparing a response"}</strong>
              <small>{status === "speaking" ? "Speak at any time to interrupt." : "Just speak naturally. Your conversation stays private."}</small>
            </div>
          </div>
          {error && <div className="test-call-error inline">{error}</div>}
        </>
      )}
    </aside>
  );
}

"use client";

import { useEffect, useRef, useState } from "react";
import { LoaderCircle, Mic, Phone, PhoneOff, Send, Volume2 } from "lucide-react";
import {
  completeTestCall,
  getTestCallAudio,
  markTestInterruption,
  sendTestAudioTurn,
  sendTestFarewell,
  sendTestReminder,
  sendTestTurn,
  startTestCall,
  uploadCallRecording,
} from "@/lib/api/calls";
import type { StartTestCallResponse } from "@/types/api";

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

type CallStatus = "idle" | "connecting" | "listening" | "capturing" | "thinking" | "speaking" | "ending";
type TestSettings = StartTestCallResponse["settings"];

const DEFAULT_SETTINGS: TestSettings = {
  bargeInSensitivity: 0.7,
  bargeInGraceMs: 300,
  sttEndpointingMs: 300,
  maxCallDurationSeconds: 300,
  endCallOnSilenceSeconds: 600,
  reminderAfterSilenceSeconds: 10,
  maxReminders: 1,
  detectVoicemail: true,
  handleCallScreening: true,
};

export function TestCallPanel({ agentId, agentName, voiceId }: TestCallPanelProps) {
  const [callId, setCallId] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [status, setStatus] = useState<CallStatus>("idle");
  const [error, setError] = useState("");

  const statusRef = useRef<CallStatus>("idle");
  const callIdRef = useRef("");
  const callSidRef = useRef("");
  const settingsRef = useRef<TestSettings>(DEFAULT_SETTINGS);
  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const recordingChunksRef = useRef<Blob[]>([]);
  const utteranceRecorderRef = useRef<MediaRecorder | null>(null);
  const utteranceChunksRef = useRef<Blob[]>([]);
  const utteranceStartedAtRef = useRef(0);
  const utteranceModeRef = useRef<"auto" | "manual" | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const recordingDestinationRef = useRef<MediaStreamAudioDestinationNode | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const agentAudioSourceRef = useRef<AudioBufferSourceNode | null>(null);
  const interruptionPromiseRef = useRef<Promise<void> | null>(null);
  const monitorFrameRef = useRef(0);
  const voiceStartedAtRef = useRef(0);
  const lastVoiceAtRef = useRef(0);
  const callStartedAtRef = useRef(0);
  const lastActivityAtRef = useRef(0);
  const remindersRef = useRef(0);
  const endingRef = useRef(false);
  const transcriptRef = useRef<HTMLDivElement | null>(null);

  function updateStatus(next: CallStatus) {
    statusRef.current = next;
    setStatus(next);
  }

  useEffect(() => {
    transcriptRef.current?.scrollTo({ top: transcriptRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  useEffect(() => () => cleanupMedia(), []);

  async function beginCall() {
    if (!agentId) return;
    updateStatus("connecting");
    setError("");
    setMessages([]);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });
      await prepareAudio(stream);
      const started = await startTestCall(agentId, voiceId);
      callIdRef.current = started.call.id;
      callSidRef.current = started.call.twilioCallSid;
      settingsRef.current = started.settings;
      callStartedAtRef.current = Date.now();
      lastActivityAtRef.current = Date.now();
      remindersRef.current = 0;
      endingRef.current = false;
      setCallId(started.call.id);
      startVoiceMonitor();
      if (started.greeting) {
        setMessages([{ id: crypto.randomUUID(), role: "agent", text: started.greeting }]);
        await playLatestAgentAudio(started.call.id);
      } else {
        updateStatus("listening");
      }
    } catch (caught) {
      cleanupMedia();
      updateStatus("idle");
      setError(caught instanceof Error ? caught.message : "Unable to start the test call.");
    }
  }

  async function prepareAudio(stream: MediaStream) {
    streamRef.current = stream;
    const context = new AudioContext();
    await context.resume();
    const destination = context.createMediaStreamDestination();
    const microphone = context.createMediaStreamSource(stream);
    const analyser = context.createAnalyser();
    analyser.fftSize = 1024;
    analyser.smoothingTimeConstant = 0.2;
    microphone.connect(analyser);
    microphone.connect(destination);

    const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
      ? "audio/webm;codecs=opus"
      : "audio/webm";
    const recorder = new MediaRecorder(destination.stream, { mimeType });
    recordingChunksRef.current = [];
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) recordingChunksRef.current.push(event.data);
    };
    recorder.start(500);

    audioContextRef.current = context;
    recordingDestinationRef.current = destination;
    analyserRef.current = analyser;
    recorderRef.current = recorder;
  }

  function startVoiceMonitor() {
    cancelAnimationFrame(monitorFrameRef.current);
    const samples = new Uint8Array(1024);

    const monitor = () => {
      const analyser = analyserRef.current;
      const activeCallId = callIdRef.current;
      if (!analyser || !activeCallId || endingRef.current) return;

      analyser.getByteTimeDomainData(samples);
      let energy = 0;
      for (const sample of samples) {
        const normalized = (sample - 128) / 128;
        energy += normalized * normalized;
      }
      const rms = Math.sqrt(energy / samples.length);
      const now = performance.now();
      const wallClock = Date.now();
      const settings = settingsRef.current;
      const voiceThreshold = 0.075 - settings.bargeInSensitivity * 0.055;
      const voiceDetected = rms >= voiceThreshold;
      const currentStatus = statusRef.current;

      if (voiceDetected) {
        lastActivityAtRef.current = wallClock;
        lastVoiceAtRef.current = now;
        if (!voiceStartedAtRef.current) voiceStartedAtRef.current = now;
        const voiceDuration = now - voiceStartedAtRef.current;

        if (currentStatus === "listening") {
          startUtteranceCapture(false);
        } else if (currentStatus === "speaking" && voiceDuration >= settings.bargeInGraceMs) {
          interruptAgentAndCapture();
        }
      } else {
        voiceStartedAtRef.current = 0;
        if (
          currentStatus === "capturing"
          && utteranceModeRef.current === "auto"
          && lastVoiceAtRef.current
          && now - lastVoiceAtRef.current >= Math.max(450, settings.sttEndpointingMs)
        ) {
          stopUtteranceCapture();
        }
      }

      if (wallClock - callStartedAtRef.current >= settings.maxCallDurationSeconds * 1000) {
        void endCall("max-duration");
        return;
      }

      if (currentStatus === "listening") {
        const silentFor = wallClock - lastActivityAtRef.current;
        if (
          settings.maxReminders > 0
          && remindersRef.current >= settings.maxReminders
          && silentFor >= settings.reminderAfterSilenceSeconds * 1000
        ) {
          void endCall("no-response", true);
          return;
        }
        if (silentFor >= settings.endCallOnSilenceSeconds * 1000) {
          void endCall("no-response", true);
          return;
        }
        if (
          remindersRef.current < settings.maxReminders
          && silentFor >= settings.reminderAfterSilenceSeconds * 1000
        ) {
          lastActivityAtRef.current = wallClock;
          remindersRef.current += 1;
          void playSilenceReminder();
        }
      }

      monitorFrameRef.current = requestAnimationFrame(monitor);
    };

    monitorFrameRef.current = requestAnimationFrame(monitor);
  }

  function startUtteranceCapture(interruption: boolean, mode: "auto" | "manual" = "auto") {
    if (utteranceRecorderRef.current || !streamRef.current) return;
    const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
      ? "audio/webm;codecs=opus"
      : "audio/webm";
    const recorder = new MediaRecorder(streamRef.current, { mimeType });
    utteranceChunksRef.current = [];
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) utteranceChunksRef.current.push(event.data);
    };
    recorder.onerror = () => {
      utteranceRecorderRef.current = null;
      updateStatus("listening");
      setError("The microphone recording failed. Try speaking again or type your message.");
    };
    recorder.onstop = () => {
      utteranceRecorderRef.current = null;
      utteranceModeRef.current = null;
      if (endingRef.current || !callIdRef.current) {
        utteranceStartedAtRef.current = 0;
        return;
      }
      const utterance = new Blob(utteranceChunksRef.current, { type: recorder.mimeType });
      const durationMs = Date.now() - utteranceStartedAtRef.current;
      utteranceStartedAtRef.current = 0;
      if (utterance.size < 1200 || durationMs < 500) {
        updateStatus("listening");
        setError("I did not catch enough audio. Speak clearly for a moment, then pause.");
        return;
      }
      void submitAudioTurn(utterance);
    };
    utteranceRecorderRef.current = recorder;
    utteranceModeRef.current = mode;
    utteranceStartedAtRef.current = Date.now();
    lastVoiceAtRef.current = performance.now();
    updateStatus("capturing");
    recorder.start(200);
    if (interruption && callIdRef.current) {
      interruptionPromiseRef.current = markTestInterruption(callIdRef.current)
        .catch(() => undefined);
    }
  }

  function stopUtteranceCapture() {
    const recorder = utteranceRecorderRef.current;
    if (recorder?.state === "recording") {
      updateStatus("thinking");
      recorder.stop();
    }
  }

  function toggleManualCapture() {
    if (statusRef.current === "capturing" && utteranceModeRef.current === "manual") {
      stopUtteranceCapture();
      return;
    }
    if (statusRef.current === "listening") {
      setError("");
      startUtteranceCapture(false, "manual");
    }
  }

  function interruptAgentAndCapture() {
    try {
      agentAudioSourceRef.current?.stop();
    } catch {
      // The source may have ended between voice detection frames.
    }
    agentAudioSourceRef.current = null;
    startUtteranceCapture(true);
  }

  async function playLatestAgentAudio(activeCallId: string) {
    updateStatus("speaking");
    try {
      const audio = await getTestCallAudio(activeCallId);
      await playAgentAudio(await audio.arrayBuffer());
    } catch {
      // Text is still available if voice synthesis or playback fails.
    } finally {
      if (statusRef.current === "speaking" && !endingRef.current) {
        lastActivityAtRef.current = Date.now();
        voiceStartedAtRef.current = 0;
        updateStatus("listening");
      }
    }
  }

  async function playEncodedAgentAudio(encoded: string) {
    updateStatus("speaking");
    try {
      const binary = window.atob(encoded);
      const bytes = new Uint8Array(binary.length);
      for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
      await playAgentAudio(bytes.buffer);
    } catch {
      // Text remains available when browser audio decoding fails.
    } finally {
      if (statusRef.current === "speaking" && !endingRef.current) {
        lastActivityAtRef.current = Date.now();
        voiceStartedAtRef.current = 0;
        updateStatus("listening");
      }
    }
  }

  async function playAgentAudio(audio: ArrayBuffer) {
    const context = audioContextRef.current;
    if (!context) return;
    updateStatus("speaking");
    const buffer = await context.decodeAudioData(audio);
    const source = context.createBufferSource();
    source.buffer = buffer;
    source.connect(context.destination);
    if (recordingDestinationRef.current) source.connect(recordingDestinationRef.current);
    agentAudioSourceRef.current = source;
    await new Promise<void>((resolve) => {
      source.onended = () => resolve();
      source.start();
    });
    if (agentAudioSourceRef.current === source) agentAudioSourceRef.current = null;
  }

  async function submitAudioTurn(recording: Blob) {
    const activeCallId = callIdRef.current;
    if (!activeCallId || endingRef.current) return;
    updateStatus("thinking");
    setError("");
    try {
      await interruptionPromiseRef.current;
      interruptionPromiseRef.current = null;
      const turn = await sendTestAudioTurn(activeCallId, recording);
      if (endingRef.current || callIdRef.current !== activeCallId) return;
      lastActivityAtRef.current = Date.now();
      remindersRef.current = 0;
      setMessages((current) => [
        ...current,
        { id: crypto.randomUUID(), role: "caller", text: turn.callerTranscript },
        ...(turn.response ? [{ id: crypto.randomUUID(), role: "agent" as const, text: turn.response }] : []),
      ]);
      if (turn.response) {
        if (turn.audioBase64) await playEncodedAgentAudio(turn.audioBase64);
        else await playLatestAgentAudio(activeCallId);
        console.debug("Browser test turn latency", {
          endpointingMs: Math.max(450, settingsRef.current.sttEndpointingMs),
          sttMs: turn.sttLatencyMs,
          llmMs: turn.llmLatencyMs,
          ttsMs: turn.ttsLatencyMs,
          serverTotalMs: turn.totalLatencyMs,
        });
      }
      if (turn.outcome) await endCall(turn.outcome);
      else if (!turn.response) updateStatus("listening");
    } catch (caught) {
      if (endingRef.current || callIdRef.current !== activeCallId) return;
      updateStatus("listening");
      const message = caught instanceof Error ? caught.message : "Your speech could not be transcribed.";
      if (message.toLowerCase().includes("no speech was detected")) {
        setError("I did not catch that. Speak clearly for a moment, then pause.");
        return;
      }
      setError(message);
    }
  }

  async function submitTranscript(value: string) {
    const transcript = value.trim();
    const activeCallSid = callSidRef.current;
    const activeCallId = callIdRef.current;
    if (!transcript || !activeCallSid || !activeCallId || !["listening", "capturing"].includes(statusRef.current)) return;
    if (utteranceRecorderRef.current?.state === "recording") {
      utteranceRecorderRef.current.onstop = null;
      utteranceRecorderRef.current.stop();
      utteranceRecorderRef.current = null;
    }
    setInput("");
    setError("");
    setMessages((current) => [...current, { id: crypto.randomUUID(), role: "caller", text: transcript }]);
    updateStatus("thinking");
    try {
      const turn = await sendTestTurn(activeCallSid, transcript);
      lastActivityAtRef.current = Date.now();
      remindersRef.current = 0;
      if (turn.response) {
        setMessages((current) => [...current, { id: crypto.randomUUID(), role: "agent", text: turn.response }]);
        await playLatestAgentAudio(activeCallId);
        if (turn.outcome) await endCall(turn.outcome);
      } else {
        updateStatus("listening");
      }
    } catch (caught) {
      updateStatus("listening");
      setError(caught instanceof Error ? caught.message : "The agent could not respond.");
    }
  }

  async function playSilenceReminder() {
    const activeCallId = callIdRef.current;
    if (!activeCallId || statusRef.current !== "listening") return;
    try {
      const reminder = await sendTestReminder(activeCallId);
      if (!reminder.response) return;
      setMessages((current) => [
        ...current,
        { id: crypto.randomUUID(), role: "agent", text: reminder.response },
      ]);
      await playLatestAgentAudio(activeCallId);
    } catch (caught) {
      updateStatus("listening");
      setError(caught instanceof Error ? caught.message : "The silence reminder could not be played.");
    }
  }

  async function endCall(outcome = "completed", politeFarewell = false) {
    const activeCallId = callIdRef.current;
    if (!activeCallId || endingRef.current) return;
    endingRef.current = true;
    setError("");
    cancelAnimationFrame(monitorFrameRef.current);
    if (politeFarewell) {
      try {
        const farewell = await sendTestFarewell(activeCallId);
        if (farewell.response) {
          setMessages((current) => [
            ...current,
            { id: crypto.randomUUID(), role: "agent", text: farewell.response },
          ]);
          await playLatestAgentAudio(activeCallId);
        }
      } catch {
        // The call must still close if the farewell cannot be generated or played.
      }
    }
    updateStatus("ending");
    try {
      agentAudioSourceRef.current?.stop();
    } catch {
      // Audio may already have ended.
    }
    agentAudioSourceRef.current = null;
    if (utteranceRecorderRef.current?.state === "recording") {
      utteranceRecorderRef.current.onstop = null;
      utteranceRecorderRef.current.stop();
      utteranceRecorderRef.current = null;
    }
    try {
      const recording = await stopRecorder();
      if (recording.size > 0) await uploadCallRecording(activeCallId, recording);
      await completeTestCall(activeCallId, outcome);
      cleanupMedia();
      callIdRef.current = "";
      callSidRef.current = "";
      setCallId("");
      setError("");
      updateStatus("idle");
    } catch (caught) {
      endingRef.current = false;
      updateStatus("listening");
      startVoiceMonitor();
      setError(caught instanceof Error ? caught.message : "Unable to finish and save the call.");
    }
  }

  function stopRecorder() {
    const recorder = recorderRef.current;
    if (!recorder || recorder.state === "inactive") {
      return Promise.resolve(new Blob(recordingChunksRef.current, { type: "audio/webm" }));
    }
    return new Promise<Blob>((resolve) => {
      recorder.onstop = () => resolve(new Blob(recordingChunksRef.current, { type: recorder.mimeType }));
      recorder.stop();
    });
  }

  function cleanupMedia() {
    cancelAnimationFrame(monitorFrameRef.current);
    if (utteranceRecorderRef.current?.state === "recording") {
      utteranceRecorderRef.current.onstop = null;
      utteranceRecorderRef.current.stop();
    }
    utteranceRecorderRef.current = null;
    utteranceModeRef.current = null;
    utteranceStartedAtRef.current = 0;
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (recorderRef.current?.state === "recording") recorderRef.current.stop();
    recorderRef.current = null;
    try {
      agentAudioSourceRef.current?.stop();
    } catch {
      // Audio may already have ended.
    }
    agentAudioSourceRef.current = null;
    void audioContextRef.current?.close();
    audioContextRef.current = null;
    analyserRef.current = null;
    recordingDestinationRef.current = null;
  }

  const active = Boolean(callId);
  const manualCaptureActive = status === "capturing" && utteranceModeRef.current === "manual";
  const manualCaptureDisabled =
    !["listening", "capturing"].includes(status) || (status === "capturing" && !manualCaptureActive);

  return (
    <aside className={`agent-test-panel ${active ? "active" : ""}`}>
      {!active ? (
        <div className="agent-test-canvas">
          <span className="test-orb"><Mic size={28} /></span>
          <small>Browser test call</small>
          <h2>Talk to {agentName || "your agent"}</h2>
          <p>The test uses the saved prompt, variables, voice, call behaviour, and connected tools.</p>
          <button disabled={!agentId || status === "connecting"} onClick={() => void beginCall()} type="button">
            {status === "connecting" ? <LoaderCircle className="spin" size={17} /> : <Phone size={17} />}
            {agentId ? status === "connecting" ? "Connecting..." : "Start test call" : "Save agent to test"}
          </button>
          {error && <div className="test-call-error">{error}</div>}
        </div>
      ) : (
        <>
          <header className="test-call-header">
            <div><span className="test-call-live-dot" /><div><small>Hands-free test call</small><strong>{agentName}</strong></div></div>
            <button disabled={status === "ending"} onClick={() => void endCall()} type="button">
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
            {(status === "thinking" || status === "speaking") && (
              <div className="test-call-activity"><LoaderCircle className="spin" size={14} /> {status === "thinking" ? "Agent is thinking" : "Agent is speaking"}</div>
            )}
          </div>
          <div className="test-call-auto-state">
            <span className={status === "capturing" ? "hearing" : ""}><Mic size={15} /></span>
            <div>
              <strong>{status === "capturing" ? "Listening to you…" : status === "listening" ? "Listening automatically" : status === "speaking" ? "You can interrupt the agent" : "Processing the conversation"}</strong>
              <small>Speak naturally, or tap the mic button to record manually.</small>
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
            <button
              aria-label={manualCaptureActive ? "Stop recording" : "Record voice turn"}
              className={`test-call-mic ${manualCaptureActive ? "recording" : ""}`}
              disabled={manualCaptureDisabled}
              onClick={toggleManualCapture}
              title={manualCaptureActive ? "Stop recording" : "Record voice turn"}
              type="button"
            >
              {manualCaptureActive ? <span className="test-call-stop" /> : <Mic size={16} />}
            </button>
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

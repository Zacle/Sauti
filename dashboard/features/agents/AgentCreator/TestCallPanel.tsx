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
  connectTestRealtime,
  executeTestRealtimeTool,
  recordTestRealtimeTranscript,
} from "@/lib/api/calls";
import type { StartTestCallResponse } from "@/types/api";
import { connectOpenAiRealtime, type OpenAiRealtimeConnection } from "@/features/voice-runtime/openaiRealtime";
import {
  connectBrowserVoiceRuntime,
  type BrowserVoiceRuntimeConnection,
} from "@/features/voice-runtime/browserVoiceRuntime";
import { HybridPlaybackGate } from "@/features/voice-runtime/hybridPlaybackGate";
import { PcmStreamPlayer } from "@/features/voice-runtime/pcmStreamPlayer";
import { confirmedEndCallResult } from "@/features/voice-runtime/realtimeProtocol";
import { mergeVapiTranscript } from "@/features/voice-runtime/vapiTranscript";

type TestCallPanelProps = {
  agentId?: string;
  agentName: string;
  voiceId?: string;
  runtimeProvider?: string;
};

type Message = {
  id: string;
  role: "caller" | "agent";
  text: string;
};

type CallStatus = "idle" | "connecting" | "listening" | "capturing" | "thinking" | "working" | "speaking" | "ending";
type TestRuntime = "cartesia" | "vapi";
type TestSettings = StartTestCallResponse["settings"];
type VoiceEvent = {
  type: "connected" | "transcript_partial" | "transcript_final" | "agent_response" | "speaking" | "clear_audio" | "error" | "ended";
  text?: string;
  value?: boolean;
  message?: string;
  outcome?: string;
};

const DEFAULT_SETTINGS: TestSettings = {
  bargeInSensitivity: 0.7,
  bargeInGraceMs: 300,
  sttEndpointingMs: 300,
  maxCallDurationSeconds: 300,
  endCallOnSilenceSeconds: 60,
  reminderAfterSilenceSeconds: 30,
  maxReminders: 1,
  detectVoicemail: true,
  handleCallScreening: true,
};
const REALTIME_PCM_INITIAL_PREROLL_SECONDS = 0.16;
const REALTIME_PCM_MAX_PREROLL_SECONDS = 0.32;
const REALTIME_PCM_UNDERRUN_STEP_SECONDS = 0.04;
const REALTIME_PCM_QUEUE_SAFETY_SECONDS = 0.025;
const PLAYBACK_DRAIN_GRACE_MS = 80;

export function TestCallPanel({ agentId, agentName, voiceId, runtimeProvider = "cartesia" }: TestCallPanelProps) {
  const [callId, setCallId] = useState("");
  const [activeRuntime, setActiveRuntime] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [status, setStatus] = useState<CallStatus>("idle");
  const [error, setError] = useState("");
  const [testRuntime, setTestRuntime] = useState<TestRuntime>(
    runtimeProvider.toLowerCase() === "vapi" ? "vapi" : "cartesia",
  );

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
  const processingTurnRef = useRef(false);
  const queuedInterruptionRef = useRef<Blob | null>(null);
  const callerInterruptedCurrentTurnRef = useRef(false);
  const monitorFrameRef = useRef(0);
  const voiceStartedAtRef = useRef(0);
  const lastVoiceAtRef = useRef(0);
  const noiseFloorRef = useRef(0.012);
  const utterancePeakRmsRef = useRef(0);
  const utteranceVoicedMsRef = useRef(0);
  const awaitingDictatedDetailsRef = useRef(false);
  const callStartedAtRef = useRef(0);
  const lastActivityAtRef = useRef(0);
  const acceptedCallerTurnsRef = useRef(0);
  const remindersRef = useRef(0);
  const endingRef = useRef(false);
  const remoteEndPendingRef = useRef(false);
  const transcriptRef = useRef<HTMLDivElement | null>(null);
  const socketRef = useRef<WebSocket | null>(null);
  const processorRef = useRef<AudioWorkletNode | null>(null);
  const pcmQueueRef = useRef<number[]>([]);
  const playbackTimeRef = useRef(0);
  const playbackSourcesRef = useRef(new Set<AudioBufferSourceNode>());
  const playbackCompletionTimerRef = useRef(0);
  const pcmPrerollRef = useRef(REALTIME_PCM_INITIAL_PREROLL_SECONDS);
  const pcmPlaybackActiveRef = useRef(false);
  const pcmPlayerRef = useRef<PcmStreamPlayer | null>(null);
  const activeHybridSpeechRef = useRef<{ id: string; generation: number } | null>(null);
  const hybridPlaybackGateRef = useRef(new HybridPlaybackGate());
  const openAiConnectionRef = useRef<OpenAiRealtimeConnection | null>(null);
  const browserRuntimeConnectionRef = useRef<BrowserVoiceRuntimeConnection | null>(null);
  const nativeRealtimeRef = useRef(false);
  const hybridRealtimeRef = useRef(false);
  const nativeEndPendingRef = useRef(false);
  const businessActionPendingRef = useRef(false);
  const nativeEndAuthorizedRef = useRef(false);
  const transcriptWriteRef = useRef<Promise<void>>(Promise.resolve());
  const managedAgentTranscriptRef = useRef<{ text: string; interrupted: boolean } | null>(null);
  const managedAgentCaptionRef = useRef<{ id: string; turn?: number } | null>(null);
  const cleanupMediaRef = useRef<() => void>(() => undefined);
  cleanupMediaRef.current = cleanupMedia;

  function updateStatus(next: CallStatus) {
    statusRef.current = next;
    setStatus(next);
  }

  useEffect(() => {
    transcriptRef.current?.scrollTo({ top: transcriptRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  useEffect(() => () => cleanupMediaRef.current(), []);

  async function beginCall() {
    if (!agentId) return;
    if (testRuntime === "cartesia" && !voiceId?.startsWith("cartesia:")) {
      setError("Select and save a Cartesia voice before starting the Cartesia test.");
      return;
    }
    updateStatus("connecting");
    setError("");
    setMessages([]);
    try {
      const requestedRuntime = testRuntime === "cartesia" ? "sauti" : "vapi";
      const started = await startTestCall(agentId, voiceId, requestedRuntime);
      callIdRef.current = started.call.id;
      callSidRef.current = started.call.twilioCallSid;
      settingsRef.current = started.settings;
      callStartedAtRef.current = Date.now();
      lastActivityAtRef.current = Date.now();
      acceptedCallerTurnsRef.current = 0;
      remindersRef.current = 0;
      endingRef.current = false;
      managedAgentTranscriptRef.current = null;
      managedAgentCaptionRef.current = null;
      remoteEndPendingRef.current = false;
      businessActionPendingRef.current = false;
      awaitingDictatedDetailsRef.current = false;
      if (testRuntime === "cartesia" && started.mode !== "hybrid_realtime") {
        throw new Error(
          "The Cartesia test runtime is unavailable. Confirm the production OpenAI and Cartesia credentials, then try again.",
        );
      }
      setCallId(started.call.id);
      setActiveRuntime(testRuntime === "cartesia"
        ? "Cartesia + OpenAI Realtime"
        : started.runtime?.provider ?? started.mode);
      if (started.runtime) {
        await connectManagedRuntime(started);
        return;
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });
      await prepareAudio(stream);
      if (started.mode === "openai_realtime" || started.mode === "hybrid_realtime") {
        nativeRealtimeRef.current = true;
        hybridRealtimeRef.current = started.mode === "hybrid_realtime";
        const hybrid = hybridRealtimeRef.current;
        const cachedGreeting = hybrid ? started.greetingAudioBase64 : null;
        if (cachedGreeting && started.greeting) {
          setMessages([{ id: crypto.randomUUID(), role: "agent", text: started.greeting }]);
        }
        const greetingPlayback = cachedGreeting
          ? playEncodedAgentAudio(cachedGreeting)
          : Promise.resolve(false);
        await Promise.all([
          hybrid ? connectHybridVoice(started) : Promise.resolve(),
          connectNativeRealtime(started, stream, hybrid, cachedGreeting ? "" : started.greeting),
        ]);
        const greetingPlayed = await greetingPlayback;
        if (hybrid && !greetingPlayed) openAiConnectionRef.current?.speakGreeting(started.greeting);
      } else {
        await connectRealtimeVoice(started, stream);
        startVoiceMonitor();
      }
    } catch (caught) {
      const failedCallId = callIdRef.current;
      cleanupMedia();
      callIdRef.current = "";
      callSidRef.current = "";
      setCallId("");
      setActiveRuntime("");
      updateStatus("idle");
      setError(caught instanceof Error ? caught.message : "Unable to start the test call.");
      if (failedCallId) void completeTestCall(failedCallId, "completed").catch(() => undefined);
    }
  }

  async function connectManagedRuntime(started: StartTestCallResponse) {
    if (!started.runtime) throw new Error("The selected voice runtime did not return a session configuration.");
    browserRuntimeConnectionRef.current = await connectBrowserVoiceRuntime(started.runtime, {
      onConnected: () => updateStatus("listening"),
      onCallerSpeechStarted: () => {
        if (!endingRef.current) updateStatus("capturing");
      },
      onCallerSpeechEnded: () => {
        if (!endingRef.current && statusRef.current === "capturing") updateStatus("thinking");
      },
      onCallerTranscript: (text) => {
        flushManagedAgentTranscript(started.call.id);
        acceptedCallerTurnsRef.current += 1;
        lastActivityAtRef.current = Date.now();
        setMessages((current) => [...current, { id: crypto.randomUUID(), role: "caller", text }]);
        queueTranscriptWrite(() => recordTestRealtimeTranscript(started.call.id, "caller", text));
        updateStatus("thinking");
      },
      onAgentCaption: (text, turn) => {
        const caption = managedAgentCaptionRef.current;
        const changedTurn = caption?.turn !== undefined && turn !== undefined && caption.turn !== turn;
        if (!caption || changedTurn) {
          const id = crypto.randomUUID();
          managedAgentCaptionRef.current = { id, turn };
          setMessages((current) => [...current, { id, role: "agent", text }]);
          return;
        }
        setMessages((current) => current.map((message) =>
          message.id === caption.id ? { ...message, text } : message
        ));
      },
      onAgentTranscript: (text, interrupted) => {
        rememberAgentPrompt(text);
        const pending = managedAgentTranscriptRef.current;
        const merged = mergeVapiTranscript(pending?.text ?? "", text);
        managedAgentTranscriptRef.current = {
          text: merged,
          interrupted: Boolean(pending?.interrupted || interrupted),
        };
        const caption = managedAgentCaptionRef.current;
        if (!caption) {
          const id = crypto.randomUUID();
          managedAgentCaptionRef.current = { id };
          setMessages((current) => [...current, { id, role: "agent", text: merged }]);
        } else {
          setMessages((current) => current.map((message) =>
            message.id === caption.id ? { ...message, text: merged } : message
          ));
        }
      },
      onAgentSpeaking: (value) => {
        if (!endingRef.current) updateStatus(value ? "speaking" : "listening");
      },
      onInterrupted: () => {
        if (!endingRef.current) updateStatus("capturing");
        void markTestInterruption(started.call.id).catch(() => undefined);
      },
      onError: (message) => {
        setError(message);
        if (!endingRef.current) updateStatus("listening");
      },
      onEnded: (outcome = "completed") => {
        flushManagedAgentTranscript(started.call.id);
        if (!endingRef.current) void endCall(outcome);
      },
    });
  }

  async function connectHybridVoice(started: StartTestCallResponse) {
    const socket = new WebSocket(started.websocketUrl);
    socket.binaryType = "arraybuffer";
    socketRef.current = socket;
    socket.onmessage = (event) => {
      if (event.data instanceof ArrayBuffer) {
        playRealtimePcm(event.data);
        return;
      }
      handleRealtimeEvent(JSON.parse(String(event.data)) as VoiceEvent);
    };
    socket.onclose = () => {
      if (!endingRef.current && callIdRef.current) setError("The Cartesia voice connection ended unexpectedly.");
    };
    await new Promise<void>((resolve, reject) => {
      const timeout = window.setTimeout(() => reject(new Error("The Cartesia voice connection timed out.")), 8000);
      socket.onopen = () => {
        window.clearTimeout(timeout);
        resolve();
      };
      socket.onerror = () => {
        window.clearTimeout(timeout);
        reject(new Error("The Cartesia voice connection could not be opened."));
      };
    });
  }

  async function connectNativeRealtime(
    started: StartTestCallResponse,
    stream: MediaStream,
    hybrid: boolean,
    greeting: string,
  ) {
    openAiConnectionRef.current = await connectOpenAiRealtime({
      microphone: stream,
      greeting,
      outputMode: hybrid ? "text" : "audio",
      bargeInDebounceMs: hybrid ? 180 : 0,
      responseLanguage: started.call.languageDetected ?? undefined,
      recordCallerTranscript: (text) => recordTestRealtimeTranscript(started.call.id, "caller", text),
      connectSdp: (offer) => connectTestRealtime(started.call.id, offer),
      playbackContext: audioContextRef.current,
      recordingDestination: recordingDestinationRef.current,
      callbacks: {
        onConnected: () => {
          if (!agentAudioSourceRef.current) updateStatus("listening");
        },
        onCallerTranscript: (text, generation) => {
          acceptedCallerTurnsRef.current += 1;
          lastActivityAtRef.current = Date.now();
          setMessages((current) => [...current, { id: crypto.randomUUID(), role: "caller", text }]);
          updateStatus("thinking");
          if (hybrid) sendHybridEvent({ type: "turn_started", generation });
        },
        onAgentTranscript: (text, interrupted) => {
          rememberAgentPrompt(text);
          setMessages((current) => [...current, { id: crypto.randomUUID(), role: "agent", text }]);
          queueTranscriptWrite(() => recordTestRealtimeTranscript(started.call.id, "agent", text, interrupted));
          if (nativeEndAuthorizedRef.current) {
            nativeEndAuthorizedRef.current = false;
            nativeEndPendingRef.current = true;
          }
        },
        onAgentSpeech: hybrid ? (speech) => {
          activeHybridSpeechRef.current = { id: speech.id, generation: speech.generation };
          sendHybridEvent({ type: "speak", ...speech });
        } : undefined,
        onBusinessActionPending: (pending) => {
          businessActionPendingRef.current = pending;
          if (pending) {
            updateStatus("working");
          } else if (!endingRef.current && statusRef.current !== "speaking") {
            updateStatus("thinking");
          }
        },
        onSpeaking: (value) => {
          updateStatus(value ? "speaking" : businessActionPendingRef.current ? "working" : "listening");
          if (!value && nativeEndPendingRef.current) {
            nativeEndPendingRef.current = false;
            window.setTimeout(() => void endCall("completed"), 220);
          }
        },
        onCallerAudioAbandoned: () => {
          if (statusRef.current === "capturing" || statusRef.current === "thinking") {
            updateStatus("listening");
          }
        },
        onCallerSpeechStarted: (_agentWasResponding, generation) => {
          updateStatus("capturing");
          if (hybrid) {
            // Only transcript-confirmed caller speech may stop playback. Raw
            // provider VAD is noisy enough to fire on echo and brief room
            // sounds, which previously chopped speech and flapped the UI.
            interruptHybridResponse(false, generation);
          }
        },
        onError: (message) => {
          setError(message);
          if (!endingRef.current) updateStatus("listening");
        },
        executeTool: async (toolCallId, name, argumentsJson) => {
          const result = await executeTestRealtimeTool(
            started.call.id, toolCallId, name, argumentsJson,
          );
          if (confirmedEndCallResult(name, result)) nativeEndAuthorizedRef.current = true;
          return result;
        },
      },
    });
  }

  function sendHybridEvent(payload: Record<string, unknown>) {
    const socket = socketRef.current;
    if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify(payload));
  }

  function queueTranscriptWrite(write: () => Promise<unknown>) {
    transcriptWriteRef.current = transcriptWriteRef.current
      .then(write, write)
      .then(() => undefined, () => undefined);
  }

  function flushManagedAgentTranscript(activeCallId: string) {
    const pending = managedAgentTranscriptRef.current;
    managedAgentTranscriptRef.current = null;
    managedAgentCaptionRef.current = null;
    if (!pending?.text.trim()) return;
    queueTranscriptWrite(() => recordTestRealtimeTranscript(
      activeCallId, "agent", pending.text, pending.interrupted,
    ));
  }

  function interruptHybridResponse(cancelModel: boolean, generation?: number) {
    if (cancelModel) openAiConnectionRef.current?.cancelResponse();
    hybridPlaybackGateRef.current.clear();
    clearRealtimePlayback();
    sendHybridEvent({ type: "interrupt", ...(generation === undefined ? {} : { generation }) });
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
    try {
      pcmPlayerRef.current = await PcmStreamPlayer.create(context, {
        recordingDestination: destination,
        onPlaybackStarted: () => {
          if (!endingRef.current) updateStatus("speaking");
          const speech = activeHybridSpeechRef.current;
          if (hybridRealtimeRef.current && speech) {
            sendHybridEvent({ type: "playback_started", ...speech });
          }
        },
        onPlaybackDrained: () => {
          if (endingRef.current) return;
          updateStatus("listening");
          if (hybridRealtimeRef.current && nativeEndPendingRef.current) {
            nativeEndPendingRef.current = false;
            window.setTimeout(() => void endCall("completed"), 180);
          }
        },
        onPlaybackStalled: () => sendHybridEvent({ type: "playback_stalled" }),
        onPlaybackUnderrun: () => sendHybridEvent({ type: "playback_underrun" }),
      });
    } catch {
      // Keep the existing scheduler as a compatibility fallback when a browser
      // cannot load AudioWorklet modules.
      pcmPlayerRef.current = null;
    }
  }

  async function connectRealtimeVoice(started: StartTestCallResponse, stream: MediaStream) {
    const context = audioContextRef.current;
    if (!context) throw new Error("The browser audio engine is unavailable.");
    await context.audioWorklet.addModule("/web-voice-processor.js");
    const socket = new WebSocket(started.websocketUrl);
    socket.binaryType = "arraybuffer";
    socketRef.current = socket;
    socket.onmessage = (event) => {
      if (event.data instanceof ArrayBuffer) {
        playRealtimePcm(event.data);
        return;
      }
      handleRealtimeEvent(JSON.parse(String(event.data)) as VoiceEvent);
    };
    socket.onclose = () => {
      if (!endingRef.current && !remoteEndPendingRef.current && callIdRef.current) setError("The realtime voice connection ended unexpectedly.");
    };
    await new Promise<void>((resolve, reject) => {
      const timeout = window.setTimeout(() => reject(new Error("The realtime voice connection timed out.")), 8000);
      socket.onopen = () => {
        window.clearTimeout(timeout);
        attachRealtimeMicrophone(context, stream, socket);
        updateStatus("listening");
        resolve();
      };
      socket.onerror = () => {
        window.clearTimeout(timeout);
        reject(new Error("The realtime voice connection could not be opened."));
      };
    });
  }

  function attachRealtimeMicrophone(context: AudioContext, stream: MediaStream, socket: WebSocket) {
    const source = context.createMediaStreamSource(stream);
    const processor = new AudioWorkletNode(context, "sauti-voice-processor");
    const silentGain = context.createGain();
    silentGain.gain.value = 0;
    processor.port.onmessage = (event: MessageEvent<{ samples: Float32Array; sampleRate: number }>) => {
      if (socket.readyState !== WebSocket.OPEN) return;
      const samples = downsamplePcm(event.data.samples, event.data.sampleRate, 16000);
      for (const sample of samples) pcmQueueRef.current.push(sample);
      while (pcmQueueRef.current.length >= 320) {
        const frame = pcmQueueRef.current.splice(0, 320);
        const pcm = new Int16Array(frame.length);
        frame.forEach((value, index) => {
          const clipped = Math.max(-1, Math.min(1, value));
          pcm[index] = clipped < 0 ? clipped * 32768 : clipped * 32767;
        });
        socket.send(pcm.buffer);
      }
    };
    source.connect(processor);
    processor.connect(silentGain);
    silentGain.connect(context.destination);
    processorRef.current = processor;
  }

  function handleRealtimeEvent(event: VoiceEvent) {
    if (event.type === "transcript_partial") {
      if (statusRef.current === "speaking") interruptRealtimeAgent();
      updateStatus("capturing");
    }
    if (event.type === "transcript_final" && event.text) {
      acceptedCallerTurnsRef.current += 1;
      setMessages((current) => [...current, { id: crypto.randomUUID(), role: "caller", text: event.text! }]);
      updateStatus("thinking");
    }
    if (event.type === "agent_response" && event.text) {
      rememberAgentPrompt(event.text);
      setMessages((current) => [...current, { id: crypto.randomUUID(), role: "agent", text: event.text! }]);
    }
    if (event.type === "speaking") {
      hybridPlaybackGateRef.current.speaking(event.value === true);
      if (event.value) {
        window.clearTimeout(playbackCompletionTimerRef.current);
        pcmPlayerRef.current?.begin();
        updateStatus("speaking");
      } else {
        finishRealtimePlayback();
      }
      if (!event.value && !pcmPlayerRef.current && hybridRealtimeRef.current && nativeEndPendingRef.current) {
        nativeEndPendingRef.current = false;
        const context = audioContextRef.current;
        const remainingMs = context ? Math.max(0, playbackTimeRef.current - context.currentTime) * 1000 : 0;
        window.setTimeout(() => void endCall("completed"), remainingMs + 180);
      }
    }
    if (event.type === "clear_audio") {
      hybridPlaybackGateRef.current.clear();
      clearRealtimePlayback();
    }
    if (event.type === "error") setError(event.message ?? "The realtime voice session encountered an error.");
    if (event.type === "ended" && !endingRef.current && !remoteEndPendingRef.current) {
      remoteEndPendingRef.current = true;
      const context = audioContextRef.current;
      const remainingMs = context ? Math.max(0, playbackTimeRef.current - context.currentTime) * 1000 : 0;
      window.setTimeout(() => {
        remoteEndPendingRef.current = false;
        void endCall(event.outcome || "completed");
      }, remainingMs + 180);
    }
  }

  function playRealtimePcm(data: ArrayBuffer) {
    if (!hybridPlaybackGateRef.current.accepts(hybridRealtimeRef.current)) return;
    if (pcmPlayerRef.current) {
      pcmPlayerRef.current.pushPcm16(data);
      return;
    }
    const context = audioContextRef.current;
    if (!context) return;
    window.clearTimeout(playbackCompletionTimerRef.current);
    if (statusRef.current !== "ending") updateStatus("speaking");
    const pcm = new Int16Array(data);
    const buffer = context.createBuffer(1, pcm.length, 16000);
    const channel = buffer.getChannelData(0);
    for (let index = 0; index < pcm.length; index++) channel[index] = pcm[index] / 32768;
    const source = context.createBufferSource();
    source.buffer = buffer;
    source.connect(context.destination);
    if (recordingDestinationRef.current) source.connect(recordingDestinationRef.current);
    const queuedUntil = playbackTimeRef.current;
    const hasBufferedLead = queuedUntil > context.currentTime + REALTIME_PCM_QUEUE_SAFETY_SECONDS;
    if (!hasBufferedLead && pcmPlaybackActiveRef.current) {
      pcmPrerollRef.current = Math.min(
        REALTIME_PCM_MAX_PREROLL_SECONDS,
        pcmPrerollRef.current + REALTIME_PCM_UNDERRUN_STEP_SECONDS,
      );
    }
    const startAt = hasBufferedLead
      ? queuedUntil
      : context.currentTime + pcmPrerollRef.current;
    pcmPlaybackActiveRef.current = true;
    source.start(startAt);
    playbackTimeRef.current = startAt + buffer.duration;
    playbackSourcesRef.current.add(source);
    source.onended = () => playbackSourcesRef.current.delete(source);
  }

  function finishRealtimePlayback() {
    if (pcmPlayerRef.current) {
      pcmPlayerRef.current.complete();
      return;
    }
    scheduleRealtimePlaybackIdle();
  }

  function scheduleRealtimePlaybackIdle() {
    window.clearTimeout(playbackCompletionTimerRef.current);
    const context = audioContextRef.current;
    const remainingMs = context ? Math.max(0, playbackTimeRef.current - context.currentTime) * 1000 : 0;
    playbackCompletionTimerRef.current = window.setTimeout(() => {
      pcmPlaybackActiveRef.current = false;
      pcmPrerollRef.current = REALTIME_PCM_INITIAL_PREROLL_SECONDS;
      playbackTimeRef.current = audioContextRef.current?.currentTime ?? 0;
      if (statusRef.current === "speaking" && !endingRef.current) updateStatus("listening");
    }, remainingMs + PLAYBACK_DRAIN_GRACE_MS);
  }

  function clearRealtimePlayback() {
    activeHybridSpeechRef.current = null;
    pcmPlayerRef.current?.clear();
    window.clearTimeout(playbackCompletionTimerRef.current);
    playbackSourcesRef.current.forEach((source) => {
      try { source.stop(); } catch { /* already stopped */ }
    });
    playbackSourcesRef.current.clear();
    pcmPlaybackActiveRef.current = false;
    pcmPrerollRef.current = REALTIME_PCM_INITIAL_PREROLL_SECONDS;
    playbackTimeRef.current = audioContextRef.current?.currentTime ?? 0;
  }

  function downsamplePcm(input: Float32Array, sourceRate: number, targetRate: number) {
    if (sourceRate === targetRate) return Array.from(input);
    const ratio = sourceRate / targetRate;
    const output = new Array<number>(Math.floor(input.length / ratio));
    for (let index = 0; index < output.length; index++) output[index] = input[Math.floor(index * ratio)];
    return output;
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
      const baseThreshold = 0.075 - settings.bargeInSensitivity * 0.055;
      const voiceThreshold = Math.max(0.026, baseThreshold * 0.72, noiseFloorRef.current * 2.4);
      const voiceDetected = rms >= voiceThreshold;
      // Playback can leak back into an open microphone. Require a much stronger,
      // sustained signal before treating it as an interruption of the agent.
      const bargeInDetected = rms >= Math.max(0.04, voiceThreshold * 1.5, noiseFloorRef.current * 3);
      const currentStatus = statusRef.current;
      const realtimeConnected = nativeRealtimeRef.current || socketRef.current?.readyState === WebSocket.OPEN;

      if (voiceDetected) {
        lastActivityAtRef.current = wallClock;
        lastVoiceAtRef.current = now;
        if (!voiceStartedAtRef.current) voiceStartedAtRef.current = now;
        const voiceDuration = now - voiceStartedAtRef.current;
        if (currentStatus === "capturing") {
          utterancePeakRmsRef.current = Math.max(utterancePeakRmsRef.current, rms);
          utteranceVoicedMsRef.current += 16;
        }

        if (!realtimeConnected && currentStatus === "listening" && voiceDuration >= 100) {
          startUtteranceCapture(false, "auto", voiceDuration);
        } else if (!realtimeConnected && currentStatus === "thinking" && voiceDuration >= 100) {
          startUtteranceCapture(true, "auto", voiceDuration);
        } else if (!realtimeConnected && currentStatus === "speaking" && bargeInDetected
          && voiceDuration >= Math.max(180, Math.min(250, settings.bargeInGraceMs))) {
          // Realtime interruption is owned by the transcript-aware gate. A
          // second raw microphone-energy path caused echo/noise to stop valid
          // speech even when no caller words were recognized.
          interruptAgentAndCapture(voiceDuration);
        }
      } else {
        if (currentStatus === "listening") {
          noiseFloorRef.current = noiseFloorRef.current * 0.96 + rms * 0.04;
        }
        voiceStartedAtRef.current = 0;
        if (
          currentStatus === "capturing"
          && utteranceModeRef.current === "auto"
          && lastVoiceAtRef.current
          && now - lastVoiceAtRef.current >= endpointSilenceMs(settings)
        ) {
          stopUtteranceCapture();
        }
      }

      if (wallClock - callStartedAtRef.current >= settings.maxCallDurationSeconds * 1000) {
        void endCall("max-duration");
        return;
      }

      // Realtime sessions are governed by the backend's single silence policy.
      // Keep this legacy timer only for prerecorded/fallback calls; running both
      // produced duplicate reminder and farewell agent turns.
      if (currentStatus === "listening" && !nativeRealtimeRef.current && !socketRef.current) {
        const silentFor = wallClock - lastActivityAtRef.current;
        const reminderDelayMs = Math.max(
          settings.reminderAfterSilenceSeconds * 1000,
          acceptedCallerTurnsRef.current === 0 ? 25000 : 15000,
        );
        if (
          settings.maxReminders > 0
          && remindersRef.current >= settings.maxReminders
          && silentFor >= reminderDelayMs
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
          && silentFor >= reminderDelayMs
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

  function endpointSilenceMs(settings: TestSettings) {
    // Most turns should feel responsive. When the last question requested a
    // dictated detail, retain a longer pause so names, addresses, and numbers
    // are not split into separate turns.
    return awaitingDictatedDetailsRef.current
      ? Math.max(1300, settings.sttEndpointingMs)
      : Math.max(650, settings.sttEndpointingMs);
  }

  function agentRequestsDictatedDetails(text: string) {
    const normalized = text
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase();
    return /\b(nom|prenom|telephone|numero|coordonnees|adresse|email|courriel|epeler|chiffre|digits?|name|phone|number|contact|address|spell)\b/.test(normalized);
  }

  function rememberAgentPrompt(text: string) {
    awaitingDictatedDetailsRef.current = agentRequestsDictatedDetails(text);
  }

  function startUtteranceCapture(
    interruption: boolean,
    mode: "auto" | "manual" = "auto",
    initialVoicedMs = 0,
  ) {
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
      const peakRms = utterancePeakRmsRef.current;
      const voicedMs = utteranceVoicedMsRef.current;
      utteranceStartedAtRef.current = 0;
      utterancePeakRmsRef.current = 0;
      utteranceVoicedMsRef.current = 0;
      if (utterance.size < 1200 || durationMs < 700 || (mode === "auto" && (peakRms < 0.03 || voicedMs < 180))) {
        updateStatus("listening");
        setError("I did not catch clear speech. Move closer to the mic or reduce background noise, then try again.");
        return;
      }
      if (processingTurnRef.current) {
        queuedInterruptionRef.current = utterance;
        callerInterruptedCurrentTurnRef.current = true;
        updateStatus("thinking");
        return;
      }
      void submitAudioTurn(utterance);
    };
    utteranceRecorderRef.current = recorder;
    utteranceModeRef.current = mode;
    utteranceStartedAtRef.current = Date.now();
    utterancePeakRmsRef.current = 0;
    utteranceVoicedMsRef.current = initialVoicedMs;
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

  function interruptAgentAndCapture(initialVoicedMs = 0) {
    void initialVoicedMs;
    interruptRealtimeAgent();
  }

  function interruptRealtimeAgent() {
    hybridPlaybackGateRef.current.clear();
    clearRealtimePlayback();
    const socket = socketRef.current;
    if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "interrupt" }));
    try {
      agentAudioSourceRef.current?.stop();
    } catch {
      // The source may have ended between voice detection frames.
    }
    agentAudioSourceRef.current = null;
    callerInterruptedCurrentTurnRef.current = true;
    updateStatus("capturing");
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

  async function playEncodedAgentAudio(encoded: string): Promise<boolean> {
    updateStatus("speaking");
    try {
      const binary = window.atob(encoded);
      const bytes = new Uint8Array(binary.length);
      for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
      await playAgentAudio(bytes.buffer);
      return true;
    } catch {
      // Text remains available when browser audio decoding fails.
      return false;
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
    if (processingTurnRef.current) {
      queuedInterruptionRef.current = recording;
      callerInterruptedCurrentTurnRef.current = true;
      updateStatus("thinking");
      return;
    }
    processingTurnRef.current = true;
    callerInterruptedCurrentTurnRef.current = false;
    updateStatus("thinking");
    setError("");
    try {
      await interruptionPromiseRef.current;
      interruptionPromiseRef.current = null;
      const turn = await sendTestAudioTurn(activeCallId, recording);
      if (endingRef.current || callIdRef.current !== activeCallId) return;
      lastActivityAtRef.current = Date.now();
      remindersRef.current = 0;
      const wasInterrupted = callerInterruptedCurrentTurnRef.current || Boolean(queuedInterruptionRef.current);
      if (turn.callerTranscript) acceptedCallerTurnsRef.current += 1;
      if (turn.response && !wasInterrupted) rememberAgentPrompt(turn.response);
      setMessages((current) => [
        ...current,
        ...(turn.callerTranscript ? [{ id: crypto.randomUUID(), role: "caller" as const, text: turn.callerTranscript }] : []),
        ...(turn.response && !wasInterrupted ? [{ id: crypto.randomUUID(), role: "agent" as const, text: turn.response }] : []),
      ]);
      let playedAgentAudio = false;
      if (turn.response && !wasInterrupted) {
        if (turn.audioBase64) await playEncodedAgentAudio(turn.audioBase64);
        else if (turn.callerTranscript) await playLatestAgentAudio(activeCallId);
        playedAgentAudio = Boolean(turn.audioBase64 || turn.callerTranscript);
        console.debug("Browser test turn latency", {
          endpointingMs: endpointSilenceMs(settingsRef.current),
          sttMs: turn.sttLatencyMs,
          llmMs: turn.llmLatencyMs,
          ttsMs: turn.ttsLatencyMs,
          serverTotalMs: turn.totalLatencyMs,
        });
      }
      if (turn.outcome && !queuedInterruptionRef.current) await endCall(turn.outcome);
      else if (!turn.response || wasInterrupted || !playedAgentAudio) updateStatus(queuedInterruptionRef.current ? "thinking" : "listening");
    } catch (caught) {
      if (endingRef.current || callIdRef.current !== activeCallId) return;
      updateStatus("listening");
      const message = caught instanceof Error ? caught.message : "Your speech could not be transcribed.";
      if (message.toLowerCase().includes("no speech was detected")) {
        setError("I did not catch that. Speak clearly for a moment, then pause.");
        return;
      }
      setError(message);
    } finally {
      processingTurnRef.current = false;
      callerInterruptedCurrentTurnRef.current = false;
      const queued = queuedInterruptionRef.current;
      queuedInterruptionRef.current = null;
      if (queued && !endingRef.current && callIdRef.current === activeCallId) {
        void submitAudioTurn(queued);
      }
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
    if (browserRuntimeConnectionRef.current) flushManagedAgentTranscript(activeCallId);
    setInput("");
    setError("");
    setMessages((current) => [...current, { id: crypto.randomUUID(), role: "caller", text: transcript }]);
    updateStatus("thinking");
    if (browserRuntimeConnectionRef.current) {
      acceptedCallerTurnsRef.current += 1;
      lastActivityAtRef.current = Date.now();
      queueTranscriptWrite(() => recordTestRealtimeTranscript(activeCallId, "caller", transcript));
      browserRuntimeConnectionRef.current.sendUserText(transcript);
      return;
    }
    if (nativeRealtimeRef.current && openAiConnectionRef.current) {
      openAiConnectionRef.current.sendUserText(transcript);
      return;
    }
    try {
      const turn = await sendTestTurn(activeCallSid, transcript);
      lastActivityAtRef.current = Date.now();
      remindersRef.current = 0;
      acceptedCallerTurnsRef.current += 1;
      if (turn.response) {
        rememberAgentPrompt(turn.response);
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
    flushManagedAgentTranscript(activeCallId);
    endingRef.current = true;
    const managedRuntime = browserRuntimeConnectionRef.current;
    browserRuntimeConnectionRef.current = null;
    if (managedRuntime) {
      try {
        await managedRuntime.stop();
      } catch {
        // Persist the Sauti call even if the provider session already closed.
      }
    }
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
      await transcriptWriteRef.current;
      await completeTestCall(activeCallId, outcome);
      cleanupMedia();
      callIdRef.current = "";
      callSidRef.current = "";
      setCallId("");
      setActiveRuntime("");
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
    awaitingDictatedDetailsRef.current = false;
    processingTurnRef.current = false;
    queuedInterruptionRef.current = null;
    callerInterruptedCurrentTurnRef.current = false;
    managedAgentTranscriptRef.current = null;
    managedAgentCaptionRef.current = null;
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
    pcmPlayerRef.current?.close();
    pcmPlayerRef.current = null;
    void audioContextRef.current?.close();
    audioContextRef.current = null;
    analyserRef.current = null;
    recordingDestinationRef.current = null;
    processorRef.current?.disconnect();
    processorRef.current = null;
    openAiConnectionRef.current?.close();
    openAiConnectionRef.current = null;
    void browserRuntimeConnectionRef.current?.stop();
    browserRuntimeConnectionRef.current = null;
    nativeRealtimeRef.current = false;
    hybridRealtimeRef.current = false;
    hybridPlaybackGateRef.current.clear();
    nativeEndPendingRef.current = false;
    nativeEndAuthorizedRef.current = false;
    businessActionPendingRef.current = false;
    const socket = socketRef.current;
    socketRef.current = null;
    if (socket && socket.readyState < WebSocket.CLOSING) socket.close(1000, "Test call ended");
    clearRealtimePlayback();
  }

  const active = Boolean(callId);
  return (
    <aside className={`agent-test-panel ${active ? "active" : ""}`}>
      {!active ? (
        <div className="agent-test-canvas">
          <span className="test-orb"><Mic size={28} /></span>
          <small>Browser test call</small>
          <h2>Talk to {agentName || "your agent"}</h2>
          <p>Compare the same saved agent, prompt, tools, and business data through either runtime.</p>
          <div aria-label="Test call runtime" className="test-runtime-picker" role="group">
            <button
              className={testRuntime === "cartesia" ? "selected" : ""}
              disabled={status === "connecting"}
              onClick={() => { setTestRuntime("cartesia"); setError(""); }}
              type="button"
            >
              <strong>Sauti + Cartesia</strong>
              <small>OpenAI Realtime conversation, Cartesia voice</small>
            </button>
            <button
              className={testRuntime === "vapi" ? "selected" : ""}
              disabled={status === "connecting"}
              onClick={() => { setTestRuntime("vapi"); setError(""); }}
              type="button"
            >
              <strong>Vapi</strong>
              <small>Vapi-managed conversation and voice pipeline</small>
            </button>
          </div>
          {testRuntime === "cartesia" && !voiceId?.startsWith("cartesia:") && (
            <p className="test-runtime-note">Select and save a Cartesia voice in Voice settings to run this test.</p>
          )}
          <button
            disabled={!agentId || status === "connecting" || (testRuntime === "cartesia" && !voiceId?.startsWith("cartesia:"))}
            onClick={() => void beginCall()}
            type="button"
          >
            {status === "connecting" ? <LoaderCircle className="spin" size={17} /> : <Phone size={17} />}
            {agentId ? status === "connecting" ? "Connecting..." : `Test ${testRuntime === "cartesia" ? "Cartesia" : "Vapi"}` : "Save agent to test"}
          </button>
          {error && <div className="test-call-error">{error}</div>}
        </div>
      ) : (
        <>
          <header className="test-call-header">
            <div><span className="test-call-live-dot" /><div><small>Hands-free test call{activeRuntime ? ` · ${activeRuntime.toUpperCase()}` : ""}</small><strong>{agentName}</strong></div></div>
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
            {(status === "thinking" || status === "working" || status === "speaking") && (
              <div className="test-call-activity"><LoaderCircle className="spin" size={14} /> {
                status === "thinking"
                  ? "Agent is thinking"
                  : status === "working"
                    ? "Agent is still working"
                    : "Agent is speaking"
              }</div>
            )}
          </div>
          <div className="test-call-auto-state">
            <span className={status === "capturing" ? "hearing" : status === "thinking" || status === "working" ? "thinking" : ""}><Mic size={17} /></span>
            <div>
              <strong>{status === "capturing" ? "Hearing you…" : status === "listening" ? "Listening" : status === "working" ? "Still working on your request…" : status === "speaking" ? `${agentName} is speaking` : "Preparing a response…"}</strong>
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

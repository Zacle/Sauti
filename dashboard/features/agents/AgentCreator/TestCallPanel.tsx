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
  const openAiConnectionRef = useRef<OpenAiRealtimeConnection | null>(null);
  const nativeRealtimeRef = useRef(false);
  const hybridRealtimeRef = useRef(false);
  const nativeEndPendingRef = useRef(false);

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
      acceptedCallerTurnsRef.current = 0;
      remindersRef.current = 0;
      endingRef.current = false;
      remoteEndPendingRef.current = false;
      awaitingDictatedDetailsRef.current = false;
      setCallId(started.call.id);
      if (started.mode === "openai_realtime" || started.mode === "hybrid_realtime") {
        nativeRealtimeRef.current = true;
        hybridRealtimeRef.current = started.mode === "hybrid_realtime";
        if (hybridRealtimeRef.current) await connectHybridVoice(started);
        await connectNativeRealtime(started, stream, hybridRealtimeRef.current);
      } else {
        await connectRealtimeVoice(started, stream);
        startVoiceMonitor();
      }
    } catch (caught) {
      cleanupMedia();
      updateStatus("idle");
      setError(caught instanceof Error ? caught.message : "Unable to start the test call.");
    }
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

  async function connectNativeRealtime(started: StartTestCallResponse, stream: MediaStream, hybrid: boolean) {
    openAiConnectionRef.current = await connectOpenAiRealtime({
      microphone: stream,
      greeting: started.greeting,
      outputMode: hybrid ? "text" : "audio",
      connectSdp: (offer) => connectTestRealtime(started.call.id, offer),
      playbackContext: audioContextRef.current,
      recordingDestination: recordingDestinationRef.current,
      callbacks: {
        onConnected: () => updateStatus("listening"),
        onCallerTranscript: (text) => {
          acceptedCallerTurnsRef.current += 1;
          lastActivityAtRef.current = Date.now();
          setMessages((current) => [...current, { id: crypto.randomUUID(), role: "caller", text }]);
          updateStatus("thinking");
          void recordTestRealtimeTranscript(started.call.id, "caller", text);
        },
        onAgentTranscript: (text, interrupted) => {
          rememberAgentPrompt(text);
          setMessages((current) => [...current, { id: crypto.randomUUID(), role: "agent", text }]);
          void recordTestRealtimeTranscript(started.call.id, "agent", text, interrupted);
          if (isFarewell(text)) nativeEndPendingRef.current = true;
        },
        onAgentTextDelta: hybrid ? (delta) => sendHybridEvent({ type: "tts_delta", text: delta }) : undefined,
        onAgentTextComplete: hybrid ? (interrupted) => {
          if (!interrupted) sendHybridEvent({ type: "tts_complete" });
        } : undefined,
        onSpeaking: (value) => {
          updateStatus(value ? "speaking" : "listening");
          if (!value && nativeEndPendingRef.current) {
            nativeEndPendingRef.current = false;
            window.setTimeout(() => void endCall("completed"), 220);
          }
        },
        onCallerSpeechStarted: (agentWasResponding) => {
          const context = audioContextRef.current;
          const cartesiaStillAudible = hybrid && (
            statusRef.current === "speaking"
            || Boolean(context && playbackTimeRef.current > context.currentTime + 0.03)
          );
          updateStatus("capturing");
          if (hybrid && (agentWasResponding || cartesiaStillAudible)) {
            interruptHybridResponse(agentWasResponding);
          }
        },
        onError: (message) => setError(message),
        executeTool: (toolCallId, name, argumentsJson) => executeTestRealtimeTool(
          started.call.id, toolCallId, name, argumentsJson,
        ),
      },
    });
  }

  function sendHybridEvent(payload: Record<string, unknown>) {
    const socket = socketRef.current;
    if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify(payload));
  }

  function interruptHybridResponse(cancelModel: boolean) {
    if (cancelModel) openAiConnectionRef.current?.cancelResponse();
    clearRealtimePlayback();
    sendHybridEvent({ type: "interrupt" });
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
      updateStatus(event.value ? "speaking" : "listening");
      if (!event.value && hybridRealtimeRef.current && nativeEndPendingRef.current) {
        nativeEndPendingRef.current = false;
        const context = audioContextRef.current;
        const remainingMs = context ? Math.max(0, playbackTimeRef.current - context.currentTime) * 1000 : 0;
        window.setTimeout(() => void endCall("completed"), remainingMs + 180);
      }
    }
    if (event.type === "clear_audio") clearRealtimePlayback();
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
    const context = audioContextRef.current;
    if (!context) return;
    const pcm = new Int16Array(data);
    const buffer = context.createBuffer(1, pcm.length, 16000);
    const channel = buffer.getChannelData(0);
    for (let index = 0; index < pcm.length; index++) channel[index] = pcm[index] / 32768;
    const source = context.createBufferSource();
    source.buffer = buffer;
    source.connect(context.destination);
    if (recordingDestinationRef.current) source.connect(recordingDestinationRef.current);
    const startAt = Math.max(context.currentTime + 0.02, playbackTimeRef.current);
    source.start(startAt);
    playbackTimeRef.current = startAt + buffer.duration;
    playbackSourcesRef.current.add(source);
    source.onended = () => playbackSourcesRef.current.delete(source);
  }

  function clearRealtimePlayback() {
    playbackSourcesRef.current.forEach((source) => {
      try { source.stop(); } catch { /* already stopped */ }
    });
    playbackSourcesRef.current.clear();
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
        } else if (currentStatus === "speaking" && bargeInDetected && voiceDuration >= Math.max(180, Math.min(250, settings.bargeInGraceMs))) {
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
      if (currentStatus === "listening" && !socketRef.current) {
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

  function toggleManualCapture() {
    if (nativeRealtimeRef.current) return;
    if (socketRef.current?.readyState === WebSocket.OPEN) {
      if (statusRef.current === "speaking") interruptRealtimeAgent();
      return;
    }
    if (statusRef.current === "capturing" && utteranceModeRef.current === "manual") {
      stopUtteranceCapture();
      return;
    }
    if (statusRef.current === "speaking") {
      setError("");
      try {
        agentAudioSourceRef.current?.stop();
      } catch {
        // The source may have ended between the button click and stop request.
      }
      agentAudioSourceRef.current = null;
      startUtteranceCapture(true, "manual");
      return;
    }
    if (statusRef.current === "listening" || statusRef.current === "thinking") {
      setError("");
      startUtteranceCapture(statusRef.current === "thinking", "manual");
    }
  }

  function interruptAgentAndCapture(initialVoicedMs = 0) {
    void initialVoicedMs;
    interruptRealtimeAgent();
  }

  function interruptRealtimeAgent() {
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
    setInput("");
    setError("");
    setMessages((current) => [...current, { id: crypto.randomUUID(), role: "caller", text: transcript }]);
    updateStatus("thinking");
    if (nativeRealtimeRef.current && openAiConnectionRef.current) {
      await recordTestRealtimeTranscript(activeCallId, "caller", transcript);
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
    awaitingDictatedDetailsRef.current = false;
    processingTurnRef.current = false;
    queuedInterruptionRef.current = null;
    callerInterruptedCurrentTurnRef.current = false;
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
    processorRef.current?.disconnect();
    processorRef.current = null;
    openAiConnectionRef.current?.close();
    openAiConnectionRef.current = null;
    nativeRealtimeRef.current = false;
    hybridRealtimeRef.current = false;
    nativeEndPendingRef.current = false;
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
            <span className={status === "capturing" ? "hearing" : status === "thinking" ? "thinking" : ""}><Mic size={17} /></span>
            <div>
              <strong>{status === "capturing" ? "Hearing you…" : status === "listening" ? "Listening" : status === "speaking" ? `${agentName} is speaking` : "Preparing a response…"}</strong>
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

function isFarewell(text: string) {
  return /(?:au revoir|bonne journ[ée]e|goodbye|bye[.! ]*$|مع السلامة|إلى اللقاء)/i.test(text.trim());
}

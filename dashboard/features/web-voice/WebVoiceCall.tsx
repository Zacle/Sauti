"use client";

import { type CSSProperties, useEffect, useRef, useState } from "react";
import { Mic, MicOff, PhoneOff, ShieldCheck, Sparkles } from "lucide-react";
import {
  completePublicWebVoiceSession,
  getPublicWebVoiceAgent,
  sendPublicWebVoiceAudioTurn,
  startPublicWebVoiceSession,
  connectPublicRealtime,
  executePublicRealtimeTool,
  recordPublicRealtimeTranscript,
  type PublicWebVoiceAgent,
} from "@/lib/api/public-web-voice";
import { connectOpenAiRealtime, type OpenAiRealtimeConnection } from "@/features/voice-runtime/openaiRealtime";
import { HybridPlaybackGate } from "@/features/voice-runtime/hybridPlaybackGate";
import { PcmStreamPlayer } from "@/features/voice-runtime/pcmStreamPlayer";
import { confirmedEndCallResult } from "@/features/voice-runtime/realtimeProtocol";
import styles from "./WebVoiceCall.module.css";

type Message = { role: "visitor" | "agent"; text: string };
type VoiceEvent = {
  type: string;
  text?: string;
  value?: boolean;
  message?: string;
  outcome?: string;
};
const REALTIME_PCM_INITIAL_PREROLL_SECONDS = 0.16;
const REALTIME_PCM_MAX_PREROLL_SECONDS = 0.32;
const REALTIME_PCM_UNDERRUN_STEP_SECONDS = 0.04;
const REALTIME_PCM_QUEUE_SAFETY_SECONDS = 0.025;
const PLAYBACK_DRAIN_GRACE_MS = 80;

export function WebVoiceCall({ publicId }: { publicId: string }) {
  const [agent, setAgent] = useState<PublicWebVoiceAgent | null>(null);
  const [consent, setConsent] = useState(false);
  const [status, setStatus] = useState<"loading" | "idle" | "connecting" | "live" | "ended">("loading");
  const [speaking, setSpeaking] = useState(false);
  const [partial, setPartial] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [error, setError] = useState("");
  const [language, setLanguage] = useState("");
  const [mode, setMode] = useState<"openai_realtime" | "hybrid_realtime" | "realtime" | "turn">("realtime");
  const [recordingTurn, setRecordingTurn] = useState(false);
  const [processingTurn, setProcessingTurn] = useState(false);
  const [accent, setAccent] = useState("#31d9c9");
  const socketRef = useRef<WebSocket | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const contextRef = useRef<AudioContext | null>(null);
  const processorRef = useRef<AudioWorkletNode | null>(null);
  const turnRecorderRef = useRef<MediaRecorder | null>(null);
  const turnChunksRef = useRef<Blob[]>([]);
  const turnStartedAtRef = useRef(0);
  const sessionIdRef = useRef("");
  const tokenRef = useRef("");
  const playbackTimeRef = useRef(0);
  const playbackSourcesRef = useRef<Set<AudioBufferSourceNode>>(new Set());
  const playbackCompletionTimerRef = useRef(0);
  const pcmPrerollRef = useRef(REALTIME_PCM_INITIAL_PREROLL_SECONDS);
  const pcmPlaybackActiveRef = useRef(false);
  const pcmPlayerRef = useRef<PcmStreamPlayer | null>(null);
  const activeHybridSpeechRef = useRef<{ id: string; generation: number } | null>(null);
  const hybridPlaybackGateRef = useRef(new HybridPlaybackGate());
  const pcmQueueRef = useRef<number[]>([]);
  const speakingRef = useRef(false);
  const openAiConnectionRef = useRef<OpenAiRealtimeConnection | null>(null);
  const nativeEndPendingRef = useRef(false);
  const nativeEndAuthorizedRef = useRef(false);
  const hybridRealtimeRef = useRef(false);
  const transcriptWriteRef = useRef<Promise<void>>(Promise.resolve());
  const cleanupRef = useRef<() => void>(() => undefined);
  cleanupRef.current = cleanup;

  function updateSpeaking(value: boolean) {
    speakingRef.current = value;
    setSpeaking(value);
  }

  useEffect(() => {
    const requestedAccent = new URLSearchParams(window.location.search).get("color") ?? "";
    if (/^#[0-9a-f]{6}$/i.test(requestedAccent)) setAccent(requestedAccent);
    getPublicWebVoiceAgent(publicId)
      .then((loaded) => {
        setAgent(loaded);
        const requested = new URLSearchParams(window.location.search).get("lang")?.toLowerCase() ?? "";
        setLanguage(loaded.languages.includes(requested) ? requested : loaded.defaultLanguage);
        setConsent(!loaded.consentRequired);
        setStatus("idle");
      })
      .catch((caught) => {
        setError(caught instanceof Error ? caught.message : "This voice agent is unavailable.");
        setStatus("ended");
      });
    return () => cleanupRef.current();
  }, [publicId]);

  async function start() {
    if (!agent || (agent.consentRequired && !consent)) return;
    setStatus("connecting");
    setError("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
      streamRef.current = stream;
      const embedded = new URLSearchParams(window.location.search).get("embed") === "1";
      const origin = embedded && document.referrer ? new URL(document.referrer).origin : window.location.origin;
      const context = new AudioContext();
      contextRef.current = context;
      const [session, player] = await Promise.all([
        startPublicWebVoiceSession(publicId, consent, origin, language || agent.defaultLanguage),
        context.resume().then(async () => {
          try {
            return await PcmStreamPlayer.create(context, {
              onPlaybackStarted: () => {
                updateSpeaking(true);
                const speech = activeHybridSpeechRef.current;
                if (hybridRealtimeRef.current && speech) {
                  sendHybridEvent({ type: "playback_started", ...speech });
                }
              },
              onPlaybackDrained: () => {
                updateSpeaking(false);
                if (hybridRealtimeRef.current && nativeEndPendingRef.current) {
                  nativeEndPendingRef.current = false;
                  window.setTimeout(end, 180);
                }
              },
              onPlaybackStalled: () => sendHybridEvent({ type: "playback_stalled" }),
              onPlaybackUnderrun: () => sendHybridEvent({ type: "playback_underrun" }),
            });
          } catch {
            return null;
          }
        }),
      ]);
      pcmPlayerRef.current = player;
      sessionIdRef.current = session.sessionId;
      tokenRef.current = session.token;
      setMode(session.mode);
      if (session.mode === "openai_realtime" || session.mode === "hybrid_realtime") {
        const hybrid = session.mode === "hybrid_realtime";
        hybridRealtimeRef.current = hybrid;
        const cachedGreeting = hybrid ? session.greetingAudioBase64 : null;
        if (cachedGreeting && session.greeting) {
          setMessages([{ role: "agent", text: session.greeting }]);
        }
        const greetingPlayback = cachedGreeting
          ? playEncodedAudio(cachedGreeting)
          : Promise.resolve(false);
        const realtimeConnection = connectOpenAiRealtime({
          microphone: stream,
          greeting: cachedGreeting ? "" : session.greeting,
          outputMode: hybrid ? "text" : "audio",
          bargeInDebounceMs: hybrid ? 180 : 0,
          responseLanguage: session.language,
          recordCallerTranscript: (text) => recordPublicRealtimeTranscript(
            session.sessionId, session.token, "caller", text,
          ),
          connectSdp: (offer) => connectPublicRealtime(session.sessionId, session.token, offer),
          playbackContext: context,
          callbacks: {
            onConnected: () => setStatus("live"),
            onCallerTranscript: (text, generation) => {
              setMessages((current) => [...current, { role: "visitor", text }]);
              setPartial("");
              if (hybrid) sendHybridEvent({ type: "turn_started", generation });
            },
            onAgentTranscript: (text, interrupted) => {
              setMessages((current) => [...current, { role: "agent", text }]);
              queueTranscriptWrite(() => recordPublicRealtimeTranscript(session.sessionId, session.token, "agent", text, interrupted));
              if (nativeEndAuthorizedRef.current) {
                nativeEndAuthorizedRef.current = false;
                nativeEndPendingRef.current = true;
              }
            },
            onAgentSpeech: hybrid ? (speech) => {
              activeHybridSpeechRef.current = { id: speech.id, generation: speech.generation };
              sendHybridEvent({ type: "speak", ...speech });
            } : undefined,
            onSpeaking: (value) => {
              updateSpeaking(value);
              if (!value && nativeEndPendingRef.current) {
                nativeEndPendingRef.current = false;
                window.setTimeout(end, 220);
              }
            },
            onCallerAudioStarted: () => {
              setPartial("Listening...");
              updateSpeaking(false);
              if (hybrid) {
                // Stop external TTS as soon as the caller starts speaking;
                // recognized speech still owns model-turn cancellation.
                hybridPlaybackGateRef.current.clear();
                clearPlayback();
                sendHybridEvent({ type: "stop_playback" });
              }
            },
            onCallerAudioStopped: () => {
              setPartial((current) => current === "Listening..." ? "Processing..." : current);
            },
            onCallerAudioAbandoned: () => setPartial(""),
            onCallerSpeechStarted: (_agentWasResponding, generation) => {
              updateSpeaking(false);
              if (hybrid) {
                // Always advance the external TTS generation. A Cartesia context
                // may still be generating before its first audio frame arrives.
                hybridPlaybackGateRef.current.clear();
                clearPlayback();
                sendHybridEvent({ type: "interrupt", generation });
              }
            },
            onError: (message) => {
              setError(message);
              setPartial("");
            },
            executeTool: async (callId, name, argumentsJson) => {
              const result = await executePublicRealtimeTool(
                session.sessionId, session.token, callId, name, argumentsJson,
              );
              if (confirmedEndCallResult(name, result)) nativeEndAuthorizedRef.current = true;
              return result;
            },
          },
        });
        const [, connection, greetingPlayed] = await Promise.all([
          hybrid ? connectHybridVoice(session.websocketUrl, context) : Promise.resolve(),
          realtimeConnection,
          greetingPlayback,
        ]);
        openAiConnectionRef.current = connection;
        if (hybrid && !greetingPlayed) {
          setMessages((current) => current.length === 1
            && current[0]?.role === "agent"
            && current[0]?.text === session.greeting ? [] : current);
          connection.speakGreeting(session.greeting);
        }
        return;
      }
      if (session.mode === "turn") {
        if (session.greeting) setMessages([{ role: "agent", text: session.greeting }]);
        if (session.greetingAudioBase64) await playEncodedAudio(session.greetingAudioBase64);
        setStatus("live");
        return;
      }
      await context.audioWorklet.addModule("/web-voice-processor.js");
      const socket = new WebSocket(session.websocketUrl);
      socket.binaryType = "arraybuffer";
      socketRef.current = socket;
      socket.onopen = () => {
        attachMicrophone(context, stream, socket);
        setStatus("live");
      };
      socket.onmessage = (event) => {
        if (event.data instanceof ArrayBuffer) {
          playPcm(event.data);
          return;
        }
        const payload = JSON.parse(String(event.data)) as VoiceEvent;
        handleEvent(payload);
      };
      socket.onerror = () => setError("The live voice connection was interrupted.");
      socket.onclose = () => {
        stopCapture();
        updateSpeaking(false);
        setStatus((current) => current === "connecting" ? "ended" : current === "live" ? "ended" : current);
      };
    } catch (caught) {
      cleanup();
      setStatus("idle");
      setError(caught instanceof Error ? caught.message : "Microphone access could not be started.");
    }
  }

  async function connectHybridVoice(websocketUrl: string, context: AudioContext) {
    void context;
    const socket = new WebSocket(websocketUrl);
    socket.binaryType = "arraybuffer";
    socketRef.current = socket;
    socket.onmessage = (event) => {
      if (event.data instanceof ArrayBuffer) {
        playPcm(event.data);
        return;
      }
      handleEvent(JSON.parse(String(event.data)) as VoiceEvent);
    };
    socket.onclose = () => {
      if (sessionIdRef.current) setError("The Cartesia voice connection ended unexpectedly.");
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

  function sendHybridEvent(payload: Record<string, unknown>) {
    const socket = socketRef.current;
    if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify(payload));
  }

  function queueTranscriptWrite(write: () => Promise<unknown>) {
    transcriptWriteRef.current = transcriptWriteRef.current
      .then(write, write)
      .then(() => undefined, () => undefined);
  }

  function startTurnRecording() {
    if (!streamRef.current || status !== "live" || speaking || processingTurn || recordingTurn) return;
    const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
      ? "audio/webm;codecs=opus"
      : "audio/webm";
    const recorder = new MediaRecorder(streamRef.current, { mimeType });
    turnChunksRef.current = [];
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) turnChunksRef.current.push(event.data);
    };
    recorder.onstop = () => {
      const durationMs = Date.now() - turnStartedAtRef.current;
      turnStartedAtRef.current = 0;
      setRecordingTurn(false);
      const recording = new Blob(turnChunksRef.current, { type: recorder.mimeType });
      turnRecorderRef.current = null;
      if (recording.size < 1200 || durationMs < 500) {
        setProcessingTurn(false);
        setError("I did not catch enough audio. Speak clearly for a moment, then stop.");
        return;
      }
      void submitTurnRecording(recording);
    };
    recorder.onerror = () => {
      turnRecorderRef.current = null;
      setRecordingTurn(false);
      setProcessingTurn(false);
      setError("The microphone recording failed. Try again.");
    };
    setError("");
    setRecordingTurn(true);
    turnStartedAtRef.current = Date.now();
    turnRecorderRef.current = recorder;
    recorder.start(200);
  }

  function stopTurnRecording() {
    const recorder = turnRecorderRef.current;
    if (recorder?.state === "recording") {
      setProcessingTurn(true);
      recorder.stop();
    }
  }

  async function submitTurnRecording(recording: Blob) {
    const sessionId = sessionIdRef.current;
    const token = tokenRef.current;
    if (!sessionId || !token) return;
    setProcessingTurn(true);
    setError("");
    try {
      const turn = await sendPublicWebVoiceAudioTurn(sessionId, token, recording);
      setMessages((current) => [
        ...current,
        { role: "visitor", text: turn.callerTranscript },
        ...(turn.response ? [{ role: "agent" as const, text: turn.response }] : []),
      ]);
      if (turn.audioBase64) await playEncodedAudio(turn.audioBase64);
      if (turn.outcome) {
        setStatus("ended");
        stopCapture();
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to process your voice message.");
    } finally {
      setProcessingTurn(false);
    }
  }

  function attachMicrophone(context: AudioContext, stream: MediaStream, socket: WebSocket) {
    const source = context.createMediaStreamSource(stream);
    const processor = new AudioWorkletNode(context, "sauti-voice-processor");
    const silentGain = context.createGain();
    silentGain.gain.value = 0;
    processor.port.onmessage = (event: MessageEvent<{ samples: Float32Array; sampleRate: number }>) => {
      if (socket.readyState !== WebSocket.OPEN) return;
      const downsampled = downsample(event.data.samples, event.data.sampleRate, 16000);
      for (const sample of downsampled) pcmQueueRef.current.push(sample);
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

  function handleEvent(event: VoiceEvent) {
    if (event.type === "transcript_partial") {
      if (speakingRef.current) {
        hybridPlaybackGateRef.current.clear();
        clearPlayback();
        updateSpeaking(false);
        const socket = socketRef.current;
        if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "interrupt" }));
      }
      setPartial(event.text ?? "");
    }
    if (event.type === "transcript_final" && event.text) {
      setPartial("");
      setMessages((current) => [...current, { role: "visitor", text: event.text! }]);
    }
    if (event.type === "agent_response" && event.text) {
      setMessages((current) => [...current, { role: "agent", text: event.text! }]);
    }
    if (event.type === "speaking") {
      hybridPlaybackGateRef.current.speaking(event.value === true);
      if (event.value) {
        window.clearTimeout(playbackCompletionTimerRef.current);
        pcmPlayerRef.current?.begin();
        updateSpeaking(true);
      } else {
        finishPlayback();
      }
      if (!event.value && !pcmPlayerRef.current && hybridRealtimeRef.current && nativeEndPendingRef.current) {
        nativeEndPendingRef.current = false;
        const context = contextRef.current;
        const remainingMs = context ? Math.max(0, playbackTimeRef.current - context.currentTime) * 1000 : 0;
        window.setTimeout(end, remainingMs + 180);
      }
    }
    if (event.type === "clear_audio") {
      hybridPlaybackGateRef.current.clear();
      clearPlayback();
    }
    if (event.type === "error") setError(event.message ?? "The voice session encountered an error.");
    if (event.type === "ended") {
      setStatus("ended");
      stopCapture();
    }
  }

  function playPcm(buffer: ArrayBuffer) {
    if (!hybridPlaybackGateRef.current.accepts(hybridRealtimeRef.current)) return;
    if (pcmPlayerRef.current) {
      pcmPlayerRef.current.pushPcm16(buffer);
      return;
    }
    const context = contextRef.current;
    if (!context) return;
    window.clearTimeout(playbackCompletionTimerRef.current);
    if (!speakingRef.current) updateSpeaking(true);
    const pcm = new Int16Array(buffer);
    const audioBuffer = context.createBuffer(1, pcm.length, 16000);
    const channel = audioBuffer.getChannelData(0);
    for (let index = 0; index < pcm.length; index++) channel[index] = pcm[index] / 32768;
    const source = context.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(context.destination);
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
    playbackTimeRef.current = startAt + audioBuffer.duration;
    playbackSourcesRef.current.add(source);
    source.onended = () => playbackSourcesRef.current.delete(source);
  }

  function finishPlayback() {
    if (pcmPlayerRef.current) {
      pcmPlayerRef.current.complete();
      return;
    }
    schedulePlaybackIdle();
  }

  function schedulePlaybackIdle() {
    window.clearTimeout(playbackCompletionTimerRef.current);
    const context = contextRef.current;
    const remainingMs = context ? Math.max(0, playbackTimeRef.current - context.currentTime) * 1000 : 0;
    playbackCompletionTimerRef.current = window.setTimeout(() => {
      pcmPlaybackActiveRef.current = false;
      pcmPrerollRef.current = REALTIME_PCM_INITIAL_PREROLL_SECONDS;
      playbackTimeRef.current = contextRef.current?.currentTime ?? 0;
      updateSpeaking(false);
    }, remainingMs + PLAYBACK_DRAIN_GRACE_MS);
  }

  async function playEncodedAudio(encoded: string): Promise<boolean> {
    const context = contextRef.current;
    if (!context) return false;
    updateSpeaking(true);
    try {
      const binary = window.atob(encoded);
      const bytes = new Uint8Array(binary.length);
      for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
      const buffer = await context.decodeAudioData(bytes.buffer);
      const source = context.createBufferSource();
      source.buffer = buffer;
      source.connect(context.destination);
      await new Promise<void>((resolve) => {
        source.onended = () => resolve();
        source.start();
      });
      return true;
    } catch {
      return false;
    } finally {
      updateSpeaking(false);
    }
  }

  function clearPlayback() {
    activeHybridSpeechRef.current = null;
    pcmPlayerRef.current?.clear();
    window.clearTimeout(playbackCompletionTimerRef.current);
    playbackSourcesRef.current.forEach((source) => {
      try { source.stop(); } catch { /* already stopped */ }
    });
    playbackSourcesRef.current.clear();
    pcmPlaybackActiveRef.current = false;
    pcmPrerollRef.current = REALTIME_PCM_INITIAL_PREROLL_SECONDS;
    playbackTimeRef.current = contextRef.current?.currentTime ?? 0;
  }

  function end() {
    if (sessionIdRef.current && tokenRef.current) {
      void completePublicWebVoiceSession(sessionIdRef.current, tokenRef.current).catch(() => undefined);
    }
    socketRef.current?.close(1000, "Visitor ended the call");
    cleanup();
    setStatus("ended");
  }

  function stopCapture() {
    if (turnRecorderRef.current?.state === "recording") {
      turnRecorderRef.current.onstop = null;
      turnRecorderRef.current.stop();
    }
    turnRecorderRef.current = null;
    setRecordingTurn(false);
    processorRef.current?.disconnect();
    processorRef.current = null;
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }

  function cleanup() {
    hybridPlaybackGateRef.current.clear();
    stopCapture();
    clearPlayback();
    const socket = socketRef.current;
    socketRef.current = null;
    if (socket && socket.readyState < WebSocket.CLOSING) socket.close();
    pcmPlayerRef.current?.close();
    pcmPlayerRef.current = null;
    void contextRef.current?.close();
    contextRef.current = null;
    pcmQueueRef.current = [];
    openAiConnectionRef.current?.close();
    openAiConnectionRef.current = null;
    nativeEndPendingRef.current = false;
    nativeEndAuthorizedRef.current = false;
    hybridRealtimeRef.current = false;
    sessionIdRef.current = "";
    tokenRef.current = "";
  }

  const pageStyle = { "--web-voice-accent": accent } as CSSProperties;

  return (
    <main className={styles.page} style={pageStyle}>
      <section className={styles.card}>
        <header><span><Sparkles size={22} /></span><div><small>WEB VOICE</small><h1>{agent?.name ?? "Voice assistant"}</h1></div><i className={status === "live" ? styles.online : ""}>{status === "live" ? "Live" : status}</i></header>
        <div className={`${styles.orb} ${speaking || recordingTurn ? styles.speaking : ""}`}><Mic size={34} /></div>
        <h2>{status === "live" ? (speaking ? `${agent?.name} is speaking` : recordingTurn ? "Listening to you" : mode === "turn" ? "Tap the mic and speak" : "Listening to you") : status === "ended" ? "Conversation ended" : "Talk with our assistant"}</h2>
        <p>{agent?.description ?? "Loading the voice assistant…"}</p>
        {status === "idle" && agent && agent.languages.length > 1 && (
          <label className={styles.language}>
            Conversation language
            <select value={language} onChange={(event) => setLanguage(event.target.value)}>
              {agent.languages.map((code) => <option value={code} key={code}>{languageName(code)}</option>)}
            </select>
          </label>
        )}
        {status === "idle" && agent?.consentRequired && (
          <label className={styles.consent}><input type="checkbox" checked={consent} onChange={(event) => setConsent(event.target.checked)} /><ShieldCheck size={18} /><span>I agree to use my microphone for this conversation{agent.recordingEnabled ? " and understand that the conversation will be recorded" : ""}.</span></label>
        )}
        {status === "idle" && <button className={styles.start} disabled={!consent} onClick={() => void start()}><Mic size={18} /> Start conversation</button>}
        {status === "connecting" && <button className={styles.start} disabled><span className={styles.spinner} /> Connecting…</button>}
        {status === "live" && mode === "turn" && (
          <button
            className={recordingTurn ? styles.recording : styles.start}
            disabled={speaking || processingTurn}
            onClick={recordingTurn ? stopTurnRecording : startTurnRecording}
            type="button"
          >
            {processingTurn ? <span className={styles.spinner} /> : <Mic size={18} />}
            {processingTurn ? "Processing..." : recordingTurn ? "Stop recording" : "Record message"}
          </button>
        )}
        {status === "live" && <button className={styles.end} onClick={end}><PhoneOff size={18} /> End conversation</button>}
        {status === "ended" && <button className={styles.start} onClick={() => window.location.reload()}><MicOff size={18} /> Start a new conversation</button>}
        {error && <div className={styles.error}>{error}</div>}
        <div className={styles.transcript}>
          {messages.length === 0 && !partial ? <span>Your conversation will appear here.</span> : messages.map((message, index) => <div className={message.role === "agent" ? styles.agent : styles.visitor} key={`${index}-${message.text}`}><small>{message.role === "agent" ? agent?.name : "You"}</small><p>{message.text}</p></div>)}
          {partial && <div className={styles.visitor}><small>You</small><p>{partial}<i>…</i></p></div>}
        </div>
        <footer><ShieldCheck size={15} /> Secure browser audio · No phone charges</footer>
      </section>
    </main>
  );
}

function languageName(code: string) {
  return ({ en: "English", fr: "French", ar: "Arabic" } as Record<string, string>)[code] ?? code.toUpperCase();
}

function downsample(samples: Float32Array, sourceRate: number, targetRate: number) {
  if (sourceRate === targetRate) return samples;
  const ratio = sourceRate / targetRate;
  const result = new Float32Array(Math.floor(samples.length / ratio));
  for (let index = 0; index < result.length; index++) {
    const start = Math.floor(index * ratio);
    const end = Math.min(samples.length, Math.floor((index + 1) * ratio));
    let sum = 0;
    for (let cursor = start; cursor < end; cursor++) sum += samples[cursor];
    result[index] = sum / Math.max(1, end - start);
  }
  return result;
}

"use client";

import { type CSSProperties, useEffect, useRef, useState } from "react";
import { Mic, MicOff, PhoneOff, ShieldCheck, Sparkles } from "lucide-react";
import { getPublicWebVoiceAgent, startPublicWebVoiceSession, type PublicWebVoiceAgent } from "@/lib/api/public-web-voice";
import styles from "./WebVoiceCall.module.css";

type Message = { role: "visitor" | "agent"; text: string };
type VoiceEvent = {
  type: string;
  text?: string;
  value?: boolean;
  message?: string;
  outcome?: string;
};

export function WebVoiceCall({ publicId }: { publicId: string }) {
  const [agent, setAgent] = useState<PublicWebVoiceAgent | null>(null);
  const [consent, setConsent] = useState(false);
  const [status, setStatus] = useState<"loading" | "idle" | "connecting" | "live" | "ended">("loading");
  const [speaking, setSpeaking] = useState(false);
  const [partial, setPartial] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [error, setError] = useState("");
  const [language, setLanguage] = useState("");
  const [accent, setAccent] = useState("#31d9c9");
  const socketRef = useRef<WebSocket | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const contextRef = useRef<AudioContext | null>(null);
  const processorRef = useRef<AudioWorkletNode | null>(null);
  const playbackTimeRef = useRef(0);
  const playbackSourcesRef = useRef<Set<AudioBufferSourceNode>>(new Set());
  const pcmQueueRef = useRef<number[]>([]);

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
    return () => cleanup();
  }, [publicId]);

  async function start() {
    if (!agent || (agent.consentRequired && !consent)) return;
    setStatus("connecting");
    setError("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
      const embedded = new URLSearchParams(window.location.search).get("embed") === "1";
      const origin = embedded && document.referrer ? new URL(document.referrer).origin : window.location.origin;
      const session = await startPublicWebVoiceSession(publicId, consent, origin, language || agent.defaultLanguage);
      const context = new AudioContext();
      await context.resume();
      await context.audioWorklet.addModule("/web-voice-processor.js");
      streamRef.current = stream;
      contextRef.current = context;
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
        setSpeaking(false);
        setStatus((current) => current === "connecting" ? "ended" : current === "live" ? "ended" : current);
      };
    } catch (caught) {
      cleanup();
      setStatus("idle");
      setError(caught instanceof Error ? caught.message : "Microphone access could not be started.");
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
    if (event.type === "transcript_partial") setPartial(event.text ?? "");
    if (event.type === "transcript_final" && event.text) {
      setPartial("");
      setMessages((current) => [...current, { role: "visitor", text: event.text! }]);
    }
    if (event.type === "agent_response" && event.text) {
      setMessages((current) => [...current, { role: "agent", text: event.text! }]);
    }
    if (event.type === "speaking") setSpeaking(Boolean(event.value));
    if (event.type === "clear_audio") clearPlayback();
    if (event.type === "error") setError(event.message ?? "The voice session encountered an error.");
    if (event.type === "ended") {
      setStatus("ended");
      stopCapture();
    }
  }

  function playPcm(buffer: ArrayBuffer) {
    const context = contextRef.current;
    if (!context) return;
    const pcm = new Int16Array(buffer);
    const audioBuffer = context.createBuffer(1, pcm.length, 16000);
    const channel = audioBuffer.getChannelData(0);
    for (let index = 0; index < pcm.length; index++) channel[index] = pcm[index] / 32768;
    const source = context.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(context.destination);
    const startAt = Math.max(context.currentTime + 0.02, playbackTimeRef.current);
    source.start(startAt);
    playbackTimeRef.current = startAt + audioBuffer.duration;
    playbackSourcesRef.current.add(source);
    source.onended = () => playbackSourcesRef.current.delete(source);
  }

  function clearPlayback() {
    playbackSourcesRef.current.forEach((source) => {
      try { source.stop(); } catch { /* already stopped */ }
    });
    playbackSourcesRef.current.clear();
    playbackTimeRef.current = contextRef.current?.currentTime ?? 0;
  }

  function end() {
    socketRef.current?.close(1000, "Visitor ended the call");
    cleanup();
    setStatus("ended");
  }

  function stopCapture() {
    processorRef.current?.disconnect();
    processorRef.current = null;
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }

  function cleanup() {
    stopCapture();
    clearPlayback();
    const socket = socketRef.current;
    socketRef.current = null;
    if (socket && socket.readyState < WebSocket.CLOSING) socket.close();
    void contextRef.current?.close();
    contextRef.current = null;
    pcmQueueRef.current = [];
  }

  const pageStyle = { "--web-voice-accent": accent } as CSSProperties;

  return (
    <main className={styles.page} style={pageStyle}>
      <section className={styles.card}>
        <header><span><Sparkles size={22} /></span><div><small>WEB VOICE</small><h1>{agent?.name ?? "Voice assistant"}</h1></div><i className={status === "live" ? styles.online : ""}>{status === "live" ? "Live" : status}</i></header>
        <div className={`${styles.orb} ${speaking ? styles.speaking : ""}`}><Mic size={34} /></div>
        <h2>{status === "live" ? (speaking ? `${agent?.name} is speaking` : "Listening to you") : status === "ended" ? "Conversation ended" : "Talk with our assistant"}</h2>
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
  return ({ sw: "Swahili", en: "English", fr: "French", ar: "Arabic" } as Record<string, string>)[code] ?? code.toUpperCase();
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

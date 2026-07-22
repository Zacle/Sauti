import Vapi from "@vapi-ai/web";
import type { BrowserVoiceRuntimeSession } from "@/types/api";
import type {
  BrowserVoiceRuntimeCallbacks,
  BrowserVoiceRuntimeConnection,
} from "./browserVoiceRuntime";
import { vapiErrorMessage } from "./vapiErrors";
import {
  accumulateVapiCaption,
  isVapiOpeningTranscript,
  mergeVapiTranscript,
  type VapiCaptionState,
} from "./vapiTranscript";

type VapiMessage = {
  type?: string;
  role?: string;
  transcriptType?: string;
  transcript?: string;
  status?: string;
  text?: string;
  input?: string;
  turn?: number;
  source?: string;
  call?: { id?: string };
  [key: string]: unknown;
};

const CAPTION_EVENT_GRACE_MS = 160;

export async function connectVapiRuntime(
  session: BrowserVoiceRuntimeSession,
  callbacks: BrowserVoiceRuntimeCallbacks,
): Promise<BrowserVoiceRuntimeConnection> {
  const vapi = new Vapi(session.clientToken, session.apiBaseUrl);
  let stopped = false;
  let assistantSpeaking = false;
  let callerSpeaking = false;
  let interruptedAgentTurn = false;
  let endAuthorized = false;
  let assistantSpeechStartedAfterEndAuthorization = false;
  let ended = false;
  let lastFinal = "";
  let startFailure = "";
  let providerCallId = "";
  let pendingVoiceInput = "";
  let captionState: VapiCaptionState | null = null;
  let captionEventReceivedForSpeech = false;
  let lastCaptionEventAt = 0;
  let captionFallbackTimer: number | null = null;
  let lastCallerFinalAt = 0;
  const runtimeStartedAt = performance.now();
  const configuredFirstMessage = typeof session.configuration.firstMessage === "string"
    ? session.configuration.firstMessage.trim()
    : "";
  let initialAssistantTranscriptPending = configuredFirstMessage.length > 0;
  let openingCaptionDisplayed = false;
  const typedTranscripts: string[] = [];

  const trace = (event: string, details: Record<string, unknown> = {}) => {
    console.debug("Vapi runtime timing", {
      event,
      providerCallId: providerCallId || undefined,
      sinceRuntimeStartMs: Math.round(performance.now() - runtimeStartedAt),
      sinceCallerFinalMs: lastCallerFinalAt ? Math.round(performance.now() - lastCallerFinalAt) : undefined,
      ...details,
    });
  };

  const cancelCaptionFallback = () => {
    if (captionFallbackTimer === null) return;
    window.clearTimeout(captionFallbackTimer);
    captionFallbackTimer = null;
  };

  const scheduleCaptionFallback = () => {
    cancelCaptionFallback();
    captionFallbackTimer = window.setTimeout(() => {
      captionFallbackTimer = null;
      if (!assistantSpeaking || captionEventReceivedForSpeech || !pendingVoiceInput) return;
      // voice-input is the exact text Vapi handed to TTS. Use it only when the
      // playback-synchronized event did not arrive, and only after remote audio
      // actually began; model output is intentionally never shown as speech.
      captionState = accumulateVapiCaption(captionState, pendingVoiceInput);
      callbacks.onAgentCaption(captionState.text, captionState.turn);
      trace("caption-fallback", { textLength: captionState.text.length });
    }, CAPTION_EVENT_GRACE_MS);
  };

  const setAssistantSpeaking = (value: boolean) => {
    if (assistantSpeaking === value) return;
    assistantSpeaking = value;
    // Vapi's generic speech-start event is synchronized to remote audio. Use
    // it as a fallback for the fixed opening on SDK/provider versions that do
    // not emit the more precise assistant.speechStarted caption event.
    if (value && initialAssistantTranscriptPending && !openingCaptionDisplayed) {
      openingCaptionDisplayed = true;
      callbacks.onAgentCaption(configuredFirstMessage, 0);
    }
    if (value) {
      captionEventReceivedForSpeech = performance.now() - lastCaptionEventAt < 1000;
      scheduleCaptionFallback();
      trace("assistant-audio-start");
    } else {
      cancelCaptionFallback();
      captionEventReceivedForSpeech = false;
      lastCaptionEventAt = 0;
      trace("assistant-audio-end");
    }
    if (value && endAuthorized) assistantSpeechStartedAfterEndAuthorization = true;
    if (value || !callerSpeaking) callbacks.onAgentSpeaking(value);
    if (!value && endAuthorized && assistantSpeechStartedAfterEndAuthorization && !stopped) {
      window.setTimeout(() => vapi.end(), 180);
    }
  };

  const cancelAuthorizedEnd = () => {
    endAuthorized = false;
    assistantSpeechStartedAfterEndAuthorization = false;
  };

  const finish = (outcome: string) => {
    if (ended || stopped) return;
    ended = true;
    callbacks.onEnded(outcome);
  };

  vapi.on("call-start", callbacks.onConnected);
  vapi.on("call-start-progress", (event) => {
    trace("call-start-progress", {
      stage: event.stage,
      status: event.status,
      durationMs: event.duration === undefined ? undefined : Math.round(event.duration),
    });
  });
  vapi.on("call-start-success", (event) => {
    providerCallId = event.callId ?? providerCallId;
    trace("call-start-success", { setupMs: Math.round(event.totalDuration) });
  });
  vapi.on("speech-start", () => setAssistantSpeaking(true));
  vapi.on("speech-end", () => setAssistantSpeaking(false));
  vapi.on("message", (raw: unknown) => {
    const message = raw as VapiMessage;
    if (!providerCallId && message.call?.id) providerCallId = message.call.id;
    if (message.type === "assistant.speechStarted") {
      const text = message.text?.trim();
      if (text) {
        captionEventReceivedForSpeech = true;
        lastCaptionEventAt = performance.now();
        cancelCaptionFallback();
        if (message.source === "force-say" && initialAssistantTranscriptPending) {
          openingCaptionDisplayed = true;
        }
        captionState = accumulateVapiCaption(captionState, text, message.turn);
        callbacks.onAgentCaption(captionState.text, captionState.turn);
        trace("assistant-caption", {
          source: message.source,
          turn: message.turn,
          textLength: captionState.text.length,
        });
      }
      return;
    }
    if (message.type === "voice-input") {
      const input = message.input?.trim();
      if (input) {
        pendingVoiceInput = mergeVapiTranscript(pendingVoiceInput, input);
        trace("voice-input", { textLength: input.length });
        if (assistantSpeaking && !captionEventReceivedForSpeech) scheduleCaptionFallback();
      }
      return;
    }
    if (message.type === "speech-update") {
      if (message.role === "user" && message.status === "started") {
        callerSpeaking = true;
        pendingVoiceInput = "";
        captionState = null;
        cancelAuthorizedEnd();
        callbacks.onCallerSpeechStarted();
      }
      if (message.role === "user" && message.status === "stopped") {
        callerSpeaking = false;
        callbacks.onCallerSpeechEnded();
      }
      if (message.role === "assistant") setAssistantSpeaking(message.status === "started");
      return;
    }
    if (message.type === "user-interrupted") {
      callerSpeaking = true;
      pendingVoiceInput = "";
      captionState = null;
      cancelAuthorizedEnd();
      interruptedAgentTurn = true;
      callbacks.onInterrupted();
      return;
    }
    if (message.type === "tool-calls-result" && containsAuthorizedEnd(message)) {
      endAuthorized = true;
      assistantSpeechStartedAfterEndAuthorization = assistantSpeaking;
      return;
    }
    if (message.type === "status-update" && ["ended", "failed"].includes(String(message.status))) {
      if (message.status === "failed") callbacks.onError("The Vapi voice session ended unexpectedly.");
      finish(message.status === "failed" ? "failed" : "completed");
      return;
    }
    if (!["transcript", "transcript[transcriptType='final']"].includes(String(message.type))
        || message.transcriptType !== "final") return;
    const text = message.transcript?.trim();
    if (!text) return;
    const fingerprint = `${message.role}:${text}`;
    if (fingerprint === lastFinal) return;
    lastFinal = fingerprint;
    if (["user", "customer"].includes(String(message.role))) {
      callerSpeaking = false;
      interruptedAgentTurn = false;
      lastCallerFinalAt = performance.now();
      pendingVoiceInput = "";
      captionState = null;
      const typedIndex = typedTranscripts.findIndex((candidate) => normalize(candidate) === normalize(text));
      if (typedIndex >= 0) {
        typedTranscripts.splice(typedIndex, 1);
        return;
      }
      callbacks.onCallerTranscript(text);
      return;
    }
    if (["assistant", "bot"].includes(String(message.role))) {
      // Sauti already persists and displays the configured opening. Vapi also
      // emits its spoken firstMessage as a transcript, which otherwise creates
      // a duplicate opening turn (and may contain TTS/STT spelling drift).
      if (initialAssistantTranscriptPending) {
        initialAssistantTranscriptPending = false;
        if (isVapiOpeningTranscript(configuredFirstMessage, text)) {
          interruptedAgentTurn = false;
          return;
        }
      }
      callbacks.onAgentTranscript(text, interruptedAgentTurn);
      trace("assistant-final-transcript", { textLength: text.length });
      interruptedAgentTurn = false;
    }
  });
  vapi.on("call-end", () => finish("completed"));
  vapi.on("error", (error: unknown) => {
    const message = vapiErrorMessage(error);
    if (!startFailure) startFailure = message;
    callbacks.onError(message);
  });

  const call = await vapi.start(session.configuration as never);
  if (!call) throw new Error(startFailure || "Vapi did not create the browser call.");
  providerCallId = call.id || providerCallId;
  trace("call-created");

  return {
    sendUserText(text: string) {
      typedTranscripts.push(text);
      vapi.send({
        type: "add-message",
        message: { role: "user", content: text },
        triggerResponseEnabled: true,
      });
    },
    async stop() {
      if (stopped) return;
      stopped = true;
      ended = true;
      cancelCaptionFallback();
      await vapi.stop();
    },
  };
}

function containsAuthorizedEnd(message: VapiMessage) {
  try {
    return JSON.stringify(message).includes('"sautiEndCall":true');
  } catch {
    return false;
  }
}

function normalize(value: string) {
  return value.trim().replace(/\s+/g, " ").toLocaleLowerCase();
}

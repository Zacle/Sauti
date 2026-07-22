import Vapi from "@vapi-ai/web";
import type { BrowserVoiceRuntimeSession } from "@/types/api";
import type {
  BrowserVoiceRuntimeCallbacks,
  BrowserVoiceRuntimeConnection,
} from "./browserVoiceRuntime";
import { vapiErrorMessage } from "./vapiErrors";
import { isVapiOpeningTranscript } from "./vapiTranscript";

type VapiMessage = {
  type?: string;
  role?: string;
  transcriptType?: string;
  transcript?: string;
  status?: string;
  [key: string]: unknown;
};

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
  const configuredFirstMessage = typeof session.configuration.firstMessage === "string"
    ? session.configuration.firstMessage.trim()
    : "";
  let initialAssistantTranscriptPending = configuredFirstMessage.length > 0;
  const typedTranscripts: string[] = [];

  const setAssistantSpeaking = (value: boolean) => {
    if (assistantSpeaking === value) return;
    assistantSpeaking = value;
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
  vapi.on("speech-start", () => setAssistantSpeaking(true));
  vapi.on("speech-end", () => setAssistantSpeaking(false));
  vapi.on("message", (raw: unknown) => {
    const message = raw as VapiMessage;
    if (message.type === "speech-update") {
      if (message.role === "user" && message.status === "started") {
        callerSpeaking = true;
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

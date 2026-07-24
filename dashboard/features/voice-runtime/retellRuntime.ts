import { RetellWebClient } from "retell-client-js-sdk";
import type { BrowserVoiceRuntimeSession } from "@/types/api";
import type {
  BrowserVoiceRuntimeCallbacks,
  BrowserVoiceRuntimeConnection,
} from "./browserVoiceRuntime";
import { providerError } from "./managedRuntimeConfig";

type RetellTranscriptItem = {
  role?: string;
  content?: string;
  transcript?: string;
  text?: string;
  words?: Array<{ word?: string }>;
};

type RetellUpdate = {
  transcript?: RetellTranscriptItem[];
};

export async function connectRetellRuntime(
  session: BrowserVoiceRuntimeSession,
  callbacks: BrowserVoiceRuntimeCallbacks,
): Promise<BrowserVoiceRuntimeConnection> {
  const client = new RetellWebClient();
  let stopped = false;
  let ended = false;
  let agentSpeaking = false;
  let latestAgentText = "";
  let deliveredAgentText = "";
  let deliveredCallerText = "";
  const seenTranscriptItems = new Set<string>();

  const finish = () => {
    if (ended || stopped) return;
    ended = true;
    callbacks.onEnded("completed");
  };

  client.on("call_started", () => callbacks.onConnected());
  client.on("call_ended", finish);
  client.on("agent_start_talking", () => {
    agentSpeaking = true;
    callbacks.onAgentSpeaking(true);
    if (latestAgentText) callbacks.onAgentCaption(latestAgentText);
  });
  client.on("agent_stop_talking", () => {
    agentSpeaking = false;
    callbacks.onAgentSpeaking(false);
    if (latestAgentText && latestAgentText !== deliveredAgentText) {
      deliveredAgentText = latestAgentText;
      callbacks.onAgentCaption(latestAgentText);
      callbacks.onAgentTranscript(latestAgentText, false);
    }
  });
  client.on("update", (update: RetellUpdate) => {
    const transcript = Array.isArray(update.transcript) ? update.transcript : [];
    for (const item of transcript) {
      const role = String(item.role ?? "").toLowerCase();
      const text = transcriptText(item);
      if (!text) continue;
      const fingerprint = `${role}:${text}`;
      if (seenTranscriptItems.has(fingerprint)) continue;
      seenTranscriptItems.add(fingerprint);
      if (["user", "caller", "customer"].includes(role)) {
        if (text !== deliveredCallerText) {
          deliveredCallerText = text;
          callbacks.onCallerTranscript(text);
        }
      } else if (["agent", "assistant", "bot"].includes(role)) {
        latestAgentText = text;
        if (agentSpeaking) callbacks.onAgentCaption(text);
      }
    }
    const last = transcript.at(-1);
    if (last && ["agent", "assistant", "bot"].includes(String(last.role ?? "").toLowerCase())) {
      const text = transcriptText(last);
      if (text) {
        latestAgentText = text;
        if (agentSpeaking) callbacks.onAgentCaption(text);
      }
    }
  });
  client.on("error", (error: unknown) => callbacks.onError(providerError("Retell", error)));

  try {
    await client.startCall({ accessToken: session.clientToken });
  } catch (error) {
    throw new Error(providerError("Retell", error));
  }

  return {
    sendUserText() {
      callbacks.onError("Retell browser voice sessions do not support typed turns.");
    },
    async stop() {
      if (stopped) return;
      stopped = true;
      ended = true;
      client.stopCall();
    },
  };
}

function transcriptText(item: RetellTranscriptItem) {
  const direct = item.content ?? item.transcript ?? item.text;
  if (typeof direct === "string" && direct.trim()) return direct.trim();
  if (!Array.isArray(item.words)) return "";
  return item.words.map((word) => word.word ?? "").join(" ").trim();
}

import { RetellWebClient } from "retell-client-js-sdk";
import type { BrowserVoiceRuntimeSession } from "@/types/api";
import type {
  BrowserVoiceRuntimeCallbacks,
  BrowserVoiceRuntimeConnection,
} from "./browserVoiceRuntime";
import { configString, providerError } from "./managedRuntimeConfig";
import { RetellTranscriptReconciler, type RetellTranscriptTurn } from "./retellTranscript";
import { isVapiOpeningTranscript } from "./vapiTranscript";

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
  let openingPending = true;
  const configuredOpening = configString(session.configuration, "greeting");
  const transcriptReconciler = new RetellTranscriptReconciler();

  const deliverTurns = (turns: RetellTranscriptTurn[]) => {
    for (const turn of turns) {
      if (turn.role === "caller") {
        callbacks.onCallerTranscript(turn.text);
        continue;
      }
      callbacks.onAgentCaption(turn.text, turn.index);
      if (openingPending && isVapiOpeningTranscript(configuredOpening, turn.text)) {
        openingPending = false;
        continue;
      }
      openingPending = false;
      callbacks.onAgentTranscript(turn.text, false);
    }
  };

  const finish = () => {
    if (ended || stopped) return;
    deliverTurns(transcriptReconciler.completeAll());
    ended = true;
    callbacks.onEnded("completed");
  };

  client.on("call_started", () => callbacks.onConnected());
  client.on("call_ended", finish);
  client.on("agent_start_talking", () => {
    deliverTurns(transcriptReconciler.completeLatest("caller"));
    agentSpeaking = true;
    callbacks.onAgentSpeaking(true);
    const activeAgent = transcriptReconciler.activeAgentTurn();
    if (activeAgent) callbacks.onAgentCaption(activeAgent.text, activeAgent.index);
  });
  client.on("agent_stop_talking", () => {
    deliverTurns(transcriptReconciler.completeLatest("agent"));
    agentSpeaking = false;
    callbacks.onAgentSpeaking(false);
  });
  client.on("update", (update: RetellUpdate) => {
    const transcript = Array.isArray(update.transcript) ? update.transcript : [];
    const snapshot: RetellTranscriptTurn[] = [];
    for (const [index, item] of transcript.entries()) {
      const role = String(item.role ?? "").toLowerCase();
      const text = transcriptText(item);
      if (!text) continue;
      if (["user", "caller", "customer"].includes(role)) {
        snapshot.push({ index, role: "caller", text });
      } else if (["agent", "assistant", "bot"].includes(role)) {
        snapshot.push({ index, role: "agent", text });
      }
    }
    deliverTurns(transcriptReconciler.update(snapshot));
    const activeAgent = transcriptReconciler.activeAgentTurn();
    if (agentSpeaking && activeAgent) callbacks.onAgentCaption(activeAgent.text, activeAgent.index);
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

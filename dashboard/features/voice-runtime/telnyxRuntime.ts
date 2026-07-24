import type { BrowserVoiceRuntimeSession } from "@/types/api";
import type {
  BrowserVoiceRuntimeCallbacks,
  BrowserVoiceRuntimeConnection,
} from "./browserVoiceRuntime";
import {
  configString,
  configStringArray,
  providerError,
} from "./managedRuntimeConfig";
import { startTelnyxConversationWhenReady } from "./telnyxReadiness";

export async function connectTelnyxRuntime(
  session: BrowserVoiceRuntimeSession,
  callbacks: BrowserVoiceRuntimeCallbacks,
): Promise<BrowserVoiceRuntimeConnection> {
  const { TelnyxAIAgent } = await import("@telnyx/ai-agent-lib");
  const agentId = configString(session.configuration, "agentId");
  if (!agentId) throw new Error("Telnyx did not return an AI assistant id.");
  const toolNames = configStringArray(session.configuration, "toolNames");
  const clientTools = Object.fromEntries(toolNames.map((name) => [
    name,
    async (parameters: unknown, context: { callId: string }) => callbacks.executeTool(
      context.callId,
      name,
      JSON.stringify(parameters ?? {}),
    ),
  ]));
  const environment = configString(session.configuration, "environment") === "development"
    ? "development"
    : "production";
  const region = configString(session.configuration, "region");
  const client = new TelnyxAIAgent({
    agentId,
    versionId: configString(session.configuration, "versionId") || "main",
    environment,
    region: region || undefined,
    clientTools,
    clientToolTimeoutMs: 30_000,
    vad: {
      volumeThreshold: 10,
      silenceDurationMs: 700,
      minSpeechDurationMs: 120,
      maxLatencyMs: 15_000,
    },
  });
  const audio = document.createElement("audio");
  audio.autoplay = true;
  audio.setAttribute("playsinline", "");
  audio.hidden = true;
  document.body.append(audio);
  let stopped = false;
  let ended = false;
  let agentSpeaking = false;
  let latestAgentText = "";
  let conversationStarted = false;

  const finish = () => {
    if (!conversationStarted || ended || stopped) return;
    ended = true;
    callbacks.onEnded("completed");
  };

  client.on("agent.error", (error) => {
    if (conversationStarted) callbacks.onError(providerError("Telnyx", error));
  });
  client.on("agent.disconnected", finish);
  client.on("conversation.update", (notification) => {
    const stream = notification.call?.remoteStream;
    if (stream && audio.srcObject !== stream) {
      audio.srcObject = stream;
      void audio.play().catch(() => undefined);
    }
  });
  client.on("conversation.agent.state", ({ state }) => {
    if (state === "speaking") {
      agentSpeaking = true;
      callbacks.onAgentSpeaking(true);
      if (latestAgentText) callbacks.onAgentCaption(latestAgentText);
      return;
    }
    if (state === "thinking") {
      if (agentSpeaking) callbacks.onInterrupted();
      callbacks.onCallerSpeechEnded();
    }
    agentSpeaking = false;
    callbacks.onAgentSpeaking(false);
  });
  client.on("transcript.item", (item) => {
    const text = item.content.trim();
    if (!text) return;
    if (item.role === "user") {
      callbacks.onCallerTranscript(text);
      return;
    }
    latestAgentText = text;
    callbacks.onAgentTranscript(text, false);
    if (agentSpeaking) callbacks.onAgentCaption(text);
  });

  try {
    await startTelnyxConversationWhenReady({
      connect: () => client.connect(),
      startConversation: () => client.startConversation({
        customHeaders: [
          { name: "X-Sauti-Call-Sid", value: configString(session.configuration, "callSid") },
        ],
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      }),
      subscribe: ({ ready, failed, disconnected }) => {
        client.on("agent.connected", ready);
        client.on("agent.error", failed);
        client.on("agent.disconnected", disconnected);
        return () => {
          client.off("agent.connected", ready);
          client.off("agent.error", failed);
          client.off("agent.disconnected", disconnected);
        };
      },
    });
    conversationStarted = true;
    callbacks.onConnected();
  } catch (error) {
    stopped = true;
    ended = true;
    audio.remove();
    await client.disconnect().catch(() => undefined);
    throw new Error(providerError("Telnyx", error));
  }

  return {
    sendUserText(text: string) {
      client.sendConversationMessage(text);
    },
    async stop() {
      if (stopped) return;
      stopped = true;
      ended = true;
      await client.endConversation();
      await client.disconnect();
      audio.srcObject = null;
      audio.remove();
    },
  };
}

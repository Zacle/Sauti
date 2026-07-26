import type { BrowserVoiceRuntimeSession } from "@/types/api";
import type {
  BrowserVoiceRuntimeCallbacks,
  BrowserVoiceRuntimeConnection,
} from "./browserVoiceRuntime";
import {
  configString,
  providerError,
} from "./managedRuntimeConfig";
import { finalizeTelnyxEndConversation } from "./telnyxEndConversation";
import { startTelnyxConversationWhenReady } from "./telnyxReadiness";
import { callerClearlyRequestedBrowserEnd } from "./terminalIntent";

export async function connectTelnyxRuntime(
  session: BrowserVoiceRuntimeSession,
  callbacks: BrowserVoiceRuntimeCallbacks,
): Promise<BrowserVoiceRuntimeConnection> {
  const { TelnyxAIAgent } = await import("@telnyx/ai-agent-lib");
  callbacks.onStartupStage?.("sdk_loaded");
  const agentId = configString(session.configuration, "agentId");
  if (!agentId) throw new Error("Telnyx did not return an AI assistant id.");
  const environment = configString(session.configuration, "environment") === "development"
    ? "development"
    : "production";
  const region = configString(session.configuration, "region");
  const client = new TelnyxAIAgent({
    agentId,
    versionId: configString(session.configuration, "versionId") || "main",
    environment,
    region: region || undefined,
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
  let endingRequested = false;
  let providerCallControlId = "";
  let providerCallLegId = "";
  let terminalIntentPending = false;
  let terminalFarewellStarted = false;
  let terminalEndTimer: number | undefined;

  const retainProviderCallControlId = (value: string | null | undefined) => {
    const normalized = value?.trim() ?? "";
    if (!normalized || normalized === providerCallControlId) return;
    providerCallControlId = normalized;
    callbacks.onProviderCallControlId?.(normalized);
  };

  const retainProviderCallLegId = (value: string | null | undefined) => {
    const normalized = value?.trim() ?? "";
    if (!normalized || normalized === providerCallLegId) return;
    providerCallLegId = normalized;
    callbacks.onProviderCallLegId?.(normalized);
  };

  const retainProviderIds = () => {
    retainProviderCallControlId(client.activeCall?.telnyxIDs.telnyxCallControlId);
    retainProviderCallLegId(client.activeCall?.telnyxIDs.telnyxLegId);
  };

  const clearTerminalEndTimer = () => {
    if (terminalEndTimer !== undefined) window.clearTimeout(terminalEndTimer);
    terminalEndTimer = undefined;
  };

  const finish = () => {
    if (!conversationStarted || ended || stopped) return;
    clearTerminalEndTimer();
    ended = true;
    agentSpeaking = false;
    callbacks.onAgentSpeaking(false);
    audio.srcObject = null;
    audio.remove();
    callbacks.onEnded("completed");
  };

  const endBrowserConversation = () => {
    if (!conversationStarted || endingRequested || ended || stopped) return;
    endingRequested = true;
    clearTerminalEndTimer();
    retainProviderIds();
    void finalizeTelnyxEndConversation(
      () => client.endConversation(),
      (error) => callbacks.onError(providerError("Telnyx", error)),
      finish,
    );
  };

  const scheduleTerminalEnd = (delayMs: number) => {
    clearTerminalEndTimer();
    terminalEndTimer = window.setTimeout(endBrowserConversation, delayMs);
  };

  client.registerClientTool("end_browser_call", () => {
    // Acknowledge the tool before closing WebRTC so the SDK does not discard
    // its result as an output from an already-shut-down conversation.
    window.setTimeout(() => {
      endBrowserConversation();
    }, 100);
    return { success: true, ending: true };
  });
  client.on("client.tool.invoked", ({ toolName }) => callbacks.onToolInvoked?.(toolName));
  client.on("client.tool.completed", ({ toolName, isError }) =>
    callbacks.onToolCompleted?.(toolName, isError)
  );
  client.on("client.tool.error", ({ toolName, reason }) =>
    callbacks.onToolError?.(toolName, reason)
  );
  client.on("agent.error", (error) => {
    if (conversationStarted) callbacks.onError(providerError("Telnyx", error));
  });
  client.on("agent.disconnected", finish);
  client.on("conversation.update", (notification) => {
    retainProviderCallControlId(notification.call?.telnyxIDs.telnyxCallControlId);
    retainProviderCallLegId(notification.call?.telnyxIDs.telnyxLegId);
    const stream = notification.call?.remoteStream;
    if (stream && audio.srcObject !== stream) {
      audio.srcObject = stream;
      void audio.play().catch(() => undefined);
    }
  });
  client.on("conversation.agent.state", ({ state }) => {
    if (state === "speaking") {
      agentSpeaking = true;
      if (terminalIntentPending) terminalFarewellStarted = true;
      callbacks.onAgentSpeaking(true);
      if (latestAgentText) callbacks.onAgentCaption(latestAgentText);
      return;
    }
    if (state === "thinking") {
      if (agentSpeaking) callbacks.onInterrupted();
      callbacks.onCallerSpeechEnded();
    }
    const completedTerminalFarewell = agentSpeaking && terminalIntentPending && terminalFarewellStarted;
    agentSpeaking = false;
    callbacks.onAgentSpeaking(false);
    if (completedTerminalFarewell) scheduleTerminalEnd(450);
  });
  client.on("transcript.item", (item) => {
    const text = item.content.trim();
    if (!text) return;
    if (item.role === "user") {
      terminalIntentPending = callerClearlyRequestedBrowserEnd(text);
      terminalFarewellStarted = false;
      clearTerminalEndTimer();
      if (terminalIntentPending) scheduleTerminalEnd(12_000);
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
          { name: "X-Sauti-Conversation-Channel", value: "web_call" },
        ],
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      }),
      subscribe: ({ ready, failed, disconnected }) => {
        const signalingReady = (info: {
          dc: string | null;
          region: string | null;
          callReportId: string | null;
        }) => {
          callbacks.onStartupStage?.("signaling_ready", {
            dc: info.dc,
            region: info.region,
          });
          ready();
        };
        client.on("agent.connected", signalingReady);
        client.on("agent.error", failed);
        client.on("agent.disconnected", disconnected);
        return () => {
          client.off("agent.connected", signalingReady);
          client.off("agent.error", failed);
          client.off("agent.disconnected", disconnected);
        };
      },
    });
    conversationStarted = true;
    callbacks.onStartupStage?.("conversation_started");
    retainProviderIds();
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
    providerCallControlId() {
      return providerCallControlId;
    },
    providerCallLegId() {
      return providerCallLegId;
    },
    async stop() {
      if (stopped) return;
      clearTerminalEndTimer();
      retainProviderIds();
      stopped = true;
      ended = true;
      await client.endConversation();
      await client.disconnect();
      audio.srcObject = null;
      audio.remove();
    },
  };
}

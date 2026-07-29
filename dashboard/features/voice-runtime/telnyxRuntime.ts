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
import {
  startTelnyxConversationWithAuthenticationRetry,
  startTelnyxConversationWhenReady,
} from "./telnyxReadiness";
import { callerClearlyRequestedBrowserEnd } from "./terminalIntent";
import {
  TelnyxAgentTranscriptAccumulator,
  type CompletedAgentTranscript,
} from "./telnyxTranscript";

const RESPONSE_TIMEOUT_MS = 25_000;
const AGENT_TRANSCRIPT_SETTLE_MS = 1_200;
type TelnyxAgentModule = typeof import("@telnyx/ai-agent-lib");

let telnyxAgentModule: Promise<TelnyxAgentModule> | undefined;

export function preloadTelnyxRuntime() {
  telnyxAgentModule ??= import("@telnyx/ai-agent-lib").catch((error) => {
    telnyxAgentModule = undefined;
    throw error;
  });
  return telnyxAgentModule.then(() => undefined);
}

export async function connectTelnyxRuntime(
  session: BrowserVoiceRuntimeSession,
  callbacks: BrowserVoiceRuntimeCallbacks,
): Promise<BrowserVoiceRuntimeConnection> {
  await preloadTelnyxRuntime();
  const { TelnyxAIAgent } = await telnyxAgentModule!;
  callbacks.onStartupStage?.("sdk_loaded");
  const agentId = configString(session.configuration, "agentId");
  if (!agentId) throw new Error("Telnyx did not return an AI assistant id.");
  const environment = configString(session.configuration, "environment") === "development"
    ? "development"
    : "production";
  const region = configString(session.configuration, "region");
  const createClient = () => new TelnyxAIAgent({
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
  let client = createClient();
  const audio = document.createElement("audio");
  audio.autoplay = true;
  audio.setAttribute("playsinline", "");
  audio.hidden = true;
  document.body.append(audio);
  let stopped = false;
  let ended = false;
  let agentSpeaking = false;
  let conversationStarted = false;
  let endingRequested = false;
  let providerCallControlId = "";
  let providerCallLegId = "";
  let terminalIntentPending = false;
  let terminalFarewellStarted = false;
  let terminalEndTimer: number | undefined;
  let responseTimer: number | undefined;
  let transcriptFlushTimer: number | undefined;
  const agentTranscript = new TelnyxAgentTranscriptAccumulator();

  const emitCompletedAgentTranscript = (
    completed: CompletedAgentTranscript | undefined,
  ) => {
    if (completed) callbacks.onAgentTranscript(completed.text, completed.interrupted);
  };

  const clearResponseTimer = () => {
    if (responseTimer !== undefined) window.clearTimeout(responseTimer);
    responseTimer = undefined;
  };

  const clearTranscriptFlushTimer = () => {
    if (transcriptFlushTimer !== undefined) window.clearTimeout(transcriptFlushTimer);
    transcriptFlushTimer = undefined;
  };

  const scheduleAgentTranscriptFlush = () => {
    clearTranscriptFlushTimer();
    transcriptFlushTimer = window.setTimeout(() => {
      transcriptFlushTimer = undefined;
      emitCompletedAgentTranscript(agentTranscript.flush(false));
    }, AGENT_TRANSCRIPT_SETTLE_MS);
  };

  const armResponseTimer = () => {
    clearResponseTimer();
    responseTimer = window.setTimeout(() => {
      responseTimer = undefined;
      callbacks.onError(
        "Telnyx did not return response audio within 25 seconds. You can repeat your last answer or end the conversation.",
      );
    }, RESPONSE_TIMEOUT_MS);
  };

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
    clearResponseTimer();
    clearTranscriptFlushTimer();
    emitCompletedAgentTranscript(agentTranscript.flush(false));
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

  const configureClient = (target: InstanceType<typeof TelnyxAIAgent>) => {
    target.registerClientTool("end_browser_call", () => {
      // The model can emit the terminal tool before remote TTS has drained.
      // Keep WebRTC open until Telnyx reports that the farewell stopped playing;
      // the longer timer is only a fallback for a missing speaking-state event.
      terminalIntentPending = true;
      if (agentSpeaking) {
        terminalFarewellStarted = true;
        scheduleTerminalEnd(12_000);
      } else {
        scheduleTerminalEnd(650);
      }
      return { success: true, ending: true };
    });
    target.on("client.tool.invoked", ({ toolName }) => callbacks.onToolInvoked?.(toolName));
    target.on("client.tool.completed", ({ toolName, isError }) =>
      callbacks.onToolCompleted?.(toolName, isError)
    );
    target.on("client.tool.error", ({ toolName, reason }) =>
      callbacks.onToolError?.(toolName, reason)
    );
    target.on("agent.error", (error) => {
      if (conversationStarted) callbacks.onError(providerError("Telnyx", error));
    });
    target.on("agent.disconnected", finish);
    target.on("conversation.update", (notification) => {
      retainProviderCallControlId(notification.call?.telnyxIDs.telnyxCallControlId);
      retainProviderCallLegId(notification.call?.telnyxIDs.telnyxLegId);
      const stream = notification.call?.remoteStream;
      if (stream && audio.srcObject !== stream) {
        audio.srcObject = stream;
        callbacks.onStartupStage?.("remote_audio_ready");
        void audio.play().catch((error) => callbacks.onError(
          `The browser could not play Telnyx audio: ${providerError("Browser", error)}`,
        ));
      }
    });
    target.on("conversation.agent.state", ({
      state,
      greetingLatencyMs,
      userPerceivedLatencyMs,
    }) => {
      if (typeof greetingLatencyMs === "number" && greetingLatencyMs >= 0) {
        callbacks.onLatencyMeasured?.("greeting", Math.round(greetingLatencyMs));
      }
      if (typeof userPerceivedLatencyMs === "number" && userPerceivedLatencyMs >= 0) {
        callbacks.onLatencyMeasured?.("turn", Math.round(userPerceivedLatencyMs));
      }
      if (state === "speaking") {
        clearResponseTimer();
        clearTranscriptFlushTimer();
        if (terminalIntentPending) clearTerminalEndTimer();
        agentSpeaking = true;
        if (terminalIntentPending) terminalFarewellStarted = true;
        callbacks.onAgentSpeaking(true);
        return;
      }
      if (state === "thinking") {
        armResponseTimer();
        clearTranscriptFlushTimer();
        if (agentSpeaking) {
          emitCompletedAgentTranscript(agentTranscript.flush(true));
          callbacks.onInterrupted();
        }
        callbacks.onCallerSpeechEnded();
      }
      const completedTerminalFarewell =
        agentSpeaking && terminalIntentPending && terminalFarewellStarted;
      if (agentSpeaking && state === "listening") {
        scheduleAgentTranscriptFlush();
      }
      agentSpeaking = false;
      callbacks.onAgentSpeaking(false);
      if (completedTerminalFarewell) scheduleTerminalEnd(1_500);
    });
    target.on("transcript.item", (item) => {
      const text = item.content.trim();
      if (!text) return;
      if (item.role === "user") {
        clearTranscriptFlushTimer();
        emitCompletedAgentTranscript(agentTranscript.flush(false));
        terminalIntentPending = callerClearlyRequestedBrowserEnd(text);
        terminalFarewellStarted = false;
        clearTerminalEndTimer();
        if (terminalIntentPending) scheduleTerminalEnd(12_000);
        callbacks.onCallerTranscript(text);
        return;
      }
      if (agentSpeaking) clearResponseTimer();
      else armResponseTimer();
      const update = agentTranscript.append(item.id, item.content);
      emitCompletedAgentTranscript(update.completed);
      callbacks.onAgentCaption(update.caption);
    });
  };
  configureClient(client);

  try {
    await startTelnyxConversationWithAuthenticationRetry({
      start: () => startTelnyxConversationWhenReady({
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
        subscribeConversation: ({ active, failed, disconnected }) => {
          const conversationActive = (notification: {
            call?: { state?: string } | null;
          }) => {
            if (notification.call?.state !== "active") return;
            callbacks.onStartupStage?.("conversation_active");
            active();
          };
          client.on("conversation.update", conversationActive);
          client.on("agent.error", failed);
          client.on("agent.disconnected", disconnected);
          return () => {
            client.off("conversation.update", conversationActive);
            client.off("agent.error", failed);
            client.off("agent.disconnected", disconnected);
          };
        },
        subscribe: ({ ready, failed, disconnected }) => {
          const signalingReady = (info: {
            dc: string | null;
            region: string | null;
            callReportId: string | null;
          }) => {
            callbacks.onStartupStage?.("signaling_ready", {
              dc: info.dc,
              region: info.region,
              callReportId: info.callReportId,
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
      }),
      clearReconnectToken: () => client.clearReconnectToken(),
      onRetry: (attempt) => callbacks.onStartupStage?.("authentication_retry", { attempt }),
      resetConversation: async () => {
        const previousClient = client;
        const ending = previousClient.endConversation();
        if (ending) await ending.catch(() => undefined);
        await previousClient.disconnect().catch(() => undefined);
        client = createClient();
        configureClient(client);
      },
      onConversationRetry: (attempt) =>
        callbacks.onStartupStage?.("conversation_retry", { attempt }),
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
      clearResponseTimer();
      clearTranscriptFlushTimer();
      emitCompletedAgentTranscript(agentTranscript.flush(false));
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

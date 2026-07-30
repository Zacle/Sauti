import type { BrowserVoiceRuntimeSession } from "@/types/api";
import type {
  BrowserVoiceRuntimeCallbacks,
  BrowserVoiceRuntimeConnection,
  BrowserVoiceRuntimeOptions,
} from "./browserVoiceRuntime";
import { browserMicrophoneConstraints } from "./browserVoiceRuntime";
import {
  configString,
  providerError,
} from "./managedRuntimeConfig";
import { finalizeTelnyxEndConversation } from "./telnyxEndConversation";
import {
  startTelnyxConversationWhenConnected,
  startTelnyxConversationWithAuthenticationRetry,
  startTelnyxConversationWhenReady,
} from "./telnyxReadiness";
import {
  TELNYX_BROWSER_VAD,
  TERMINAL_END_AFTER_DRAIN_MS,
  TERMINAL_END_FALLBACK_MS,
  terminalToolEndDelay,
} from "./telnyxAudioPolicy";
import { callerClearlyRequestedBrowserEnd } from "./terminalIntent";
import {
  isTelnyxControlTranscript,
  TelnyxAgentTranscriptAccumulator,
  type CompletedAgentTranscript,
} from "./telnyxTranscript";

const RESPONSE_TIMEOUT_MS = 25_000;
const AGENT_TRANSCRIPT_SETTLE_MS = 1_200;
const PRECONNECTED_CLIENT_TTL_MS = 60_000;
type TelnyxAgentModule = typeof import("@telnyx/ai-agent-lib");
type TelnyxClient = InstanceType<TelnyxAgentModule["TelnyxAIAgent"]>;

let telnyxAgentModule: Promise<TelnyxAgentModule> | undefined;
let preconnectedClient: {
  key: string;
  client: TelnyxClient;
  ready: Promise<void>;
  expires: ReturnType<typeof setTimeout>;
  invalidate: () => void;
} | undefined;

export function preloadTelnyxRuntime() {
  telnyxAgentModule ??= import("@telnyx/ai-agent-lib").catch((error) => {
    telnyxAgentModule = undefined;
    throw error;
  });
  return telnyxAgentModule.then(() => undefined);
}

function runtimeKey(session: BrowserVoiceRuntimeSession) {
  return [
    configString(session.configuration, "agentId"),
    configString(session.configuration, "versionId") || "latest",
    configString(session.configuration, "environment") || "production",
    configString(session.configuration, "region"),
  ].join("|");
}

function createTelnyxClient(
  TelnyxAIAgent: TelnyxAgentModule["TelnyxAIAgent"],
  session: BrowserVoiceRuntimeSession,
) {
  const agentId = configString(session.configuration, "agentId");
  if (!agentId) throw new Error("Telnyx did not return an AI assistant id.");
  const environment = configString(session.configuration, "environment") === "development"
    ? "development"
    : "production";
  const region = configString(session.configuration, "region");
  const versionId = configString(session.configuration, "versionId");
  return new TelnyxAIAgent({
    agentId,
    versionId: versionId || undefined,
    environment,
    region: region || undefined,
    // The same threshold observes local and remote audio. The policy keeps
    // residual remote noise from leaving the UI stuck in "speaking" while the
    // retained microphone stream gets its own gain before reaching the SDK.
    vad: TELNYX_BROWSER_VAD,
  });
}

function connectClient(client: TelnyxClient, timeoutMs = 15_000) {
  return new Promise<void>((resolve, reject) => {
    let settled = false;
    const settle = (error?: unknown) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      client.off("agent.connected", connected);
      client.off("agent.error", failed);
      client.off("agent.disconnected", disconnected);
      if (error === undefined) resolve();
      else reject(error instanceof Error ? error : new Error(String(error)));
    };
    const connected = () => settle();
    const failed = (error: unknown) => settle(error);
    const disconnected = () => settle(
      new Error("Telnyx disconnected before the prepared connection became ready."),
    );
    const timeout = setTimeout(
      () => settle(new Error("Telnyx did not preconnect before the connection timeout.")),
      timeoutMs,
    );
    client.on("agent.connected", connected);
    client.on("agent.error", failed);
    client.on("agent.disconnected", disconnected);
    void client.connect().catch(failed);
  });
}

export async function releasePreconnectedTelnyxRuntime() {
  const prepared = preconnectedClient;
  if (!prepared) return;
  preconnectedClient = undefined;
  clearTimeout(prepared.expires);
  prepared.client.off("agent.disconnected", prepared.invalidate);
  await prepared.client.disconnect().catch(() => undefined);
}

export async function preconnectTelnyxRuntime(session: BrowserVoiceRuntimeSession) {
  await preloadTelnyxRuntime();
  const { TelnyxAIAgent } = await telnyxAgentModule!;
  const key = runtimeKey(session);
  if (preconnectedClient?.key === key) {
    await preconnectedClient.ready;
    return;
  }
  await releasePreconnectedTelnyxRuntime();
  const client = createTelnyxClient(TelnyxAIAgent, session);
  const ready = startTelnyxConversationWithAuthenticationRetry({
    start: () => connectClient(client),
    clearReconnectToken: () => client.clearReconnectToken(),
    conversationRetryDelaysMs: [],
  });
  const invalidate = () => {
    if (preconnectedClient?.client !== client) return;
    clearTimeout(preconnectedClient.expires);
    preconnectedClient = undefined;
  };
  const expires = setTimeout(() => {
    if (preconnectedClient?.client !== client) return;
    void releasePreconnectedTelnyxRuntime();
  }, PRECONNECTED_CLIENT_TTL_MS);
  preconnectedClient = { key, client, ready, expires, invalidate };
  client.on("agent.disconnected", invalidate);
  try {
    await ready;
  } catch (error) {
    if (preconnectedClient?.client === client) {
      await releasePreconnectedTelnyxRuntime();
    }
    throw error;
  }
}

export async function connectTelnyxRuntime(
  session: BrowserVoiceRuntimeSession,
  callbacks: BrowserVoiceRuntimeCallbacks,
  options: BrowserVoiceRuntimeOptions = {},
): Promise<BrowserVoiceRuntimeConnection> {
  await preloadTelnyxRuntime();
  const { TelnyxAIAgent } = await telnyxAgentModule!;
  callbacks.onStartupStage?.("sdk_loaded");
  const createClient = () => createTelnyxClient(TelnyxAIAgent, session);
  const prepared = preconnectedClient?.key === runtimeKey(session)
    ? preconnectedClient
    : undefined;
  let client = prepared?.client ?? createClient();
  let signalingAlreadyConnected = Boolean(prepared);
  if (prepared) {
    await prepared.ready;
    clearTimeout(prepared.expires);
    prepared.client.off("agent.disconnected", prepared.invalidate);
    if (preconnectedClient?.client === prepared.client) preconnectedClient = undefined;
    callbacks.onStartupStage?.("signaling_ready", { prepared: true });
  }
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
  let lastAgentStoppedAt = 0;
  let terminalEndTimer: number | undefined;
  let responseTimer: number | undefined;
  let transcriptFlushTimer: number | undefined;
  let microphoneLevelTimer: number | undefined;
  let microphoneLevelWindowTimer: number | undefined;
  let microphoneRmsDb = -100;
  let microphonePeakDb = -100;
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

  const stopMicrophoneLevelMonitoring = () => {
    if (microphoneLevelTimer !== undefined) window.clearInterval(microphoneLevelTimer);
    if (microphoneLevelWindowTimer !== undefined) {
      window.clearInterval(microphoneLevelWindowTimer);
    }
    microphoneLevelTimer = undefined;
    microphoneLevelWindowTimer = undefined;
  };

  const startMicrophoneLevelMonitoring = () => {
    const microphone = options.microphone;
    if (!microphone || !callbacks.onMicrophoneLevel) return;
    microphoneLevelTimer = window.setInterval(() => {
      const level = microphone.readLevel();
      microphoneRmsDb = Math.max(microphoneRmsDb, level.rmsDb);
      microphonePeakDb = Math.max(microphonePeakDb, level.peakDb);
    }, 100);
    microphoneLevelWindowTimer = window.setInterval(() => {
      callbacks.onMicrophoneLevel?.(microphoneRmsDb, microphonePeakDb);
      microphoneRmsDb = -100;
      microphonePeakDb = -100;
    }, 2_000);
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
    stopMicrophoneLevelMonitoring();
    emitCompletedAgentTranscript(agentTranscript.flush(false));
    ended = true;
    agentSpeaking = false;
    callbacks.onAgentSpeaking(false);
    audio.srcObject = null;
    audio.remove();
    void options.microphone?.stop();
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
      if (agentSpeaking) terminalFarewellStarted = true;
      // If invocation arrives before TTS, speaking cancels this fallback. If
      // it arrives after speech, leave time for WebRTC playout to drain.
      scheduleTerminalEnd(terminalToolEndDelay({
        agentSpeaking,
        lastAgentStoppedAt,
        now: Date.now(),
      }));
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
      if (agentSpeaking) lastAgentStoppedAt = Date.now();
      agentSpeaking = false;
      callbacks.onAgentSpeaking(false);
      if (completedTerminalFarewell) scheduleTerminalEnd(TERMINAL_END_AFTER_DRAIN_MS);
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
        if (terminalIntentPending) scheduleTerminalEnd(TERMINAL_END_FALLBACK_MS);
        callbacks.onCallerTranscript(text);
        return;
      }
      if (isTelnyxControlTranscript(text)) return;
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
      start: () => signalingAlreadyConnected
        ? startTelnyxConversationWhenConnected({
          startConversation: () => client.startConversation({
            customHeaders: [
              { name: "X-Sauti-Call-Sid", value: configString(session.configuration, "callSid") },
              { name: "X-Sauti-Conversation-Channel", value: "web_call" },
            ],
            audio: browserMicrophoneConstraints(),
            localStream: options.microphone?.stream,
          } as Parameters<TelnyxClient["startConversation"]>[0] & {
            localStream?: MediaStream;
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
        })
        : startTelnyxConversationWhenReady({
        connect: () => client.connect(),
        startConversation: () => client.startConversation({
          customHeaders: [
            { name: "X-Sauti-Call-Sid", value: configString(session.configuration, "callSid") },
            { name: "X-Sauti-Conversation-Channel", value: "web_call" },
          ],
          audio: browserMicrophoneConstraints(),
          localStream: options.microphone?.stream,
        } as Parameters<TelnyxClient["startConversation"]>[0] & {
          localStream?: MediaStream;
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
        signalingAlreadyConnected = false;
        configureClient(client);
      },
      onConversationRetry: (attempt) =>
        callbacks.onStartupStage?.("conversation_retry", { attempt }),
    });
    conversationStarted = true;
    callbacks.onStartupStage?.("conversation_started");
    retainProviderIds();
    startMicrophoneLevelMonitoring();
    callbacks.onConnected();
  } catch (error) {
    stopped = true;
    ended = true;
    audio.remove();
    await client.disconnect().catch(() => undefined);
    await options.microphone?.stop();
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
      stopMicrophoneLevelMonitoring();
      emitCompletedAgentTranscript(agentTranscript.flush(false));
      retainProviderIds();
      stopped = true;
      ended = true;
      await client.endConversation();
      await client.disconnect();
      await options.microphone?.stop();
      audio.srcObject = null;
      audio.remove();
    },
  };
}

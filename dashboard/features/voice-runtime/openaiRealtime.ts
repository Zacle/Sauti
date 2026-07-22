import { RealtimeTurnGate } from "./realtimeTurnGate";
import {
  authorizedNextToolRequest,
  businessActionProgressInstruction,
  callerGuidanceInstruction,
  completedResponseText,
  completedRealtimeToolCalls,
  isSlowResponseProgressEvent,
  realtimeCancellationDecision,
  realtimeResponseRequestId,
  realtimeTranscriptMirrorItem,
  SAUTI_REALTIME_REQUEST_ID,
  shouldRetrySlowResponse,
  slowResponseProgressRequest,
} from "./realtimeProtocol";

const SEMANTIC_TURN_TOOL = "update_conversation_state";
const PRIMARY_RESPONSE_WATCHDOG_MS = 8_000;
const RECOVERY_RESPONSE_WATCHDOG_MS = 6_000;
const TOOL_EXECUTION_TIMEOUT_MS = 12_000;
const BOOKING_EXECUTION_TIMEOUT_MS = 30_000;
const BUSINESS_ACTION_PROGRESS_DELAY_MS = 1_500;
const SLOW_RESPONSE_PROGRESS_DELAY_MS = 2_500;
const RESPONSE_CANCELLATION_WATCHDOG_MS = 2_000;

export type OpenAiRealtimeCallbacks = {
  onConnected: () => void;
  onCallerTranscript: (text: string, generation: number) => void;
  onAgentTranscript: (text: string, interrupted: boolean) => void;
  onSpeaking: (speaking: boolean) => void;
  onCallerAudioAbandoned?: () => void;
  onCallerSpeechStarted: (agentWasResponding: boolean, generation: number) => void;
  onAgentSpeech?: (speech: { id: string; generation: number; text: string }) => void;
  onError: (message: string) => void;
  executeTool: (callId: string, name: string, argumentsJson: string) => Promise<Record<string, unknown>>;
};

export type OpenAiRealtimeConnection = {
  peer: RTCPeerConnection;
  channel: RTCDataChannel;
  close: () => void;
  sendUserText: (text: string) => void;
  speakGreeting: (text: string) => void;
  cancelResponse: () => void;
};

type AgentSpeech = {
  id: string;
  generation: number;
  text: string;
};

type PendingResponseRequest = {
  requestId: string;
  generation: number;
  send: (requestId: string) => void;
  retryWithoutTools: boolean;
  purpose: "conversation" | "business_progress" | "post_booking_guidance";
  progressKey: string;
  progressOnDelay: boolean;
};

type SlowResponseProgress = {
  requestId: string;
  generation: number;
  responseId: string;
  active: boolean;
};

type ResponsePhase = "idle" | "awaiting_response" | "generating_message" | "generating_tool";

export async function connectOpenAiRealtime(options: {
  microphone: MediaStream;
  greeting: string;
  connectSdp: (offer: string) => Promise<string>;
  callbacks: OpenAiRealtimeCallbacks;
  playbackContext?: AudioContext | null;
  recordingDestination?: MediaStreamAudioDestinationNode | null;
  outputMode?: "audio" | "text";
  bargeInDebounceMs?: number;
  recordCallerTranscript?: (text: string) => Promise<unknown>;
  responseLanguage?: string;
}): Promise<OpenAiRealtimeConnection> {
  if ((options.outputMode ?? "text") === "audio") {
    throw new Error("Native Realtime audio is disabled because agent speech must be validated before playback.");
  }
  const peer = new RTCPeerConnection();
  options.microphone.getTracks().forEach((track) => peer.addTrack(track, options.microphone));
  peer.ontrack = (event) => {
    // This session is text-only. Stop any unexpected provider audio track so it
    // cannot bypass complete-message validation and the guarded TTS channel.
    event.track.stop();
  };

  const channel = peer.createDataChannel("oai-events");
  let agentTranscript = "";
  let agentInterrupted = false;
  let textCompletionSent = false;
  let responseActive = false;
  let callerSpeechActive = false;
  let bargeInTimer = 0;
  let activeCallerTurnKey = "";
  let callerTurnSequence = 0;
  const pendingCallerTurns = new Map<string, number>();
  let currentOutputItemId = "";
  const outputItemTypes = new Map<string, string>();
  let responseHasToolCall = false;
  let protocolRecoveryAttempts = 0;
  let expectedResponses = 0;
  let responseRequestInFlight = false;
  let responseWatchdog = 0;
  let toolResponseSettleTimer = 0;
  let responsePhase: ResponsePhase = "idle";
  let currentResponseCanRetryWithoutTools = false;
  let currentResponsePurpose: PendingResponseRequest["purpose"] = "conversation";
  let currentBusinessActionProgressKey = "";
  let responseCancellationPending = false;
  let currentResponseId = "";
  let currentResponseGeneration = 0;
  let outputGeneration = 0;
  let callerTurnGeneration: number | null = null;
  let callerTurnAgentWasResponding = false;
  let localSpeechSequence = 0;
  let discardCurrentResponseOutput = false;
  const ignoredResponseIds = new Set<string>();
  const abandonedResponseRequestIds = new Set<string>();
  const processedCallerItemIds = new Set<string>();
  const processedToolCallIds = new Set<string>();
  let lastCallerTranscript = "";
  let lastCallerTranscriptAt = 0;
  const deferredAgentSpeech: Array<AgentSpeech> = [];
  const deliveredSpeechIds = new Set<string>();
  const deliveredSpeechFingerprints = new Set<string>();
  const toolExecutions = new Map<string, Promise<Record<string, unknown>>>();
  const businessActionProgressTimers = new Map<string, number>();
  const activeBusinessActionProgress = new Set<string>();
  const toolFollowupGenerations = new Set<number>();
  const noToolRecoveryGenerations = new Set<number>();
  const slowResponseRetryGenerations = new Set<number>();
  const slowResponseProgress = new Map<string, SlowResponseProgress>();
  const slowResponseProgressResponseIds = new Set<string>();
  const pendingResponseRequests: PendingResponseRequest[] = [];
  let dispatchedResponseRequest: PendingResponseRequest | null = null;
  let currentResponseRequest: PendingResponseRequest | null = null;
  let responseRequestSequence = 0;
  let slowResponseProgressSequence = 0;
  let slowResponseProgressTimer = 0;
  let callerTranscriptWriteChain = Promise.resolve();
  const callerSpeechGate = new RealtimeTurnGate();

  const responseId = (event: Record<string, unknown>) => {
    const response = event.response as { id?: string } | undefined;
    return String(event.response_id ?? response?.id ?? "").trim();
  };

  const activeMainResponseRequest = () => responseActive
    ? currentResponseRequest
    : responseRequestInFlight ? dispatchedResponseRequest : null;

  const cancelSlowResponseProgress = (generation?: number) => {
    window.clearTimeout(slowResponseProgressTimer);
    slowResponseProgressTimer = 0;
    slowResponseProgress.forEach((progress) => {
      if (generation !== undefined && progress.generation !== generation) return;
      progress.active = false;
      if (progress.responseId && channel.readyState === "open") {
        send(channel, { type: "response.cancel", response_id: progress.responseId });
      }
    });
  };

  const armSlowResponseProgress = (request: PendingResponseRequest) => {
    window.clearTimeout(slowResponseProgressTimer);
    slowResponseProgressTimer = 0;
    if (!request.progressOnDelay) return;
    slowResponseProgressTimer = window.setTimeout(() => {
      slowResponseProgressTimer = 0;
      const active = activeMainResponseRequest();
      if (request.generation !== outputGeneration || active?.requestId !== request.requestId) return;
      const requestId = `browser-progress-${request.generation}-${++slowResponseProgressSequence}`;
      slowResponseProgress.set(requestId, {
        requestId,
        generation: request.generation,
        responseId: "",
        active: true,
      });
      send(channel, slowResponseProgressRequest(requestId));
      window.setTimeout(() => {
        const pending = slowResponseProgress.get(requestId);
        if (pending && !pending.responseId) slowResponseProgress.delete(requestId);
      }, 15_000);
    }, SLOW_RESPONSE_PROGRESS_DELAY_MS);
  };

  const handleSlowResponseProgressEvent = (event: Record<string, unknown>) => {
    if (!isSlowResponseProgressEvent(event, slowResponseProgressResponseIds)) return false;
    const type = String(event.type ?? "");
    const response = event.response as {
      id?: unknown;
      metadata?: Record<string, unknown>;
    } | undefined;
    const eventResponseId = String(event.response_id ?? response?.id ?? "").trim();
    const requestId = String(response?.metadata?.[SAUTI_REALTIME_REQUEST_ID] ?? "").trim();
    const progress = slowResponseProgress.get(requestId)
      ?? [...slowResponseProgress.values()].find((candidate) => candidate.responseId === eventResponseId);
    if (type === "response.created" && progress && eventResponseId) {
      progress.responseId = eventResponseId;
      slowResponseProgressResponseIds.add(eventResponseId);
      if (!progress.active || progress.generation !== outputGeneration) {
        send(channel, { type: "response.cancel", response_id: eventResponseId });
      }
    } else if (type === "response.created" && !progress && eventResponseId) {
      send(channel, { type: "response.cancel", response_id: eventResponseId });
    }
    if ((type === "response.done" || type === "response.cancelled") && progress) {
      const speech = sanitizeVoiceOutput(completedResponseText(event));
      if (type === "response.done" && progress.active
        && progress.generation === outputGeneration
        && activeMainResponseRequest() && speech) {
        deliverAgentSpeech({
          id: eventResponseId || `slow-progress:${progress.requestId}`,
          generation: progress.generation,
          text: speech,
        });
      }
      slowResponseProgress.delete(progress.requestId);
      if (eventResponseId) slowResponseProgressResponseIds.delete(eventResponseId);
    }
    return true;
  };

  const drainResponseQueue = () => {
    if (responseActive || responseRequestInFlight || responseCancellationPending) return;
    let request = pendingResponseRequests.shift();
    while (request && request.generation !== outputGeneration) {
      request = pendingResponseRequests.shift();
    }
    if (!request || channel.readyState !== "open") return;
    responseRequestInFlight = true;
    dispatchedResponseRequest = request;
    expectedResponses += 1;
    responseHasToolCall = false;
    responsePhase = "awaiting_response";
    request.send(request.requestId);
    armSlowResponseProgress(request);
    armResponseWatchdog(
      request.generation,
      noToolRecoveryGenerations.has(request.generation)
        ? RECOVERY_RESPONSE_WATCHDOG_MS
        : PRIMARY_RESPONSE_WATCHDOG_MS,
    );
  };

  const enqueueResponse = (
    request: (requestId: string) => void,
    generation = outputGeneration,
    retryWithoutTools = false,
    purpose: PendingResponseRequest["purpose"] = "conversation",
    progressKey = "",
    progressOnDelay = false,
  ) => {
    if (generation !== outputGeneration) return;
    pendingResponseRequests.push({
      requestId: `browser-${generation}-${++responseRequestSequence}`,
      generation,
      send: request,
      retryWithoutTools,
      purpose,
      progressKey,
      progressOnDelay,
    });
    drainResponseQueue();
  };

  const finishResponse = (preserveSlowProgress = false) => {
    window.clearTimeout(responseWatchdog);
    responseWatchdog = 0;
    window.clearTimeout(toolResponseSettleTimer);
    toolResponseSettleTimer = 0;
    responseActive = false;
    responseRequestInFlight = false;
    responsePhase = "idle";
    currentResponseCanRetryWithoutTools = false;
    currentResponsePurpose = "conversation";
    currentBusinessActionProgressKey = "";
    dispatchedResponseRequest = null;
    responseCancellationPending = false;
    currentResponseId = "";
    currentResponseRequest = null;
    currentResponseGeneration = outputGeneration;
    if (!preserveSlowProgress) cancelSlowResponseProgress();
    window.setTimeout(drainResponseQueue, 0);
  };

  const armResponseWatchdog = (generation: number, timeoutMs: number) => {
    window.clearTimeout(responseWatchdog);
    responseWatchdog = window.setTimeout(() => {
      if (responseCancellationPending) {
        if (currentResponseId) ignoredResponseIds.add(currentResponseId);
        if (responseRequestInFlight && dispatchedResponseRequest) {
          abandonedResponseRequestIds.add(dispatchedResponseRequest.requestId);
          expectedResponses = Math.max(0, expectedResponses - 1);
        }
        discardCurrentResponseOutput = false;
        agentTranscript = "";
        finishResponse();
        return;
      }
      if (generation !== outputGeneration || (!responseActive && !responseRequestInFlight)) return;
      const failedPhase = responsePhase;
      const timedOutRequest = activeMainResponseRequest();
      const retrySlowResponse = Boolean(timedOutRequest)
        && shouldRetrySlowResponse(
          timedOutRequest?.progressOnDelay === true,
          slowResponseRetryGenerations.has(generation),
        );
      const auxiliaryOnly = responseActive
        ? isAuxiliaryResponsePurpose(currentResponsePurpose)
        : isAuxiliaryResponsePurpose(dispatchedResponseRequest?.purpose);
      const canRetryWithoutTools = !responseHasToolCall
        && !noToolRecoveryGenerations.has(generation)
        && (currentResponseCanRetryWithoutTools
          || dispatchedResponseRequest?.retryWithoutTools === true);
      const providerResponseActive = responseActive;
      if (currentResponseId) ignoredResponseIds.add(currentResponseId);
      if (responseRequestInFlight) {
        if (dispatchedResponseRequest) {
          abandonedResponseRequestIds.add(dispatchedResponseRequest.requestId);
        }
        expectedResponses = Math.max(0, expectedResponses - 1);
      }
      for (let index = pendingResponseRequests.length - 1; index >= 0; index -= 1) {
        if (pendingResponseRequests[index]?.generation === generation) {
          pendingResponseRequests.splice(index, 1);
        }
      }
      if (providerResponseActive) {
        send(channel, currentResponseId
          ? { type: "response.cancel", response_id: currentResponseId }
          : { type: "response.cancel" });
      }
      finishResponse(retrySlowResponse);
      if (auxiliaryOnly) return;
      if (retrySlowResponse && timedOutRequest && generation === outputGeneration) {
        slowResponseRetryGenerations.add(generation);
        enqueueResponse(
          timedOutRequest.send,
          generation,
          timedOutRequest.retryWithoutTools,
          timedOutRequest.purpose,
          timedOutRequest.progressKey,
          false,
        );
        return;
      }
      if (canRetryWithoutTools && generation === outputGeneration) {
        noToolRecoveryGenerations.add(generation);
        enqueueResponse((requestId) => requestToolResultResponse(channel, requestId), generation);
        return;
      }
      deliverAgentSpeech({
        id: `response-timeout:${generation}`,
        generation,
        text: localizedResponseFailure(options.responseLanguage),
      });
      options.callbacks.onError(responseTimeoutMessage(failedPhase));
    }, timeoutMs);
  };

  const cancelActiveResponse = () => {
    const decision = realtimeCancellationDecision(responseActive, responseRequestInFlight);
    if (!decision.pending) return;
    if (currentResponseId) ignoredResponseIds.add(currentResponseId);
    discardCurrentResponseOutput = true;
    responseCancellationPending = true;
    const cancelledGeneration = responseRequestInFlight
      ? (dispatchedResponseRequest?.generation ?? currentResponseGeneration)
      : currentResponseGeneration;
    armResponseWatchdog(cancelledGeneration, RESPONSE_CANCELLATION_WATCHDOG_MS);
    if (decision.cancelProviderNow) {
      send(channel, currentResponseId
        ? { type: "response.cancel", response_id: currentResponseId }
        : { type: "response.cancel" });
    }
  };

  const invalidateOutputForCallerTurn = () => {
    cancelSlowResponseProgress();
    outputGeneration += 1;
    callerTurnGeneration = outputGeneration;
    for (let index = pendingResponseRequests.length - 1; index >= 0; index -= 1) {
      if (pendingResponseRequests[index]?.generation !== outputGeneration) {
        pendingResponseRequests.splice(index, 1);
      }
    }
    for (let index = deferredAgentSpeech.length - 1; index >= 0; index -= 1) {
      if (deferredAgentSpeech[index]?.generation !== outputGeneration) {
        deferredAgentSpeech.splice(index, 1);
      }
    }
    toolFollowupGenerations.clear();
    noToolRecoveryGenerations.clear();
    slowResponseRetryGenerations.clear();
    toolExecutions.clear();
    businessActionProgressTimers.forEach((timer) => window.clearTimeout(timer));
    businessActionProgressTimers.clear();
    activeBusinessActionProgress.clear();
    deliveredSpeechIds.clear();
    deliveredSpeechFingerprints.clear();
    if (responseActive || responseRequestInFlight) cancelActiveResponse();
    return outputGeneration;
  };

  const shouldProcessCallerTranscript = (event: Record<string, unknown>, transcript: string) => {
    const item = event.item as { id?: string } | undefined;
    const itemId = String(event.item_id ?? item?.id ?? "").trim();
    if (itemId && processedCallerItemIds.has(itemId)) return false;
    if (itemId) {
      processedCallerItemIds.add(itemId);
      if (processedCallerItemIds.size > 128) {
        const oldest = processedCallerItemIds.values().next().value as string | undefined;
        if (oldest) processedCallerItemIds.delete(oldest);
      }
    }
    const normalized = transcript.normalize("NFKC").trim().replace(/\s+/g, " ").toLocaleLowerCase();
    const now = performance.now();
    if (normalized === lastCallerTranscript && now - lastCallerTranscriptAt < 3_000) return false;
    lastCallerTranscript = normalized;
    lastCallerTranscriptAt = now;
    return true;
  };

  const emitAgentSpeech = (candidate: AgentSpeech) => {
    if (candidate.generation !== outputGeneration) return;
    const speech = sanitizeVoiceOutput(candidate.text);
    if (!speech) return;
    protocolRecoveryAttempts = 0;
    options.callbacks.onAgentSpeech?.({ ...candidate, text: speech });
    options.callbacks.onAgentTranscript(speech, false);
  };

  const deliverAgentSpeech = (candidate: AgentSpeech) => {
    if (candidate.generation !== outputGeneration) return false;
    const speech = sanitizeVoiceOutput(candidate.text);
    if (!speech) return false;
    const idKey = `${candidate.generation}:${candidate.id}`;
    const fingerprintKey = `${candidate.generation}:${speechFingerprint(speech)}`;
    if (deliveredSpeechIds.has(idKey) || deliveredSpeechFingerprints.has(fingerprintKey)) return false;
    deliveredSpeechIds.add(idKey);
    deliveredSpeechFingerprints.add(fingerprintKey);
    if (pendingCallerTurns.size > 0) {
      deferredAgentSpeech.push({ ...candidate, text: speech });
      return true;
    }
    emitAgentSpeech({ ...candidate, text: speech });
    return true;
  };

  const flushDeferredAgentSpeech = () => {
    if (pendingCallerTurns.size > 0) return;
    while (deferredAgentSpeech.length > 0) {
      const speech = deferredAgentSpeech.shift();
      if (speech) emitAgentSpeech(speech);
    }
  };

  const callerTurnKey = (event: Record<string, unknown>) =>
    String(event.item_id ?? "").trim();

  const beginPendingCallerTurn = (event: Record<string, unknown>) => {
    const key = callerTurnKey(event) || "caller-turn-" + (++callerTurnSequence);
    activeCallerTurnKey = key;
    if (!pendingCallerTurns.has(key)) pendingCallerTurns.set(key, 0);
    return key;
  };

  const finishPendingCallerTranscription = (event: Record<string, unknown>) => {
    let key = callerTurnKey(event);
    if (!key || !pendingCallerTurns.has(key)) {
      key = activeCallerTurnKey && pendingCallerTurns.has(activeCallerTurnKey)
        ? activeCallerTurnKey
        : pendingCallerTurns.keys().next().value ?? "";
    }
    if (key) {
      window.clearTimeout(pendingCallerTurns.get(key) ?? 0);
      pendingCallerTurns.delete(key);
      if (activeCallerTurnKey === key) activeCallerTurnKey = "";
    }
    flushDeferredAgentSpeech();
  };

  const armCallerTranscriptionWatchdog = (key: string, timeoutMs: number) => {
    if (!key || !pendingCallerTurns.has(key)) return;
    window.clearTimeout(pendingCallerTurns.get(key) ?? 0);
    const watchdog = window.setTimeout(() => {
      pendingCallerTurns.delete(key);
      if (activeCallerTurnKey === key) {
        activeCallerTurnKey = "";
        callerSpeechActive = false;
        callerTurnGeneration = null;
        callerSpeechGate.reset();
        options.callbacks.onCallerAudioAbandoned?.();
      }
      flushDeferredAgentSpeech();
    }, timeoutMs);
    pendingCallerTurns.set(key, watchdog);
  };

  const deliverToolFailure = (name: string, generation: number, speechId: string) => {
    const failure = name === SEMANTIC_TURN_TOOL
      ? localizedResponseFailure(options.responseLanguage)
      : name === "check_availability"
      ? localizedAvailabilityFailure(options.responseLanguage)
      : name === "book_slot"
        ? localizedBookingFailure(options.responseLanguage)
        : name === "reschedule_booking" || name === "cancel_booking"
          ? localizedBookingMutationFailure(options.responseLanguage, name)
        : localizedAvailabilityClarification(options.responseLanguage);
    deliverAgentSpeech({ id: speechId, generation, text: failure });
  };

  const settleCompletedToolResponse = (generation: number) => {
    window.clearTimeout(toolResponseSettleTimer);
    toolResponseSettleTimer = window.setTimeout(() => {
      if (generation !== outputGeneration || !responseActive || !responseHasToolCall) return;
      if (currentResponseId) ignoredResponseIds.add(currentResponseId);
      send(channel, { type: "response.cancel" });
      finishResponse();
    }, 500);
  };

  const armBusinessActionProgress = (
    executionKey: string,
    name: string,
    generation: number,
  ) => {
    if (!supportsBusinessActionProgress(name)
      || businessActionProgressTimers.has(executionKey)) return;
    const timer = window.setTimeout(() => {
      businessActionProgressTimers.delete(executionKey);
      if (generation !== outputGeneration) return;
      activeBusinessActionProgress.add(executionKey);
      enqueueResponse(
        (requestId) => requestBusinessActionProgress(channel, name, requestId),
        generation,
        false,
        "business_progress",
        executionKey,
      );
    }, BUSINESS_ACTION_PROGRESS_DELAY_MS);
    businessActionProgressTimers.set(executionKey, timer);
  };

  const clearBusinessActionProgress = (executionKey: string) => {
    const timer = businessActionProgressTimers.get(executionKey);
    if (timer !== undefined) window.clearTimeout(timer);
    businessActionProgressTimers.delete(executionKey);
    activeBusinessActionProgress.delete(executionKey);
    for (let index = pendingResponseRequests.length - 1; index >= 0; index -= 1) {
      const pending = pendingResponseRequests[index];
      if (pending?.purpose === "business_progress" && pending.progressKey === executionKey) {
        pendingResponseRequests.splice(index, 1);
      }
    }
  };

  const executeToolCall = (
    callId: string,
    name: string,
    argumentsJson: string,
    generation: number,
    publishFunctionOutput = true,
  ) => {
    cancelSlowResponseProgress(generation);
    const executionKey = `${generation}:${name}:${canonicalJson(argumentsJson)}`;
    armBusinessActionProgress(executionKey, name, generation);
    let execution = toolExecutions.get(executionKey);
    if (!execution) {
      execution = withTimeout(
        options.callbacks.executeTool(callId, name, argumentsJson),
        isBookingMutation(name) ? BOOKING_EXECUTION_TIMEOUT_MS : TOOL_EXECUTION_TIMEOUT_MS,
        "The voice tool did not complete in time.",
      );
      toolExecutions.set(executionKey, execution);
    }
    void execution
      .then((result) => {
        clearBusinessActionProgress(executionKey);
        if (publishFunctionOutput) {
          send(channel, {
            type: "conversation.item.create",
            item: { type: "function_call_output", call_id: callId, output: JSON.stringify(result) },
          });
        }
        if (generation !== outputGeneration) return;
        if (result.success === false) {
          deliverToolFailure(name, generation, `tool-failure:${callId}`);
          return;
        }
        const nextTool = authorizedNextToolRequest(result);
        if (nextTool) {
          if (nextTool.argumentsJson) {
            executeToolCall(
              `sauti-chain:${callId}:${nextTool.name}`,
              nextTool.name,
              nextTool.argumentsJson,
              generation,
              false,
            );
          } else {
            enqueueResponse(
              (requestId) => requestRequiredToolResponse(channel, nextTool.name, requestId),
              generation,
              false,
              "conversation",
              "",
              true,
            );
          }
          return;
        }
        const deterministicResponse = toolSpokenResponse(name, result);
        if (deterministicResponse) {
          let confirmationAccepted = false;
          if ((options.outputMode ?? "text") === "text") {
            const accepted = deliverAgentSpeech({
              id: `tool:${callId}`,
              generation,
              text: deterministicResponse,
            });
            if (accepted) {
              confirmationAccepted = true;
              send(channel, {
                type: "conversation.item.create",
                item: {
                  type: "message",
                  role: "assistant",
                  content: [{ type: "output_text", text: deterministicResponse }],
                },
              });
            }
          } else {
            enqueueResponse(
              (requestId) => requestExactToolResponse(channel, deterministicResponse, requestId),
              generation,
            );
          }
          const guidance = callerGuidanceInstruction(name, result);
          if (confirmationAccepted && guidance) {
            enqueueResponse(
              (requestId) => requestPostBookingGuidance(channel, guidance, requestId),
              generation,
              false,
              "post_booking_guidance",
            );
          }
          return;
        }
        if (toolRequiresBusinessAction(result)) {
          enqueueResponse(
            (requestId) => requestCallerResponse(channel, requestId),
            generation,
            false,
            "conversation",
            "",
            true,
          );
          return;
        }
        if (!toolFollowupGenerations.has(generation)) {
          toolFollowupGenerations.add(generation);
          enqueueResponse((requestId) => requestToolResultResponse(channel, requestId), generation);
        }
      })
      .catch(() => {
        clearBusinessActionProgress(executionKey);
        send(channel, {
          type: "conversation.item.create",
          item: { type: "function_call_output", call_id: callId, output: JSON.stringify({ success: false }) },
        });
        if (generation === outputGeneration) {
          deliverToolFailure(name, generation, `tool-failure:${callId}`);
          options.callbacks.onError("The requested voice action could not be completed.");
        }
      })
      .finally(() => {
        settleCompletedToolResponse(generation);
      });
  };

  const recoverProtocolOutput = (cancelResponse: boolean) => {
    agentTranscript = "";
    textCompletionSent = true;
    if (currentOutputItemId) {
      send(channel, { type: "conversation.item.delete", item_id: currentOutputItemId });
    }
    if (cancelResponse) {
      cancelActiveResponse();
      send(channel, { type: "output_audio_buffer.clear" });
    }
    if (protocolRecoveryAttempts++ === 0) {
      // Keep the complete session prompt and business facts, but prevent the
      // malformed retry from attempting another tool call.
      enqueueResponse((requestId) => requestToolResultResponse(channel, requestId));
      return;
    }
    if (protocolRecoveryAttempts === 2) {
      enqueueResponse((requestId) => requestExactToolResponse(
        channel,
        localizedResponseFailure(options.responseLanguage),
        requestId,
      ));
      return;
    }
    options.callbacks.onError("The voice provider returned an invalid internal protocol response.");
  };

  const recordAndRequestCallerResponse = (
    transcript: string,
    generation: number,
    mirrorAcceptedAudio: boolean,
  ) => {
    // Persist transcript/analytics in order, but never put that HTTP round trip,
    // prompt reconstruction, retrieval, or a session rewrite in front of speech.
    if (options.recordCallerTranscript) {
      callerTranscriptWriteChain = callerTranscriptWriteChain
        .catch(() => undefined)
        .then(() => options.recordCallerTranscript?.(transcript))
        .then(() => undefined, () => undefined);
    }
    if (generation !== outputGeneration) return;
    if (mirrorAcceptedAudio) {
      send(channel, {
        type: "conversation.item.create",
        item: realtimeTranscriptMirrorItem(transcript),
      });
    }
    enqueueResponse(
      (requestId) => requestCallerResponse(channel, requestId),
      generation,
      true,
      "conversation",
      "",
      true,
    );
  };

  const confirmRecognizedCallerSpeech = () => {
    if (callerTurnGeneration !== null) return callerTurnGeneration;
    const agentWasResponding = callerTurnAgentWasResponding || responseActive || responseRequestInFlight;
    const generation = invalidateOutputForCallerTurn();
    agentInterrupted = agentWasResponding;
    options.callbacks.onCallerSpeechStarted(agentWasResponding, generation);
    return generation;
  };

  const isOutputItem = (event: Record<string, unknown>, expectedType: string) => {
    const itemId = String(event.item_id ?? "").trim();
    if (itemId) return outputItemTypes.get(itemId) === expectedType;
    if (currentOutputItemId) return outputItemTypes.get(currentOutputItemId) === expectedType;
    return false;
  };

  const acceptToolCall = (callId: string) => {
    if (!callId || processedToolCallIds.has(callId)) return false;
    processedToolCallIds.add(callId);
    return true;
  };

  channel.onmessage = (message) => {
    let event: Record<string, unknown>;
    try { event = JSON.parse(String(message.data)) as Record<string, unknown>; } catch { return; }
    if (handleSlowResponseProgressEvent(event)) return;
    const type = String(event.type ?? "");
    const eventResponseId = responseId(event);
    if (type === "response.created") {
      const createdRequestId = realtimeResponseRequestId(event);
      if (createdRequestId && abandonedResponseRequestIds.delete(createdRequestId)) {
        if (eventResponseId) ignoredResponseIds.add(eventResponseId);
        send(channel, eventResponseId
          ? { type: "response.cancel", response_id: eventResponseId }
          : { type: "response.cancel" });
        return;
      }
      if (createdRequestId && dispatchedResponseRequest
        && createdRequestId !== dispatchedResponseRequest.requestId) {
        if (eventResponseId) ignoredResponseIds.add(eventResponseId);
        send(channel, eventResponseId
          ? { type: "response.cancel", response_id: eventResponseId }
          : { type: "response.cancel" });
        return;
      }
      if (expectedResponses <= 0) {
        if (eventResponseId) ignoredResponseIds.add(eventResponseId);
        send(channel, eventResponseId
          ? { type: "response.cancel", response_id: eventResponseId }
          : { type: "response.cancel" });
        return;
      }
      const requestedGeneration = dispatchedResponseRequest?.generation ?? outputGeneration;
      currentResponsePurpose = dispatchedResponseRequest?.purpose ?? "conversation";
      currentBusinessActionProgressKey = dispatchedResponseRequest?.progressKey ?? "";
      currentResponseCanRetryWithoutTools = dispatchedResponseRequest?.retryWithoutTools === true;
      currentResponseRequest = dispatchedResponseRequest;
      expectedResponses -= 1;
      responseRequestInFlight = false;
      dispatchedResponseRequest = null;
      responseActive = true;
      responsePhase = "generating_message";
      currentResponseId = eventResponseId;
      currentResponseGeneration = requestedGeneration;
      if (responseCancellationPending || requestedGeneration !== outputGeneration) {
        if (eventResponseId) ignoredResponseIds.add(eventResponseId);
        discardCurrentResponseOutput = true;
        responseCancellationPending = true;
        armResponseWatchdog(requestedGeneration, RESPONSE_CANCELLATION_WATCHDOG_MS);
        send(channel, eventResponseId
          ? { type: "response.cancel", response_id: eventResponseId }
          : { type: "response.cancel" });
        return;
      }
      armResponseWatchdog(
        requestedGeneration,
        noToolRecoveryGenerations.has(requestedGeneration)
          ? RECOVERY_RESPONSE_WATCHDOG_MS
          : PRIMARY_RESPONSE_WATCHDOG_MS,
      );
      discardCurrentResponseOutput = false;
      currentOutputItemId = "";
      outputItemTypes.clear();
      responseHasToolCall = false;
      agentTranscript = "";
      textCompletionSent = false;
    }
    const responseTerminal = type === "response.done" || type === "response.cancelled";
    const ignoredResponse = Boolean(eventResponseId && ignoredResponseIds.has(eventResponseId));
    if (ignoredResponse && responseTerminal) {
      if (!eventResponseId || currentResponseId === eventResponseId
        || (responseCancellationPending && !responseActive)) {
        currentResponseId = "";
        discardCurrentResponseOutput = false;
        agentTranscript = "";
        finishResponse();
      }
      return;
    }
    if (ignoredResponse || (discardCurrentResponseOutput && type.startsWith("response."))) return;
    if (type === "response.output_item.added" || type === "response.output_item.created") {
      const item = event.item as { id?: string; type?: string } | undefined;
      const itemId = String(item?.id ?? "").trim();
      const itemType = String(item?.type ?? "").trim();
      if (itemId) outputItemTypes.set(itemId, itemType);
      if (itemType === "message") currentOutputItemId = itemId;
      if (itemType === "function_call") {
        responseHasToolCall = true;
        currentResponseCanRetryWithoutTools = false;
        responsePhase = "generating_tool";
        armResponseWatchdog(currentResponseGeneration, TOOL_EXECUTION_TIMEOUT_MS);
      }
    }
    if (type === "conversation.item.input_audio_transcription.completed") {
      const transcript = String(event.transcript ?? "").trim();
      const meaningfulTranscript = isMeaningfulCallerTranscript(transcript);
      if (meaningfulTranscript && shouldProcessCallerTranscript(event, transcript)) {
        const speechStartGeneration = callerTurnGeneration;
        callerSpeechGate.confirmFinal(transcript);
        const generation = speechStartGeneration ?? confirmRecognizedCallerSpeech();
        protocolRecoveryAttempts = 0;
        options.callbacks.onCallerTranscript(transcript, generation);
        // The session disables provider-managed response creation. Only a
        // usable final transcript is allowed to advance the conversation.
        recordAndRequestCallerResponse(transcript, generation, true);
      }
      if (!meaningfulTranscript) options.callbacks.onCallerAudioAbandoned?.();
      callerTurnGeneration = null;
      callerSpeechActive = false;
      callerTurnAgentWasResponding = false;
      callerSpeechGate.reset();
      finishPendingCallerTranscription(event);
    }
    if (type === "conversation.item.input_audio_transcription.failed") {
      callerTurnGeneration = null;
      callerSpeechActive = false;
      callerTurnAgentWasResponding = false;
      callerSpeechGate.reset();
      options.callbacks.onCallerAudioAbandoned?.();
      finishPendingCallerTranscription(event);
    }
    if (type === "conversation.item.input_audio_transcription.delta") {
      const delta = String(event.delta ?? "");
      if (callerSpeechGate.addTranscriptDelta(delta)) confirmRecognizedCallerSpeech();
    }
    if (type === "response.output_audio_transcript.delta"
      || type === "response.output_audio_transcript.done") {
      if (!textCompletionSent) recoverProtocolOutput(true);
      return;
    }
    if (type === "response.output_text.delta") {
      if (!isOutputItem(event, "message")) return;
      const delta = String(event.delta ?? "");
      if (delta) {
        responseActive = true;
        textCompletionSent = false;
        agentTranscript += delta;
      }
    }
    if (type === "response.output_text.done") {
      const transcript = String(event.text ?? agentTranscript).trim();
      if (!isOutputItem(event, "message")) {
        agentTranscript = "";
        return;
      }
      // Wait for response.done before validating or releasing the message. A
      // later item may establish that this is a tool turn, making all message
      // text in the same response silent.
      agentTranscript = transcript;
    }
    if (type === "response.done") {
      const isAuxiliaryResponse = isAuxiliaryResponsePurpose(currentResponsePurpose);
      const completed = completedRealtimeResponse(event);
      for (const toolCall of completedRealtimeToolCalls(event)) {
        responseHasToolCall = true;
        if (acceptToolCall(toolCall.callId)) {
          executeToolCall(
            toolCall.callId,
            toolCall.name,
            toolCall.argumentsJson,
            currentResponseGeneration,
          );
        }
      }
      if (completed.hasToolCall) responseHasToolCall = true;
      if (completed.hasPhases) {
        // Realtime 2 can return commentary and final_answer items in one
        // response. Sauti speaks only the final user-facing phase; commentary
        // and tool preambles remain internal to the voice workflow.
        agentTranscript = completed.finalAnswerText;
        textCompletionSent = !completed.finalAnswerText.trim();
      }
      if (!isAuxiliaryResponse
        && (completed.status === "failed" || completed.status === "incomplete")
        && !completed.hasToolCall && !completed.finalAnswerText.trim()) {
        deliverAgentSpeech({
          id: eventResponseId || currentResponseId || `response-failed:${currentResponseGeneration}`,
          generation: currentResponseGeneration,
          text: localizedResponseFailure(options.responseLanguage),
        });
        options.callbacks.onError("The voice provider could not complete its response.");
        textCompletionSent = true;
      }
      if (completed.status === "completed") {
        noToolRecoveryGenerations.delete(currentResponseGeneration);
        slowResponseRetryGenerations.delete(currentResponseGeneration);
      }
    }
    // A completed response is a defensive flush for transports that omit the
    // more specific output_text.done event.
    if (type === "response.done" && currentResponsePurpose === "business_progress") {
      const transcript = agentTranscript.trim();
      const speech = sanitizeVoiceOutput(transcript);
      if (activeBusinessActionProgress.has(currentBusinessActionProgressKey) && speech) {
        deliverAgentSpeech({
          id: eventResponseId || currentResponseId || `tool-progress:${currentBusinessActionProgressKey}`,
          generation: currentResponseGeneration,
          text: speech,
        });
      } else if (currentOutputItemId) {
        send(channel, { type: "conversation.item.delete", item_id: currentOutputItemId });
      }
      agentTranscript = "";
      agentInterrupted = false;
      textCompletionSent = true;
    } else if (type === "response.done" && currentResponsePurpose === "post_booking_guidance") {
      const speech = sanitizeVoiceOutput(agentTranscript.trim());
      if (speech) {
        deliverAgentSpeech({
          id: eventResponseId || currentResponseId || `booking-guidance:${currentResponseGeneration}`,
          generation: currentResponseGeneration,
          text: speech,
        });
      }
      agentTranscript = "";
      agentInterrupted = false;
      textCompletionSent = true;
    } else if (type === "response.done" && responseHasToolCall) {
      agentTranscript = "";
      agentInterrupted = false;
      textCompletionSent = true;
    } else if (type === "response.done" && (options.outputMode ?? "text") === "text"
      && !textCompletionSent && agentTranscript.trim()) {
      const interrupted = agentInterrupted;
      const transcript = agentTranscript.trim();
      const speech = sanitizeVoiceOutput(transcript);
      if (!speech) {
        agentInterrupted = false;
        recoverProtocolOutput(false);
        return;
      }
      if (!interrupted) {
        deliverAgentSpeech({
          id: eventResponseId || currentResponseId || `response-${++localSpeechSequence}`,
          generation: currentResponseGeneration,
          text: speech,
        });
      }
      agentTranscript = "";
      agentInterrupted = false;
      textCompletionSent = true;
    }
    if (type === "input_audio_buffer.speech_started") {
      const pendingTurnKey = beginPendingCallerTurn(event);
      callerSpeechActive = true;
      callerTurnAgentWasResponding = responseActive || responseRequestInFlight;
      callerSpeechGate.begin();
      armCallerTranscriptionWatchdog(pendingTurnKey, 15_000);
      const debounceMs = Math.max(180, options.bargeInDebounceMs ?? 0);
      window.clearTimeout(bargeInTimer);
      if (debounceMs > 0) {
        bargeInTimer = window.setTimeout(() => {
          if (callerSpeechActive && callerSpeechGate.markDebounceElapsed()) {
            confirmRecognizedCallerSpeech();
          }
        }, debounceMs);
      } else if (callerSpeechGate.markDebounceElapsed()) {
        confirmRecognizedCallerSpeech();
      }
    }
    if (type === "input_audio_buffer.speech_stopped") {
      callerSpeechActive = false;
      window.clearTimeout(bargeInTimer);
      const pendingTurnKey = callerTurnKey(event) || activeCallerTurnKey;
      armCallerTranscriptionWatchdog(pendingTurnKey, 4_000);
    }
    if (type === "response.cancelled") {
      if (!eventResponseId || currentResponseId === eventResponseId || responseCancellationPending) {
        agentTranscript = "";
        agentInterrupted = false;
        textCompletionSent = true;
        finishResponse();
      }
      return;
    }
    if (type === "response.done") {
      if (!eventResponseId || currentResponseId === eventResponseId) finishResponse();
    }
    if (type === "response.function_call_arguments.done") {
      const completedToolCalls = completedRealtimeToolCalls(event);
      if (completedToolCalls.length > 0) {
        responseHasToolCall = true;
        currentResponseCanRetryWithoutTools = false;
        window.clearTimeout(responseWatchdog);
        responseWatchdog = 0;
      }
      for (const toolCall of completedToolCalls) {
        if (acceptToolCall(toolCall.callId)) {
          executeToolCall(
            toolCall.callId,
            toolCall.name,
            toolCall.argumentsJson,
            currentResponseGeneration,
          );
        }
      }
    }
    if (type === "response.output_item.done") {
      const completedToolCalls = completedRealtimeToolCalls(event);
      if (completedToolCalls.length > 0) {
        responseHasToolCall = true;
        currentResponseCanRetryWithoutTools = false;
        window.clearTimeout(responseWatchdog);
        responseWatchdog = 0;
      }
      for (const toolCall of completedToolCalls) {
        if (acceptToolCall(toolCall.callId)) {
          executeToolCall(
            toolCall.callId,
            toolCall.name,
            toolCall.argumentsJson,
            currentResponseGeneration,
          );
        }
      }
    }
    if (type === "error") {
      const error = event.error as { message?: string } | undefined;
      const message = error?.message ?? "The realtime voice session encountered an error.";
      if (/conversation already has an active response|active response in progress/i.test(message)) {
        if (dispatchedResponseRequest) pendingResponseRequests.unshift(dispatchedResponseRequest);
        dispatchedResponseRequest = null;
        responseRequestInFlight = false;
        expectedResponses = Math.max(0, expectedResponses - 1);
        responseCancellationPending = true;
        armResponseWatchdog(currentResponseGeneration, RESPONSE_CANCELLATION_WATCHDOG_MS);
        send(channel, { type: "response.cancel" });
      } else if (/no active response|response.*not active/i.test(message)) {
        if (responseCancellationPending && !responseRequestInFlight) finishResponse();
      } else {
        const failedAuxiliaryResponse = responseActive
          ? isAuxiliaryResponsePurpose(currentResponsePurpose)
          : isAuxiliaryResponsePurpose(dispatchedResponseRequest?.purpose);
        const failedGeneration = dispatchedResponseRequest?.generation ?? outputGeneration;
        if (responseRequestInFlight) expectedResponses = Math.max(0, expectedResponses - 1);
        finishResponse();
        if (failedAuxiliaryResponse) return;
        deliverAgentSpeech({
          id: `response-error:${failedGeneration}`,
          generation: failedGeneration,
          text: localizedResponseFailure(options.responseLanguage),
        });
        options.callbacks.onError(message);
      }
    }
  };

  const opened = new Promise<void>((resolve, reject) => {
    const timeout = window.setTimeout(() => reject(new Error("The OpenAI Realtime channel timed out.")), 10_000);
    channel.onopen = () => {
      window.clearTimeout(timeout);
      options.callbacks.onConnected();
      if (options.greeting.trim()) {
        enqueueResponse(
          (requestId) => requestGreeting(
            channel,
            options.greeting,
            options.outputMode ?? "text",
            requestId,
          ),
        );
      }
      resolve();
    };
    channel.onerror = () => {
      window.clearTimeout(timeout);
      reject(new Error("The OpenAI Realtime data channel could not be opened."));
    };
  });

  const offer = await peer.createOffer();
  await peer.setLocalDescription(offer);
  const answer = await options.connectSdp(offer.sdp ?? "");
  await peer.setRemoteDescription({ type: "answer", sdp: answer });
  await opened;

  return {
    peer,
    channel,
    sendUserText: (text) => {
      const generation = invalidateOutputForCallerTurn();
      callerTurnGeneration = null;
      send(channel, {
        type: "conversation.item.create",
        item: { type: "message", role: "user", content: [{ type: "input_text", text }] },
      });
      recordAndRequestCallerResponse(text, generation, false);
    },
    speakGreeting: (text) => {
      enqueueResponse(
        (requestId) => requestGreeting(channel, text, options.outputMode ?? "text", requestId),
      );
    },
    cancelResponse: cancelActiveResponse,
    close: () => {
      window.clearTimeout(bargeInTimer);
      window.clearTimeout(responseWatchdog);
      window.clearTimeout(toolResponseSettleTimer);
      cancelSlowResponseProgress();
      slowResponseProgress.clear();
      slowResponseProgressResponseIds.clear();
      businessActionProgressTimers.forEach((timer) => window.clearTimeout(timer));
      businessActionProgressTimers.clear();
      activeBusinessActionProgress.clear();
      pendingCallerTurns.forEach((watchdog) => window.clearTimeout(watchdog));
      pendingCallerTurns.clear();
      pendingResponseRequests.length = 0;
      channel.close();
      peer.close();
    },
  };
}

function isMeaningfulCallerTranscript(transcript: string) {
  const normalized = transcript
    .normalize("NFKC")
    .trim()
    .replace(/^[\s\p{P}]+|[\s\p{P}]+$/gu, "")
    .toLocaleLowerCase();
  if (!/[\p{L}\p{N}]/u.test(normalized)) return false;
  const caption = normalized.replace(/^[\[(<{]+|[\])> }]+$/gu, "").trim();
  return !new Set([
    "silence", "no speech", "inaudible", "unintelligible",
    "background noise", "music", "blank audio", "audio unclear",
  ]).has(caption);
}

function requestGreeting(
  channel: RTCDataChannel,
  greeting: string,
  outputMode: "audio" | "text",
  requestId: string,
) {
  const text = greeting.trim();
  if (!text) return;
  send(channel, {
    type: "response.create",
    response: {
      instructions: `Say exactly this greeting, with no additions: ${text}`,
      output_modalities: [outputMode],
      metadata: responseRequestMetadata(requestId),
    },
  });
}

function requestCallerResponse(channel: RTCDataChannel, requestId: string) {
  // The full, transcript-aware agent prompt was applied with session.update
  // immediately before this request. Response-level instructions override that
  // session prompt in the Realtime API, which would discard personalized facts
  // such as service prices and the one-field-at-a-time intake rules.
  send(channel, {
    type: "response.create",
    response: { metadata: responseRequestMetadata(requestId) },
  });
}

function requestToolResultResponse(channel: RTCDataChannel, requestId: string) {
  send(channel, {
    type: "response.create",
    response: {
      tool_choice: "none",
      metadata: responseRequestMetadata(requestId),
    },
  });
}

function requestRequiredToolResponse(channel: RTCDataChannel, toolName: string, requestId: string) {
  send(channel, {
    type: "response.create",
    response: {
      tool_choice: { type: "function", name: toolName },
      metadata: responseRequestMetadata(requestId),
    },
  });
}

function requestBusinessActionProgress(
  channel: RTCDataChannel,
  toolName: string,
  requestId: string,
) {
  send(channel, {
    type: "response.create",
    response: {
      instructions: businessActionProgressInstruction(toolName),
      tool_choice: "none",
      output_modalities: ["text"],
      metadata: responseRequestMetadata(requestId),
    },
  });
}

function requestPostBookingGuidance(
  channel: RTCDataChannel,
  instruction: string,
  requestId: string,
) {
  send(channel, {
    type: "response.create",
    response: {
      instructions: instruction,
      tool_choice: "none",
      output_modalities: ["text"],
      metadata: responseRequestMetadata(requestId),
    },
  });
}

function requestExactToolResponse(channel: RTCDataChannel, response: string, requestId: string) {
  send(channel, {
    type: "response.create",
    response: {
      instructions: `Say exactly this sentence with no additions or omissions: ${response}`,
      tool_choice: "none",
      metadata: responseRequestMetadata(requestId),
    },
  });
}

function responseRequestMetadata(requestId: string) {
  return { [SAUTI_REALTIME_REQUEST_ID]: requestId };
}

function toolSpokenResponse(name: string, result: Record<string, unknown>) {
  if (result.success === false) return "";
  const payload = result.result;
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return "";
  const response = (payload as Record<string, unknown>).spokenResponse;
  return typeof response === "string" ? response.trim() : "";
}

function toolRequiresBusinessAction(result: Record<string, unknown>) {
  const payload = result.result;
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return false;
  return (payload as Record<string, unknown>).nextAction === "use_business_tool";
}

function canonicalJson(value: string) {
  try {
    return stableJson(JSON.parse(value) as unknown);
  } catch {
    return value.trim();
  }
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(",")}]`;
  if (value && typeof value === "object") {
    const record = value as Record<string, unknown>;
    return `{${Object.keys(record).sort().map((key) => `${JSON.stringify(key)}:${stableJson(record[key])}`).join(",")}}`;
  }
  return JSON.stringify(value) ?? "null";
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number, message: string): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timeout = globalThis.setTimeout(() => reject(new Error(message)), timeoutMs);
    promise.then(
      (value) => {
        globalThis.clearTimeout(timeout);
        resolve(value);
      },
      (error) => {
        globalThis.clearTimeout(timeout);
        reject(error);
      },
    );
  });
}

function responseTimeoutMessage(phase: ResponsePhase) {
  switch (phase) {
    case "awaiting_response":
      return "The voice provider did not start its response in time.";
    case "generating_tool":
      return "The voice provider did not finish preparing the requested action in time.";
    case "generating_message":
      return "The voice provider did not finish its conversational response in time.";
    default:
      return "The voice provider did not complete its response in time.";
  }
}

function speechFingerprint(value: string) {
  return value.normalize("NFKC").trim().replace(/\s+/gu, " ").toLocaleLowerCase();
}

function localizedBookingFailure(language?: string) {
  switch (language?.toLocaleLowerCase()) {
    case "fr": return "Je n’ai pas pu enregistrer le rendez-vous dans le calendrier. Il n’est pas réservé. Souhaitez-vous réessayer ?";
    case "ar": return "لم أتمكن من حفظ الموعد في التقويم، لذلك لم يتم حجزه. هل تريد المحاولة مرة أخرى؟";
    case "sw": return "Sikuweza kuhifadhi miadi kwenye kalenda, kwa hiyo haijawekwa. Ungependa kujaribu tena?";
    default: return "I couldn’t complete the booking, so it is not saved. I still have the details. Would you like me to try once more?";
  }
}

function localizedBookingMutationFailure(language: string | undefined, toolName: string) {
  const cancellation = toolName === "cancel_booking";
  if (language?.toLocaleLowerCase() === "ar") {
    return cancellation
      ? "\u062A\u0639\u0630\u0631 \u0625\u0644\u063A\u0627\u0621 \u0627\u0644\u0645\u0648\u0639\u062F. \u0644\u0645 \u064A\u062A\u063A\u064A\u0631 \u0627\u0644\u062D\u062C\u0632."
      : "\u062A\u0639\u0630\u0631 \u062A\u063A\u064A\u064A\u0631 \u0645\u0648\u0639\u062F \u0627\u0644\u062D\u062C\u0632. \u0644\u0645 \u064A\u062A\u063A\u064A\u0631 \u0627\u0644\u062D\u062C\u0632.";
  }
  switch (language?.toLocaleLowerCase()) {
    case "fr": return cancellation
      ? "Je n'ai pas pu annuler le rendez-vous. La reservation reste inchangee."
      : "Je n'ai pas pu deplacer le rendez-vous. La reservation reste inchangee.";
    case "ar": return cancellation
      ? "Ù„Ù… Ø£ØªÙ…ÙƒÙ† Ù…Ù† Ø¥Ù„ØºØ§Ø¡ Ø§Ù„Ù…ÙˆØ¹Ø¯. Ù…Ø§ Ø²Ø§Ù„ Ø§Ù„Ø­Ø¬Ø² Ø¯ÙˆÙ† ØªØºÙŠÙŠØ±."
      : "Ù„Ù… Ø£ØªÙ…ÙƒÙ† Ù…Ù† ØªØºÙŠÙŠØ± Ù…ÙˆØ¹Ø¯ Ø§Ù„Ø­Ø¬Ø². Ù…Ø§ Ø²Ø§Ù„ Ø§Ù„Ø­Ø¬Ø² Ø¯ÙˆÙ† ØªØºÙŠÙŠØ±.";
    case "sw": return cancellation
      ? "Sikuweza kughairi miadi. Nafasi hiyo haijabadilishwa."
      : "Sikuweza kubadilisha muda wa miadi. Nafasi hiyo haijabadilishwa.";
    default: return cancellation
      ? "I couldn't cancel the appointment, so the booking remains unchanged."
      : "I couldn't reschedule the appointment, so the booking remains unchanged.";
  }
}

function supportsBusinessActionProgress(toolName: string) {
  return toolName === "check_availability" || isBookingMutation(toolName);
}

function isBookingMutation(toolName: string) {
  return toolName === "book_slot"
    || toolName === "reschedule_booking"
    || toolName === "cancel_booking";
}

function isAuxiliaryResponsePurpose(purpose?: PendingResponseRequest["purpose"]) {
  return purpose === "business_progress" || purpose === "post_booking_guidance";
}

function localizedResponseFailure(language?: string) {
  switch (language?.toLocaleLowerCase()) {
    case "fr": return "Desole, je n'ai pas pu terminer ma reponse. Pouvez-vous repeter votre question, s'il vous plait ?";
    case "ar": return "عذرا، لم أتمكن من إكمال إجابتي. هل يمكنك تكرار سؤالك من فضلك؟";
    case "sw": return "Samahani, sikuweza kukamilisha jibu langu. Tafadhali rudia swali lako.";
    default: return "Sorry, I couldn't complete that answer. Could you repeat your question, please?";
  }
}

function completedRealtimeResponse(event: Record<string, unknown>) {
  const response = event.response as { output?: unknown; status?: unknown } | undefined;
  const output = Array.isArray(response?.output) ? response.output : [];
  let hasPhases = false;
  let hasToolCall = false;
  const finalParts: string[] = [];

  for (const rawItem of output) {
    if (!rawItem || typeof rawItem !== "object") continue;
    const item = rawItem as { type?: unknown; phase?: unknown; content?: unknown };
    const itemType = String(item.type ?? "").trim();
    if (itemType === "function_call") hasToolCall = true;
    const phase = String(item.phase ?? "").trim().toLocaleLowerCase().replace(/-/gu, "_");
    if (!phase) continue;
    hasPhases = true;
    if (phase !== "final_answer" || (itemType && itemType !== "message")) continue;

    const content = Array.isArray(item.content) ? item.content : [];
    for (const rawContent of content) {
      if (!rawContent || typeof rawContent !== "object") continue;
      const part = rawContent as { type?: unknown; text?: unknown; transcript?: unknown };
      const contentType = String(part.type ?? "");
      const text = contentType === "output_audio"
        ? String(part.transcript ?? "")
        : contentType === "output_text"
          ? String(part.text ?? "")
          : "";
      if (text.trim()) finalParts.push(text.trim());
    }
  }

  return {
    hasPhases,
    hasToolCall,
    finalAnswerText: finalParts.join("\n").trim(),
    status: String(response?.status ?? "").trim().toLocaleLowerCase(),
  };
}

const spokenRoleLabels = new Set([
  "assistant", "agent", "ai", "ai assistant", "virtual assistant", "answer", "response", "final",
]);
const privateRoleLabels = new Set([
  "analysis", "reasoning", "commentary", "tool", "tools", "function", "functions",
  "system", "developer", "user", "caller", "recipient",
]);
const roleLabelPattern = /^(?:\*\*|__)?([\p{L}][\p{L}\p{N}_ ]{0,39})(?:\*\*|__)?(?:\s*:\s*|\s+[\-\u2013\u2014]\s+)/iu;
const privateRoleLinePattern = /(?:^|\r?\n)\s*(?:\*\*|__)?(?:analysis|reasoning|commentary|tool|tools|function|functions|system|developer|user|caller|recipient)(?:\*\*|__)?\s*(?::|[\-\u2013\u2014])/imu;
const privateSectionHeadingPattern = /(?:^|\r?\n)\s*(?:#{1,6}\s*)?(?:\*\*|__)?(?:analysis|reasoning|commentary|tool(?:\s+calls?|\s+results?)?|function(?:\s+calls?|\s+results?)?|system|developer|user|caller|recipient)(?:\*\*|__)?\s*(?:\r?\n\s*(?:-{3,}|={3,})\s*)?(?=\r?\n|$)/imu;
const spokenSectionHeadingPattern = /^\s*(?:#{1,6}\s*)?(?:\*\*|__)?(?:assistant|agent|ai|ai assistant|virtual assistant|answer|final answer|response|final response|assistant answer|assistant response|final)(?:\*\*|__)?\s*(?:\r?\n\s*(?:-{3,}|={3,})\s*)?(?:\r?\n|$)/iu;
const markupOnlyPattern = /^\s*(?:[-=_*#]{3,}\s*)+$/u;
const routedChannelPattern = /(?:^|\r?\n)\s*[\p{L}_][\p{L}\p{N}_-]{0,39}\s+(?:to|recipient)\s*=/imu;
const privateRoutingPattern = /\b(?:analysis|reasoning|commentary|tool|tools|function|functions|assistant|final)\s+(?:to|recipient)\s*=/iu;
const namespaceCallPattern = /\b(?:functions|tools)\.[A-Za-z_][A-Za-z0-9_.-]*(?:\s|\(|$)/iu;
const structuredLinePattern = /(?:^|\r?\n)\s*(?:\{|\[|```|<\||<tool_call|<function(?:=|\s))/mu;
const inlineStructuredPattern = /(?:\{|\[)\s*["']?[\p{L}_][\p{L}\p{N}_-]{0,63}["']?\s*:/u;

function sanitizeVoiceOutput(text: string, depth = 0): string {
  if (!text?.trim() || depth > 3) return "";
  const candidate = text.trimStart();
  const normalized = candidate.toLocaleLowerCase();

  if (structuredLinePattern.test(candidate)
    || inlineStructuredPattern.test(candidate)
    || privateRoleLinePattern.test(candidate)
    || privateSectionHeadingPattern.test(candidate)
    || privateRoutingPattern.test(candidate)
    || routedChannelPattern.test(candidate)
    || namespaceCallPattern.test(candidate)
    || markupOnlyPattern.test(candidate)
    || normalized.startsWith("to=")
    || normalized.startsWith("recipient=")) {
    return "";
  }

  const section = candidate.match(spokenSectionHeadingPattern);
  if (section?.[0]) {
    return sanitizeVoiceOutput(candidate.slice(section[0].length).trimStart(), depth + 1);
  }

  const role = candidate.match(roleLabelPattern);
  if (role?.[0] && role[1]) {
    const label = role[1].trim().replace(/\s+/gu, " ").toLocaleLowerCase();
    if (privateRoleLabels.has(label)) return "";
    if (spokenRoleLabels.has(label)) {
      return sanitizeVoiceOutput(candidate.slice(role[0].length).trimStart(), depth + 1);
    }
  }
  return candidate.trim();
}

function localizedAvailabilityClarification(language?: string) {
  switch (language?.toLocaleLowerCase()) {
    case "fr": return "Je n’ai pas pu vérifier ce créneau. Pouvez-vous répéter la date et l’heure, s’il vous plaît ?";
    case "ar": return "لم أتمكن من التحقق من هذا الموعد. هل يمكنك تكرار التاريخ والوقت من فضلك؟";
    case "sw": return "Sikuweza kuthibitisha muda huo. Tafadhali rudia tarehe na saa.";
    default: return "I couldn’t verify that time. Could you repeat the date and time, please?";
  }
}

function localizedAvailabilityFailure(language?: string) {
  switch (language?.toLocaleLowerCase()) {
    case "fr": return "Je ne peux pas confirmer la disponibilité pour le moment. Le créneau demandé n’est pas réservé.";
    case "ar": return "تعذر تأكيد التقويم المباشر الآن. الموعد المطلوب غير محجوز.";
    case "sw": return "Siwezi kuthibitisha kalenda kwa sasa. Muda ulioomba haujawekwa nafasi.";
    default: return "I cannot confirm the live calendar right now. Your requested time is not booked.";
  }
}

function send(channel: RTCDataChannel, event: Record<string, unknown>) {
  if (channel.readyState === "open") channel.send(JSON.stringify(event));
}

export type OpenAiRealtimeCallbacks = {
  onConnected: () => void;
  onCallerTranscript: (text: string) => void;
  onAgentTranscript: (text: string, interrupted: boolean) => void;
  onSpeaking: (speaking: boolean) => void;
  onCallerSpeechStarted: (agentWasResponding: boolean) => void;
  onAgentTextDelta?: (delta: string) => void;
  onAgentTextComplete?: (interrupted: boolean) => void;
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

export async function connectOpenAiRealtime(options: {
  microphone: MediaStream;
  greeting: string;
  connectSdp: (offer: string) => Promise<string>;
  callbacks: OpenAiRealtimeCallbacks;
  playbackContext?: AudioContext | null;
  recordingDestination?: MediaStreamAudioDestinationNode | null;
  outputMode?: "audio" | "text";
  bargeInDebounceMs?: number;
  prepareCallerResponse?: (text: string) => Promise<string | null>;
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
  let pendingCallerTranscriptions = 0;
  let callerSpeechActive = false;
  let bargeInTimer = 0;
  let currentOutputItemId = "";
  const outputItemTypes = new Map<string, string>();
  let responseHasToolCall = false;
  let protocolRecoveryAttempts = 0;
  let expectedResponses = 0;
  let responseRequestInFlight = false;
  let responseCancellationPending = false;
  let currentResponseId = "";
  let discardCurrentResponseOutput = false;
  const ignoredResponseIds = new Set<string>();
  const processedCallerItemIds = new Set<string>();
  const processedToolCallIds = new Set<string>();
  let lastCallerTranscript = "";
  let lastCallerTranscriptAt = 0;
  const deferredAgentTranscripts: Array<{ text: string; interrupted: boolean }> = [];
  const pendingResponseRequests: Array<() => void> = [];
  let dispatchedResponseRequest: (() => void) | null = null;
  let callerPreparationChain = Promise.resolve();

  const responseId = (event: Record<string, unknown>) => {
    const response = event.response as { id?: string } | undefined;
    return String(event.response_id ?? response?.id ?? "").trim();
  };

  const drainResponseQueue = () => {
    if (responseActive || responseRequestInFlight || responseCancellationPending) return;
    const request = pendingResponseRequests.shift();
    if (!request || channel.readyState !== "open") return;
    responseRequestInFlight = true;
    dispatchedResponseRequest = request;
    expectedResponses += 1;
    request();
  };

  const enqueueResponse = (request: () => void) => {
    pendingResponseRequests.push(request);
    drainResponseQueue();
  };

  const finishResponse = () => {
    responseActive = false;
    responseRequestInFlight = false;
    responseCancellationPending = false;
    currentResponseId = "";
    window.setTimeout(drainResponseQueue, 0);
  };

  const cancelActiveResponse = () => {
    if (currentResponseId) ignoredResponseIds.add(currentResponseId);
    discardCurrentResponseOutput = true;
    responseCancellationPending = responseActive || responseRequestInFlight;
    send(channel, { type: "response.cancel" });
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

  const deliverAgentTranscript = (text: string, interrupted: boolean) => {
    const speech = sanitizeVoiceOutput(text);
    if (!speech) return;
    protocolRecoveryAttempts = 0;
    if (pendingCallerTranscriptions > 0) {
      deferredAgentTranscripts.push({ text: speech, interrupted });
      return;
    }
    options.callbacks.onAgentTranscript(speech, interrupted);
  };

  const flushDeferredAgentTranscripts = () => {
    if (pendingCallerTranscriptions > 0) return;
    while (deferredAgentTranscripts.length > 0) {
      const transcript = deferredAgentTranscripts.shift();
      if (transcript) options.callbacks.onAgentTranscript(transcript.text, transcript.interrupted);
    }
  };

  const deliverToolFailure = (name: string) => {
    const failure = name === "check_availability"
      ? localizedAvailabilityFailure(options.responseLanguage)
      : name === "book_slot"
        ? localizedBookingFailure(options.responseLanguage)
        : localizedAvailabilityClarification(options.responseLanguage);
    options.callbacks.onAgentTextDelta?.(failure);
    options.callbacks.onAgentTextComplete?.(false);
    deliverAgentTranscript(failure, false);
  };

  const executeToolCall = (callId: string, name: string, argumentsJson: string) => {
    void options.callbacks.executeTool(callId, name, argumentsJson)
      .then((result) => {
        send(channel, {
          type: "conversation.item.create",
          item: { type: "function_call_output", call_id: callId, output: JSON.stringify(result) },
        });
        if (result.success === false) {
          deliverToolFailure(name);
          return;
        }
        const deterministicResponse = toolSpokenResponse(name, result);
        if (deterministicResponse) {
          if ((options.outputMode ?? "text") === "text") {
            send(channel, {
              type: "conversation.item.create",
              item: {
                type: "message",
                role: "assistant",
                content: [{ type: "output_text", text: deterministicResponse }],
              },
            });
            options.callbacks.onAgentTextDelta?.(deterministicResponse);
            options.callbacks.onAgentTextComplete?.(false);
            deliverAgentTranscript(deterministicResponse, false);
          } else {
            enqueueResponse(() => requestExactToolResponse(channel, deterministicResponse));
          }
          return;
        }
        enqueueResponse(() => requestToolResultResponse(channel));
      })
      .catch(() => {
        send(channel, {
          type: "conversation.item.create",
          item: { type: "function_call_output", call_id: callId, output: JSON.stringify({ success: false }) },
        });
        deliverToolFailure(name);
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
      enqueueResponse(() => requestToolResultResponse(channel));
      return;
    }
    if (protocolRecoveryAttempts === 2) {
      enqueueResponse(() => requestExactToolResponse(
        channel,
        localizedResponseFailure(options.responseLanguage),
      ));
      return;
    }
    options.callbacks.onError("The voice provider returned an invalid internal protocol response.");
  };

  const prepareAndRequestCallerResponse = (transcript: string) => {
    callerPreparationChain = callerPreparationChain.then(async () => {
      try {
        const instructions = await options.prepareCallerResponse?.(transcript);
        if (instructions?.trim()) {
          send(channel, { type: "session.update", session: { type: "realtime", instructions } });
        }
      } catch {
        // The active Realtime session still has its original safe instructions.
      }
      enqueueResponse(() => requestCallerResponse(channel));
    });
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
    const type = String(event.type ?? "");
    const eventResponseId = responseId(event);
    if (type === "response.created") {
      if (expectedResponses <= 0) {
        if (eventResponseId) ignoredResponseIds.add(eventResponseId);
        discardCurrentResponseOutput = true;
        send(channel, { type: "response.cancel" });
        responseActive = false;
        return;
      }
      expectedResponses -= 1;
      responseRequestInFlight = false;
      dispatchedResponseRequest = null;
      responseActive = true;
      currentResponseId = eventResponseId;
      if (responseCancellationPending) {
        if (eventResponseId) ignoredResponseIds.add(eventResponseId);
        discardCurrentResponseOutput = true;
        send(channel, { type: "response.cancel" });
        return;
      }
      discardCurrentResponseOutput = false;
      currentOutputItemId = "";
      outputItemTypes.clear();
      responseHasToolCall = false;
      agentTranscript = "";
      textCompletionSent = false;
    }
    const ignoredResponse = Boolean(eventResponseId && ignoredResponseIds.has(eventResponseId));
    if (ignoredResponse && type === "response.done") {
      ignoredResponseIds.delete(eventResponseId);
      if (currentResponseId === eventResponseId) {
        currentResponseId = "";
        discardCurrentResponseOutput = false;
        agentTranscript = "";
      }
      finishResponse();
      return;
    }
    if (ignoredResponse || (discardCurrentResponseOutput && type.startsWith("response."))) return;
    if (type === "response.output_item.added" || type === "response.output_item.created") {
      const item = event.item as { id?: string; type?: string } | undefined;
      const itemId = String(item?.id ?? "").trim();
      const itemType = String(item?.type ?? "").trim();
      if (itemId) outputItemTypes.set(itemId, itemType);
      if (itemType === "message") currentOutputItemId = itemId;
      if (itemType === "function_call") responseHasToolCall = true;
    }
    if (type === "conversation.item.input_audio_transcription.completed") {
      const transcript = String(event.transcript ?? "").trim();
      if (isMeaningfulCallerTranscript(transcript) && shouldProcessCallerTranscript(event, transcript)) {
        protocolRecoveryAttempts = 0;
        options.callbacks.onCallerTranscript(transcript);
        // The session disables provider-managed response creation. Only a
        // usable final transcript is allowed to advance the conversation.
        prepareAndRequestCallerResponse(transcript);
      }
      pendingCallerTranscriptions = Math.max(0, pendingCallerTranscriptions - 1);
      flushDeferredAgentTranscripts();
    }
    if (type === "conversation.item.input_audio_transcription.failed") {
      pendingCallerTranscriptions = Math.max(0, pendingCallerTranscriptions - 1);
      flushDeferredAgentTranscripts();
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
    // A completed response is a defensive flush for transports that omit the
    // more specific output_text.done event.
    if (type === "response.done" && responseHasToolCall) {
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
      if (!interrupted) options.callbacks.onAgentTextDelta?.(speech);
      deliverAgentTranscript(speech, interrupted);
      agentTranscript = "";
      agentInterrupted = false;
      textCompletionSent = true;
      options.callbacks.onAgentTextComplete?.(interrupted);
    }
    if (type === "input_audio_buffer.speech_started") {
      pendingCallerTranscriptions += 1;
      callerSpeechActive = true;
      const agentWasResponding = responseActive;
      const debounceMs = Math.max(180, options.bargeInDebounceMs ?? 0);
      window.clearTimeout(bargeInTimer);
      if (debounceMs > 0) {
        bargeInTimer = window.setTimeout(() => {
          if (callerSpeechActive) {
            agentInterrupted = agentWasResponding;
            options.callbacks.onCallerSpeechStarted(agentWasResponding);
          }
        }, debounceMs);
      } else {
        agentInterrupted = agentWasResponding;
        options.callbacks.onCallerSpeechStarted(agentWasResponding);
      }
    }
    if (type === "input_audio_buffer.speech_stopped") {
      callerSpeechActive = false;
      window.clearTimeout(bargeInTimer);
    }
    if (type === "response.done") {
      if (!eventResponseId || currentResponseId === eventResponseId) finishResponse();
    }
    if (type === "response.function_call_arguments.done") {
      const callId = String(event.call_id ?? "");
      const name = String(event.name ?? "");
      const argumentsJson = String(event.arguments ?? "{}");
      responseHasToolCall = true;
      if (acceptToolCall(callId)) executeToolCall(callId, name, argumentsJson);
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
      } else if (!/no active response|response.*not active/i.test(message)) {
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
        enqueueResponse(() => requestGreeting(channel, options.greeting, options.outputMode ?? "text"));
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
      send(channel, {
        type: "conversation.item.create",
        item: { type: "message", role: "user", content: [{ type: "input_text", text }] },
      });
      prepareAndRequestCallerResponse(text);
    },
    speakGreeting: (text) => {
      enqueueResponse(() => requestGreeting(channel, text, options.outputMode ?? "text"));
    },
    cancelResponse: cancelActiveResponse,
    close: () => {
      window.clearTimeout(bargeInTimer);
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

function requestGreeting(channel: RTCDataChannel, greeting: string, outputMode: "audio" | "text") {
  const text = greeting.trim();
  if (!text) return;
  send(channel, {
    type: "response.create",
    response: {
      instructions: `Say exactly this greeting, with no additions: ${text}`,
      output_modalities: [outputMode],
    },
  });
}

function requestCallerResponse(channel: RTCDataChannel) {
  // The full, transcript-aware agent prompt was applied with session.update
  // immediately before this request. Response-level instructions override that
  // session prompt in the Realtime API, which would discard personalized facts
  // such as service prices and the one-field-at-a-time intake rules.
  send(channel, { type: "response.create" });
}

function requestToolResultResponse(channel: RTCDataChannel) {
  send(channel, {
    type: "response.create",
    response: {
      tool_choice: "none",
    },
  });
}

function requestExactToolResponse(channel: RTCDataChannel, response: string) {
  send(channel, {
    type: "response.create",
    response: {
      instructions: `Say exactly this sentence with no additions or omissions: ${response}`,
      tool_choice: "none",
    },
  });
}

function toolSpokenResponse(name: string, result: Record<string, unknown>) {
  if (result.success === false) return "";
  const payload = result.result;
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return "";
  const response = (payload as Record<string, unknown>).spokenResponse;
  return typeof response === "string" ? response.trim() : "";
}

function localizedBookingFailure(language?: string) {
  switch (language?.toLocaleLowerCase()) {
    case "fr": return "Je n’ai pas pu enregistrer le rendez-vous dans le calendrier. Il n’est pas réservé. Souhaitez-vous réessayer ?";
    case "ar": return "لم أتمكن من حفظ الموعد في التقويم، لذلك لم يتم حجزه. هل تريد المحاولة مرة أخرى؟";
    case "sw": return "Sikuweza kuhifadhi miadi kwenye kalenda, kwa hiyo haijawekwa. Ungependa kujaribu tena?";
    default: return "I couldn’t save the appointment to the calendar, so it is not booked. Would you like to try again?";
  }
}

function localizedResponseFailure(language?: string) {
  switch (language?.toLocaleLowerCase()) {
    case "fr": return "Desole, je n'ai pas pu terminer ma reponse. Pouvez-vous repeter votre question, s'il vous plait ?";
    case "ar": return "عذرا، لم أتمكن من إكمال إجابتي. هل يمكنك تكرار سؤالك من فضلك؟";
    case "sw": return "Samahani, sikuweza kukamilisha jibu langu. Tafadhali rudia swali lako.";
    default: return "Sorry, I couldn't complete that answer. Could you repeat your question, please?";
  }
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
    || privateRoutingPattern.test(candidate)
    || routedChannelPattern.test(candidate)
    || namespaceCallPattern.test(candidate)
    || normalized.startsWith("to=")
    || normalized.startsWith("recipient=")) {
    return "";
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

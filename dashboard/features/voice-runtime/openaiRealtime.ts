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
  remoteStream: MediaStream;
  close: () => void;
  sendUserText: (text: string) => void;
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
}): Promise<OpenAiRealtimeConnection> {
  const peer = new RTCPeerConnection();
  const remoteStream = new MediaStream();
  const audio = options.playbackContext ? null : new Audio();
  if (audio) {
    audio.autoplay = true;
    audio.srcObject = remoteStream;
  }
  let remoteSource: MediaStreamAudioSourceNode | null = null;
  options.microphone.getTracks().forEach((track) => peer.addTrack(track, options.microphone));
  peer.ontrack = (event) => {
    const incoming = event.streams[0];
    if (incoming) incoming.getTracks().forEach((track) => remoteStream.addTrack(track));
    else remoteStream.addTrack(event.track);
    if (options.playbackContext && !remoteSource) {
      remoteSource = options.playbackContext.createMediaStreamSource(remoteStream);
      remoteSource.connect(options.playbackContext.destination);
      if (options.recordingDestination) remoteSource.connect(options.recordingDestination);
    } else if (audio) {
      void audio.play().catch(() => {
        if (peer.connectionState !== "closed") {
          options.callbacks.onError("Voice playback could not start. Select Start test call again.");
        }
      });
    }
  };

  const channel = peer.createDataChannel("oai-events");
  let agentTranscript = "";
  let agentInterrupted = false;
  let textCompletionSent = false;
  let responseActive = false;
  channel.onmessage = (message) => {
    let event: Record<string, unknown>;
    try { event = JSON.parse(String(message.data)) as Record<string, unknown>; } catch { return; }
    const type = String(event.type ?? "");
    if (type === "response.created") responseActive = true;
    if (type === "conversation.item.input_audio_transcription.completed") {
      const transcript = String(event.transcript ?? "").trim();
      if (transcript) options.callbacks.onCallerTranscript(transcript);
    }
    if (type === "response.output_audio_transcript.delta") {
      responseActive = true;
      agentTranscript += String(event.delta ?? "");
    }
    if (type === "response.output_audio_transcript.done") {
      const transcript = String(event.transcript ?? agentTranscript).trim();
      if (transcript) options.callbacks.onAgentTranscript(transcript, agentInterrupted);
      agentTranscript = "";
      agentInterrupted = false;
    }
    if (type === "response.output_text.delta") {
      const delta = String(event.delta ?? "");
      if (delta) {
        responseActive = true;
        textCompletionSent = false;
        agentTranscript += delta;
        options.callbacks.onAgentTextDelta?.(delta);
      }
    }
    if (type === "response.output_text.done") {
      const interrupted = agentInterrupted;
      const transcript = String(event.text ?? agentTranscript).trim();
      if (transcript) options.callbacks.onAgentTranscript(transcript, interrupted);
      agentTranscript = "";
      agentInterrupted = false;
      textCompletionSent = true;
      options.callbacks.onAgentTextComplete?.(interrupted);
    }
    // A completed response is a defensive flush for transports that omit the
    // more specific output_text.done event.
    if (type === "response.done" && options.outputMode === "text" && !textCompletionSent) {
      const interrupted = agentInterrupted;
      const transcript = agentTranscript.trim();
      if (transcript) options.callbacks.onAgentTranscript(transcript, interrupted);
      agentTranscript = "";
      agentInterrupted = false;
      textCompletionSent = true;
      options.callbacks.onAgentTextComplete?.(interrupted);
    }
    if (type === "input_audio_buffer.speech_started") {
      const agentWasResponding = responseActive;
      agentInterrupted = agentWasResponding;
      options.callbacks.onCallerSpeechStarted(agentWasResponding);
    }
    if (type === "response.done") responseActive = false;
    if (type === "output_audio_buffer.started") options.callbacks.onSpeaking(true);
    if (type === "output_audio_buffer.stopped" || type === "output_audio_buffer.cleared") options.callbacks.onSpeaking(false);
    if (type === "response.function_call_arguments.done") {
      const callId = String(event.call_id ?? "");
      const name = String(event.name ?? "");
      const argumentsJson = String(event.arguments ?? "{}");
      void options.callbacks.executeTool(callId, name, argumentsJson)
        .then((result) => {
          send(channel, {
            type: "conversation.item.create",
            item: { type: "function_call_output", call_id: callId, output: JSON.stringify(result) },
          });
          send(channel, { type: "response.create" });
        })
        .catch(() => {
          send(channel, {
            type: "conversation.item.create",
            item: { type: "function_call_output", call_id: callId, output: JSON.stringify({ success: false }) },
          });
          send(channel, { type: "response.create" });
        });
    }
    if (type === "error") {
      const error = event.error as { message?: string } | undefined;
      const message = error?.message ?? "The realtime voice session encountered an error.";
      if (!/no active response|response.*not active/i.test(message)) options.callbacks.onError(message);
    }
  };

  const opened = new Promise<void>((resolve, reject) => {
    const timeout = window.setTimeout(() => reject(new Error("The OpenAI Realtime channel timed out.")), 10_000);
    channel.onopen = () => {
      window.clearTimeout(timeout);
      options.callbacks.onConnected();
      if (options.greeting.trim()) {
        send(channel, {
          type: "response.create",
          response: {
            instructions: `Say exactly this greeting, with no additions: ${options.greeting.trim()}`,
            output_modalities: [options.outputMode ?? "audio"],
          },
        });
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
    remoteStream,
    sendUserText: (text) => {
      send(channel, {
        type: "conversation.item.create",
        item: { type: "message", role: "user", content: [{ type: "input_text", text }] },
      });
      send(channel, { type: "response.create" });
    },
    cancelResponse: () => send(channel, { type: "response.cancel" }),
    close: () => {
      channel.close();
      peer.close();
      remoteSource?.disconnect();
      remoteSource = null;
      remoteStream.getTracks().forEach((track) => track.stop());
      audio?.pause();
      if (audio) audio.srcObject = null;
    },
  };
}

function send(channel: RTCDataChannel, event: Record<string, unknown>) {
  if (channel.readyState === "open") channel.send(JSON.stringify(event));
}

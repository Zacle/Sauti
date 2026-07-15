export type OpenAiRealtimeCallbacks = {
  onConnected: () => void;
  onCallerTranscript: (text: string) => void;
  onAgentTranscript: (text: string, interrupted: boolean) => void;
  onSpeaking: (speaking: boolean) => void;
  onCallerSpeechStarted: () => void;
  onError: (message: string) => void;
  executeTool: (callId: string, name: string, argumentsJson: string) => Promise<Record<string, unknown>>;
};

export type OpenAiRealtimeConnection = {
  peer: RTCPeerConnection;
  channel: RTCDataChannel;
  remoteStream: MediaStream;
  close: () => void;
  sendUserText: (text: string) => void;
};

export async function connectOpenAiRealtime(options: {
  microphone: MediaStream;
  greeting: string;
  connectSdp: (offer: string) => Promise<string>;
  callbacks: OpenAiRealtimeCallbacks;
  recordingDestination?: MediaStreamAudioDestinationNode | null;
}): Promise<OpenAiRealtimeConnection> {
  const peer = new RTCPeerConnection();
  const remoteStream = new MediaStream();
  const audio = new Audio();
  audio.autoplay = true;
  audio.srcObject = remoteStream;
  options.microphone.getTracks().forEach((track) => peer.addTrack(track, options.microphone));
  peer.ontrack = (event) => {
    const incoming = event.streams[0];
    if (incoming) incoming.getTracks().forEach((track) => remoteStream.addTrack(track));
    else remoteStream.addTrack(event.track);
    void audio.play().catch(() => options.callbacks.onError("Voice playback was blocked. Allow audio and try again."));
    if (options.recordingDestination) {
      const context = options.recordingDestination.context as AudioContext;
      context.createMediaStreamSource(incoming ?? remoteStream).connect(options.recordingDestination);
    }
  };

  const channel = peer.createDataChannel("oai-events");
  let agentTranscript = "";
  let agentInterrupted = false;
  channel.onmessage = (message) => {
    let event: Record<string, unknown>;
    try { event = JSON.parse(String(message.data)) as Record<string, unknown>; } catch { return; }
    const type = String(event.type ?? "");
    if (type === "conversation.item.input_audio_transcription.completed") {
      const transcript = String(event.transcript ?? "").trim();
      if (transcript) options.callbacks.onCallerTranscript(transcript);
    }
    if (type === "response.output_audio_transcript.delta") {
      agentTranscript += String(event.delta ?? "");
    }
    if (type === "response.output_audio_transcript.done") {
      const transcript = String(event.transcript ?? agentTranscript).trim();
      if (transcript) options.callbacks.onAgentTranscript(transcript, agentInterrupted);
      agentTranscript = "";
      agentInterrupted = false;
    }
    if (type === "input_audio_buffer.speech_started") {
      agentInterrupted = true;
      options.callbacks.onCallerSpeechStarted();
    }
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
      options.callbacks.onError(error?.message ?? "The realtime voice session encountered an error.");
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
            output_modalities: ["audio"],
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
    close: () => {
      channel.close();
      peer.close();
      remoteStream.getTracks().forEach((track) => track.stop());
      audio.pause();
      audio.srcObject = null;
    },
  };
}

function send(channel: RTCDataChannel, event: Record<string, unknown>) {
  if (channel.readyState === "open") channel.send(JSON.stringify(event));
}

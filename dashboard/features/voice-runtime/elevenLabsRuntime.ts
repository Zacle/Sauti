import { Conversation, type Conversation as ElevenLabsConversation } from "@elevenlabs/client";
import type { BrowserVoiceRuntimeSession } from "@/types/api";
import type {
  BrowserVoiceRuntimeCallbacks,
  BrowserVoiceRuntimeConnection,
} from "./browserVoiceRuntime";
import {
  configPrimitives,
  configStringArray,
  providerError,
} from "./managedRuntimeConfig";

export async function connectElevenLabsRuntime(
  session: BrowserVoiceRuntimeSession,
  callbacks: BrowserVoiceRuntimeCallbacks,
): Promise<BrowserVoiceRuntimeConnection> {
  let stopped = false;
  let ended = false;
  let callerSpeaking = false;
  let agentSpeaking = false;
  let finalAgentText = "";
  let alignedAgentCaption = "";
  const typedTranscripts = new Set<string>();
  const toolNames = configStringArray(session.configuration, "toolNames");
  const clientTools = Object.fromEntries(toolNames.map((name) => [
    name,
    async (parameters: unknown) => JSON.stringify(await callbacks.executeTool(
      crypto.randomUUID(),
      name,
      JSON.stringify(parameters ?? {}),
    )),
  ]));

  const finish = () => {
    if (ended || stopped) return;
    ended = true;
    callbacks.onEnded("completed");
  };

  let conversation: ElevenLabsConversation;
  try {
    conversation = await Conversation.startSession({
      conversationToken: session.clientToken,
      connectionType: "webrtc",
      dynamicVariables: configPrimitives(session.configuration, "dynamicVariables"),
      clientTools,
      onConnect: () => callbacks.onConnected(),
      onDisconnect: (details) => {
        if (details.reason === "error") callbacks.onError(providerError("ElevenLabs", details.message));
        finish();
      },
      onError: (message) => callbacks.onError(providerError("ElevenLabs", message)),
      onVadScore: ({ vadScore }) => {
        if (vadScore >= 0.65 && !callerSpeaking) {
          callerSpeaking = true;
          callbacks.onCallerSpeechStarted();
          if (agentSpeaking) callbacks.onInterrupted();
        }
      },
      onMessage: ({ role, message }) => {
        const text = message.trim();
        if (!text) return;
        if (role === "user") {
          callerSpeaking = false;
          callbacks.onCallerSpeechEnded();
          if (typedTranscripts.delete(normalize(text))) return;
          callbacks.onCallerTranscript(text);
          return;
        }
        finalAgentText = text;
        callbacks.onAgentTranscript(text, false);
        if (agentSpeaking && !alignedAgentCaption) callbacks.onAgentCaption(text);
      },
      onAudioAlignment: ({ chars }) => {
        const aligned = chars.join("").trim();
        if (!aligned) return;
        alignedAgentCaption = appendAlignedCaption(alignedAgentCaption, aligned);
        callbacks.onAgentCaption(alignedAgentCaption);
      },
      onModeChange: ({ mode }) => {
        agentSpeaking = mode === "speaking";
        callbacks.onAgentSpeaking(agentSpeaking);
        if (agentSpeaking) {
          alignedAgentCaption = "";
          if (finalAgentText) callbacks.onAgentCaption(finalAgentText);
        } else if (finalAgentText) {
          callbacks.onAgentCaption(finalAgentText);
        }
      },
      onInterruption: () => callbacks.onInterrupted(),
    });
  } catch (error) {
    throw new Error(providerError("ElevenLabs", error));
  }

  return {
    sendUserText(text: string) {
      const normalized = normalize(text);
      if (!normalized) return;
      typedTranscripts.add(normalized);
      conversation.sendUserMessage(text);
    },
    async stop() {
      if (stopped) return;
      stopped = true;
      ended = true;
      await conversation.endSession();
    },
  };
}

function normalize(value: string) {
  return value.trim().replace(/\s+/g, " ").toLocaleLowerCase();
}

function appendAlignedCaption(previous: string, fragment: string) {
  if (!previous) return fragment;
  if (fragment.startsWith(previous)) return fragment;
  const needsSpace = !/\s$/.test(previous) && !/^[\s,.;:!?'"’)\]}-]/.test(fragment);
  return `${previous}${needsSpace ? " " : ""}${fragment}`;
}

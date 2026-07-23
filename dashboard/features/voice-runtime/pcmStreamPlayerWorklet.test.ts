import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";

type WorkletInstance = {
  initialBufferFrames: number;
  learnedBufferFrames: number;
  targetBufferFrames: number;
  port: {
    onmessage: ((message: { data: Record<string, unknown> }) => void) | null;
  };
  process: (inputs: unknown[], outputs: Float32Array[][]) => boolean;
  reset: () => void;
};

function loadWorklet() {
  const events: Array<Record<string, unknown>> = [];
  let Processor: (new (options?: Record<string, unknown>) => WorkletInstance) | null = null;
  class FakeAudioWorkletProcessor {
    port = {
      onmessage: null as ((message: { data: Record<string, unknown> }) => void) | null,
      postMessage: (message: Record<string, unknown>) => events.push(message),
    };
  }
  const context = vm.createContext({
    AudioWorkletProcessor: FakeAudioWorkletProcessor,
    Float32Array,
    Math,
    currentFrame: 0,
    sampleRate: 48_000,
    registerProcessor: (_name: string, registered: new () => WorkletInstance) => {
      Processor = registered;
    },
  });
  const source = readFileSync(new URL("../../public/pcm-stream-player.js", import.meta.url), "utf8");
  vm.runInContext(source, context);
  assert.ok(Processor);
  const RegisteredProcessor = Processor as new (options: Record<string, unknown>) => WorkletInstance;
  return {
    context,
    events,
    create: () => new RegisteredProcessor({}),
  };
}

function send(instance: WorkletInstance, data: Record<string, unknown>) {
  instance.port.onmessage?.({ data });
}

test("retains a learned underrun buffer across utterances and interruption clears", () => {
  const worklet = loadWorklet();
  const player = worklet.create();

  send(player, { type: "begin" });
  send(player, {
    type: "push",
    samples: new Float32Array(player.initialBufferFrames),
  });
  while (player.learnedBufferFrames === player.initialBufferFrames) {
    player.process([], [[new Float32Array(128)]]);
  }

  const learned = player.learnedBufferFrames;
  assert.ok(learned > player.initialBufferFrames);

  player.reset();
  send(player, { type: "begin" });
  assert.equal(player.learnedBufferFrames, learned);
  assert.equal(player.targetBufferFrames, learned);
});

test("learns from provider arrival jitter before another underrun occurs", () => {
  const worklet = loadWorklet();
  const player = worklet.create();

  send(player, { type: "begin" });
  worklet.context.currentFrame = 100;
  send(player, { type: "push", samples: new Float32Array(1_920) });
  worklet.context.currentFrame = 24_100;
  send(player, { type: "push", samples: new Float32Array(1_920) });

  assert.ok(player.learnedBufferFrames > player.initialBufferFrames);
  assert.equal(player.targetBufferFrames, player.learnedBufferFrames);
});

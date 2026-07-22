# Browser voice runtime providers

Sauti can delegate browser call transport, turn detection, transcription, and speech playback to a managed provider without delegating business authority. The provider-neutral session contract is `BrowserVoiceRuntimeSession`; backend providers register through `BrowserVoiceRuntimeProvider`, and the dashboard connects them through `browserVoiceRuntime.ts`.

## Vapi pilot scope

The first adapter applies to authenticated Agent Studio test calls only. Existing public web voice and carrier calls remain on Sauti's current runtimes. Vapi owns the test call's WebRTC audio, barge-in, STT, LLM turn, and TTS. Sauti still owns:

- tenant and agent selection;
- the saved system prompt and business facts;
- available tool definitions;
- action effects, confirmation policy, and tool execution;
- authoritative booking/CRM/payment state;
- transcript persistence and post-call analysis.

Agent Studio exposes an explicit runtime selector. `Vapi` requests the managed Vapi adapter and displays `VAPI` in the active-call header. `Sauti + Cartesia` runs Sauti's guarded OpenAI Realtime conversation path with the agent's saved Cartesia voice. A missing provider configuration fails visibly instead of silently turning the comparison into the legacy cascade runtime.

The browser never receives `VAPI_API_KEY` or `VAPI_PUBLIC_KEY`. It receives a short-lived, call-scoped Sauti token and sends Vapi's `/call/web` request through an authenticated Sauti proxy. The proxy authenticates that endpoint with the Vapi public key, as required by Vapi's Web SDK contract. Vapi tool callbacks use the Sauti scoped token and are accepted only while the matching test or web call is active.

## Configuration

Set these in the uncommitted local `.env` or the production secret environment:

```text
SAUTI_TEST_VOICE_RUNTIME=vapi
VAPI_API_KEY=replace-with-private-vapi-key
VAPI_PUBLIC_KEY=replace-with-public-vapi-key
VAPI_API_BASE_URL=https://api.vapi.ai
VAPI_PUBLIC_BASE_URL=https://sauti.uk
VAPI_MODEL_PROVIDER=openai
VAPI_MODEL=gpt-4.1-mini
VAPI_TRANSCRIBER_PROVIDER=deepgram
VAPI_TRANSCRIBER_MODEL=nova-3
VAPI_TRANSCRIBER_LANGUAGE=multi
VAPI_VOICE_PROVIDER=vapi
VAPI_VOICE_ID=Savannah
VAPI_VOICE_VERSION=2
VAPI_VOICE_LANGUAGE=auto
VAPI_TOOL_TIMEOUT_SECONDS=30
VAPI_DELAYED_MESSAGE_MS=1000
```

`VAPI_PUBLIC_BASE_URL` must reach the same backend/database that created the test call. Production uses `https://sauti.uk`. A local test with tools needs an HTTPS tunnel to the local backend; pointing a local call at the production URL will fail call-token validation because production does not own the local call record.

`VAPI_PUBLIC_KEY` and `VAPI_API_KEY` are not interchangeable. The browser-call `/call/web` endpoint requires the public key; the private API key is for authenticated server-management endpoints. Both are available under **Vapi Dashboard → Vapi API Keys**. Sauti keeps even the public key behind its proxy so callers cannot replace the server-generated assistant configuration.

The pilot uses the Vapi voice configured above, not the agent's existing Sauti/Cartesia voice ID. This keeps provider comparison explicit instead of assuming that voice identifiers are portable. A later provider-specific voice catalog can map choices deliberately.

Vapi supplies localized tool-start filler and, after `VAPI_DELAYED_MESSAGE_MS`, a delayed apology while the tool continues. Sauti does not maintain translated waiting phrases. Final availability, save, update, cancellation, payment, messaging, and other outcomes always come from the Sauti tool result.

Agent Studio subscribes to Vapi's playback-synchronized `assistant.speechStarted` event. The fixed greeting is displayed only when its audio begins, and later assistant captions update as each synthesized speech segment starts instead of waiting for a final assistant transcript. The default Vapi voice provides text-only speech-start events, not word timestamps, so this is segment-synchronized captioning rather than exact character-by-character highlighting. A voice provider that exposes word alignment is required for exact karaoke-style timing.

Vapi remains a cascaded voice pipeline (endpointing, transcription, model, speech synthesis, then playback). UI synchronization can remove misleading display delay, but it cannot remove the provider's connection and model/TTS latency floor. Evaluate first-audio latency and audible chunk continuity separately from Sauti tool latency when comparing runtimes.

API clients that do not send an explicit runtime can choose the backend default with:

```text
SAUTI_TEST_VOICE_RUNTIME=sauti
```

## Cartesia comparison scope

The Agent Studio Cartesia option is deliberately labelled `Sauti + Cartesia`: Cartesia provides realtime Sonic speech while OpenAI Realtime provides transcription, turn handling, reasoning, and tool calls. It requires a saved `cartesia:` voice and both provider credentials. Sauti keeps tool execution, confirmation policy, transcript persistence, and database outcomes authoritative.

This is a useful comparison for voice quality, uninterrupted playback, first-audio latency, and Sauti's own orchestration. It is not a test of Cartesia Line's separately managed agent platform. Integrating Cartesia Line would require another peer `BrowserVoiceRuntimeProvider` and an explicit decision about how its tools return to Sauti's authority boundary.

## Running Spring directly on Windows

Spring Boot does not automatically import the repository `.env` when it is launched directly with Gradle. Use the repository helper so provider credentials become process environment variables without printing them:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/run-local-backend.ps1
```

To validate loading without starting Spring, add `-ValidateOnly`.

The direct backend listens on `http://localhost:8080`. If Vapi needs to call this local process, run `cloudflared tunnel --url http://localhost:8080`, put the resulting HTTPS URL in `VAPI_PUBLIC_BASE_URL`, stop the backend, and run the helper again. If the backend is publicly hosted at `https://sauti.uk`, no local tunnel is required.

## Adding another provider

Implement `BrowserVoiceRuntimeProvider` on the backend and register a matching connector in `dashboard/features/voice-runtime/browserVoiceRuntime.ts`. Keep the shared session response free of provider secrets. New providers must continue to route side effects through `ToolFulfillmentRouter`; a provider callback must never call a business integration directly.

Telnyx and managed-agent evaluations should be added as peer adapters and compared with the same scenarios, agent prompt, tool fixtures, languages, and latency measurements. A Cartesia TTS comparison still needs an STT, turn-taking, and model runtime, so the current Sauti + Cartesia option is not directly equivalent to Vapi's complete orchestration layer.

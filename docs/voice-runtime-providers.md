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
VAPI_TRANSCRIBER_MODEL=agent
VAPI_TRANSCRIBER_LANGUAGE=agent
VAPI_FLUX_EOT_THRESHOLD=0.7
VAPI_FLUX_EOT_TIMEOUT_MS=2500
VAPI_VOICE_PROVIDER=vapi
VAPI_VOICE_ID=Savannah
VAPI_VOICE_VERSION=2
VAPI_VOICE_LANGUAGE=auto
VAPI_CARTESIA_MODEL=sonic-3
VAPI_TOOL_TIMEOUT_SECONDS=30
VAPI_DELAYED_MESSAGE_MS=1000
```

`VAPI_PUBLIC_BASE_URL` must reach the same backend/database that created the test call. Production uses `https://sauti.uk`. A local test with tools needs an HTTPS tunnel to the local backend; pointing a local call at the production URL will fail call-token validation because production does not own the local call record.

`VAPI_PUBLIC_KEY` and `VAPI_API_KEY` are not interchangeable. The browser-call `/call/web` endpoint requires the public key; the private API key is for authenticated server-management endpoints. Both are available under **Vapi Dashboard → Vapi API Keys**. Sauti keeps even the public key behind its proxy so callers cannot replace the server-generated assistant configuration.

For a saved `cartesia:` agent voice, the Vapi adapter strips Sauti's namespace prefix and sends that exact provider voice ID to Vapi with the `cartesia` provider and `sonic-3` model. This keeps the voice identity consistent between `Sauti + Cartesia` and `Vapi`; only the conversation orchestration changes. `VAPI_VOICE_PROVIDER` and `VAPI_VOICE_ID` are fallback values for agents without a Cartesia voice, and `VAPI_CARTESIA_MODEL` can explicitly change the Vapi-side Cartesia model.

Vapi's OpenAI-native Realtime configuration cannot use Cartesia TTS: native speech-to-speech requires an OpenAI-compatible voice. The Vapi + Cartesia comparison therefore remains a cascaded Deepgram, text-model, and Cartesia pipeline. Do not set a realtime OpenAI model merely to reduce latency unless changing the test's voice provider is intentional.

`VAPI_TRANSCRIBER_LANGUAGE=agent` resolves a single-language agent to its configured default language and uses `multi` only when the agent supports more than one language. An explicit provider language such as `en`, `fr`, or `multi` still overrides this behavior. Deepgram also receives the business identity and the agent's saved boosted-keyword list as keyterms; Sauti does not hard-code phrases from one business.

`VAPI_TRANSCRIBER_MODEL=agent` selects Deepgram Flux with native conversational end-of-turn detection when every configured agent language is supported: `flux-general-en` for English-only agents and `flux-general-multi` for supported multilingual or non-English agents such as French. Agents containing Arabic or another language outside Flux's supported set automatically retain Nova-3 and Sauti's transcription endpointing plan. Flux uses a balanced `VAPI_FLUX_EOT_THRESHOLD=0.7` and a faster 2500 ms maximum EOT timeout; explicit model values still override this routing.

Potentially remote or state-changing tools receive Vapi's generated `request-start` filler in the active call language and, after `VAPI_DELAYED_MESSAGE_MS`, its delayed apology while the tool continues. Static business-hours reads and internal conversation-state updates remain on the fast path. Sauti does not maintain translated waiting phrases. Final availability, save, update, cancellation, payment, messaging, and other outcomes always come from the Sauti tool result.

Agent Studio subscribes to Vapi's playback-synchronized `assistant.speechStarted` event. The fixed greeting is displayed only when its audio begins, and later assistant captions update as each synthesized speech segment starts instead of waiting for a final assistant transcript. It also subscribes to `voice-input`, the exact text submitted to TTS, and reveals that text only after remote audio starts when `assistant.speechStarted` is absent. It never presents streamed model output as spoken text. The default Vapi voice provides text-only speech-start events, not word timestamps, so this is segment-synchronized captioning rather than exact character-by-character highlighting. A voice provider that exposes word alignment is required for exact karaoke-style timing.

Vapi tool callbacks log the provider call ID, Sauti call ID, tool name, success flag, and server execution duration without logging tool arguments. End-of-call reports log Vapi's aggregate turn, model, voice, transcriber, and endpointing metrics. These two records separate Sauti/database latency from provider pipeline latency while keeping customer content out of operational logs.

Vapi + Cartesia remains a cascaded voice pipeline (endpointing, transcription, model, speech synthesis, then playback). Flux removes the extra heuristic endpointing wait for supported languages, but it cannot remove the provider's connection, model, or Cartesia synthesis stages. UI synchronization can remove misleading display delay, not this pipeline floor. Evaluate first-audio latency and audible chunk continuity separately from Sauti tool latency when comparing runtimes.

API clients that do not send an explicit runtime can choose the backend default with:

```text
SAUTI_TEST_VOICE_RUNTIME=sauti
```

## Cartesia comparison scope

The Agent Studio Cartesia option is deliberately labelled `Sauti + Cartesia`: Cartesia provides realtime Sonic speech while OpenAI Realtime provides transcription, turn handling, reasoning, and tool calls. It requires a saved `cartesia:` voice and both provider credentials. Sauti keeps tool execution, confirmation policy, transcript persistence, and database outcomes authoritative.

This is a useful comparison for voice quality, uninterrupted playback, first-audio latency, and Sauti's own orchestration. It is not a test of Cartesia Line's separately managed agent platform. Integrating Cartesia Line would require another peer `BrowserVoiceRuntimeProvider` and an explicit decision about how its tools return to Sauti's authority boundary.

The hybrid browser media path coalesces Cartesia's arbitrary provider chunks into 40 ms PCM WebSocket frames before forwarding them. The AudioWorklet starts with a 280 ms jitter buffer and increases its target by 80 ms after a real underrun, up to 720 ms. This intentionally trades about 120 ms of additional first-playback buffering for continuous speech; it does not add seconds to model or tool latency. Interruption still clears the worklet and closes the old Cartesia context immediately.

`CARTESIA_MAX_BUFFER_DELAY_MS` controls Cartesia's handling of incrementally streamed text, not Sauti's downstream PCM jitter buffer. Sauti sends each validated assistant message as one complete, punctuated transcript with `continue=false`, so browser continuity is handled at the PCM boundary rather than by adding a multi-second text-generation delay.

## Shared conversational authority

Changing a voice provider must not change which customer facts or actions Sauti trusts. Every runtime uses the same three-layer contract:

1. The active model interprets the caller's latest meaning and language through `update_conversation_state`; it may propose facts, corrections, questions, and actions but cannot perform a side effect.
2. Sauti retains authoritative workflow state in the call session. Booking creation uses a signed review token. Other explicitly confirmed writes retain the exact tool name and sanitized arguments at one semantic-state revision.
3. `ToolFulfillmentRouter` performs the action only after a later clean approval matches and atomically consumes that retained proposal. It returns factual `actionPerformed`, `effect`, and `action` fields; a successful provider request is not a completed business mutation when the domain result says otherwise.

This applies to booking CRUD, confirmed CRM or sheet updates, payment requests, messages that require approval, transfers, and custom side-effecting tools. It uses structured meaning rather than English phrases, business-specific keywords, or provider conversation memory. A compound turn such as approval plus a price question is not authorization: the question must be answered and the caller must confirm again on a later turn. Terminal call ending is intentionally different so a clear caller farewell can be answered respectfully without an unnatural second confirmation.

## OpenAI Realtime slow-operation lifecycle

Sauti's browser and telephony OpenAI Realtime paths separate a conversational deadline from an accepted tool operation. A caller-facing watchdog may retry an incomplete model response, but it must not cancel or declare failure for a booking, CRM write, payment, message, or custom integration that is still running.

Potentially remote tools now receive a model-generated progress update after 1.5 seconds and another short update every 8 seconds while the operation remains pending. The model chooses natural wording in the active caller language; Sauti supplies only the factual lifecycle state and prohibits invented success or failure. Internal conversation-state updates, static business-hours reads, and call ending stay on the immediate path.

If the caller interrupts a progress update, playback stops and the new caller turn takes priority, but the accepted tool future remains alive. Semantically identical provider retries share that active execution. When the operation completes, its function result is added to conversation history and its factual outcome is queued against the current turn rather than discarded as stale.

An OpenAI response that finishes as `failed` or `incomplete` without text or a tool call is retried once with the already accepted transcript. Only a second terminal failure reaches the safety fallback, which states that nothing changed and offers a retry; it no longer asks the caller to repeat a question Sauti already received.

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

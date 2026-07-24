# Managed voice provider comparison

Sauti Agent Studio can run the same saved Sauti agent through Retell, ElevenLabs Agents, or a Telnyx AI Assistant. Each provider is a peer `BrowserVoiceRuntimeProvider`; selecting one does not replace the Sauti, Vapi, public web voice, or carrier paths.

Provider resources are provisioned automatically. Create a Sauti agent from a template, save it, select a provider in Agent Studio, and press the test button. On the first test, Sauti compiles the saved agent into the provider's configuration and creates the external agent. Later tests reuse the stored tenant-scoped binding. If the Sauti agent, greeting, languages, tools, or live behavior changes, Sauti detects the blueprint hash change and synchronizes the provider before starting the call.

Generated provider IDs are operational metadata stored in `managed_voice_agent_bindings`; they are not environment variables and are not shown to business users. Sauti remains the source of truth for business behavior and tool effects.

## Security boundary

- Account API keys remain in the Spring process environment. Next.js never receives them.
- Retell receives the one-time web-call access token returned by `POST /v2/create-web-call`.
- ElevenLabs receives a short-lived WebRTC conversation token returned by `GET /v1/convai/conversation/token`.
- Telnyx's official browser library connects with the generated AI Assistant ID. Sauti sends only that public identifier and version to the browser; the account API key remains server-side.
- Sauti creates the test-call record before contacting a provider. Provider transcripts are written through the existing authenticated test-call APIs and remain marked as test data.
- Provider tools do not receive database, calendar, CRM, payment, or workspace credentials. ElevenLabs and Telnyx browser client tools call Sauti's authenticated tool endpoint. Retell webhook tools call a short-lived URL bound to one active call and one agent.
- Every business action still passes through `ToolFulfillmentRouter`, including confirmation policy and factual CRUD outcomes. A provider response alone is never proof that a create, update, or delete succeeded.
- Retell webhook redelivery is deduplicated for the lifetime of the browser test call. Operational logs include provider, Sauti call ID, tool name, duration, and success only; arguments, results, transcripts, URLs, and credentials are excluded.

Use test accounts, provider spending limits, minimal provider retention, and synthetic customer information for this comparison. Review each provider's recording, transcript retention, and model-training settings before using real customer data.

## Environment variables

Set real values only in the uncommitted local `.env` or the deployed process secret environment.

| Variable | Required | Purpose |
| --- | --- | --- |
| `MANAGED_VOICE_PUBLIC_BASE_URL` | Retell tools | Public Sauti backend that owns the test-call record. Production is `https://sauti.uk`. |
| `RETELL_API_KEY` | Retell | Server credential used to create/synchronize the Retell response engine and agent, then mint web calls. |
| `RETELL_API_BASE_URL` | No | Defaults to `https://api.retellai.com`. |
| `RETELL_DEFAULT_VOICE_ID` | No | Built-in Retell voice used when provisioning; defaults to `retell-Cimo`. |
| `RETELL_MODEL` | No | Retell response-engine model; defaults to `gpt-4.1-mini`. |
| `ELEVENLABS_API_KEY` | ElevenLabs | Restricted, quota-limited server key allowed to manage agents/tools and mint conversation tokens. |
| `ELEVENLABS_ENVIRONMENT` | No | Defaults to `production`; use a provider staging environment when configured. |
| `ELEVENLABS_API_BASE_URL` | No | Defaults to `https://api.elevenlabs.io`. |
| `TELNYX_AI_ENVIRONMENT` | No | `production` or `development`; defaults to `production`. |
| `TELNYX_AI_REGION` | No | Optional WebRTC region such as the closest supported Telnyx region. |
| `TELNYX_API_KEY` | Telnyx | Server-only credential used to create/synchronize the generated assistant. It is never sent to the browser adapter. |
| `SAUTI_TEST_VOICE_RUNTIME` | No | Default when an API client omits a runtime: `sauti`, `vapi`, `retell`, `elevenlabs`, or `telnyx`. Agent Studio sends the explicit selection. |

Example:

```text
MANAGED_VOICE_PUBLIC_BASE_URL=https://sauti.uk

RETELL_API_KEY=
RETELL_API_BASE_URL=https://api.retellai.com
RETELL_DEFAULT_VOICE_ID=retell-Cimo
RETELL_MODEL=gpt-4.1-mini

ELEVENLABS_API_KEY=
ELEVENLABS_ENVIRONMENT=production
ELEVENLABS_API_BASE_URL=https://api.elevenlabs.io

TELNYX_API_KEY=
TELNYX_AI_ENVIRONMENT=production
TELNYX_AI_REGION=
```

The three API keys are the only required provider values. The backend reports a provider as unavailable until its API key is present. Restart Spring after changing `.env`; Spring does not reload process environment values during runtime.

## Provider setup

### Retell

Sauti creates a Retell LLM response engine and voice agent. Enabled Sauti tools become Retell custom functions that use the dynamic `{{sauti_tool_url}}`; each web call supplies a short-lived URL bound to that Sauti test call. The native Retell end-call tool handles respectful call completion.

Retell invalidates an unused web-call access token shortly after creation, so Agent Studio mints it only after the user presses the test button.

### ElevenLabs Agents

Sauti creates a private authenticated ElevenLabs agent and standalone client tools from the enabled Sauti schemas. It attaches the generated tool IDs to the agent, registers matching browser handlers, and mints a short-lived conversation token. Give the restricted API key agent read/write, tool read/write, and conversation-token permissions plus an explicit credit quota.

Client tools work for this WebRTC browser comparison, not ElevenLabs phone/SIP calls. A later carrier deployment must use authenticated provider webhook tools and signature verification; do not assume a successful browser test has secured the telephone webhook path.

### Telnyx AI Assistants

Sauti creates a web-enabled Telnyx AI Assistant with the compiled prompt, greeting, interruption settings, and client-side tools. Telnyx returns the assistant/version identifiers that Sauti stores and passes to the official browser library. Use the development environment where possible and apply account-level usage controls.

Telnyx client tools are WebRTC-only. Phone/SIP assistants require webhook tools with server-side authentication.

## Synchronization behavior

- The provider API is contacted for provisioning only on the first test or when the canonical Sauti blueprint changes.
- A failed provider create/update does not write or advance the binding hash, so the next test retries safely.
- Sauti updates resources that it previously created; it does not discover or overwrite manually created provider agents.
- ElevenLabs tool resources removed from a Sauti agent are detached on the next agent synchronization. They are intentionally not deleted automatically because provider resources may have audit value.
- Provider voice catalogs are not interchangeable. Retell uses `RETELL_DEFAULT_VOICE_ID`; ElevenLabs and Telnyx use their account defaults until Sauti adds an explicit provider-neutral voice mapping UI.

## Dependency audit note

The pilot uses the current official managed-provider browser SDK releases listed in `dashboard/package.json`. The 2026-07-24 production dependency audit still reports:

- a moderate transitive `uuid` advisory under `@telnyx/ai-agent-lib` / `@telnyx/webrtc`, with no upstream fix available;
- high transitive `postcss` and `sharp` advisories under Next.js for which npm currently proposes an unsafe breaking downgrade rather than a compatible fix.

Next.js was moved to the latest compatible 15.5 patch, which clears the direct Next.js advisories but not those transitive reports. Do not run `npm audit fix --force`. Keep the Telnyx adapter limited to the test pilot, monitor upstream releases, and re-run the audit before promoting any managed provider to a customer-facing channel.

## Fair test procedure

Use one fixed scenario set and record the same measurements for every provider:

1. greeting connection-to-first-audio latency;
2. simple factual response latency;
3. short and long caller turns, names, phone numbers, dates, and multilingual correction;
4. mid-sentence interruption and recovery;
5. a fast read tool and an intentionally delayed read tool;
6. confirmed create, update, and delete operations, including provider redelivery;
7. caller cancellation before approval;
8. respectful caller-initiated ending;
9. at least a ten-turn call to expose context loss;
10. provider cost, failed-turn rate, transcript accuracy, and Sauti tool duration separately.

Provider captions are driven by the closest playback event available. ElevenLabs uses audio alignment, Retell uses agent audio start plus transcript updates, and Telnyx uses agent speaking state. Final transcripts are persisted separately, so completed model text is not presented as audible speech before provider playback begins.

Official references:

- Retell web calls: https://docs.retellai.com/deploy/web-call
- Retell create-web-call API: https://docs.retellai.com/api-references/create-web-call
- Retell custom functions: https://docs.retellai.com/build/conversation-flow/custom-function
- ElevenLabs agent authentication: https://elevenlabs.io/docs/eleven-agents/customization/authentication
- ElevenLabs WebRTC token: https://elevenlabs.io/docs/api-reference/conversations/get-webrtc-token
- ElevenLabs JavaScript/React SDK: https://elevenlabs.io/docs/eleven-agents/libraries/react
- Telnyx AI Assistant quickstart: https://developers.telnyx.com/docs/inference/ai-assistants/no-code-voice-assistant
- Telnyx client-side tools: https://developers.telnyx.com/docs/inference/ai-assistants/client-side-tools

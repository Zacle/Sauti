# Sauti Channel Expansion Plan

## Context

Sauti is a voice AI platform for African businesses. The core constraint is telephony
infrastructure: traditional PSTN carriers are fragmented across Africa, local number
provisioning requires per-country regulatory compliance, and international calling costs
are prohibitive for end customers.

## Implementation status

- Phase 1 uses secure browser PCM streaming over a short-lived authenticated WebSocket.
  This is intentionally simpler than a true WebRTC media server and does not require
  STUN/TURN. The widget supports agent, language, color, position, and label attributes.
- Phase 2 now supports Meta webhook verification, HMAC signature validation, webhook
  deduplication, per-agent phone-number-ID routing, 24-hour conversation threads, text
  replies, voice-note download/transcription, TTS replies, and OGG Opus media upload.
- Phase 3 and Phase 4 remain future work. Existing Twilio and SignalWire paths are retained.

The dashboard stores the WhatsApp phone-number ID per agent. Access tokens and the Meta app
secret remain server environment variables and are never returned to the browser.

This plan describes three complementary channels in launch order, followed by the
telephony roadmap once revenue supports carrier agreements.

---

## Phase 1 — WebRTC Click-to-Call Widget (Launch)

### What it is
An embeddable JavaScript widget that businesses place on their website, WhatsApp link-in-bio,
or Facebook page. The customer clicks "Talk to us", the browser opens a WebRTC audio session
directly to Sauti, and the AI agent handles the conversation in real time.

### Why first
- Zero telephony dependency — no carrier, no number, no geographic restriction
- The `TestCallPanel` in the dashboard is already 90% of this infrastructure
- Businesses in DRC, Kenya, Nigeria all have websites or Facebook pages
- Customer uses WiFi or mobile data — no per-minute call cost on either side
- Full duplex real-time conversation, identical pipeline to phone calls

### Architecture
```
Customer browser
    |  WebRTC (audio over DTLS-SRTP)
    v
TURN/STUN server  (coturn, self-hosted or Metered.ca free tier)
    |
    v
Sauti backend  ←  existing STT → LLM → TTS pipeline, unchanged
    |
    v
Widget iframe sends/receives audio via existing WebSocket session
```

### Backend changes needed
- New `/ws/webrtc/{sessionId}` WebSocket endpoint (separate from Twilio media stream path)
- WebRTC signalling endpoint: `POST /api/v1/calls/webrtc/offer` → returns SDP answer
- `WebRtcMediaSession` class implementing the same `TwilioMediaSession` contract
- Audio codec: WebRTC sends Opus 48kHz → transcode to PCM 16kHz for Deepgram (same as
  Twilio mulaw path, just different codec converter)
- Session lifecycle maps to existing Call entity with `direction = "webrtc"`

### Widget (frontend SDK)
- Extracted from `TestCallPanel.tsx` as a standalone npm package: `@sauti/widget`
- Published to npm, loaded via `<script>` tag on any site
- Configurable: agent ID, primary color, language, position
- Communicates with Sauti backend via the WebRTC signalling endpoint
- No server-side rendering required — pure client JS

### Widget embed (one line for the business)
```html
<script src="https://cdn.sauti.ai/widget.js"
        data-agent="<agentId>"
        data-lang="sw"
        async></script>
```

### Dashboard integration
- "Widget" tab in AgentCreator → shows embed code, color picker, preview
- Widget calls appear in call history with `direction = webrtc`
- Analytics and recordings work identically to phone calls

### Milestones
1. WebRTC signalling endpoint + `WebRtcMediaSession` — 3 days
2. TURN server setup (coturn on existing VPS) — 1 day
3. Extract `TestCallPanel` into `@sauti/widget` npm package — 2 days
4. Widget configuration UI in AgentCreator — 1 day
5. End-to-end test across Chrome, Safari, Firefox on mobile — 1 day

**Total estimated effort: 1.5 weeks**

---

## Phase 2 — WhatsApp Business API (Async Voice)

### What it is
Integration with Meta's WhatsApp Cloud API. When a customer sends a voice note to a
business's WhatsApp number, Sauti transcribes it, runs it through the LLM, synthesizes
a voice response, and sends it back as a voice note — all within seconds.

### Why second
- WhatsApp penetration in Sub-Saharan Africa is 80%+
- Businesses already have WhatsApp Business numbers — no new provisioning
- Async voice covers the majority of customer service use cases (FAQs, bookings,
  status checks) without requiring real-time streaming
- Meta's Cloud API is free to register from any country including DRC

### Architecture
```
Customer sends voice note (OGG Opus) on WhatsApp
    |
    v
Meta sends webhook POST to Sauti: /webhooks/whatsapp
    |
    v
Sauti downloads audio from Meta CDN
    |
    v
BrowserSpeechToTextService.transcribe() — already handles prerecorded audio
    |
    v
ConversationOrchestrator.handleUserUtterance() — unchanged
    |
    v
VoiceCatalogService.synthesize() → MP3 audio
    |
    v
Convert MP3 to OGG Opus (ffmpeg or jave2 library)
    |
    v
Upload audio to Meta, send as voice message reply
```

### Conversation threading
- WhatsApp conversations are keyed by the customer's phone number
- Redis stores conversation history per `whatsapp:{businessNumber}:{customerNumber}`
- Same `CallSessionStore` TTL logic applies
- Each voice note exchange is one "turn" stored as a `CallTurn`
- A "call" is created per conversation thread with `direction = "whatsapp"`

### Components needed
- `WhatsAppWebhookController` — handles Meta webhook verification and inbound events
- `WhatsAppMessageSender` — downloads audio from Meta CDN, uploads response, sends reply
- `WhatsAppCallSession` — maps WhatsApp conversation thread to Sauti's Call entity
- OGG Opus codec converter (WhatsApp requires OGG Opus for audio messages)
- Meta app setup: Webhooks URL, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`

### Text fallback
When the customer sends a text message instead of voice, route it through the same
`processTextTurn` path. The agent responds with text. Voice-only agents can optionally
reply with both a voice note and a text transcript.

### Limitations vs real-time
- Turn latency: 3–8 seconds (download + STT + LLM + TTS + upload) vs 400–600ms real-time
- No barge-in — conversation is sequential
- Meta's 24-hour messaging window applies (standard WhatsApp Business rule)
- Rate limits: 1,000 messages/day on free tier, scales with Meta Business verification

### Milestones
1. Meta app registration + webhook verification endpoint — 1 day
2. `WhatsAppWebhookController` + audio download from CDN — 1 day
3. OGG Opus conversion (ffmpeg wrapper or jave2) — 1 day
4. `WhatsAppMessageSender` — 1 day
5. Dashboard: WhatsApp number connect flow + conversation view — 2 days
6. End-to-end test in Swahili, French, Arabic, English — 1 day

**Total estimated effort: 1 week**

---

## Phase 3 — Telnyx Telephony (Real Phone Numbers)

### Why Telnyx over alternatives

| Provider | DRC +243 | KE/NG/ZA | WebSocket streams | Account from DRC | Migration effort |
|---|---|---|---|---|---|
| Twilio | No | Yes | Yes (reference impl) | No | None |
| SignalWire | No | Very limited | Yes (Twilio-compat) | Yes | Low |
| Africa's Talking | Unreliable | Yes | Different protocol | Yes | High |
| **Telnyx** | **Yes (Oct 2024)** | **Yes** | **Yes (different framing)** | **Yes** | **Medium** |

Telnyx added DRC (+243) numbers in October 2024. They cover KE, NG, ZA, GH, TZ, UG, RW,
CM, ET and more. Account registration is open to DRC-based businesses.

Additional Telnyx advantages:
- **L16 codec** (16kHz PCM natively) eliminates the mulaw transcoding step, reducing
  latency by ~20ms per hop
- **$0.002/min** vs Twilio's $0.014/min — 7x cheaper at scale
- **TeXML** is Twilio-compatible XML — `buildMediaStreamTwiMl()` needs minimal changes
- **Programmatic number provisioning API** — same `provisionNumber()` pattern already
  architected in `TelephonyProvider`

### Protocol differences from Twilio (what needs adapting)

| Aspect | Twilio | Telnyx |
|---|---|---|
| Inbound WebSocket audio | JSON + base64 mulaw | JSON + base64 mulaw (same) |
| Outbound audio to caller | JSON wrapper with `streamSid` | JSON `media.payload` without `streamSid` |
| Mark/clear frames | JSON `event: mark/clear` | JSON `event: mark/clear` without `streamSid` |
| DTMF | Inline WebSocket event | Inline WebSocket `dtmf` event |
| Sequence guarantee | Yes | No — must reorder |
| Webhook signature header | `X-Twilio-Signature` | `telnyx-signature-ed25519` (Ed25519, not HMAC-SHA1) |

The biggest change is the provider-specific JSON envelope and codec. Telnyx bidirectional
streaming accepts base64 RTP payload data in `media.payload` without Twilio's `streamSid`.
`DefaultTwilioMediaStreamService` therefore selects a provider-specific frame factory and
uses the L16 conversion path for Telnyx sessions.

### Signature validation
Telnyx uses **Ed25519** public-key signatures, not HMAC-SHA1. The existing
`WebhookSignatureValidator` HMAC logic does not apply. A separate
`TelnyxSignatureValidator` using `java.security.Signature` with the Ed25519 public key
from the Telnyx portal is required.

### Implementation plan
1. `TelnyxTelephonyProvider` implementing `TelephonyProvider` — TeXML generation + REST
   number provisioning via `https://api.telnyx.com/v2/`
2. `TelnyxSignatureValidator` — Ed25519 verification
3. `TelnyxWebhookController` at `/webhooks/telnyx/*`
4. `TelnyxMediaFrameFactory` — Telnyx JSON media, mark, and clear frames
5. `TelnyxMediaSession` variant inside `DefaultTwilioMediaStreamService` (or a parallel
   `TelnyxMediaStreamService`)
6. L16 codec path in `AudioCodecConverter` — skip the mulaw step when Telnyx supplies L16
7. `application.yml`: `sauti.telnyx.*` config section
8. Dashboard: number provisioning UI that calls
   `POST /api/v1/agents/{id}/provision-number` (already planned in `AgentController`)

### Milestones
1. Telnyx account, portal setup, test number in Kenya — 1 day
2. TeXML generation + webhook controller + number provisioning — 2 days
3. Ed25519 signature validator — 1 day
4. `TelnyxMediaSession` (raw binary outbound + event mapping) — 3 days
5. L16 codec path — 1 day
6. End-to-end test with real DRC/KE number — 2 days

**Total estimated effort: 2 weeks**

---

## Phase 4 — SIP Trunking for Uncovered Markets (Future)

For any country not covered by Telnyx (or where cheaper local rates matter), add a
FreeSWITCH SIP bridge that makes Sauti carrier-agnostic:

```
Local carrier (any country) → SIP → FreeSWITCH → WebSocket → Sauti backend
```

FreeSWITCH converts the inbound SIP call to a Telnyx-compatible (or Twilio-compatible)
WebSocket media stream. Sauti sees no difference. Each new country = one SIP trunk
agreement with the local operator, no Sauti code changes.

Target markets for direct SIP agreements: Airtel DRC, Vodacom DRC, Orange DRC,
MTN Cameroon, Safaricom (fallback to Telnyx KE).

---

## Summary: Launch sequence

```
Month 1-2:   WebRTC widget  →  any business with a website goes live immediately
Month 2-3:   WhatsApp       →  captures the dominant African consumer channel
Month 3-5:   Telnyx         →  real dial-in numbers in DRC, KE, NG, ZA and 10+ more
Month 6+:    SIP trunks     →  fills remaining coverage gaps country by country
```

Each phase adds a channel without breaking the others. The STT → LLM → TTS core
remains unchanged across all channels.

---

## Environment variables summary

### Phase 1 — WebRTC
```
TURN_SERVER_URL=turns:your-server.com:5349
TURN_USERNAME=sauti
TURN_CREDENTIAL=secret
```

### Phase 2 — WhatsApp
```
WHATSAPP_ACCESS_TOKEN=...
WHATSAPP_PHONE_NUMBER_ID=...
WHATSAPP_VERIFY_TOKEN=...   # for Meta webhook verification
```

### Phase 3 — Telnyx
```
SAUTI_TELEPHONY_PROVIDER=telnyx
TELNYX_API_KEY=...
TELNYX_PUBLIC_KEY=...       # Ed25519 public key for webhook verification
TELNYX_CONNECTION_ID=...    # Call Control application / connection
TELNYX_MEDIA_WEBSOCKET_BASE_URL=wss://your-backend.com/ws/telnyx/media
TELNYX_REQUIREMENT_GROUP_ID= # optional, required for regulated number types
PUBLIC_BASE_URL=https://your-backend.com
```

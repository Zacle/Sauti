# Sauti — Production Readiness Plan
### From Static CRUD to a Real AI Voice-Agent Booking Platform

> Generated: 2026-06-23  
> Reference apps: Retell AI, VAPI, Bland AI  
> Target: Sub-800ms round-trip latency, real bookings, real AI

---

## 1. Honest Assessment of the Current State

The current implementation is a **CRUD application with a voice wrapper**. It is not a voice AI system in any meaningful sense. Here is why:

| Component | What exists | What is needed |
|-----------|-------------|----------------|
| Speech-to-Text | `FakeStreamingSttProvider` returns a hardcoded string | Deepgram Nova-3 streaming over WebSocket |
| Language Model | `FakeStreamingLlmProvider` returns a hardcoded reply | Claude / GPT-4o with streaming + tool calling |
| Text-to-Speech | `FakeStreamingTtsProvider` returns empty bytes | ElevenLabs Flash v2.5 streaming WebSocket |
| Audio pipeline | JSON passed as audio bytes (BUG-01); plain text sent back (BUG-02) | Correct Twilio Media Streams parsing and mu-law encoding |
| Booking | Row inserted into a `bookings` table | Availability checked against real calendar; event created via API |
| Calendar | None — custom table only | Google Calendar, Calendly, or Cal.com OAuth per tenant |
| AI decision-making | Keyword matching (`"speak to a human"`) | LLM function calling with tools: `check_availability`, `book_slot`, `transfer`, `end_call` |
| Conversation state | Flat concatenated text string per call | Structured Redis-backed message array per live call |
| Interruption handling | None | Voice Activity Detection + barge-in pipeline cancellation |
| Twilio integration | Fake number provisioning; no signature validation | Real account, purchased numbers, HMAC signature check |

**The gap is not configuration — it is architecture.** Without real STT the agent cannot hear. Without LLM tool use the agent cannot act. Without calendar integration the bookings exist only inside Sauti's own database, invisible to the business owner.

---

## 2. The 8 Pillars of Production Readiness

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Pillar 1: Correct Twilio Media Streams Protocol                        │
│  Pillar 2: Real-Time Streaming Audio Pipeline (STT → LLM → TTS)        │
│  Pillar 3: LLM Tool Use — the AI Brain                                  │
│  Pillar 4: Dynamic Agent Tools & External System Integration             │
│  Pillar 5: Redis-Backed Conversation State                              │
│  Pillar 6: Voice Activity Detection + Barge-In Handling                 │
│  Pillar 7: Operational Dashboard (Live Monitoring + Booking Calendar)   │
│  Pillar 8: Production Infrastructure (Twilio, Docker, CI/CD, Observability) │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Pillar 1 — Correct Twilio Media Streams Protocol

### Why it matters
Every subsequent pillar depends on this being correct. Right now the system
passes raw JSON as audio bytes to the fake STT, and sends plain text back to
Twilio. A real STT provider will reject garbage input; Twilio will silently
ignore non-conforming responses. The caller hears nothing.

### Twilio Media Streams framing (exact protocol)

Twilio sends **text WebSocket frames** containing JSON. The sequence of events
for one inbound call is:

```
1. connected  → one-time, no audio
2. start      → metadata (accountSid, streamSid, callSid, mediaFormat)
3. media      → repeated, contains base64 mu-law audio (8 kHz, mono)
4. stop       → call ended, no more audio
```

**Inbound media frame (Twilio → backend):**
```json
{
  "event": "media",
  "sequenceNumber": "3",
  "streamSid": "MZ...",
  "media": {
    "track": "inbound",
    "chunk": "1",
    "timestamp": "5",
    "payload": "<base64-encoded-mulaw-8kHz-audio>"
  }
}
```

**Outbound audio frame (backend → Twilio):**
```json
{
  "event": "media",
  "streamSid": "MZ...",
  "media": {
    "payload": "<base64-encoded-mulaw-8kHz-audio>"
  }
}
```

**Mark frame (synchronisation — know when audio finishes playing):**
```json
{ "event": "mark", "streamSid": "MZ...", "mark": { "name": "turn-3-end" } }
```

Twilio sends a reciprocal mark event back when playback reaches that point.
This is used to detect when the agent has finished speaking (needed for
barge-in logic).

**Clear frame (stop Twilio's audio buffer — used during barge-in):**
```json
{ "event": "clear", "streamSid": "MZ..." }
```

### Audio encoding

Twilio sends and accepts **mu-law (G.711 μ-law) at 8 kHz, mono**.
All internal processing (Deepgram, ElevenLabs) works at **16 kHz linear PCM**.
Two conversions are required on every turn:

```
Twilio mu-law 8kHz → PCM 16kHz → Deepgram
ElevenLabs PCM 16kHz → mu-law 8kHz → Twilio
```

Use a lightweight Java library (`javax.sound.sampled` or `com.github.wendykierp:JTransforms`)
for the codec conversions, or delegate to a small native FFmpeg subprocess.

### TwiML to initiate streaming

The `/webhooks/twilio/voice` endpoint must return TwiML that instructs Twilio
to open a WebSocket. The current implementation is partially correct but
must include the stream URL explicitly:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Connect>
    <Stream url="wss://your-domain.com/ws/twilio/media/{callSid}">
      <Parameter name="agentId" value="{agentId}"/>
    </Stream>
  </Connect>
</Response>
```

The base URL must be HTTPS/WSS reachable from Twilio's servers (not localhost).

### Implementation steps

1. Inject `ObjectMapper` into `TwilioMediaWebSocketHandler`.
2. On `connected` → log and no-op.
3. On `start` → extract and store `streamSid`, `callSid`, `mediaFormat` in `WebSocketSession` attributes. Look up the `Call` record. Create a `CallSession` in Redis (Pillar 5).
4. On `media` → Base64-decode `media.payload` → convert mu-law 8kHz bytes to PCM 16kHz bytes → push raw PCM bytes into the `AudioAccumulationBuffer` for this call (Pillar 6).
5. On `stop` → flush any pending audio, mark call complete, archive session.
6. Sending audio back: Base64-encode mu-law bytes and wrap in the outbound JSON frame above. Send a `mark` frame after each agent turn so barge-in logic knows when playback finished.
7. Remove dead `handleBinaryMessage` override — Twilio never sends binary frames.

---

## Pillar 2 — Real-Time Streaming Audio Pipeline

### End-to-end data flow

```
Twilio mu-law chunks (every ~20ms)
        │
        ▼
AudioAccumulationBuffer
  (accumulates until VAD detects end-of-utterance)
        │
        ▼
mu-law → PCM 16kHz converter
        │
        ▼
Deepgram Nova-3 WebSocket (streaming STT)
  ┌─ partial transcripts ("is_final": false) → barge-in check only
  └─ final transcript ("speech_final": true or UtteranceEnd event)
        │
        ▼
ConversationOrchestrator.handleUserUtterance(transcript)
  ├─ add user message to CallSession history
  ├─ call streaming LLM with tool definitions
  │    ├─ LLM emits text tokens → sentence buffer
  │    └─ LLM emits tool calls → tool executor (Pillar 3)
  │
  ▼
SentenceChunker (split first complete sentence from streaming tokens)
        │
        ▼
ElevenLabs Flash v2.5 WebSocket (streaming TTS)
  (text tokens forwarded as they arrive; audio starts before LLM finishes)
        │
        ▼
PCM 16kHz → mu-law 8kHz converter
        │
        ▼
Base64-encode → Twilio media JSON frame → WebSocket → caller hears agent
```

### Why streaming matters for latency

A non-streaming (sequential) pipeline takes:
- STT: wait for full utterance → 500ms
- LLM: wait for full response → 800ms
- TTS: wait for full audio → 400ms
- **Total: ~1700ms** — Feels broken to the caller

A streaming pipeline overlaps all three stages:
- STT streams partials; sends final at utterance end → 200ms
- LLM streams first sentence tokens → 300ms to first words
- TTS starts on first sentence before LLM finishes response → 100ms
- **Total time-to-first-audio: ~400ms** — Feels near-human

The target is < 800ms total round-trip. Retell AI achieves ~600ms.

### Deepgram Nova-3 integration

**Connection:** One persistent WebSocket per call to
`wss://api.deepgram.com/v1/listen?model=nova-3&encoding=linear16&sample_rate=16000
&interim_results=true&utterance_end_ms=1000&vad_events=true&endpointing=300`

**Sending audio:** Raw PCM bytes as binary WebSocket frames, pushed continuously
as they arrive from Twilio.

**Receiving transcripts:** Parse JSON frames:
- `"is_final": false` → partial transcript for barge-in detection only
- `"speech_final": true` → end of utterance detected; trigger LLM turn
- `"type": "UtteranceEnd"` → fallback end-of-utterance detection

**Key design:** One `DeepgramSession` object per active call, managing its own
WebSocket connection lifecycle. Opened when the Twilio WebSocket starts;
closed when call ends.

### ElevenLabs Flash v2.5 TTS integration

**Connection:** Persistent WebSocket to
`wss://api.elevenlabs.io/v1/text-to-speech/{voiceId}/stream-input?model_id=eleven_flash_v2_5`

**Sending text:** As LLM tokens arrive, forward them directly to the ElevenLabs
WebSocket. ElevenLabs streams audio back immediately upon receiving enough text.

```json
// Send each chunk as LLM tokens arrive:
{ "text": "I can help you with that. ", "flush": false }

// At end of agent turn, flush remaining buffer:
{ "text": "", "flush": true }
```

**Receiving audio:** Binary WebSocket frames (PCM 16kHz). Convert to mu-law
8kHz and send to Twilio.

**Latency:** ~75ms model inference, ~288ms time-to-first-byte from ElevenLabs
servers in North America/Europe.

### Audio format conversion

Create an `AudioCodecConverter` service:

```
MulawDecoder:  byte[] mulawBytes (8kHz) → short[] pcmSamples (8kHz)
Resampler:     short[] samples (8kHz) → short[] samples (16kHz)  [linear interpolation]
ShortToBytes:  short[] pcmSamples → byte[] pcmBytes (little-endian)

Reverse path for TTS output:
BytesToShort → Downsample 16kHz→8kHz → MulawEncoder → byte[] for Twilio
```

Java's `javax.sound.sampled` handles basic PCM resampling. Mu-law codec can
be implemented directly (<60 lines) following the ITU-T G.711 standard, or
use the `jsyn` or `tritonus` library.

### SentenceChunker

The LLM streams tokens. TTS should start as soon as the first complete
sentence arrives, not after the full response:

```java
class SentenceChunker {
    // Accumulates LLM tokens
    // Emits a chunk when it sees: ". " "? " "! " "\n" or buffer > 120 chars
    // Sends each chunk to ElevenLabs immediately
    // Sends flush=true when LLM stream ends
}
```

This is the single biggest latency win: instead of waiting for a 200-word
response to complete, TTS starts on the first 10-15 words.

---

## Pillar 3 — LLM Tool Use: The AI Brain

### Why this is the most critical pillar

The current system has no AI intelligence at all. The fake LLM returns a
hardcoded string. Even if replaced with a real LLM that generates good
sentences, the agent still cannot **do anything** — it cannot check if
3pm Tuesday is available, cannot create a booking, cannot look up the
caller's history. It is simply a chatbot that talks.

Production voice agent systems treat the LLM as an **orchestrator** —
it decides what to say AND what actions to take. Actions are expressed as
**function/tool calls** that the backend executes and returns results for.

### Tool definitions

Define these tools in the system prompt and LLM API call:

```
check_availability(date, time_preference, duration_minutes, timezone)
  → returns list of open slots: [{start, end, displayString}]

book_slot(date, time, caller_name, caller_phone, service_type, agent_id)
  → returns {bookingId, confirmationCode, calendarEventUrl}

reschedule_booking(booking_id, new_date, new_time)
  → returns {success, newSlot} or {error, alternatives}

cancel_booking(booking_id, reason)
  → returns {success, cancellationRef}

get_caller_history(caller_phone)
  → returns {previousBookings, lastVisit, preferences}

transfer_to_human(reason)
  → signals pipeline to execute Twilio <Dial> TwiML

send_confirmation_sms(phone, message)
  → returns {sent: true}

end_call(summary)
  → signals pipeline to close call gracefully
```

### The LLM conversation loop (ConversationOrchestrator)

This class replaces the current `CallPipelineService.processTurn()` and is the
heart of the system:

```
LOOP until end_call or max_turns:

  1. Build messages array from CallSession.history
     [system_prompt, ...conversation_so_far, {role:user, content:transcript}]

  2. Call LLM with tool definitions (streaming)

  3. For each streamed token:
     → append to SentenceChunker (starts TTS on first sentence)
     → append to accumulated response buffer

  4. If LLM emits a tool_call:
     a. Pause TTS (say "one moment..." if caller is waiting)
     b. Execute the tool synchronously (calendar API, DB lookup)
     c. Append tool_result to messages
     d. Loop back to step 2 (LLM continues responding with tool result)

  5. When LLM stream ends:
     → flush TTS
     → save complete turn to CallSession.history
     → wait for barge-in or next user utterance

  6. Check: if LLM called end_call or transfer_to_human → exit loop
```

### System prompt structure

The system prompt is the agent's "instruction manual". It must be structured,
not free-form, to produce reliable tool calling behavior:

```
You are {agentName}, an AI assistant for {businessName}.

OBJECTIVE: Help callers book, reschedule, or cancel appointments. 
Collect: caller name, service type, preferred date and time.
Always confirm the details before booking. Read back date and time 
explicitly: "Thursday the 15th at 3pm — is that correct?"

TOOLS: You have access to check_availability, book_slot, etc.
Always check availability before promising a slot.
Never make up available times.

LANGUAGE: Respond in {language}. If the caller switches language, 
switch with them and update your responses accordingly.

BUSINESS HOURS: {operatingHours}
SERVICES OFFERED: {serviceList}
DURATION PER SESSION: {sessionDurationMinutes} minutes
HUMAN TRANSFER NUMBER: {humanTransferNumber}

ESCALATION: Transfer to a human if:
- The caller is upset or frustrated
- The caller explicitly requests a human
- You cannot resolve the issue after 2 attempts
```

### Structured information gathering

The LLM must be guided to collect required fields before booking:

1. **Name** — "May I have your name?"
2. **Service type** — "What service are you coming in for?"
3. **Date preference** — "Do you have a preferred date?"
4. **Time preference** — "Morning or afternoon?"
5. **Availability check** → tool call → present options
6. **Confirmation** — "I have Thursday the 15th at 2pm for a General Consultation. Shall I confirm that?"
7. **Booking** → tool call → confirmation number

The LLM handles the natural language variations, interruptions, and people
who jump straight to "book me for Tuesday at 3" while the backend handles
whether Tuesday at 3 is actually available.

### LLM provider choice

**Claude claude-sonnet-4-6 (Anthropic)** — Recommended for this project:
- Superior instruction following for structured tool-calling workflows
- Best-in-class function calling reliability
- Streaming API available
- 200k context window for long conversation histories

**GPT-4o (OpenAI)** — Alternative; slightly faster TTFT in some regions.

**Gemini 2.0 Flash (Google)** — Fastest TTFT (~200ms); good for latency-critical scenarios.

Do NOT use a single provider exclusively. Build a `LlmProvider` interface
(already exists in the codebase as `StreamingLlmProvider`) and implement
adapters for at least two providers for failover.

---

## Pillar 4 — Dynamic Agent Tools & External System Integration

### Why the current tool system is unshippable

`LlmToolCatalog` returns a hardcoded list of six tools shared across every
agent in the platform. This means:

- Every agent has booking tools even if the business never does appointments.
- A hair salon and a law firm send identical tool definitions to the LLM.
- A business that uses its own database instead of Google Calendar cannot plug
  in its own endpoints.
- There is no way to add a custom tool from the dashboard without redeploying.

Pillar 4 introduces **per-agent configurable tools**. Every tool is a row in
the `agent_tools` table. The dashboard is where the business configures them.
The LLM receives only the tools attached to the specific agent taking the call.

---

### 4.1 — The `agent_tools` Table

This is the central data structure for the entire pillar.

```sql
CREATE TABLE agent_tools (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id          UUID         NOT NULL REFERENCES agents(id) ON DELETE CASCADE,

    -- What the LLM sees
    tool_name         VARCHAR(64)  NOT NULL,
    tool_description  TEXT         NOT NULL,
    parameters_schema JSONB        NOT NULL DEFAULT '{}',

    -- How Sauti fulfills the call
    fulfillment_type  VARCHAR(32)  NOT NULL,
    -- allowed values: sauti_calendar | sauti_sms | webhook | noop

    -- For fulfillment_type = webhook
    webhook_url       TEXT,
    webhook_method    VARCHAR(8)   DEFAULT 'POST',
    auth_type         VARCHAR(16)  DEFAULT 'none',
    -- allowed values: none | bearer | api_key | hmac_sha256
    auth_credential   TEXT,        -- encrypted at rest (AES-256-GCM, see §4.5)
    auth_header_name  VARCHAR(64), -- for auth_type = api_key (e.g. "X-Api-Key")

    -- For fulfillment_type = sauti_calendar
    calendar_type     VARCHAR(32), -- google | calendly | calcom | noop_calendar
    calendar_credential_id UUID REFERENCES calendar_credentials(id),

    -- Shared config
    is_active         BOOLEAN      NOT NULL DEFAULT true,
    display_order     INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_agent_tool_name UNIQUE (agent_id, tool_name)
);

CREATE INDEX idx_agent_tools_agent_id ON agent_tools(agent_id) WHERE is_active = true;
```

**`parameters_schema` column** holds a complete JSON Schema `properties`
object with `type`, `description`, and — critically — the `required` array.
This is what `LlmToolDefinition` is currently missing. Example value:

```json
{
  "type": "object",
  "properties": {
    "date": {
      "type": "string",
      "description": "Preferred date in yyyy-MM-dd format",
      "format": "date"
    },
    "duration_minutes": {
      "type": "integer",
      "description": "Appointment duration in minutes",
      "default": 60
    }
  },
  "required": ["date"]
}
```

**`calendar_credentials` table** (referenced above):

```sql
CREATE TABLE calendar_credentials (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    provider    VARCHAR(32) NOT NULL,  -- google | calendly | calcom
    access_token  TEXT,                -- encrypted
    refresh_token TEXT,                -- encrypted
    token_expiry  TIMESTAMPTZ,
    external_id   TEXT,               -- Google calendar ID / Calendly event type URI
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

### 4.2 — JPA Entity: `AgentTool`

```
package com.sauti.tool;

@Entity @Table(name = "agent_tools")
public class AgentTool extends Auditable {
    @Id UUID id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    Agent agent;

    String toolName;
    String toolDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    Map<String, Object> parametersSchema;   // the full JSON Schema object

    String fulfillmentType;   // sauti_calendar | sauti_sms | webhook | noop
    String webhookUrl;
    String webhookMethod;
    String authType;
    String authCredential;   // encrypted
    String authHeaderName;
    String calendarType;
    UUID   calendarCredentialId;
    boolean isActive;
    int displayOrder;
}
```

Add the complementary `AgentToolRepository`:

```
public interface AgentToolRepository extends JpaRepository<AgentTool, UUID> {
    List<AgentTool> findByAgent_IdAndIsActiveTrueOrderByDisplayOrderAsc(UUID agentId);
    Optional<AgentTool> findByIdAndAgent_TenantId(UUID toolId, UUID tenantId);
}
```

---

### 4.3 — Fix `LlmToolDefinition` to Emit Real JSON Schema

The current `LlmToolDefinition` record stores flat parameter fields. Real LLM
APIs (Claude, GPT-4o) accept the OpenAI-compatible function calling format, which
requires a proper JSON Schema object:

```json
{
  "name": "check_availability",
  "description": "...",
  "input_schema": {
    "type": "object",
    "properties": {
      "date": { "type": "string", "description": "...", "format": "date" }
    },
    "required": ["date"]
  }
}
```

**Replace** the current `LlmToolDefinition` record with one that stores the
schema as a raw `Map<String, Object>` already in the correct shape:

```java
// com.sauti.llm.LlmToolDefinition
public record LlmToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema   // full JSON Schema, passed as-is to LLM
) {
    public static LlmToolDefinition from(AgentTool tool) {
        return new LlmToolDefinition(
                tool.getToolName(),
                tool.getToolDescription(),
                tool.getParametersSchema()
        );
    }
}
```

Update `LlmToolTurnContext` and both LLM provider implementations to use
`definition.inputSchema()` instead of the old parameter list when serialising
the tool definitions for the API call.

---

### 4.4 — `AgentToolLoader` Replaces `LlmToolCatalog`

`LlmToolCatalog` is a Spring `@Component` with hardcoded tools. Replace it with
`AgentToolLoader`, which loads the tools for a specific agent from the database
at the beginning of each call:

```
package com.sauti.tool;

@Service
public class AgentToolLoader {
    private final AgentToolRepository agentToolRepository;

    public List<LlmToolDefinition> loadForAgent(UUID agentId) {
        return agentToolRepository
                .findByAgent_IdAndIsActiveTrueOrderByDisplayOrderAsc(agentId)
                .stream()
                .map(LlmToolDefinition::from)
                .toList();
    }
}
```

`ConversationOrchestrator` currently receives `LlmToolCatalog` in its
constructor. Change that dependency to `AgentToolLoader` and call
`agentToolLoader.loadForAgent(call.getAgent().getId())` when building the
`LlmToolTurnContext`. The LLM then receives only the tools that belong to
this call's agent.

**One-time data migration:** The six tools currently hardcoded in
`LlmToolCatalog` become seed data. On first startup, an `ApplicationRunner`
can insert default tools for every existing agent that has `bookingEnabled =
true`. For new agents, the dashboard creates them explicitly. Do not rely on
the hardcoded catalog at runtime — it can be deleted once migration runs.

---

### 4.5 — `ToolFulfillmentRouter` Replaces the Switch in `LlmToolExecutor`

`LlmToolExecutor` uses a `switch(toolCall.name())` over hardcoded names. This
fails as soon as a business configures a tool named `reschedule_appointment` or
`lookup_order_status`. The name is now meaningless to the router — only the
`fulfillment_type` column matters.

**New structure:**

```
package com.sauti.tool;

@Service
public class ToolFulfillmentRouter {
    private final AgentToolRepository agentToolRepository;
    private final Map<String, ToolFulfillment> fulfillments;

    public ToolFulfillmentRouter(
            AgentToolRepository agentToolRepository,
            SautiCalendarFulfillment calendarFulfillment,
            WebhookToolFulfillment webhookFulfillment,
            SautiSmsFulfillment smsFulfillment,
            NoopFulfillment noopFulfillment
    ) {
        this.agentToolRepository = agentToolRepository;
        this.fulfillments = Map.of(
                "sauti_calendar", calendarFulfillment,
                "webhook",        webhookFulfillment,
                "sauti_sms",      smsFulfillment,
                "noop",           noopFulfillment
        );
    }

    public LlmToolResult route(Call call, LlmToolCall toolCall) {
        var tool = agentToolRepository
                .findByIdAndAgent_TenantId(/* lookup by name within agent */ ...)
                // or find by (agentId, toolName):
                // findByAgent_IdAndToolName(call.getAgent().getId(), toolCall.name())
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolCall.name()));

        var fulfillment = fulfillments.get(tool.getFulfillmentType());
        if (fulfillment == null) {
            return LlmToolResult.error(toolCall, "Unknown fulfillment type: " + tool.getFulfillmentType());
        }
        return fulfillment.execute(call, tool, toolCall);
    }
}
```

Add a convenience finder to `AgentToolRepository`:

```
Optional<AgentTool> findByAgent_IdAndToolNameAndIsActiveTrue(UUID agentId, String toolName);
```

Each fulfillment type is its own class implementing:

```java
public interface ToolFulfillment {
    LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall);
}
```

---

### 4.6 — Fulfillment Implementations

#### `SautiCalendarFulfillment`

Handles the two calendar-related operations: `check_availability` and
`book_slot`. The logic currently in `LlmToolExecutor.checkAvailability()` and
`bookSlot()` moves here, but driven by the `calendarType` field on the tool row.

```
@Component
public class SautiCalendarFulfillment implements ToolFulfillment {
    private final CalendarProviderFactory calendarProviderFactory;
    private final BookingService bookingService;

    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        var provider = calendarProviderFactory.forTool(toolConfig);
        return switch (toolCall.name()) {
            case "check_availability" -> checkAvailability(call, toolConfig, toolCall, provider);
            case "book_slot"          -> bookSlot(call, toolConfig, toolCall, provider);
            default -> LlmToolResult.error(toolCall, "Unrecognised calendar sub-action");
        };
    }
}
```

`CalendarProviderFactory` creates the right provider based on
`toolConfig.getCalendarType()`: `google`, `calendly`, `calcom`, or
`noop_calendar`. Each provider still implements the existing `CalendarProvider`
interface (which already exists and is wired up correctly for the webhook case).

For **Google Calendar** specifically, the factory loads the
`CalendarCredential` record and constructs a `GoogleCalendarProvider` that
uses the stored OAuth2 access/refresh tokens. Token refresh must happen
transparently before any API call:

```
1. Load CalendarCredential for this tool
2. If access_token expiry < now + 60s → call Google token endpoint with refresh_token
3. Update stored access_token and token_expiry
4. Proceed with the API call
```

For **Calendly**:

```
Check availability:
  GET https://api.calendly.com/event_type_available_times
    ?event_type={credential.externalId}
    &start_time={date}T00:00:00Z
    &end_time={date}T23:59:59Z
  Authorization: Bearer {credential.accessToken}

Book slot:
  POST https://api.calendly.com/scheduling_links
  { max_event_count: 1, owner: {eventTypeUri}, owner_type: "EventType" }
  → Returns a booking_url; then use the one-time link to confirm
```

**Free slot computation for Google Calendar** (non-trivial — implement carefully):

```
1. Call freebusy.query for the requested date, full working day window
2. Response: list of {start, end} busy periods
3. Compute candidate slots: every 30-min boundary within agent.operatingHours
4. Filter: slot.end ≤ agent.operatingHoursEnd - bufferMinutes
5. Filter: !overlaps(slot, any busy period)
6. Filter: slot.start ≥ now + 1 hour (same-day cutoff)
7. Return max 6 slots to keep LLM context size manageable
```

#### `WebhookToolFulfillment`

This is the generic bridge for businesses with custom databases, CRMs, or
proprietary systems. Sauti POSTs a standardised JSON payload to the business's
endpoint and expects a structured JSON response.

```
@Component
public class WebhookToolFulfillment implements ToolFulfillment {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CredentialEncryption credentialEncryption;

    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        var body = buildRequestBody(call, toolConfig, toolCall);
        var request = buildHttpRequest(toolConfig, body);
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(toolCall, response);
    }
}
```

**Request body contract** (business receives this):

```json
{
  "toolName": "lookup_order_status",
  "callId": "uuid",
  "agentId": "uuid",
  "tenantId": "uuid",
  "callerPhone": "+221771234567",
  "arguments": {
    "order_number": "ORD-12345"
  },
  "timestamp": "2026-06-24T10:30:00Z",
  "signature": "sha256=<hmac>"
}
```

**Signature computation** (same pattern as `WebhookCalendarProvider`):

```
X-Sauti-Timestamp: {epochSeconds}
X-Sauti-Signature: sha256={HMAC-SHA256(secret, epochSeconds + "." + bodyJson)}
```

The business verifies by recomputing HMAC with the shared secret stored in
`toolConfig.authCredential` (decrypted at runtime). Reject requests where
`|now - X-Sauti-Timestamp| > 300` seconds to prevent replay.

**Response contract** (business must return this):

```json
{
  "success": true,
  "data": {
    "status": "shipped",
    "estimatedDelivery": "2026-06-27"
  },
  "displayMessage": "Your order has shipped and arrives Thursday.",
  "errorMessage": null
}
```

Rules:
- Always HTTP 200 (Sauti distinguishes success from failure via `success` flag).
- `data` is passed back to the LLM as the tool result.
- `displayMessage` is an optional hint that can be injected into the LLM
  system prompt or used as a direct TTS response when confidence is high.
- A non-200 response is treated as a transient error; Sauti tells the caller
  "I'm having trouble reaching that system right now."

**Response parsing:**

```
if (response.statusCode() != 200) → LlmToolResult.error("Webhook unreachable")
if (!parsed.success)              → LlmToolResult.error(parsed.errorMessage)
else                              → LlmToolResult.success(toolCall, parsed.data)
```

**Timeout:** Use `HttpRequest.Builder.timeout(Duration.ofSeconds(5))`. A
webhook that takes more than 5 seconds will block the voice turn. Document
this limit clearly to business integrators.

#### `SautiSmsFulfillment`

Fulfills `send_confirmation_sms`. Sends an SMS via the Twilio Messages API,
not a stub.

```
@Component
public class SautiSmsFulfillment implements ToolFulfillment {
    // POST https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Messages.json
    // Body: From={twilioNumber}&To={phone}&Body={message}
    // Auth: Basic accountSid:authToken

    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        var phone   = requiredArg(toolCall, "phone");
        var message = requiredArg(toolCall, "message");
        // Use the same Twilio credentials as the voice integration
        // Store them in application properties, not in agent_tools
        var messageSid = twilioSmsClient.send(call.getAgent().getTwilioPhoneNumber(), phone, message);
        return LlmToolResult.success(toolCall, Map.of("sent", true, "messageSid", messageSid));
    }
}
```

#### `NoopFulfillment`

For flow-control tools (`end_call`, `transfer_to_human`) and testing. Returns
a canned response so the LLM gets a result, then `ConversationOrchestrator`
inspects the tool name to take the real action (close WebSocket, initiate
Twilio transfer).

```
@Component
public class NoopFulfillment implements ToolFulfillment {
    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        return LlmToolResult.success(toolCall, Map.of("acknowledged", true));
    }
}
```

**Terminal tool handling in `ConversationOrchestrator`:** After routing a tool
call through `ToolFulfillmentRouter`, the orchestrator must check if the tool
name is `end_call` or `transfer_to_human` and set a flag in the result or
session. The media stream service then closes the WebSocket or initiates a
Twilio `<Dial>` transfer after TTS finishes. This is the fix for the terminal
outcome bug identified in the Pillar 3 review.

---

### 4.7 — Credential Encryption

`auth_credential` (for webhooks) and `access_token`/`refresh_token` (for
calendar OAuth) must never be stored in plaintext.

**Implementation: AES-256-GCM with a per-row IV:**

```
Stored value format: base64(IV [12 bytes] + ciphertext + auth tag [16 bytes])

Encrypt:
  key    = AES key loaded from application property "sauti.encryption.key"
           (32 bytes, base64-encoded, loaded from Vault or env variable)
  iv     = SecureRandom.generateSeed(12)
  cipher = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Encrypt, key, new GCMParameterSpec(128, iv))
  stored = base64(iv + cipher.doFinal(plaintext.getBytes(UTF_8)))

Decrypt:
  decoded  = Base64.decode(stored)
  iv       = decoded[0..11]
  cipher   = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Decrypt, key, new GCMParameterSpec(128, iv))
  plaintext = new String(cipher.doFinal(decoded[12..end]), UTF_8)
```

Create a `CredentialEncryption` Spring service that wraps these two operations.
Both `WebhookToolFulfillment` and `CalendarProviderFactory` inject it and
call `decrypt()` at runtime, never storing the plaintext in any field.

The AES key itself must come from:
- Local dev: `application-dev.yml` with a generated test key
- Production: environment variable `SAUTI_ENCRYPTION_KEY` injected by
  Kubernetes Secret or HashiCorp Vault

---

### 4.8 — REST API for Managing Agent Tools

Add a `AgentToolController` under `/api/agents/{agentId}/tools`:

```
GET    /api/agents/{agentId}/tools
       → List all tools for the agent (active and inactive)

POST   /api/agents/{agentId}/tools
       → Create a new tool (body: CreateAgentToolRequest)

GET    /api/agents/{agentId}/tools/{toolId}
       → Get a single tool

PATCH  /api/agents/{agentId}/tools/{toolId}
       → Update name, description, schema, fulfillment config, active status

DELETE /api/agents/{agentId}/tools/{toolId}
       → Soft-delete (set is_active = false) or hard-delete

POST   /api/agents/{agentId}/tools/reorder
       → Body: [{toolId, displayOrder}] — update display_order for the LLM
```

**`CreateAgentToolRequest` DTO:**

```json
{
  "toolName": "check_availability",
  "toolDescription": "Check available slots before confirming a time.",
  "parametersSchema": {
    "type": "object",
    "properties": {
      "date": { "type": "string", "description": "Date in yyyy-MM-dd", "format": "date" }
    },
    "required": ["date"]
  },
  "fulfillmentType": "sauti_calendar",
  "calendarType": "google",
  "calendarCredentialId": "uuid"
}
```

For `fulfillmentType = webhook`:

```json
{
  "fulfillmentType": "webhook",
  "webhookUrl": "https://my-crm.example.com/sauti/lookup",
  "webhookMethod": "POST",
  "authType": "hmac_sha256",
  "authCredential": "my-raw-secret"   ← encrypted before persist
}
```

**Security rules for the controller:**
- All routes require `@PreAuthorize` that checks the agent belongs to the
  caller's tenant. Tenants must never see or modify another tenant's tools.
- `authCredential` must never appear in GET responses. Return only
  `"authConfigured": true/false`.
- Validate `fulfillmentType` is one of the four allowed values.
- Validate `webhookUrl` is HTTPS. Block `localhost`, RFC-1918 ranges, and
  `169.254.0.0/16` to prevent SSRF.

---

### 4.9 — Agent Knowledge Base (Static Data Injection)

Some agents need access to information that never changes during a call:
price lists, service menus, operating hours, FAQs. This should not require
a tool call — it should be injected into the system prompt so the LLM knows
it up front.

**Add `knowledgeBase` column to the `agents` table:**

```sql
ALTER TABLE agents ADD COLUMN knowledge_base TEXT;
```

The dashboard provides a text area (or structured key-value editor) where
the business owner enters:

```
Services: Haircut (30min, 5000 FCFA), Color treatment (90min, 15000 FCFA)
Hours: Mon–Fri 09:00–18:00, Sat 09:00–14:00, closed Sunday
Location: 14 Avenue Bourguiba, Dakar
Parking: Free parking behind the building
```

**Injection in `ConversationOrchestrator`:**

When building the `LlmToolTurnContext` for the first turn of a call, if
`agent.getKnowledgeBase()` is not blank, append it to the system prompt:

```
[Your existing system prompt]

--- Business Information ---
{agent.knowledgeBase}
```

This costs zero extra API calls and ensures the LLM can answer "how much is a
haircut?" without calling a tool, keeping latency low.

**Size limit:** Cap `knowledge_base` at 4000 characters in the API validation
layer. Larger knowledge sets will need RAG (Pillar beyond scope), but 4000
characters covers 90% of small business use cases.

---

### 4.10 — Default Tool Seed for New Agents

When a new agent is created via `AgentService.create()`, the service should
call a `DefaultToolSeeder` that inserts a sensible starter set:

| Tool name              | Description                                        | Fulfillment        |
|------------------------|----------------------------------------------------|--------------------|
| `check_availability`   | Check available appointment slots                  | `sauti_calendar`   |
| `book_slot`            | Book an appointment                                | `sauti_calendar`   |
| `send_confirmation_sms`| Send a booking confirmation SMS to the caller      | `sauti_sms`        |
| `transfer_to_human`    | Transfer the call to a human agent                 | `noop`             |
| `end_call`             | End the call with an outcome summary               | `noop`             |

All five are inserted with `is_active = false` initially. The dashboard
prompts the owner to activate the ones they need and configure the calendar
credential before going live. This prevents the agent from trying to book
via a calendar that has not been connected yet.

---

### 4.11 — Summary of File Changes

| File / Location                                 | Change                                              |
|-------------------------------------------------|-----------------------------------------------------|
| `db/migration/V4__agent_tools.sql`              | Create `agent_tools`, `calendar_credentials` tables |
| `com.sauti.tool.AgentTool`                      | New JPA entity                                      |
| `com.sauti.tool.AgentToolRepository`            | New repository                                      |
| `com.sauti.tool.AgentToolService`               | CRUD + reorder + validation                         |
| `com.sauti.api.AgentToolController`             | REST API (CRUD on agent tools)                      |
| `com.sauti.tool.AgentToolLoader`                | Replaces `LlmToolCatalog`                           |
| `com.sauti.tool.ToolFulfillmentRouter`          | Replaces switch in `LlmToolExecutor`                |
| `com.sauti.tool.ToolFulfillment` (interface)    | New                                                 |
| `com.sauti.tool.SautiCalendarFulfillment`       | Replaces `LlmToolExecutor.checkAvailability/bookSlot` |
| `com.sauti.tool.WebhookToolFulfillment`         | Replaces `WebhookToolCallingLlmProvider` dispatch   |
| `com.sauti.tool.SautiSmsFulfillment`            | New (real Twilio SMS)                               |
| `com.sauti.tool.NoopFulfillment`                | New (for flow-control tools)                        |
| `com.sauti.tool.CalendarProviderFactory`        | New (creates right CalendarProvider by type)        |
| `com.sauti.calendar.GoogleCalendarProvider`     | New (Google Calendar OAuth2 + freebusy)             |
| `com.sauti.tool.CredentialEncryption`           | New (AES-256-GCM encrypt/decrypt)                   |
| `com.sauti.tool.DefaultToolSeeder`              | New (insert starter tools on agent create)          |
| `com.sauti.llm.LlmToolDefinition`              | Replace flat param list with `Map<String,Object> inputSchema` |
| `com.sauti.llm.ConversationOrchestrator`        | Inject `AgentToolLoader` and `ToolFulfillmentRouter`; handle terminal tools |
| `com.sauti.agent.Agent`                         | Add `knowledgeBase` field                           |

Files that become **dead code and should be deleted after migration:**
- `com.sauti.llm.LlmToolCatalog` (replaced by `AgentToolLoader`)
- `com.sauti.llm.LlmToolExecutor` (replaced by `ToolFulfillmentRouter`)

---

### 4.12 — Configuration Walkthrough (how a business sets up a tool)

**Scenario A — Booking via Google Calendar:**

1. Owner opens the dashboard → Agents → their agent → Tools tab.
2. Clicks "Activate" on the pre-seeded `check_availability` tool.
3. Clicks "Connect Calendar" → Google OAuth flow opens.
4. After consent, a `CalendarCredential` row is created with encrypted tokens.
5. Owner selects which Google Calendar to use (primary or a team calendar).
6. Owner sets working hours (e.g. Mon–Fri 09:00–18:00) and appointment duration (60min).
7. Clicks "Activate" on `book_slot`, links it to the same credential.
8. Optionally activates `send_confirmation_sms`.
9. Done. The agent now has three live tools and will use the real calendar.

**Scenario B — Custom CRM webhook (no calendar):**

1. Owner opens Tools tab → "Add custom tool".
2. Fills in:
   - Tool name: `lookup_client`
   - Description: "Look up a client account by their phone number."
   - Parameters (JSON Schema editor in UI):
     ```json
     { "type": "object", "properties": { "phone": {"type":"string"} }, "required": ["phone"] }
     ```
3. Fulfillment type: Webhook.
4. Webhook URL: `https://crm.mybusiness.com/sauti/lookup-client`
5. Auth type: HMAC-SHA256.
6. Secret: generates a random 32-byte hex secret, copies it to their CRM config.
7. Saves. The tool goes live immediately; the agent will call the CRM on every
   turn where the LLM decides to invoke `lookup_client`.

**Scenario C — Noop tool for custom escalation phrases:**

1. Owner creates a `transfer_to_vip_line` tool with:
   - Description: "Transfer caller to the VIP support line when they mention a
     gold membership or executive account."
   - Fulfillment: `noop`
2. In `ConversationOrchestrator`, `noop` tools with `transfer_` prefix trigger
   the real Twilio `<Dial>` redirect to a specific number configured on the agent.
3. The LLM learns from the description when to invoke it; the infrastructure
   handles the actual transfer.

---

## Pillar 5 — Redis-Backed Conversation State

### What must live in Redis (not the database)

During an active call, the following state must be accessible in milliseconds
and must survive backend restarts (to support horizontal scaling):

```json
{
  "callSid": "CA...",
  "streamSid": "MZ...",
  "agentId": "uuid",
  "tenantId": "uuid",
  "callerPhone": "+221771234567",
  "conversationHistory": [
    { "role": "system",    "content": "You are Amina..." },
    { "role": "assistant", "content": "Bonjour, comment puis-je vous aider?" },
    { "role": "user",      "content": "Je voudrais prendre rendez-vous." },
    { "role": "assistant", "content": "Je serais ravie de vous aider...",
      "tool_calls": [{"name": "check_availability", "args": {...}}] },
    { "role": "tool",      "content": "{'slots': [...]}" }
  ],
  "pendingBookingDraft": {
    "callerName": "Fatou",
    "serviceType": null,
    "preferredDate": "2026-06-25",
    "confirmedSlot": null
  },
  "agentSpeakingMarkName": "turn-4-end",
  "isSpeaking": true,
  "turnCount": 4,
  "startedAt": "2026-06-23T14:00:00Z",
  "lastActivityAt": "2026-06-23T14:03:21Z"
}
```

**Redis key:** `call:session:{callSid}` with TTL = 7200 seconds (2 hours)

### Operations on CallSession

```java
interface CallSessionStore {
    void create(String callSid, CallSession session);
    Optional<CallSession> get(String callSid);
    void appendUserMessage(String callSid, String transcript);
    void appendAssistantMessage(String callSid, String text, List<ToolCall> toolCalls);
    void appendToolResult(String callSid, String toolName, String result);
    void updatePendingBooking(String callSid, BookingDraft draft);
    void setSpeaking(String callSid, boolean speaking, String markName);
    void archive(String callSid); // persist to DB, then delete from Redis
}
```

### Why the flat transcript string must be replaced

`Call.transcript` is a single `"Caller: ...\nAgent: ...\n"` string.
It is used as `List.of(call.getTranscript())` when building LLM context —
a single element list containing one giant text blob. This is not how any
LLM API works. The API requires a structured array of `{role, content}` pairs.

The `CallSession.conversationHistory` in Redis replaces this.
At call end, the history is serialized to JSON and stored in
`Call.conversationJson` (new column) for replay and debugging.
The legacy `Call.transcript` column can be derived from it on read.

---

## Pillar 6 — Voice Activity Detection and Barge-In Handling

### The two hardest problems in voice AI

1. **Endpointing** — knowing when the caller has finished speaking
2. **Barge-in** — stopping the agent when the caller starts speaking while the agent is talking

Both require real-time audio analysis. Neither is addressed at all in the
current implementation.

### Endpointing (when to trigger LLM)

**Option A — Deepgram native (recommended for MVP):**
Use Deepgram's `utterance_end_ms=1000` and `endpointing=300` parameters.
Deepgram emits `UtteranceEnd` events when it detects a pause after speech.
This offloads VAD to Deepgram and simplifies the backend significantly.
Accuracy for telephone speech: ~92%.

**Option B — Server-side VAD:**
Run WebRTC VAD or Silero VAD on the backend.
WebRTC VAD: GMM-based, runs in <1ms, good for clean audio.
Silero VAD: DNN-based, runs in ~10ms, much better for telephone noise.
Required if you want latency below 300ms or need to detect pauses shorter
than Deepgram's minimum endpointing window.

**Implementation with Deepgram endpointing (Option A):**
1. Receive `media` frames from Twilio → decode base64 → convert to PCM
2. Forward PCM bytes as binary frames to Deepgram WebSocket continuously
3. Listen for Deepgram's `UtteranceEnd` event → trigger `handleUserUtterance()`
4. `Partial transcripts` (`is_final: false`) → check for barge-in keywords only

### Barge-in handling (when caller speaks while agent is talking)

**Why it is hard:** By the time VAD detects the caller is speaking, Twilio
has already buffered ~300-500ms of agent audio in its media buffer. Simply
stopping audio generation does not stop audio that is already playing.

**The four-step barge-in procedure:**

```
1. DETECT: Deepgram partial transcript arrives while isSpeaking == true
           OR VAD fires while isSpeaking == true

2. STOP TTS: Cancel the ElevenLabs WebSocket stream in progress
             (close and reopen the WebSocket for next turn)

3. CLEAR TWILIO BUFFER: Send {"event":"clear","streamSid":"MZ..."} to Twilio
   This drops audio already buffered at Twilio's edge — critical, otherwise
   the caller still hears the next 300-500ms of agent speech

4. UPDATE STATE: Mark the turn in CallSession as "interrupted"
                 The partial agent text that was spoken is preserved so
                 the LLM knows what the caller actually heard
```

**What NOT to do:**
- Do not cancel on every partial transcript (noise, backchannels trigger false positives)
- Do not barge-in if the partial transcript is < 150ms of audio
- Do not barge-in during the first 300ms of an agent turn (echo of agent voice)
- Use a confidence threshold: only barge-in if Deepgram confidence > 0.70

**Mark frame synchronisation:**
Send a Twilio `mark` frame at the end of each agent turn. Twilio echoes it
back when playback reaches that mark. Use this to update `isSpeaking = false`
accurately instead of estimating when audio finished playing.

---

## Pillar 7 — Operational Dashboard

### What the Flutter dashboard currently provides

Static widget-based views: create agent, list agents, list calls.
No real-time anything. No calendar. No booking management.

### What it must provide for an operator

#### 7.1 Live Call Monitor

Real-time view of every active call:
- Caller number, which agent is handling it
- Live transcript updating as the conversation progresses
- Current pipeline stage: `listening | processing | speaking`
- Round-trip latency for the last turn (STT + LLM + TTS ms)
- One-click "take over" button → puts the AI on hold, connects operator's phone via Twilio conference

**Technical approach:**
- Backend: Spring WebSocket endpoint `/ws/dashboard/{tenantId}` that pushes
  events to connected dashboard clients
- Events: `call.started`, `transcript.partial`, `transcript.final`,
  `agent.speaking`, `call.ended`, `booking.created`
- Flutter: WebSocket client updates state in real-time; use `flutter_riverpod`
  `StreamProvider` to consume the event stream

#### 7.2 Booking Calendar

Replace the current flat `bookingsList` with a proper calendar widget:
- Month / week / day views
- Each booking shows: caller name, service type, time slot, status (confirmed / cancelled / no-show)
- Click a booking → full conversation transcript + call recording link
- Operator can confirm, reschedule, or cancel bookings from the dashboard
- Calendar syncs with Google Calendar / Calendly in real time (via webhook updates)

Flutter package: `table_calendar` (open source, production-quality).

#### 7.3 Agent Prompt Tester

Text-based chat interface in the dashboard to test the agent's system prompt
without making a real phone call:
- Type as the caller would speak
- LLM responds in real time
- Tool calls are executed (can be simulated with mock calendar data)
- Shows the full `conversationHistory` JSON for debugging

#### 7.4 System Health Panel

Real-time metrics visible to the operator:
| Metric | Source |
|--------|--------|
| Active calls right now | Redis key count `call:session:*` |
| P50/P95 STT latency (last hour) | `call_turns.stt_latency_ms` |
| P50/P95 LLM latency (last hour) | `call_turns.llm_latency_ms` |
| P50/P95 TTS latency (last hour) | `call_turns.tts_latency_ms` |
| Total round-trip P50 | Sum of above |
| Barge-in rate | `call_turns` where `interrupted = true` |
| Tool-call success rate | `tool_results` where `success = false` |
| Calendar API error rate | Dedicated metric |

---

## Pillar 8 — Production Infrastructure

### 8.1 Twilio production setup

**Number provisioning (real):**
Use the Twilio REST API to search and purchase a number in the tenant's country:
```
POST /2010-04-01/Accounts/{AccountSid}/IncomingPhoneNumbers
  AreaCode: 221         (Senegal country prefix example)
  VoiceUrl: https://api.sauti.io/webhooks/twilio/voice
  StatusCallback: https://api.sauti.io/webhooks/twilio/status
```
Store the purchased number's SID (`PN...`) for release/update operations.

**Signature validation (required):**
Every Twilio webhook must validate the `X-Twilio-Signature` HMAC header using
the Twilio auth token and the full request URL. Reject with HTTP 403 if invalid.
Add the `com.twilio.sdk:twilio` dependency for the `RequestValidator` class.

**Recording (optional but expected by operators):**
Add `<Record>` to TwiML or configure recording via Twilio Console.
Store recording URL on `Call.recordingUrl` (field already exists).
Recording files can be stored in an S3-compatible bucket.

**Outbound calls (appointment reminders):**
Needed in Pillar 3 (agent can call back missed callers) and for reminders:
```java
class OutboundCallService {
    void initiateReminderCall(Booking booking);   // 24h before appointment
    void initiateFollowUpCall(Call completedCall); // if booking not made
    void initiateCallbackRequest(String callerPhone, UUID agentId);
}
```
Twilio REST API call creation: `POST /2010-04-01/Accounts/{sid}/Calls`.

### 8.2 Webhook delivery to tenants

When a booking is created, cancelled, or modified, Sauti should POST a webhook
to a URL configured by the tenant (their CRM, their Zapier, etc.):

```json
{
  "event": "booking.created",
  "tenantId": "...",
  "booking": {
    "id": "...",
    "callerName": "Fatou",
    "callerPhone": "+221771234567",
    "serviceType": "Consultation",
    "appointmentAt": "2026-06-25T14:00:00+03:00",
    "agentId": "...",
    "confirmationCode": "SAT-4829"
  }
}
```

Use a `WebhookDeliveryService` with:
- Retry logic: exponential backoff, max 5 attempts over 24 hours
- HMAC-SHA256 signature in `X-Sauti-Signature` header
- Store delivery attempts and status in `webhook_deliveries` table

### 8.3 Dockerfile

Two stages: Gradle build → JRE runtime:

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY settings.gradle build.gradle ./
COPY backend/ backend/
RUN ./gradlew :backend:bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S sauti && adduser -S sauti -G sauti
USER sauti
COPY --from=build /app/backend/build/libs/*.jar /app/sauti.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/sauti.jar"]
```

### 8.4 Environment configuration

All secrets via environment variables. Never committed to source control:

```
TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN
DEEPGRAM_API_KEY
ELEVENLABS_API_KEY
ANTHROPIC_API_KEY (or OPENAI_API_KEY)
GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET
CALENDLY_CLIENT_ID, CALENDLY_CLIENT_SECRET
SPRING_DATASOURCE_URL, SPRING_DATASOURCE_PASSWORD
SPRING_REDIS_HOST, SPRING_REDIS_PASSWORD
SAUTI_CORS_ALLOWED_ORIGIN
SAUTI_WEBHOOK_SIGNING_SECRET
```

### 8.5 Database schema changes required

New columns and tables beyond what currently exists:

```sql
-- Per-agent calendar connection
ALTER TABLE agents ADD COLUMN calendar_provider TEXT;
ALTER TABLE agents ADD COLUMN calendar_external_id TEXT;
ALTER TABLE agents ADD COLUMN calendar_oauth_tokens JSONB;
ALTER TABLE agents ADD COLUMN session_duration_minutes INT NOT NULL DEFAULT 60;
ALTER TABLE agents ADD COLUMN buffer_minutes INT NOT NULL DEFAULT 15;
ALTER TABLE agents ADD COLUMN operating_hours JSONB;

-- Booking improvements
ALTER TABLE bookings ADD COLUMN external_booking_id TEXT;  -- Google/Calendly event ID
ALTER TABLE bookings ADD COLUMN confirmation_code TEXT;
ALTER TABLE bookings ADD COLUMN service_duration_minutes INT;
ALTER TABLE bookings ADD COLUMN notes TEXT;

-- Call transcript stored as structured JSON
ALTER TABLE calls ADD COLUMN conversation_json JSONB;
ALTER TABLE calls ADD COLUMN recording_sid TEXT;

-- Call turn improvements
ALTER TABLE call_turns ADD COLUMN interrupted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE call_turns ADD COLUMN tool_calls_json JSONB;
ALTER TABLE call_turns ADD COLUMN tool_results_json JSONB;
ALTER TABLE call_turns ADD COLUMN total_latency_ms INT;

-- Tenant calendar OAuth tokens
CREATE TABLE tenant_calendar_connections (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    provider TEXT NOT NULL,    -- 'google', 'calendly', 'calcom', 'outlook'
    access_token_enc TEXT,     -- encrypted at rest
    refresh_token_enc TEXT,
    token_expires_at TIMESTAMPTZ,
    calendar_id TEXT,
    webhook_channel_id TEXT,
    connected_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, provider)
);

-- Outbound call scheduling
CREATE TABLE scheduled_calls (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    booking_id UUID REFERENCES bookings(id),
    call_type TEXT NOT NULL,   -- 'reminder', 'followup', 'callback'
    target_phone TEXT NOT NULL,
    scheduled_for TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',   -- pending/initiated/completed/failed
    twilio_call_sid TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

-- Webhook delivery log
CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    event_type TEXT NOT NULL,
    payload_json JSONB NOT NULL,
    endpoint_url TEXT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    last_status_code INT,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);
```

### 8.6 GitHub Actions CI

```yaml
# .github/workflows/ci.yml
on: [push, pull_request]
jobs:
  backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env: { POSTGRES_DB: sauti_test, POSTGRES_PASSWORD: test }
      redis:
        image: redis:7
    env:
      SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/sauti_test
      SPRING_REDIS_HOST: localhost
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: temurin }
      - run: ./gradlew :backend:test
      - run: ./gradlew :backend:bootJar -x test

  dashboard:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: subosito/flutter-action@v2
        with: { flutter-version: '3.24.x' }
      - run: dart pub get
        working-directory: dashboard
      - run: flutter analyze
        working-directory: dashboard
      - run: flutter test
        working-directory: dashboard
```

### 8.7 Observability

Add Prometheus metrics via `micrometer-registry-prometheus`:

| Metric | What it measures |
|--------|-----------------|
| `sauti_calls_active` | Gauge: current live calls |
| `sauti_call_duration_seconds` | Histogram: call durations |
| `sauti_turn_stt_latency_ms` | Histogram: STT latency per turn |
| `sauti_turn_llm_latency_ms` | Histogram: LLM latency per turn |
| `sauti_turn_tts_latency_ms` | Histogram: TTS latency per turn |
| `sauti_turn_total_latency_ms` | Histogram: full round-trip per turn |
| `sauti_bookings_created_total` | Counter: successful bookings |
| `sauti_barge_ins_total` | Counter: barge-in events |
| `sauti_tool_calls_total{tool,success}` | Counter: tool call outcomes |
| `sauti_calendar_errors_total{provider}` | Counter: calendar API failures |

Expose on port 8081 at `/actuator/prometheus`. Connect to a Grafana dashboard.

---

## 3. Implementation Sequence

The pillars have dependencies. This is the correct build order:

```
Week 1–2: Pillar 1 (Fix Twilio protocol)
          + Pillar 5 (Redis session, CallSession schema)
          
Week 3–4: Pillar 2 (Real STT + TTS streaming, audio codecs)
          These two pillars together give you a working audio pipeline
          for the first time.

Week 5–6: Pillar 3 (LLM Tool Use, ConversationOrchestrator)
          This is the largest and most critical pillar.
          The app becomes an AI agent here.

Week 7:   Pillar 4 (Dynamic agent tools, webhook fulfillment, Google Calendar)
          Bookings are real, custom tools are configurable per agent.

Week 8:   Pillar 6 (VAD + Barge-in)
          Now calls feel natural.

Week 9:   Pillar 7 (Dashboard: live monitor, booking calendar, health panel)
          
Week 10:  Pillar 8 (Twilio production, Dockerfile, CI, observability)
```

---

## 4. What Production Looks Like (The Full Flow)

A caller dials the Twilio number. Here is what happens in a production Sauti:

```
00:00  Twilio receives call → POST /webhooks/twilio/voice
       Backend looks up agent by Twilio number
       Validates Twilio signature (HMAC)
       Creates Call record + CallSession in Redis
       Returns TwiML: <Connect><Stream url="wss://..."/></Connect>

00:00  Twilio opens WebSocket to /ws/twilio/media/{callSid}
       Backend opens Deepgram WebSocket + ElevenLabs WebSocket
       Plays greeting: TTS "Bonjour, vous êtes bien chez Demo Clinic..."
       (mu-law encoded, base64-wrapped media frame → Twilio)

00:04  Caller speaks: "Je voudrais prendre un rendez-vous"
       Twilio streams mu-law chunks every 20ms to backend WebSocket
       Backend decodes + resamples to PCM 16kHz → forwards to Deepgram
       Deepgram returns UtteranceEnd after 500ms pause

00:04.5  ConversationOrchestrator.handleUserUtterance("Je voudrais prendre...")
         Builds message array [system, assistant(greeting), user(utterance)]
         Calls Claude claude-sonnet-4-6 streaming with tool definitions
         LLM generates: "Bien sûr! Puis-je avoir votre nom?"
         SentenceChunker sends to ElevenLabs → audio starts in 300ms
         Caller hears first words 800ms after they stopped speaking

00:06  Caller speaks: "Je m'appelle Fatou Diallo"
       (same pipeline — transcript → LLM adds name to pendingBookingDraft)
       LLM: "Merci Fatou. Pour quel service souhaitez-vous prendre rendez-vous?"

00:08  Caller: "Une consultation générale"
       LLM: "Pour quelle date préférez-vous?"

00:10  Caller: "Jeudi prochain si possible"
       LLM calls tool: check_availability("2026-06-26", "any", 60, "Africa/Dakar")
       Google Calendar freebusy query → returns busy windows
       Backend computes free slots: [09:00, 10:30, 14:00, 15:30]
       LLM: "J'ai des disponibilités jeudi à 9h, 10h30, 14h et 15h30. 
              Quelle heure vous convient le mieux?"

00:13  Caller: "14h ce serait parfait"
       LLM: "Parfait. Je confirme: consultation générale jeudi 26 juin à 14h.
              C'est bien pour vous Fatou?"

00:14  Caller: "Oui c'est bon"
       LLM calls tool: book_slot("2026-06-26", "14:00", "Fatou Diallo",
                                  "+221771234567", "Consultation générale", agentId)
       Google Calendar event created → eventId returned
       bookings row inserted with externalId + confirmationCode "SAT-4829"
       LLM: "Parfait, c'est confirmé! Votre code de confirmation est SAT-4829.
              Vous recevrez un SMS de confirmation. À jeudi Fatou, au revoir!"
       tool call: send_confirmation_sms("+221771234567", "Confirmé: Consultation...")
       tool call: end_call("Booking confirmed for Fatou Diallo, June 26 14:00")

00:19  WebSocket closes. Call record marked complete.
       Twilio status callback received → duration recorded.
       Dashboard pushes "booking.created" event to operator's browser.
       Operator sees new booking appear in calendar widget instantly.
       Business owner sees new event in their Google Calendar.
```

Total call: 19 seconds. Real booking in real calendar. Zero human involvement.
That is what Sauti is supposed to be.

---

## 5. Estimated Complexity by Pillar

| Pillar | Effort | Risk | Blocks |
|--------|--------|------|--------|
| 1 — Twilio Protocol | 2 days | Low | All others |
| 2 — Streaming Pipeline | 5 days | Medium | Pillar 3, 6 |
| 3 — LLM Tool Use | 8 days | **High** | Pillar 4 |
| 4 — Dynamic Tools & Integration | 10 days | **High** | Pillar 3 |
| 5 — Redis Session | 3 days | Low | Pillar 2, 3 |
| 6 — VAD + Barge-In | 4 days | **High** | Pillar 2 |
| 7 — Dashboard | 6 days | Low | Pillar 3, 4 |
| 8 — Infrastructure | 4 days | Low | None |
| **Total** | **~38 days** | | |

Pillar 3 (LLM tool use) is the highest-risk item because it requires
iterative prompt engineering alongside the code. The system prompt
and tool definitions must be tested against hundreds of realistic caller
scenarios to handle edge cases: callers who do not know what service they
want, callers who want multiple appointments, callers who change their mind
mid-booking, callers who speak a mix of French and Wolof.

Pillar 6 (Barge-in) is high-risk because it requires precise tuning of
VAD thresholds. Too sensitive → constant false interruptions. Too lenient →
callers cannot stop the agent. Plan for 1–2 weeks of call testing to tune it.

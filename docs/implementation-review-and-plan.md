# Sauti — Implementation Review & Better Implementation Plan

> Generated: 2026-06-23  
> Scope: Full audit of the current MVP codebase against the SRS and Flows documents,
> followed by a prioritised, phase-by-phase plan to correct and complete the project.

---

## 1. What Is Currently Correct

The following areas are implemented cleanly and are consistent with the SRS:

| Area | Status |
|------|--------|
| Auth lifecycle (register → verify → login → refresh → logout → password reset) | ✅ Correct |
| Refresh-token hashing (SHA-256) and rotation | ✅ Correct |
| BCrypt(12) password encoding | ✅ Correct |
| Redis-backed verification/reset codes with TTL | ✅ Correct |
| Thymeleaf email templates (verification + reset) | ✅ Correct |
| Tenant-scoped agent CRUD, activate/deactivate | ✅ Correct |
| Fake STT / LLM / TTS interface abstractions | ✅ Correct (good design) |
| Call escalation phrase matching | ✅ Correct |
| Voicemail branch in pipeline | ✅ Correct |
| Booking CRUD (create/list/get/cancel) | ✅ Correct |
| Analytics summary (counts + avg duration) | ✅ Correct |
| Billing usage endpoint | ✅ Correct |
| Onboarding-status endpoint | ✅ Correct |
| Clean-architecture Flutter package layout | ✅ Correct |
| Flutter auth pages (verify-email, forgot-pw, reset-pw) | ✅ Correct |
| Integration test covering the happy-path E2E flow | ✅ Correct |
| Docker Compose for local infra (Postgres, Redis, Mailpit) | ✅ Correct |

---

## 2. Bugs Found

### 2.1 Critical — Will Break Production

#### BUG-01: WebSocket handler feeds raw JSON to STT instead of decoded audio
**File:** `backend/src/main/java/com/sauti/call/TwilioMediaWebSocketHandler.java:31`

```java
// CURRENT (wrong)
var turn = callPipelineService.processTurn(call, payload.getBytes(StandardCharsets.UTF_8));
```

**Problem:** Twilio Media Streams sends JSON frames:
```json
{
  "event": "media",
  "media": { "track": "inbound", "payload": "<base64-encoded-mulaw-audio>" }
}
```
The code passes the entire raw JSON string as bytes to `processTurn()`. The fake STT ignores the content, so tests pass — but a real Deepgram adapter will receive garbage.

**Fix:**
```java
// Parse the JSON, extract and decode the base64 audio payload
var node = objectMapper.readTree(payload);
if ("media".equals(node.path("event").asText())) {
    var b64 = node.path("media").path("payload").asText();
    byte[] audio = Base64.getDecoder().decode(b64);
    var turn = callPipelineService.processTurn(call, audio);
    // Twilio expects a JSON media event back, not plain text
    session.sendMessage(new TextMessage(buildTwilioAudioResponse(turn.audio())));
}
```

---

#### BUG-02: WebSocket response format is wrong
**File:** `TwilioMediaWebSocketHandler.java:32`

```java
session.sendMessage(new TextMessage(turn.text()));  // sends plain text
```

Twilio Media Streams requires the server to respond with a JSON `media` event:
```json
{
  "event": "media",
  "streamSid": "<sid>",
  "media": { "payload": "<base64-encoded-audio>" }
}
```
Sending plain text will be silently ignored by Twilio and the caller will hear nothing.

---

#### BUG-03: Call is not closed after escalation/voicemail
**File:** `CallPipelineService.java:75–89`

`call.complete("transferred")` marks the entity completed, but the WebSocket session is never closed after escalation or voicemail. Twilio will keep streaming audio and `processTurn()` will keep being called on an already-completed call, producing extra turns and saving garbage data.

**Fix:** `TwilioMediaWebSocketHandler` must close the session after receiving a terminal turn result:
```java
if ("transferred".equals(call.getOutcome()) || "voicemail".equals(call.getOutcome())) {
    session.close(CloseStatus.NORMAL);
}
```

---

#### BUG-04: Minutes used are never incremented
**File:** `CallPipelineService.java` — no call to `tenant.addMinutesUsed()`

The `Tenant` entity tracks `minutesUsedThisCycle`, but `CallPipelineService.processTurn()` and `afterConnectionClosed` never update it. Every billing usage query will return 0 minutes used regardless of call volume.

---

#### BUG-05: `recordingUrl` field has no getter
**File:** `backend/src/main/java/com/sauti/call/Call.java:47`

The field exists but is unreachable from DTOs or services. If any downstream code tries to expose it, it silently returns null or fails at serialization.

---

### 2.2 Security Issues

#### SEC-01: No Twilio webhook signature validation
**File:** `api/TwilioWebhookController.java`

Any third party can POST to `/webhooks/twilio/voice` and trigger call pipelines and create call records. Twilio signs every webhook request with an `X-Twilio-Signature` HMAC header. This must be validated.

---

#### SEC-02: Auth endpoints have no rate limiting
**File:** `api/AuthController.java`

`/api/v1/auth/login`, `/api/v1/auth/forgot-password`, and `/api/v1/auth/verify-email` are open to brute-force attacks. No rate limiting or account lockout is implemented.

---

#### SEC-03: CORS is misconfigured
**File:** `auth/SecurityConfig.java:25`

```java
.cors(cors -> {})   // uses Spring's default bean — allows nothing cross-origin in production
```

The Flutter web dashboard runs on a different origin. Either CORS is silently allowing everything (if a global `CorsConfigurationSource` bean exists) or it is blocking all cross-origin requests. This needs an explicit, locked-down configuration.

---

### 2.3 Architectural Issues

#### ARCH-01: No Flyway migration scripts
Flyway is in `build.gradle` but `src/main/resources/db/migration/` contains no `.sql` files. The schema is created by Hibernate `ddl-auto` — acceptable for development but catastrophic in production (Hibernate will attempt to alter/drop tables on restart).

---

#### ARCH-02: Agent languages and escalation phrases stored as CSV strings
```java
private String supportedLanguages = "fr,en";   // in Agent.java
private String escalationPhrases;
```
CSV-in-a-column prevents indexed querying, breaks with commas in phrase text, and requires client-side splitting. Should use a proper `@ElementCollection` or a JSON column.

---

#### ARCH-03: Call transcript is a mutable concatenated string
```java
this.transcript = transcript + "Caller: " + callerTranscript + "\n" + "Agent: " + agentResponse + "\n";
```
Transcript is a single growing text column, making it impossible to query individual turns, apply per-turn sentiment analysis, or paginate. `CallTurn` already stores individual turns properly — the flat transcript is redundant and inconsistent.

---

#### ARCH-04: Active call state is reloaded from DB on every turn
`processTurn()` is `@Transactional` — fine for durability, but for multi-turn live calls this means a DB read + write on every 200ms audio chunk. At scale this creates heavy DB load. Active call state should live in Redis for the call's lifetime.

---

#### ARCH-05: No token-refresh logic in Flutter ApiClient
**File:** `dashboard/packages/core/lib/network/api_client.dart`

JWT access tokens expire after 15 minutes. The `ApiClient` has no interceptor to detect a 401 response, silently refresh the access token using the stored refresh token, and retry the original request. After 15 minutes every API call will fail and the user must manually log in again.

---

## 3. Missing Features (SRS vs Implementation)

| # | SRS Feature | Status | Notes |
|---|-------------|--------|-------|
| F-01 | Real Deepgram STT adapter | ❌ Missing | Fake only |
| F-02 | Real Gemini/Claude LLM adapter | ❌ Missing | Fake only |
| F-03 | Real TTS adapter (Google/ElevenLabs) | ❌ Missing | Fake only |
| F-04 | Real Twilio number provisioning | ❌ Missing | Fake (random number string) |
| F-05 | Twilio signature validation | ❌ Missing | Security requirement |
| F-06 | Proper Twilio Media Streams JSON parsing | ❌ Missing | BUG-01 above |
| F-07 | Proper TwiML audio response to Twilio | ❌ Missing | BUG-02 above |
| F-08 | Redis-backed active call state | ❌ Missing | Scales poorly without it |
| F-09 | Rate limiting on auth endpoints | ❌ Missing | SEC-02 above |
| F-10 | Flyway migration scripts | ❌ Missing | ARCH-01 above |
| F-11 | CI/CD pipeline (GitHub Actions) | ❌ Missing | |
| F-12 | Dockerfile + docker-compose for app | ❌ Missing | Only infra services exist |
| F-13 | Flutter: Login/Register as full pages | ❌ Missing | Implemented as dialogs only |
| F-14 | Flutter: Analytics dashboard view | ❌ Missing | |
| F-15 | Flutter: Billing / usage view | ❌ Missing | |
| F-16 | Flutter: JWT auto-refresh | ❌ Missing | ARCH-05 above |
| F-17 | Flutter: Onboarding wizard | ❌ Missing | Backend endpoint exists |
| F-18 | Production CORS config | ❌ Missing | SEC-03 above |
| F-19 | Operating hours enforcement | ❌ Missing | Field exists, never checked |
| F-20 | Call-turn minute billing increment | ❌ Missing | BUG-04 above |

---

## 4. Better Implementation Plan

The plan is divided into four phases. Each phase is runnable independently; later phases build on earlier ones.

---

### Phase 1 — Bug Fixes (1–2 days)

These fixes cost little but unlock correctness. Do them before anything else.

#### 1.1 Fix Twilio Media Streams JSON parsing (BUG-01 + BUG-02)

**`TwilioMediaWebSocketHandler.java`**

- Add Jackson `ObjectMapper` dependency injection.
- In `handleTextMessage()`:
  1. Parse the incoming JSON string.
  2. Check `event == "connected"` → store `streamSid` in session attributes.
  3. Check `event == "media"` → extract `media.payload`, Base64-decode it, pass raw PCM bytes to `processTurn()`.
  4. Check `event == "stop"` → call `afterConnectionClosed()` cleanup.
- Build the Twilio-format response:
  ```json
  {
    "event": "media",
    "streamSid": "<sid>",
    "media": { "payload": "<Base64(audio)>" }
  }
  ```
- Remove the dead `handleBinaryMessage()` override — Twilio only uses text frames.

#### 1.2 Close WebSocket session after terminal pipeline outcome (BUG-03)

**`TwilioMediaWebSocketHandler.java`** — after calling `processTurn()`, check `call.getOutcome()`:
```java
if (!call.getOutcome().equals("active")) {
    session.close(CloseStatus.NORMAL);
}
```

#### 1.3 Add `recordingUrl` getter to `Call.java` (BUG-05)

```java
public String getRecordingUrl() { return recordingUrl; }
```

#### 1.4 Increment tenant minutes on call completion (BUG-04)

**`TwilioMediaWebSocketHandler.afterConnectionClosed()`** — after setting outcome `"faq_answered"`, calculate duration and call `tenant.addMinutesUsed(durationMinutes)` + save tenant.

Alternatively, do it in `Call.complete()` by publishing a Spring ApplicationEvent that a `BillingEventListener` handles asynchronously.

#### 1.5 Normalise CORS configuration (SEC-03)

**`api/CorsConfig.java`** — define an explicit `CorsConfigurationSource` bean:
```java
config.setAllowedOrigins(List.of("http://localhost:8088", "${sauti.cors.allowed-origin}"));
config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
config.setAllowedHeaders(List.of("*"));
config.setAllowCredentials(true);
```

---

### Phase 2 — Foundation Hardening (3–5 days)

These changes make the backend production-safe and the Flutter dashboard properly functional.

#### 2.1 Add Flyway migration scripts

Create `backend/src/main/resources/db/migration/` with:

| File | Contents |
|------|----------|
| `V1__create_tenants.sql` | tenants table |
| `V2__create_users.sql` | users table + fk |
| `V3__create_refresh_tokens.sql` | refresh_tokens table |
| `V4__create_agents.sql` | agents table + fk |
| `V5__create_calls.sql` | calls table + fk |
| `V6__create_call_turns.sql` | call_turns table + fk |
| `V7__create_bookings.sql` | bookings table + fk |

Set `spring.jpa.hibernate.ddl-auto=validate` in `application.yml` to ensure Hibernate only validates schema, never modifies it.

#### 2.2 Replace CSV columns with proper storage

**Agent languages and escalation phrases:**

Option A (simple, no schema change): Use a Postgres `text[]` array column.
```sql
ALTER TABLE agents ADD COLUMN supported_languages text[] NOT NULL DEFAULT '{fr,en}';
ALTER TABLE agents ADD COLUMN escalation_phrases text[];
```

Then in Java:
```java
@Column(columnDefinition = "text[]")
@Convert(converter = StringArrayConverter.class)
private String[] supportedLanguages;
```

Option B (normalised): `agent_languages` join table. Adds a migration and a `@ElementCollection`.

**Recommendation:** Use Option A for MVP. Option B when you need language-level filtering across agents.

#### 2.3 Add Twilio signature validation (SEC-01)

Create `TwilioSignatureValidator` bean:
```java
@Component
public class TwilioSignatureValidator {
    private final RequestValidator validator;
    public TwilioSignatureValidator(@Value("${twilio.auth-token}") String token) {
        this.validator = new RequestValidator(token);
    }
    public boolean isValid(HttpServletRequest req) {
        var sig = req.getHeader("X-Twilio-Signature");
        var url = reconstructFullUrl(req);
        var params = extractParams(req);
        return validator.validate(url, params, sig);
    }
}
```

Inject into `TwilioWebhookController` and reject invalid requests with HTTP 403.

> **Dependency:** Add `com.twilio.sdk:twilio:10.x` to `build.gradle`.

#### 2.4 Add rate limiting on auth endpoints (SEC-02)

Add `bucket4j-spring-boot-starter` dependency. Configure a `@RateLimiter` on:
- `POST /api/v1/auth/login` — max 5 attempts / 1 minute per IP
- `POST /api/v1/auth/forgot-password` — max 3 attempts / 5 minutes per email
- `POST /api/v1/auth/verify-email` — max 10 attempts / 10 minutes per email

Store rate limit buckets in Redis so limits survive restarts.

#### 2.5 Flutter: Add JWT auto-refresh interceptor

**`dashboard/packages/core/lib/network/api_client.dart`**

```dart
Future<Response> _executeWithRefresh(Future<Response> Function() call) async {
  var response = await call();
  if (response.statusCode == 401) {
    final refreshed = await _authTokenStore.refresh();
    if (refreshed) {
      response = await call();  // retry with new token
    } else {
      _authTokenStore.clear();
      _router.go('/login');
    }
  }
  return response;
}
```

Expose `AuthTokenStore` as a singleton through `AppDependencies`.

#### 2.6 Flutter: Convert auth dialogs to dedicated pages

Create proper route-based pages for all auth transitions:
- `/login` → `LoginPage`
- `/register` → `RegisterPage`

These already exist partially. Move the inline dialog logic from `sauti_dashboard_app.dart` into proper `Scaffold`-based pages. Use `go_router` for type-safe routing.

#### 2.7 Normalise the transcript: remove redundant flat string

The flat `transcript` column on `Call` is redundant when `CallTurn` rows exist. 

Short-term: Keep it for the demo (it powers the simulated turn test). Add a `TODO` comment.  
Long-term (Phase 4): Remove the column and rebuild the transcript view from `CallTurn` rows on read.

---

### Phase 3 — Real Provider Integrations (5–10 days)

Replace the three fake providers with real implementations.

#### 3.1 Real STT — Deepgram streaming adapter

Create `DeepgramStreamingSttProvider implements StreamingSttProvider`.

**Protocol:** Deepgram accepts raw mulaw/linear16 audio over a WebSocket and returns JSON transcript events.

```java
@Component
@ConditionalOnProperty(name = "sauti.stt.provider", havingValue = "deepgram")
public class DeepgramStreamingSttProvider implements StreamingSttProvider {
    // WebSocket client connecting to wss://api.deepgram.com/v1/listen
    // Parameters: encoding=mulaw, sample_rate=8000, language=multi
    // Returns first "is_final=true" transcript
    @Override
    public String transcribe(byte[] audioPayload) { ... }
}
```

**Configuration:**
```yaml
sauti:
  stt:
    provider: deepgram       # or "fake"
    deepgram:
      api-key: ${DEEPGRAM_API_KEY}
      model: nova-2
      language: multi        # auto-detect language
```

#### 3.2 Real LLM — Gemini/Claude streaming adapter

Create `GeminiStreamingLlmProvider implements StreamingLlmProvider`.

```java
@Component
@ConditionalOnProperty(name = "sauti.llm.provider", havingValue = "gemini")
public class GeminiStreamingLlmProvider implements StreamingLlmProvider {
    // POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent
    // Build messages from call history + agent system prompt
    @Override
    public String respond(Agent agent, String language, List<String> history, String userInput) { ... }
}
```

Alternatively, use the Anthropic SDK for Claude. Both are viable.

**Configuration:**
```yaml
sauti:
  llm:
    provider: gemini          # or "claude" or "fake"
    gemini:
      api-key: ${GEMINI_API_KEY}
      model: gemini-2.0-flash
```

#### 3.3 Real TTS — Google Cloud TTS or ElevenLabs

Create `GoogleTtsProvider implements StreamingTtsProvider`.

**Configuration:**
```yaml
sauti:
  tts:
    provider: google           # or "elevenlabs" or "fake"
    google:
      api-key: ${GOOGLE_TTS_API_KEY}
      voice: fr-FR-Wavenet-A   # default; per-agent override via ttsVoiceId
```

#### 3.4 Real Twilio number provisioning

Implement `TwilioTelephonyProvider implements TelephonyProvider`:
```java
@Component
@ConditionalOnProperty(name = "sauti.telephony.provider", havingValue = "twilio")
public class TwilioTelephonyProvider implements TelephonyProvider {
    // 1. TwilioRestClient.accounts().incomingPhoneNumbers().create()
    // 2. Configure voiceUrl + statusCallbackUrl
    // 3. Return provisioned E.164 number
}
```

#### 3.5 Redis-backed active call state

For multi-turn calls, move the in-progress call context out of JPA into Redis:

```java
@Component
public class CallStateStore {
    // Key: "call:state:{callSid}" → JSON blob
    // TTL: 2 hours (max call length)
    // Fields: agentId, tenantId, transcript[], turnCount, outcome, languageDetected, startedAt
    
    public void put(String callSid, CallState state) { ... }
    public Optional<CallState> get(String callSid) { ... }
    public void remove(String callSid) { ... }
}
```

`CallPipelineService.processTurn()` reads/writes `CallStateStore`. At call end (websocket close or terminal outcome), flush to Postgres.

---

### Phase 4 — Dashboard Completion (3–5 days)

#### 4.1 Analytics dashboard view

Create `feature_analytics` package:
- `AnalyticsSummary` entity: totalCalls, transferred, voicemail, faqAnswered, avgDuration
- `ListAnalyticsSummary` use-case
- `AnalyticsDashboardPanel` widget: 4 metric tiles + a simple call volume bar chart (using `fl_chart`)

#### 4.2 Billing / usage view

Create `BillingPanel` widget inside `feature_agents` or a new `feature_billing` package:
- Shows plan name, minutes used / monthly limit as a progress bar
- Warns when > 80% of limit is consumed

#### 4.3 Onboarding wizard

Use the existing backend `/api/v1/tenant/onboarding-status` endpoint to drive a 3-step wizard:
1. **Email verified** — done at registration
2. **Agent created** — redirect to agent creation if missing
3. **Agent activated** — prompt to activate if created but inactive

Show wizarding only once; store completion state in `SharedPreferences`.

#### 4.4 Add state management (Riverpod)

Replace raw `setState` calls with `flutter_riverpod`:
- `authProvider` — session state and token lifecycle
- `agentsProvider` — agent list with optimistic updates
- `callsProvider` — paginated call log
- `analyticsProvider` — summary metrics with auto-refresh every 60 seconds

This eliminates duplicated API calls, enables proper loading/error states, and simplifies the dashboard shell.

---

### Phase 5 — Production Readiness (2–3 days)

#### 5.1 Dockerfile + multi-stage build

```dockerfile
# Stage 1: build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew :backend:bootJar -x test

# Stage 2: runtime
FROM eclipse-temurin:17-jre-alpine
COPY --from=build /app/backend/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 5.2 Full docker-compose.yml

Extend `infra/local/docker-compose.yml` to add the backend service:
```yaml
services:
  backend:
    build: .
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/sauti
      SPRING_REDIS_HOST: redis
      ...
    depends_on: [postgres, redis]
```

#### 5.3 GitHub Actions CI

`.github/workflows/ci.yml`:
```yaml
on: [push, pull_request]
jobs:
  backend:
    runs-on: ubuntu-latest
    services:
      postgres: { image: postgres:16, env: {...} }
      redis: { image: redis:7 }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - run: ./gradlew :backend:test
  dashboard:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: subosito/flutter-action@v2
      - run: flutter test
        working-directory: dashboard
```

#### 5.4 Operating hours enforcement

Add `OperatingHoursService`:
```java
public boolean isWithinOperatingHours(Agent agent) {
    // Parse agent.getOperatingHours() (e.g. "09:00-18:00 Mon-Fri")
    // Compare against OffsetDateTime.now().atZone(ZoneId.of(agent.getTimezone()))
}
```

Call from `CallPipelineService.startInboundCall()` — if outside hours, return a TwiML `<Say>` response immediately.

---

## 5. Summary Priority Order

| Priority | Item | Est. Effort |
|----------|------|-------------|
| 🔴 P0 | BUG-01: Fix Twilio media JSON parsing | 2h |
| 🔴 P0 | BUG-02: Fix WebSocket response format | 1h |
| 🔴 P0 | BUG-03: Close WebSocket after terminal outcome | 30m |
| 🔴 P0 | BUG-04: Increment tenant minutes on call completion | 1h |
| 🟠 P1 | SEC-01: Twilio signature validation | 2h |
| 🟠 P1 | SEC-02: Rate limiting on auth endpoints | 3h |
| 🟠 P1 | SEC-03: Fix CORS configuration | 1h |
| 🟠 P1 | ARCH-01: Add Flyway migrations | 4h |
| 🟡 P2 | Flutter JWT auto-refresh interceptor | 2h |
| 🟡 P2 | Flutter: Convert dialogs to pages | 3h |
| 🟡 P2 | Real Deepgram STT adapter | 6h |
| 🟡 P2 | Real Gemini/Claude LLM adapter | 4h |
| 🟡 P2 | Real TTS adapter | 4h |
| 🟡 P2 | Real Twilio provisioning | 4h |
| 🟢 P3 | Redis-backed call state | 6h |
| 🟢 P3 | Flutter analytics view | 4h |
| 🟢 P3 | Flutter billing view | 2h |
| 🟢 P3 | Flutter onboarding wizard | 3h |
| 🟢 P3 | Riverpod state management | 8h |
| 🔵 P4 | Dockerfile + docker-compose | 2h |
| 🔵 P4 | GitHub Actions CI | 3h |
| 🔵 P4 | Operating hours enforcement | 3h |
| 🔵 P4 | Replace CSV columns | 4h |

**Total estimated effort:** ~72 hours (approximately 2 focused weeks for one developer).

---

## 6. Recommended File Changes Per Phase

### Phase 1 files to touch
```
backend/src/main/java/com/sauti/call/TwilioMediaWebSocketHandler.java  ← BUG-01, BUG-02, BUG-03
backend/src/main/java/com/sauti/call/Call.java                          ← BUG-05 (add getter)
backend/src/main/java/com/sauti/call/CallPipelineService.java            ← BUG-04 (billing increment)
backend/src/main/java/com/sauti/api/CorsConfig.java                     ← SEC-03
```

### Phase 2 files to touch / create
```
backend/src/main/resources/db/migration/V1__create_tenants.sql  (NEW)
backend/src/main/resources/db/migration/V2__create_users.sql    (NEW)
... (V3–V7)
backend/src/main/java/com/sauti/auth/TwilioSignatureValidator.java  (NEW)
backend/src/main/java/com/sauti/api/TwilioWebhookController.java    ← inject validator
backend/build.gradle                                                  ← add bucket4j, twilio-sdk
dashboard/packages/core/lib/network/api_client.dart                  ← JWT refresh interceptor
dashboard/packages/feature_auth/lib/presentation/pages/login_page.dart  (NEW)
dashboard/packages/feature_auth/lib/presentation/pages/register_page.dart (NEW)
dashboard/lib/app/sauti_dashboard_app.dart                           ← use go_router pages
```

### Phase 3 files to create
```
backend/src/main/java/com/sauti/stt/DeepgramStreamingSttProvider.java
backend/src/main/java/com/sauti/llm/GeminiStreamingLlmProvider.java
backend/src/main/java/com/sauti/tts/GoogleStreamingTtsProvider.java
backend/src/main/java/com/sauti/call/CallStateStore.java
backend/src/main/java/com/sauti/agent/TwilioTelephonyProvider.java
backend/src/main/resources/application.yml  ← add stt/llm/tts/telephony config sections
```

### Phase 4 files to create
```
dashboard/packages/feature_analytics/  (new package)
dashboard/packages/feature_billing/    (new package or extend feature_agents)
dashboard/lib/app/sauti_dashboard_app.dart  ← onboarding flow
```

### Phase 5 files to create
```
Dockerfile
infra/local/docker-compose.yml     ← add backend service
.github/workflows/ci.yml           (NEW)
```

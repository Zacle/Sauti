# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

```
backend/          Spring Boot 3.5.14 (Java 17, Gradle)
dashboard/        Next.js 15 (React 19, TypeScript)
docs/             Architecture planning documents
infra/local/      docker-compose for local dev (postgres, redis, mailpit only)
docker-compose.yml  Full-stack compose (adds backend + dashboard containers)
```

> **Note:** `AGENTS.md` contains outdated Flutter clean-architecture rules. The dashboard was migrated to Next.js. Those rules no longer apply.

---

## Backend

### Commands

```bash
# Run all tests
./gradlew :backend:test

# Run a single test class
./gradlew :backend:test --tests "com.sauti.auth.AuthServiceTest"

# Build the fat JAR
./gradlew :backend:bootJar

# Run locally (reads .env automatically from root)
./gradlew :backend:bootRun
```

H2 in-memory database is used by default when `DB_URL` is not set. Flyway runs automatically. The H2 console is at `http://localhost:8080/h2-console`.

### Key environment variables

| Variable | Default | Purpose |
|---|---|---|
| `PROVIDER_MODE` | `fake` | `fake` = no external calls; `real` = real telephony/STT/TTS |
| `SAUTI_TELEPHONY_PROVIDER` | `fake` | `fake`, `twilio`, or `signalwire` |
| `SAUTI_LLM_PROVIDER` | `heuristic` | `heuristic` (no API key), `spring-ai` (Gemini/OpenAI), `webhook` |
| `SAUTI_LLM_DEFAULT_MODEL` | `gemini-2.5-flash` | Standard-tier model (Gemini Flash) |
| `SAUTI_LLM_ADVANCED_MODEL` | `gemini-2.5-pro` | Advanced-tier model — can be `gpt-4o` for OpenAI |
| `SAUTI_STT_STREAMING_PROVIDER` | `noop` | `noop` or `deepgram` |
| `SAUTI_TTS_STREAMING_PROVIDER` | `noop` | `noop` or `elevenlabs` |
| `GOOGLE_AI_API_KEY` | — | Required when `SAUTI_LLM_PROVIDER=spring-ai` using Gemini |

**SignalWire env vars** (set these instead of Twilio when `SAUTI_TELEPHONY_PROVIDER=signalwire`):

| Variable | Purpose |
|---|---|
| `SIGNALWIRE_PROJECT_ID` | Project ID (replaces Twilio account SID) |
| `SIGNALWIRE_AUTH_TOKEN` | Auth token |
| `SIGNALWIRE_SPACE_URL` | e.g. `https://your-space.signalwire.com` |
| `PUBLIC_BASE_URL` | Public HTTPS URL of the backend |
| `TELEPHONY_CALLS_API_BASE_URL` | Set to `https://your-space.signalwire.com/api/laml/2010-04-01/Accounts` |
| `TELEPHONY_TRANSFER_WEBHOOK_PATH` | Set to `/webhooks/signalwire/transfer` |

Copy `.env.example` to `.env` in the repo root to override defaults for local development.

### Architecture

Spring Boot **modular monolith** — one JAR, package boundaries map to SRS modules. Follow `docs/backend-modules.md` for the full table.

| Package | Responsibility |
|---|---|
| `com.sauti.api` | REST controllers only — no business logic |
| `com.sauti.auth` | JWT, email verification, refresh-token rotation, Google OAuth |
| `com.sauti.tenant` | Tenant account, onboarding state |
| `com.sauti.agent` | Agent entity, templates, variables, tool configuration |
| `com.sauti.call` | Twilio webhooks, WebSocket media stream, call lifecycle |
| `com.sauti.llm` | LLM providers, ConversationOrchestrator, tool loop |
| `com.sauti.nlp` | Language detection, STT/TTS provider routing |
| `com.sauti.tool` | Tool fulfillment (calendar, webhook, SMS, noop) |
| `com.sauti.calendar` | Booking lifecycle, calendar provider integrations |
| `com.sauti.analytics` | Call metrics |
| `com.sauti.billing` | Plan usage, Lemon Squeezy webhooks |

Rules:
- Business logic stays in the owning module package, not in `com.sauti.api`.
- New provider integrations must be hidden behind interfaces.
- Cross-module access goes through services or narrow repository use.

### LLM layer

The live LLM stack is **Gemini-only via Spring AI** (`SpringAiToolCallingLlmProvider`):

- `gemini-2.5-flash` — default model (fast, cheap)
- `gemini-2.5-pro` — advanced model (agent config `llmTier = "advanced"`)
- `GEMINI_THINKING_BUDGET = 0` is **mandatory** — Gemini 2.5 thinking adds ~1,400ms by default; voice target is 400–600ms

The three provider implementations are selected by `sauti.llm.provider`:
- `heuristic` — `LocalToolCallingLlmProvider` (no API key, for local dev)
- `spring-ai` — `SpringAiToolCallingLlmProvider` (Gemini via Google AI Studio)
- `webhook` — `WebhookToolCallingLlmProvider` (external LLM proxy)

`ConversationOrchestrator` runs the tool loop (max 4 iterations), reads/writes conversation history in Redis, and builds the system prompt. The agent's own `systemPrompt` field is the **primary** instruction — orchestrator appends only language, business name, tools, and knowledge base.

### Agent template variables

`{{placeholder}}` variables in system prompts are stored per-agent in the `agent_variables` table (V14 migration). They are substituted at call time inside `ConversationOrchestrator.substituteVariables()`, never at save time. Template variable definitions live in `configurationJson` on `AgentTemplate`.

### Database migrations

Flyway migrations are in `backend/src/main/resources/db/migration/`. The latest is **V14** (`agent_variables`). New migrations must follow the `V{n+1}__description.sql` naming convention.

### Docs bundled in JAR

`docs/agent-templates.md` is copied into `templates/agent-templates.md` inside the JAR at build time (see `processResources` in `backend/build.gradle`).

---

## Dashboard

### Commands

```bash
cd dashboard
npm install
npm run dev          # dev server on :8088
npm run build        # production build
npm run lint         # ESLint
npm run typecheck    # tsc --noEmit
```

### Architecture

Next.js 15 App Router with route groups:

| Route group | Path prefix | Purpose |
|---|---|---|
| `(marketing)` | `/` | Landing pages |
| `(auth)` | `/sign-in`, `/sign-up` | Auth flows |
| `(onboarding)` | `/onboarding` | Post-registration setup |
| `(console)` | `/console` | Tenant dashboard (agents, calls, analytics) |

Feature modules live in `features/` (e.g. `features/agents/`, `features/calls/`). Each feature owns its components, hooks, and API calls. Shared UI primitives go in `components/ui/`.

---

## Local development (full stack)

```bash
# Start postgres, redis, mailpit
docker compose -f infra/local/docker-compose.yml up -d

# Start backend (reads .env from root)
./gradlew :backend:bootRun

# Start dashboard
cd dashboard && npm run dev
```

Full-stack Docker Compose (builds and runs everything):

```bash
docker compose up --build
```

Backend: `http://localhost:8080` | Management: `http://localhost:8081/actuator` | Dashboard: `http://localhost:8088` | Mailpit: `http://localhost:8025`

---

## Production readiness status

See `docs/production-readiness-plan.md` for the 8-pillar plan. Current status:

| Pillar | Status |
|---|---|
| 1 — Twilio Media Streams protocol | Done |
| 2 — Streaming audio pipeline (STT → LLM → TTS) | Done |
| 3 — LLM tool use (ConversationOrchestrator) | Done |
| 4 — Dynamic agent tools & external integrations | Done |
| 5 — Redis-backed conversation state | Done |
| 6 — VAD + barge-in handling | Done |
| 7 — Operational dashboard (live monitor, booking calendar) | **Pending** |
| 8 — Production infrastructure (CI/CD, observability) | **Pending** |

# CLAUDE.md

Guidance for Claude Code in this repository. Also read `AGENTS.md` (operating
rules) and `docs/agent-handoff.md` (history and current state) before changing
code.

## Repository layout

```
backend/            Spring Boot (Java 17, Gradle) modular monolith
dashboard/          Next.js 15 App Router (React 19, TypeScript)
deploy/             Production compose, Caddy, bootstrap and server scripts
docs/               Architecture, runbooks, setup guides, handoff history
infra/local/        Local postgres, redis, mailpit
docker-compose.yml  Full local stack
```

## Backend

```bash
./gradlew :backend:test                                   # all tests
./gradlew :backend:test --tests "com.sauti.auth.AuthServiceTest"
./gradlew :backend:bootJar
./gradlew :backend:bootRun                                # reads .env from root
```

H2 in-memory is used when `DB_URL` is unset. Flyway runs automatically
(`backend/src/main/resources/db/migration`, next file is `V{n+1}__description.sql`;
check the highest existing version first). Copy `.env.example` to `.env` for local
overrides; never commit `.env`.

### Architecture

Modular monolith under `com.sauti.*`. Controllers live only in `com.sauti.api`;
business logic stays in the owning feature package; provider integrations sit
behind interfaces; cross-module access goes through services. See
`docs/backend-modules.md`.

Live voice runs on **Telnyx-managed AI Assistants**: Telnyx handles STT, the
model, TTS, and interruption. Sauti owns the tool layer:
`ManagedVoiceToolService` -> `ToolActionPolicy` (holds side-effect actions until
the caller confirms on a later turn) -> `ToolFulfillmentRouter` -> calendar,
webhook, SMS, transfer, and integration fulfillments. WhatsApp uses Sauti's own
`ConversationOrchestrator`. Post-call work (analysis, CRM sync, retention) is
durable and retryable.

Key env switches: `PROVIDER_MODE` (`fake` for local, `live` in production),
`SAUTI_TELEPHONY_PROVIDER`, `SAUTI_LLM_PROVIDER`. The full production list is
`deploy/.env.production.example`.

Agent prompt `{{placeholders}}` are stored per agent and substituted at call
time, never at save time.

## Dashboard

```bash
cd dashboard
npm ci
npm run dev        # :8088
npm run typecheck
npm run lint
npm run build
```

Route groups: `(marketing)` public site, `(auth)` sign-in/up, `(onboarding)`,
`(console)` tenant console, plus the isolated platform admin on `admin.sauti.uk`.
Feature code lives in `features/<feature>` (`domain/`, `presentation/`), API
clients in `lib/api`, shared types in `types/api.ts`, UI primitives in
`components/ui`. Pages stay thin wrappers. The console is light, calm, and
non-"AI" in tone.

## Production

Single Docker host behind Caddy (`deploy/docker-compose.prod.yml`) on Contabo,
with Neon, Upstash, and Resend as managed services. Deploys happen only through
GitHub Actions (`ci.yml` then `deploy.yml`) on push to `main`. Prepare a fresh
server with `deploy/bootstrap-vps.sh`; secrets live in `/opt/sauti/.env.production`.
See `docs/deployment.md`.

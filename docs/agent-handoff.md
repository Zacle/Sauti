# Sauti Agent Handoff

This document lets a new coding agent continue safely from the previous state. Update it after every meaningful change.

## Current baseline

- Repository: `https://github.com/Zacle/Sauti`
- Production domain: `https://sauti.uk`
- Production deploy target: OVH VPS managed by GitHub Actions over SSH.
- Production runtime: Docker Compose + Caddy + Spring Boot backend + Next.js dashboard.
- Production data services:
  - Neon PostgreSQL
  - Upstash Redis
  - Resend SMTP
- The dashboard is Next.js, not Flutter.
- Real secrets are intentionally not stored in git.

## Deployment state

Deployment is automated:

1. Push to `main`.
2. GitHub Actions runs CI:
   - backend Gradle tests/build
   - dashboard typecheck/build
3. Deploy workflow SSHes into the VPS.
4. Server checks out the exact commit that passed CI under `/opt/sauti/source`.
5. Server builds Docker images locally and runs `docker compose up -d`.
6. Health check verifies `https://sauti.uk/health`.

Important deployment files:

- `.github/workflows/ci.yml`
- `.github/workflows/deploy.yml`
- `deploy/docker-compose.prod.yml`
- `deploy/deploy.sh`
- `deploy/Caddyfile`
- `deploy/backup-postgres.sh`
- `deploy/configure-external-services-env.sh`
- `deploy/test-external-services.sh`
- `deploy/migrate-postgres-to-neon.sh`
- `deploy/update-public-callbacks-env.sh`

## Externalized infrastructure

The app was moved off local Postgres/Redis for production.

### Neon

- App traffic should use Neon pooled URL converted to `DB_URL`.
- Flyway/migrations should use Neon direct URL converted to `SPRING_FLYWAY_URL`.
- The Neon role must own the database or have CREATE privilege on `public`.
- A previous issue happened when the URL used `neondb_owner` instead of the actual DB owner role.

### Upstash

- `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, `SPRING_REDIS_PASSWORD`, and `SPRING_REDIS_SSL_ENABLED=true` are used by Spring.

### Resend

- SMTP host: `smtp.resend.com`
- SMTP port: `587`
- SMTP username: `resend`
- SMTP password: Resend API key
- `MANAGEMENT_HEALTH_MAIL_ENABLED=false` remains set so mail health does not break deploy health checks.

### Backups

- `deploy/backup-postgres.sh` backs up Neon with `postgres:18-alpine`.
- This was necessary because Neon reported PostgreSQL 18.4 and `pg_dump` 16 refused the dump.
- The cron on the server runs the backup script nightly.

## Public callback configuration

Production callback URLs were standardized on `sauti.uk`.

Use these in provider dashboards:

```text
GOOGLE_OAUTH_REDIRECT_URI=https://sauti.uk/api/v1/auth/oauth/google/callback
GOOGLE_CALENDAR_REDIRECT_URI=https://sauti.uk/api/v1/integrations/google-calendar/callback
GOOGLE_SHEETS_REDIRECT_URI=https://sauti.uk/api/v1/integrations/google_sheets/callback
HUBSPOT_REDIRECT_URI=https://sauti.uk/api/v1/integrations/hubspot/callback
SALESFORCE_REDIRECT_URI=https://sauti.uk/api/v1/integrations/salesforce/callback
```

Telnyx:

```text
Call Control webhook: https://sauti.uk/webhooks/telnyx/call-control
Media WebSocket env:  TELNYX_MEDIA_WEBSOCKET_BASE_URL=wss://sauti.uk/ws/telnyx/media
```

The WebSocket URL is not entered in the Telnyx dashboard. The backend sends it to Telnyx when answering a call.

## Analytics implementation

Analytics was implemented from `docs/analytics-plan.md`.

### Backend

Changed/added:

- `backend/src/main/java/com/sauti/analytics/AnalyticsDtos.java`
- `backend/src/main/java/com/sauti/analytics/AnalyticsService.java`
- `backend/src/main/java/com/sauti/api/AnalyticsController.java`
- `backend/src/main/java/com/sauti/call/CallRepository.java`

Endpoints now include:

- `GET /api/v1/analytics/summary`
- `GET /api/v1/analytics/daily`
- `GET /api/v1/analytics/by-language`
- `GET /api/v1/analytics/by-agent`
- `GET /api/v1/analytics/outcomes-by-day`
- `GET /api/v1/analytics/connect-rate-by-day`
- `GET /api/v1/analytics/funnel`
- `GET /api/v1/analytics/by-channel`
- `GET /api/v1/analytics/top-intents`
- `GET /api/v1/analytics/sentiment-by-day`
- `GET /api/v1/analytics/after-hours`
- `GET /api/v1/analytics/integration-events`

Backend analytics decisions:

- Query tenant-scoped calls by date/agent and aggregate in Java.
- This avoids PostgreSQL-specific grouping and preserves local test compatibility.
- Summary includes current-period values plus deltas vs the previous equal-length period.
- Connect rate is connected / attempted.
- Disconnected outcomes are currently: `failed`, `busy`, `no_answer`, `canceled`.
- Completed excludes active and disconnected outcomes.

### Dashboard

Changed/added:

- `dashboard/app/(console)/analytics/page.tsx`
- `dashboard/features/analytics/domain/date-ranges.ts`
- `dashboard/features/analytics/presentation/AnalyticsPage.tsx`
- `dashboard/features/analytics/presentation/AnalyticsPage.module.css`
- `dashboard/lib/api/analytics.ts`
- `dashboard/types/api.ts`
- `dashboard/features/dashboard/data/preview-data.ts`
- `dashboard/package.json`
- `dashboard/package-lock.json`

Dashboard analytics decisions:

- Use Recharts for charting.
- Keep Recharts imports inside the analytics feature so the chart bundle is scoped to `/analytics`.
- Use CSS modules for analytics-specific styling.
- Keep `page.tsx` thin and delegate to the feature component.
- Show a clear empty state for the call funnel when all values are zero.
- Agent dropdown should match the rounded, larger console dropdown style.

## Integration implementation state and decisions

The integration foundation exists:

- Workspace connections.
- Agent bindings.
- Encrypted credentials.
- Post-call jobs and delivery records.
- Delivery retries.

Provider-specific notes:

- Google Calendar is represented as a workspace connection with agent binding.
- Telnyx SMS is built in and does not require a customer connection record.
- Custom webhook supports HTTPS validation and authentication options.
- WhatsApp has legacy env fallback and workspace credential direction.
- Slack currently uses incoming webhooks.
- Google Sheets, HubSpot, and Salesforce have OAuth foundations.
- M-Pesa Daraja uses consumer key/secret/passkey; do not change it to a generic OAuth model.

Important follow-up:

- Ensure OAuth token refresh is used before Google Sheets, HubSpot, and Salesforce API calls. Access tokens expire quickly.
- HubSpot should upsert contacts and attach notes instead of blindly creating duplicates.
- Salesforce should avoid using a phone number as `LastName`.
- Slack notifications should move from plain text to Block Kit formatting.

## Known UI/design decisions

- Console pages use rounded cards, soft borders, subtle shadows, and high-contrast headings.
- Dropdowns should use the larger rounded style where they act as major filters.
- Empty states should explain why something is empty and what will populate it.
- Avoid showing raw UUIDs in business-owner-facing UI unless they are needed for debugging.

## Verification history

Recent verification commands that passed during the analytics work:

```powershell
.\gradlew.bat :backend:test
Push-Location dashboard
npm.cmd run typecheck
npm.cmd run build
npm.cmd audit --audit-level=high
Pop-Location
```

Recent production checks:

```powershell
curl.exe -i https://sauti.uk/health
curl.exe -I https://sauti.uk/analytics
```

Expected:

- `/health` returns `{"status":"UP"}`.
- `/analytics` redirects unauthenticated users to `/login?next=%2Fanalytics`.

## Change log

### 2026-07-08 - Conversational fallback for French LLM failures

- Replaced the repeated French apology fallback path with a context-aware recovery response.
- If the LLM/tool layer still fails after successful French transcription, the agent now asks a useful follow-up for appointment, verification, or introduction turns instead of saying it could not complete the request.
- This does not remove the server-side `Conversation turn failed` log; the root provider/tool issue remains diagnosable while the browser test call remains usable.
- Deployment:
  - Deployed commit `2a373f4`.
  - GitHub Actions CI passed.
  - Production deploy passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.ConversationOrchestratorTest --tests com.sauti.llm.SpringAiToolCallingLlmProviderContextTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Spring AI no-tool response fix

- Fixed the likely cause of repeated French fallback responses after successful transcription.
- `SpringAiToolCallingLlmProvider` now treats a null `AssistantMessage.getToolCalls()` value as no tool calls instead of throwing.
- Why: a normal conversational response such as "Bonjour Zachary..." may have text and no tool calls; throwing there caused the localized fallback even though the French transcript was understood.
- Added a regression test for no-tool Spring AI responses.
- Did not deploy.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/SpringAiToolCallingLlmProvider.java`
  - `backend/src/test/java/com/sauti/llm/SpringAiToolCallingLlmProviderContextTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.SpringAiToolCallingLlmProviderContextTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Spring AI tool callback hardening

- Diagnosed the French browser test issue: speech recognition is working, but the agent reaches the LLM/tool response layer and returns the localized fallback.
- Hardened Spring AI tool callbacks so unexpected internal callback invocation returns a controlled JSON error instead of throwing an exception.
- Why: Sauti executes tools through `ToolFulfillmentRouter` after the model returns tool calls. A callback throw can turn a valid French transcript into the generic/fallback agent response.
- Added a regression test for the Spring AI callback behavior.
- Did not deploy.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/SpringAiToolCallingLlmProvider.java`
  - `backend/src/test/java/com/sauti/llm/SpringAiToolCallingLlmProviderContextTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.SpringAiToolCallingLlmProviderContextTest --tests com.sauti.llm.ConversationOrchestratorTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - French voice turn fallback

- Hardened conversation orchestration so a valid transcribed voice turn returns a localized fallback response if the LLM/tool provider throws.
- This prevents browser test calls from showing the generic "agent could not respond" error after successful French speech recognition.
- Added a regression test covering a French turn where the LLM provider fails.
- Did not deploy.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.ConversationOrchestratorTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-07 - Onboarding voice previews

- Added a listen/pause preview control beside the onboarding voice selector.
- Filtered onboarding voice choices by the selected primary language so Swahili, English, French, and Arabic only show compatible voices.
- Clear the selected voice when the primary language changes and the previous voice is no longer compatible.
- Reused the existing `/api/v1/voices/{voiceId}/preview` audio endpoint with the selected primary language.
- Added optional preview text support to the voice preview endpoint so onboarding audio speaks the same generated greeting shown in the UI.
- Pauses active previews when the user changes voice or language, leaves provider default unpreviewable, and shows an inline playback error if audio fails.
- Polished the onboarding primary action button text and arrow icon alignment.
- Simplified onboarding booking setup to Google Calendar later or setup later; removed non-actionable Calendly/custom webhook choices from onboarding and clarified that webhooks are configured in the studio after draft creation.
- When Google Calendar is selected, finishing onboarding now creates the draft agent and immediately redirects to Google Calendar OAuth using the new agent ID.
- Fixed onboarding completion backend validation to accept the current business type cards, including `Clinics & healthcare`, and preserve healthcare-specific draft defaults for that label.
- Improved browser test-call speech handling: tiny/noisy clips are ignored before upload, no-speech copy matches hands-free capture, STT provider failures return a clear message instead of generic 500, TTS failures no longer block text responses, stale in-flight speech errors are ignored after ending a call, multi-language agents use prerecorded STT language detection instead of forcing the default language, and French browser-test STT uses a multilingual Whisper model instead of the default model.
- Did not deploy.
- Files touched:
  - `backend/src/main/java/com/sauti/api/VoiceCatalogController.java`
  - `backend/src/main/java/com/sauti/api/CallController.java`
  - `backend/src/main/java/com/sauti/agent/OnboardingCompletionService.java`
  - `backend/src/main/java/com/sauti/call/BrowserSpeechToTextService.java`
  - `backend/src/main/java/com/sauti/voice/VoiceCatalogService.java`
  - `backend/src/test/java/com/sauti/agent/OnboardingCompletionServiceTest.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.tsx`
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.css`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`
  - `npm.cmd run typecheck`
  - `npm.cmd run build`

### 2026-07-07 - Onboarding agent identity first

- Moved agent identity to the first onboarding step before business profile and calendar setup.
- Removed the hardcoded `Amina` default and placeholder from onboarding.
- Kept the preview generic until the user enters a name, then displays the chosen name.
- Disabled the first-step continue button until an agent name is entered.
- Did not deploy.
- Files touched:
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.tsx`
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.css`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build`

### 2026-07-07 - Onboarding business type suggestions

- Replaced broad onboarding business type choices with industry-specific Sauti starting points.
- Added choices for clinics and healthcare, salons and beauty, real estate, professional services, education, and local services.
- Updated the default selected business type so the first onboarding step still starts with a valid selected card.
- Did not deploy.
- Files touched:
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build`

### 2026-07-07 - Onboarding continue button hover affordance

- Improved the onboarding action button styling so it reads as clickable.
- Added onboarding-scoped hover, active, focus-visible, pointer, and disabled cursor states.
- Did not deploy.
- Files touched:
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.css`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build`

### 2026-07-07 — Documented agent operating rules and project handoff

- Replaced outdated Flutter-only `AGENTS.md` with accurate Spring Boot + Next.js instructions.
- Added this handoff document.
- Established the rule that every future meaningful change must update this handoff.
- Files touched:
  - `AGENTS.md`
  - `docs/agent-handoff.md`
- Verification:
  - Documentation-only change; no build required.

### 2026-07-07 — Analytics UI polish

- Fixed the call funnel zero-data state.
- Styled the analytics agent dropdown to match the larger rounded console dropdown controls.
- Deployed commit `9eee53e`.
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
  - GitHub Actions CI passed.
  - Production deploy passed.
  - `https://sauti.uk/health` returned `UP`.

### 2026-07-07 — Analytics dashboard with Recharts

- Added `recharts`.
- Replaced CSS-only analytics charts with Recharts components.
- Implemented charts for funnel, connect rate, outcomes, languages, channels, sentiment, top intents, after-hours, integration events, and latency.
- Deployed commit `c54d5cc`.
- Verification:
  - `.\gradlew.bat :backend:test`
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
  - `npm.cmd audit --audit-level=high`
  - GitHub Actions CI passed.
  - Production deploy passed.
  - `https://sauti.uk/health` returned `UP`.

### 2026-07-07 — Production callbacks changed to `sauti.uk`

- Replaced localhost and temporary tunnel callback values with `sauti.uk`.
- Added `deploy/update-public-callbacks-env.sh`.
- Deployed commit `0d4491b`.
- Verification:
  - Production env inspected without printing secrets.
  - Backend container env verified for public URL values.
  - GitHub Actions CI and deploy passed.
  - `https://sauti.uk/health` returned `UP`.

### 2026-07-06 — Managed services migration

- Externalized production Postgres to Neon.
- Externalized production Redis to Upstash.
- Externalized SMTP to Resend.
- Removed local Postgres/Redis services from production Compose.
- Migrated current production DB to Neon.
- Added helper scripts for service validation and migration.
- Fixed Neon backups to use PostgreSQL 18 client.
- Deployed commits:
  - `c9ce009`
  - `a9a4deb`
- Verification:
  - Neon/Upstash/Resend connectivity checks passed.
  - Migration restored 28 Flyway migrations.
  - Production health returned `UP`.
  - Neon backup test produced a valid dump.

### Earlier setup — Repository and initial production deployment

- Created public GitHub repo `Zacle/Sauti`.
- Configured CI/CD to deploy from `main`.
- Provisioned OVH VPS.
- Configured Docker, Caddy, DNS, TLS, GitHub Actions SSH deploy, and nightly backups.
- Production domain `https://sauti.uk` verified with health endpoint.

## Current known local state

- `docs/analytics-plan.md` may appear as untracked in some working trees. It was used as the plan for analytics. Decide explicitly whether to add it to git before staging.
- Do not delete server Docker volumes for old Postgres/Redis unless the user explicitly approves. They are rollback safety.

### 2026-07-08 - Multilingual browser speech auto-detect fixes

- Added server-side speech/silence commit logic for OpenAI realtime transcription so public Web Voice can stream continuously but still manually commit caller turns after a pause, as required by `gpt-realtime-whisper`.
- Serialized OpenAI realtime WebSocket send events to keep `input_audio_buffer.append` and `input_audio_buffer.commit` in order.
- Removed the forced OpenAI prerecorded transcription language hint for browser test/onboarding audio uploads so OpenAI can auto-detect spoken language instead of being biased by the agent default.
- Improved STT provider failure logs with response snippets for non-2xx responses.
- Wrapped the onboarding browser test turn after transcription so LLM/tool failures return a controlled API error instead of generic `Internal Server Error`.
- Files touched:
  - `backend/src/main/java/com/sauti/call/OpenAiRealtimeTranscriptionService.java`
  - `backend/src/main/java/com/sauti/call/BrowserSpeechToTextService.java`
  - `backend/src/main/java/com/sauti/api/CallController.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`
- Deployment:
  - Deployed commit `71b44f8`.
  - GitHub Actions CI passed.
  - Production deploy passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.

### 2026-07-08 - OpenAI realtime STT for multilingual Web Voice

- Restored public Web Voice sessions to realtime mode for every language; the turn-based non-English path is no longer selected by session start.
- Added `OpenAiRealtimeTranscriptionService` using the OpenAI Realtime transcription WebSocket for multilingual or non-English Web Voice agents when `OPENAI_API_KEY` is configured.
- English-only Web Voice continues to use the configured realtime STT provider, currently Deepgram in production.
- Added explicit OpenAI realtime transcription config defaults:
  - `OPENAI_REALTIME_URL`
  - `OPENAI_REALTIME_TRANSCRIPTION_MODEL`
  - `OPENAI_REALTIME_TRANSCRIPTION_DELAY`
- Why: Deepgram streaming treats `language` as a primary-language hint and the prior `language=multi` path was not reliable for hands-free multilingual detection. OpenAI realtime transcription supports streaming audio append events and final transcription events without forcing a language hint.
- Files touched:
  - `backend/src/main/java/com/sauti/call/OpenAiRealtimeTranscriptionService.java`
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `backend/src/main/java/com/sauti/api/PublicWebVoiceController.java`
  - `backend/src/main/resources/application.yml`
  - `.env.example`
  - `deploy/.env.production.example`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Deployed commit `181082e`.
  - GitHub Actions CI passed.
  - Production deploy passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.

### 2026-07-08 - Turn-based public Web Voice for non-English calls

- Added a turn-based public Web Voice mode for French, Swahili, and Arabic instead of routing those browser calls through Deepgram realtime STT.
- Public Web Voice session start now returns `mode`; English remains realtime, while non-English sessions return greeting audio and use explicit record/stop audio turns.
- Added public session endpoints to submit recorded audio turns and complete turn-based sessions with the existing Web Voice token.
- The backend reuses `BrowserSpeechToTextService`, so non-English public browser turns go through the OpenAI-first prerecorded STT route.
- Files touched:
  - `backend/src/main/java/com/sauti/api/PublicWebVoiceController.java`
  - `backend/src/main/java/com/sauti/call/WebVoiceDtos.java`
  - `dashboard/lib/api/public-web-voice.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `dashboard/features/web-voice/WebVoiceCall.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Deployed commit `c5d8302`.
  - GitHub Actions CI passed.
  - Production deploy passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.

### 2026-07-08 - OpenAI fallback for prerecorded speech recognition

- Added OpenAI transcription as a first-choice prerecorded STT route for non-English or multilingual browser/WhatsApp audio when `OPENAI_API_KEY` is configured.
- Kept Deepgram as the fallback provider, and added OpenAI fallback when Deepgram returns no transcript or fails for English-only audio.
- Added overridable OpenAI transcription model/URL settings with defaults.
- Why: French browser test calls were producing silence/no-transcript behavior even though the greeting and recording were correct, which points to the prerecorded STT provider route rather than the agent prompt or TTS.
- Files touched:
  - `backend/src/main/java/com/sauti/call/BrowserSpeechToTextService.java`
  - `backend/src/test/java/com/sauti/call/BrowserSpeechToTextServiceTest.java`
  - `backend/src/main/resources/application.yml`
  - `.env.example`
  - `deploy/.env.production.example`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.call.BrowserSpeechToTextServiceTest`
  - `.\gradlew.bat :backend:test`
- Deployment:
  - Deployed commit `bb56c97`.
  - GitHub Actions CI passed.
  - Production deploy passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.

### 2026-07-07 - Browser test-call manual mic fallback

- Added a manual mic record/stop control to the agent browser test-call panel.
- Kept automatic VAD capture, but manual recordings now stay open until the user taps stop so quiet accents, browser input processing, or non-English utterances are not lost before upload.
- Updated the listening helper copy and control layout for the extra mic button.
- Files touched:
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Not deployed yet.

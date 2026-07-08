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

### 2026-07-09 - STT language drift guard for long French calls

- Added the agent default language as an OpenAI prerecorded transcription hint for browser/test audio so French calls are less likely to drift into English, Arabic-script, or Portuguese artifacts.
- Added a conservative call-pipeline guard that locks onto a non-English call language after two caller turns and intercepts short obvious cross-language STT drift before it reaches the LLM.
- Drift recovery now responds in the locked call language with a brief clarification, or a localized goodbye if the noisy transcript looks like a closing phrase.
- Tightened live prompt rules so the model does not switch language for one unclear fragment and does not convert unclear name audio into a plausible-looking name.
- Why: user shared a French call transcript where late-call STT drift produced Arabic/English/Portuguese caller text even though the caller stayed in French, and the agent followed the bad transcript.
- Deployment:
  - Deployed commit `442b2bb` to production.
  - CI run `28982714803` passed.
  - Deploy production run `28982793365` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/call/BrowserSpeechToTextService.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/call/BrowserSpeechToTextServiceTest.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.call.BrowserSpeechToTextServiceTest --tests com.sauti.call.CallPipelineServiceTest --tests com.sauti.llm.ConversationOrchestratorTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-09 - Agent Studio test-call interruption capture

- Updated the Agent Studio browser test-call panel so caller speech can be captured while the agent is in the `thinking` state, not only while listening or speaking.
- Queued valid caller interruption audio recorded during an in-flight turn and processes it immediately after the current server turn returns.
- Skips stale agent audio/text playback when the caller interrupted during processing, so the browser test behaves closer to barge-in on a phone call.
- Kept manual mic capture available during `thinking` and `speaking`; during speaking it stops the current agent audio and records the caller.
- Expanded natural call-ending detection to include phrases like `no thank you`, `have a good day`, `non merci`, and `excellente journée`.
- Why: user tested against another platform and reported Sauti's browser test could not hear input while it displayed `Agent is thinking`, and calls did not end naturally unless the caller said `goodbye`.
- Deployment:
  - Deployed commit `f051702`.
  - GitHub Actions CI run `28980623157` passed.
  - GitHub Actions deploy run `28980709667` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.call.CallPipelineServiceTest`
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `.\gradlew.bat :backend:test`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`

### 2026-07-09 - Browser voice latency and template behavior tuning

- Reduced OpenAI realtime transcription commit timing for browser calls so final caller turns are emitted after a shorter silence window.
- Reduced spoken TTS chunk sizes so browser and phone TTS providers can start returning audio from shorter phrases.
- Added a shared `Live Voice Behavior` instruction block to all system templates through the system template seeder.
- Added Flyway migration `V29__agent_template_live_voice_behavior.sql` to append the same behavior block to existing published system template rows in the database.
- Why: user asked to make browser calls feel more like real calls and to improve saved template instructions that still sounded scripted or robotic.
- Deployment:
  - Deployed commit `eaf713f`.
  - GitHub Actions CI run `28978734351` passed.
  - GitHub Actions deploy run `28978814639` passed.
  - Deploy log built backend and dashboard images from `eaf713fd0e6fb00f6df778f7d330ef3ae84addc8` and reported the app healthy.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
  - Flyway migration `V29__agent_template_live_voice_behavior.sql` is included in the deployed backend; startup health passing indicates the migration did not fail.
- Files touched:
  - `backend/src/main/java/com/sauti/call/OpenAiRealtimeTranscriptionService.java`
  - `backend/src/main/java/com/sauti/call/SentenceChunker.java`
  - `backend/src/main/java/com/sauti/agent/SystemAgentTemplateSeeder.java`
  - `backend/src/main/resources/db/migration/V29__agent_template_live_voice_behavior.sql`
  - `backend/src/test/java/com/sauti/agent/SystemAgentTemplateSeederTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.agent.SystemAgentTemplateSeederTest --tests com.sauti.call.SentenceChunkerTest --tests com.sauti.call.DefaultTwilioMediaStreamServiceTest --tests com.sauti.llm.ConversationOrchestratorTest`
  - `.\gradlew.bat :backend:test`
  - `curl.exe -i https://sauti.uk/health`
  - `curl.exe -I https://sauti.uk/analytics`

### 2026-07-09 - Sync Cartesia production secret during deploy

- Added `CARTESIA_API_KEY` to the repository's GitHub Actions secrets from local `.env` without printing the key.
- Updated the production deploy workflow to copy that secret into `/opt/sauti/.env.production` as `CARTESIA_API_KEY` before running Docker Compose.
- Why: production `/api/v1/voices` showed only `enabledProviders:["elevenlabs"]`; Cartesia was working locally but was missing from the VPS environment.
- Deployment:
  - Deployed commit `953d95a`.
  - GitHub Actions CI run `28977239109` passed.
  - GitHub Actions deploy run `28977325184` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
  - `https://sauti.uk/api/v1/voices` returned `enabledProviders:["elevenlabs","cartesia"]`, with 351 Cartesia voices including 36 French and 15 Arabic voices.
- Files touched:
  - `.github/workflows/deploy.yml`
  - `docs/agent-handoff.md`
- Verification:
  - Workflow-only code change; no local build required.
  - `curl.exe -i https://sauti.uk/health`
  - `curl.exe -I https://sauti.uk/analytics`
  - `curl.exe -s https://sauti.uk/api/v1/voices -o D:\tmp\sauti-voices.json`, then parsed provider/language counts locally.

### 2026-07-09 - Voice picker empty-state clarity and accent menu fix

- Replaced the native browser accent `<select>` in Agent Studio with an in-app accent menu so the dropdown no longer renders as a blue browser overlay on Windows.
- Kept accent options scoped to the currently visible language set and the current filter state.
- Updated the French/Arabic empty-state copy to identify the real provider condition when Cartesia is not enabled in the backend environment.
- Verified production `GET https://sauti.uk/api/v1/voices` currently returns `enabledProviders:["elevenlabs"]`, so Cartesia credentials are not present in `/opt/sauti/.env.production` even though local `.env` has a working `CARTESIA_API_KEY`.
- Why: user screenshot showed French/Arabic have zero voices after native-language filtering and the accent control visually leaked the browser dropdown.
- Deployment:
  - Deployed commit `f5c7209`.
  - GitHub Actions CI run `28976193826` passed.
  - GitHub Actions deploy run `28976306027` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `dashboard/features/agents/AgentCreator/VoicePicker.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`
- Known follow-up:
  - Add `CARTESIA_API_KEY` to production `/opt/sauti/.env.production` and redeploy/restart backend so French and Arabic Cartesia voices appear.

### 2026-07-08 - Strict native-language voice filtering and accent picker polish

- Changed ElevenLabs catalog mapping so English-origin voices no longer appear as French or Arabic choices merely because the provider marks them multilingual.
- French and Arabic voice tabs now require native/origin language alignment for ElevenLabs; Cartesia voices continue to come from per-language Cartesia catalog requests.
- Updated the Agent Studio voice picker so the accent selector shows accents for the currently visible language set, resets invalid accent filters, and makes the whole accent control clickable with clearer alignment.
- Why: user reported French/Arabic tabs were filled with English-accent voices speaking French/Arabic and that the accent control looked misaligned and did not respond clearly.
- Deployment:
  - Deployed commit `495250b`.
  - GitHub Actions CI run `28975050216` passed.
  - GitHub Actions deploy run `28975131811` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/voice/VoiceCatalogService.java`
  - `dashboard/features/agents/AgentCreator/VoicePicker.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`

### 2026-07-08 - Cartesia voice catalog and Azure removal

- Added Cartesia TTS support with a backend client for voice previews and realtime audio routing when the selected voice ID is prefixed with `cartesia:`.
- Changed the voice catalog to load ElevenLabs and Cartesia only, request Cartesia voices per supported language (`en`, `fr`, `ar`), and rank professional/support/assistant-style voices first.
- Removed Azure Speech from the voice catalog, realtime TTS routing, environment examples, and tests.
- Removed Swahili from new agent/onboarding/template language validation and from onboarding/Agent Studio language selectors.
- Updated onboarding and Agent Studio voice labels so the user sees only ElevenLabs and Cartesia provider choices.
- Verified the local Cartesia API key by calling `/voices` without printing the secret; Cartesia returned professional candidates including Skylar, Gemma, and Daniel.
- Why: user wants to prioritize high-quality voices, remove Azure and Swahili from the product setup path, and load professional Cartesia voices after adding the env values.
- Deployment:
  - Deployed commit `8b53ce5`.
  - GitHub Actions CI run `28973540075` passed.
  - GitHub Actions deploy run `28973644535` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/call/CartesiaRealtimeTextToSpeechClient.java`
  - `backend/src/main/java/com/sauti/call/ElevenLabsRealtimeTextToSpeechProvider.java`
  - `backend/src/main/java/com/sauti/voice/VoiceCatalogService.java`
  - `backend/src/main/java/com/sauti/agent/AgentService.java`
  - `backend/src/main/java/com/sauti/agent/AgentTemplateService.java`
  - `backend/src/main/java/com/sauti/agent/AgentDraftGenerationService.java`
  - `backend/src/main/java/com/sauti/agent/OnboardingCompletionService.java`
  - `backend/src/main/java/com/sauti/agent/SystemAgentTemplateSeeder.java`
  - `backend/src/main/java/com/sauti/api/PublicWebVoiceController.java`
  - `backend/src/main/java/com/sauti/call/BrowserSpeechToTextService.java`
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/agents/AgentCreator/VoicePicker.tsx`
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.tsx`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `dashboard/types/api.ts`
  - `.env.example`
  - `deploy/.env.production.example`
  - `.github/workflows/production-diagnostics.yml`
- Verification:
  - `.\gradlew.bat :backend:test`
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`
- Known follow-ups:
  - Existing production agents that still store `azure:` or `sw` values should be migrated or edited before live use.
  - Public marketing/demo copy still mentions Swahili in non-console content; it was not changed in this provider-focused pass.

### 2026-07-08 - Voice quality and onboarding prompt improvements

- Changed live opening generation and fallback openings so the agent introduces itself by name once at the start of the call.
- Reduced browser-test endpointing minimum from 800ms to 450ms and lowered live LLM response token cap from 160 to 120.
- Expanded onboarding-generated prompts with category-specific workflows for clinics/healthcare, salons, real estate, professional services, education, and local services.
- Changed generated draft/opening directions and stored template opening directions to introduce `{{agent_name}}` once.
- Changed local AI draft fallback defaults to English/French/Arabic instead of Swahili/English.
- Broadened ElevenLabs catalog exposure by using ElevenLabs `verified_languages` for every voice and no longer hiding non-curated ElevenLabs voices.
- Removed the unused curated voice ID config from example/local configuration because it no longer filters ElevenLabs voices.
- Browser test calls now send the currently selected voice to the backend and persist it before the first greeting, so the first test call uses the selected voice instead of requiring another call.
- Why: user reported no agent-name introduction, slow replies, too few French/non-English ElevenLabs choices, shallow category prompts, and selected onboarding/studio voice not being used until a later call.
- Deployment:
  - Deployed commit `923cd2e`.
  - GitHub Actions CI run `28969972797` passed.
  - GitHub Actions deploy run `28970080318` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/llm/SpringAiToolCallingLlmProvider.java`
  - `backend/src/main/java/com/sauti/voice/VoiceCatalogService.java`
  - `backend/src/main/java/com/sauti/agent/Agent.java`
  - `backend/src/main/java/com/sauti/agent/OnboardingCompletionService.java`
  - `backend/src/main/java/com/sauti/agent/AgentDraftGenerationService.java`
  - `backend/src/main/java/com/sauti/agent/SystemAgentTemplateSeeder.java`
  - `backend/src/main/java/com/sauti/call/CallDtos.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/main/java/com/sauti/api/CallController.java`
  - `backend/src/main/resources/application.yml`
  - `dashboard/lib/api/calls.ts`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `.env.example`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`

### 2026-07-08 - Prioritize ElevenLabs voice selection

- Reordered the backend voice catalog so ElevenLabs voices are returned before Azure voices when ElevenLabs is configured.
- Changed onboarding's default primary language from Swahili to English and reordered the language choices to English, French, Arabic, Swahili.
- Changed onboarding voice selection to prefer ElevenLabs-compatible voices for the selected language and only show Azure voices as fallback when no ElevenLabs voices are available for that language.
- Changed the agent-studio voice picker ranking so ElevenLabs voices sort above Azure voices, with Azure treated as a fallback provider.
- Why: production diagnostics showed the French browser test was using Azure `fr-FR-VivienneMultilingualNeural`; the product should prioritize voice quality through ElevenLabs, with Swahili less important than high-quality English/French/Arabic.
- Deployment:
  - Deployed commit `af6d2cc`.
  - GitHub Actions CI run `28968030066` passed.
  - GitHub Actions deploy run `28968128673` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/voice/VoiceCatalogService.java`
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.tsx`
  - `dashboard/features/agents/AgentCreator/VoicePicker.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`

### 2026-07-08 - Catalog/browser-test TTS diagnostics

- Added safe TTS diagnostics for `VoiceCatalogService.generateAudio`, which is used by dashboard browser test-call audio, voice previews, WhatsApp audio synthesis, and turn-based public Web Voice responses.
- The log reports catalog TTS engine, language, voice ID, resolved ElevenLabs model ID, and whether the voice is Azure-prefixed.
- Updated the production diagnostics workflow to include `Generating catalog TTS audio` lines.
- Why: a production diagnostics run after a user browser test showed no realtime TTS lines, indicating the tested path did not use `RealtimeTextToSpeechProvider`.
- Deployment:
  - Deployed commit `cc01f0c`.
  - GitHub Actions CI run `28965824605` passed.
  - GitHub Actions deploy run `28965923246` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/voice/VoiceCatalogService.java`
  - `.github/workflows/production-diagnostics.yml`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Manual production voice diagnostics workflow

- Added a manually dispatched production diagnostics workflow that SSHes to the VPS with existing GitHub deploy secrets and reads filtered backend logs.
- The workflow only prints selected voice-agent diagnostic patterns, including realtime TTS provider/model lines and common STT/TTS/conversation failure markers.
- Why: after a real test call, local SSH access was unavailable from the agent sandbox, so production log inspection needed a controlled GitHub Actions path.
- Deployment:
  - Deployed commit `91ad69b`.
  - GitHub Actions CI run `28964193674` passed.
  - GitHub Actions deploy run `28964289061` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `.github/workflows/production-diagnostics.yml`
  - `docs/agent-handoff.md`
- Verification:
  - Manual diagnostics workflow run `28964379588` passed for `since=1h`, `tail=4000`.
  - The first diagnostics run printed no matching TTS lines, likely because the test call happened before the workflow deployment and the subsequent deploy restarted the backend container.

### 2026-07-08 - Realtime TTS diagnostics

- Added safe realtime TTS diagnostics at the provider boundary.
- ElevenLabs sessions now log the selected provider, actual speech engine, language, requested/resolved voice ID, resolved ElevenLabs model ID, and whether an Azure-prefixed voice routed through Azure.
- Azure Speech sessions now log the provider voice ID plus speaking-rate and pitch metadata.
- No API keys, secrets, or spoken text are logged.
- Why: the user needed to confirm whether live voice calls are actually using ElevenLabs, Azure, or an Azure-prefixed voice inside the ElevenLabs provider path.
- Deployment:
  - Deployed commit `c99f318`.
  - GitHub Actions CI run `28963358729` passed.
  - GitHub Actions deploy run `28963466205` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/call/ElevenLabsRealtimeTextToSpeechProvider.java`
  - `backend/src/main/java/com/sauti/call/AzureRealtimeTextToSpeechClient.java`
  - `backend/src/test/java/com/sauti/call/ElevenLabsRealtimeTextToSpeechProviderTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.call.ElevenLabsRealtimeTextToSpeechProviderTest --tests com.sauti.call.AzureRealtimeTextToSpeechClientTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Voice recovery hardening and faster turn timing

- Changed no-tool recovery to use plain spoken history only, stripping tool-call and tool-result messages before retrying the LLM. This avoids provider rejection of malformed tool history after a tool/provider failure.
- Replaced the spoken French hard fallback "petit souci" with phone-native repair prompts, including a specific slow-repeat request for unclear phone numbers.
- Limited live conversation history sent to the model to the most recent 12 messages to reduce prompt size and turn latency.
- Added today's date in the agent's business timezone to the live system prompt and instructed the agent not to offer past appointment dates or guess dates when the caller asks generally about availability.
- Reduced live LLM max output tokens from 220 to 160 and Deepgram realtime `utterance_end_ms` default from 700ms to 500ms.
- Why: a French test call still surfaced the hard fallback after availability/phone-number turns, offered an impossible May date, and had 2-4 second response delays.
- Deployment:
  - Deployed commit `92ced35`.
  - GitHub Actions CI run `28962267120` passed.
  - GitHub Actions deploy run `28962369514` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/llm/SpringAiToolCallingLlmProvider.java`
  - `backend/src/main/java/com/sauti/call/DeepgramRealtimeSpeechToTextProvider.java`
  - `backend/src/main/resources/application.yml`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `deploy/.env.production.example`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.ConversationOrchestratorTest --tests com.sauti.llm.SpringAiToolCallingLlmProviderContextTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Stronger prompt priority and faster voice turn defaults

- Added a final runtime priority reminder to the live voice system prompt so platform booking/safety rules override conflicting saved agent prompts, templates, examples, and prior assistant messages.
- This specifically blocks normal booking calls from collecting date of birth, medical history, insurance, symptoms, or other sensitive fields even when an older saved healthcare prompt asks for them.
- Reduced live LLM max output tokens from 384 to 220 because phone replies should be short and lower token caps reduce worst-case response time.
- Reduced Deepgram realtime `utterance_end_ms` default from 1000ms to 700ms and exposed `DEEPGRAM_UTTERANCE_END_MS=700` in the production env example.
- Why: a new French test call still asked for DOB, showing the saved agent prompt could still win. The user also reported slow agent replies.
- Deployment:
  - Deployed commit `4f16aeb`.
  - GitHub Actions CI run `28958509733` passed.
  - GitHub Actions deploy run `28958624192` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/llm/SpringAiToolCallingLlmProvider.java`
  - `backend/src/main/java/com/sauti/call/DeepgramRealtimeSpeechToTextProvider.java`
  - `backend/src/main/resources/application.yml`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `deploy/.env.production.example`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.ConversationOrchestratorTest --tests com.sauti.llm.SpringAiToolCallingLlmProviderContextTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Runtime booking prompt override and French fallback greeting

- Strengthened the live voice system prompt so platform booking policy overrides older saved agent prompts that ask for date of birth, medical history, insurance, symptoms, or other sensitive fields.
- Added an explicit rule that booking times remain proposed/preferred until a booking tool succeeds; the agent must not say a booking is confirmed or transmitted without tool confirmation.
- Changed French and English opening fallback greetings to shorter phone-native openings instead of "this is [agent], I am listening" style fallbacks.
- Why: a fresh French test call still asked for date of birth and confirmed an appointment without visible tool confirmation, indicating older saved agent instructions could still override the newer onboarding prompt. The greeting also sounded less natural than the target call style.
- Deployment:
  - Deployed commit `2d22c24`.
  - GitHub Actions CI run `28956344509` passed.
  - GitHub Actions deploy run `28956446759` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.ConversationOrchestratorTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Voice-agent target style evaluation and booking flow prompt

- Added `docs/voice-agent-evaluation.md` with a target French appointment-booking scenario, scoring rubric, release bar, and regression prompts based on the natural call style the user wants Sauti to match.
- Tightened the live conversation prompt so confused callers are repaired gently, unclear names/phones/emails are repeated slowly, service/hour questions are grounded in configured facts, and appointment booking follows a predictable phone-native order.
- Tightened onboarding-generated booking prompts so new agents collect service, full name, date, time preference, then contact detail.
- Explicitly prevents default healthcare onboarding agents from asking for date of birth, medical history, insurance, symptoms, or other sensitive details unless configured.
- Updated draft-generation prompt requirements and local fallback prompts with the same booking-order and sensitive-information rules.
- Why: user compared Sauti against a more natural appointment-booking call and asked to proceed with improving toward that target style.
- Deployment:
  - Deployed commit `6b70e92`.
  - GitHub Actions CI run `28954910896` passed.
  - GitHub Actions deploy run `28955044727` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/AgentDraftGenerationService.java`
  - `backend/src/main/java/com/sauti/agent/OnboardingCompletionService.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `docs/voice-agent-evaluation.md`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.ConversationOrchestratorTest --tests com.sauti.agent.OnboardingCompletionServiceTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Conversation quality guardrails for live voice turns

- Documented and confirmed current LLM routing:
  - Standard voice turns use `SAUTI_LLM_DEFAULT_MODEL`, default `gemini-2.5-flash`.
  - Advanced agents use `SAUTI_LLM_ADVANCED_MODEL`, default `gpt-4o-mini`, when `OPENAI_API_KEY` is configured, then fall back to the standard Gemini model if advanced is unavailable.
- Lowered live voice-turn temperature from `0.65` to `0.45` to reduce decorative phrasing and unsupported improvisation.
- Removed the prompt contradiction that told the agent not to switch languages while also telling it to follow language switches.
- Added stricter prompt rules:
  - do not invent business facts,
  - do not claim callbacks/bookings/messages/transfers were completed without tool confirmation,
  - validate phone numbers instead of accepting unclear sequences,
  - avoid pretending to have personal feelings or a human day.
- Added `hi` and `hey` to English language detection so short English switches in a French call are recognized.
- Ignored punctuation-only/no-speech transcripts such as `.` before they reach the LLM, preventing fake filler responses.
- Why: a French test conversation showed robotic phrasing, language-switch friction, accepted invalid callback details, and unsupported claims about services/follow-up.
- Deployment:
  - Deployed commit `275fe13`.
  - GitHub Actions CI run `28951904633` passed.
  - Production deploy run `28952035247` passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/llm/SpringAiToolCallingLlmProvider.java`
  - `backend/src/main/java/com/sauti/nlp/SimpleLanguageDetector.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `backend/src/test/java/com/sauti/nlp/SimpleLanguageDetectorTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.ConversationOrchestratorTest --tests com.sauti.call.CallPipelineServiceTest --tests com.sauti.nlp.SimpleLanguageDetectorTest --tests com.sauti.llm.SpringAiToolCallingLlmProviderContextTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Onboarding voice preview language match

- Fixed onboarding voice previews so the preview request only uses the selected primary language for the selected voice.
- Replaced the fixed English preview sentence with localized preview text for English, French, Swahili, and Arabic.
- Removed the onboarding preview fallback to English/first voice language, so incompatible voices do not silently preview in the wrong language.
- Why: selected onboarding languages such as French, Swahili, and Arabic could still play an English sample sentence, making the preview sound mismatched even when the voice itself supported the selected language.
- Deployment:
  - Deployed commit `bbec33f`.
  - GitHub Actions CI run `28949541469` passed.
  - Production deploy run `28949665462` passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`

### 2026-07-08 - AI-generated opening greetings from direction

- Changed onboarding, agent-studio templates, system template seeding, and AI draft generation so `greetingMessage` stores opening direction instead of exact words for the agent to say.
- Added LLM-generated opening creation at browser test-call and public Web Voice call startup. The generated opening is saved as the first call turn.
- Updated public Web Voice HTTP and WebSocket startup to reuse the saved generated opening instead of re-rendering the agent greeting template.
- Changed the onboarding preview from a quoted generated greeting to an opening-direction preview, and separated voice-preview sample text from call-opening behavior.
- Why: hardcoded onboarding greetings do not scale to multiple languages or contexts. The runtime model should decide the actual opening based on language, channel, business context, and call situation.
- Deployment:
  - Deployed commit `e2fe57a`.
  - GitHub Actions CI run `28942549653` passed.
  - Production deploy run `28942648365` passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/AgentDraftGenerationService.java`
  - `backend/src/main/java/com/sauti/agent/OnboardingCompletionService.java`
  - `backend/src/main/java/com/sauti/agent/SystemAgentTemplateSeeder.java`
  - `backend/src/main/java/com/sauti/api/PublicWebVoiceController.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.call.CallPipelineServiceTest --tests com.sauti.agent.SystemAgentTemplateSeederTest --tests com.sauti.agent.OnboardingCompletionServiceTest --tests com.sauti.llm.ConversationOrchestratorTest`
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - More natural greetings and multilingual voice options

- Replaced onboarding and agent-studio default greetings with shorter, more conversational openings that include `{{agent_name}}`.
- Updated onboarding preview greetings so they use the typed agent name before the draft is created.
- Expanded the Azure voice catalog with higher-quality French HD/multilingual candidates and kept native Swahili, Arabic, and African English options.
- Normalized ElevenLabs language labels such as `fra`, `ara`, and `swa` to Sauti's `fr`, `ar`, and `sw` filters.
- Added per-language ElevenLabs model overrides:
  - `ELEVENLABS_MODEL_ID_EN`
  - `ELEVENLABS_MODEL_ID_FR`
  - `ELEVENLABS_MODEL_ID_AR`
  - `ELEVENLABS_MODEL_ID_SW`
- Changed the default ElevenLabs model fallback from deprecated Turbo naming to `eleven_flash_v2_5`; examples suggest `eleven_multilingual_v2` for French/Arabic and `eleven_v3` for Swahili trials.
- Included the local conversation-orchestrator prompt/fallback polish that was already in the working tree, so tool failures are framed more naturally and tests match the new fallback text.
- Why: the previous greetings sounded like IVR scripts, and non-English voice quality needed better native/provider options without hardcoding one provider for every language.
- Deployment:
  - Deployed commit `7cdfcfd`.
  - GitHub Actions CI run `28941106228` passed.
  - Production deploy run `28941211654` passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `.env.example`
  - `deploy/.env.production.example`
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/java/com/sauti/agent/OnboardingCompletionService.java`
  - `backend/src/main/java/com/sauti/call/ElevenLabsRealtimeTextToSpeechProvider.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/voice/VoiceCatalogService.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/agents/AgentCreator/VoicePicker.tsx`
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.agent.OnboardingCompletionServiceTest --tests com.sauti.call.AzureRealtimeTextToSpeechClientTest`
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Manual production app-data reset workflow

- Added a manually dispatched GitHub Actions workflow for destructive production app-data resets.
- The workflow requires the exact confirmation string `RESET PRODUCTION APP DATA`.
- Reset scope:
  - Drops and recreates Neon PostgreSQL `public` schema.
  - Flushes the configured Upstash Redis database.
  - Removes and recreates Docker volume `sauti_recordings-data`.
  - Deletes `/opt/sauti/backups/sauti-*.dump` database backup files.
  - Restarts production Compose services and verifies `https://sauti.uk/health` through the VPS.
- Preserves `.env.production`, Caddy TLS/config volumes, Docker images, source checkout, and old local Postgres/Redis rollback volumes.
- Why: user requested a production restart with all saved app data and other persistent app storage erased.
- Deployment:
  - Workflow added and pushed in commit `5bcb1c3`.
  - Production data reset workflow run `28939910638` passed.
  - Follow-on production deploy workflow run `28939983064` passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `.github/workflows/production-data-reset.yml`
  - `docs/agent-handoff.md`
- Verification:
  - `gh run watch 28939910638 --exit-status`
  - `gh run watch 28939983064 --exit-status`
  - `curl.exe -i https://sauti.uk/health`
  - `curl.exe -I https://sauti.uk/analytics`

### 2026-07-08 - No-tool LLM recovery for static French fallback replies

- Added a no-tool retry path in the conversation orchestrator when a tool-enabled LLM turn fails.
- The first retry asks the configured LLM for a conversational response with tools disabled; the explicit localized technical fallback is now only used if both the tool-enabled request and no-tool retry fail.
- Stopped writing the final technical fallback into live conversation memory, so later turns are not polluted by repeated apology text.
- Why: French browser test calls were saving real caller transcripts but repeated the same backend fallback response, making conversations look static/scripted even when speech recognition worked.
- Deployment:
  - Not deployed yet.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.ConversationOrchestratorTest --tests com.sauti.llm.SpringAiToolCallingLlmProviderContextTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Advanced LLM tier fallback for healthcare onboarding agents

- Fixed another cause of French test-call fallback responses after successful transcription.
- Healthcare onboarding drafts are created with `llmTier=advanced`; `SpringAiToolCallingLlmProvider` previously threw when the advanced OpenAI model was unavailable.
- The provider now treats Advanced as a preferred model path and falls back to the standard Gemini model if OpenAI is not configured or if the advanced request fails.
- Why: a missing or failing advanced provider should not make the whole voice turn fail when the standard AI provider is available.
- Deployment:
  - Deployed commit `1996a22`.
  - GitHub Actions CI passed.
  - Production deploy passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/SpringAiToolCallingLlmProvider.java`
  - `backend/src/test/java/com/sauti/llm/SpringAiToolCallingLlmProviderContextTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.SpringAiToolCallingLlmProviderContextTest --tests com.sauti.llm.ConversationOrchestratorTest`
  - `.\gradlew.bat :backend:test`

### 2026-07-08 - Restore AI-driven French turns and sanitize tool schemas

- Removed the scripted/context-aware French fallback that tried to answer appointment, verification, and introduction turns without a successful LLM response.
- Restored the transparent localized fallback for actual LLM/provider failures so failed turns do not pretend to be AI-driven.
- Sanitized Spring AI tool input schemas before provider submission by stripping nested `format` hints such as `phone` and `date-time`.
- Why: French STT is now producing correct transcripts, so the repeated fallback happens after transcription in the LLM/tool provider path. Unsupported JSON schema `format` hints are a likely provider-side failure point when active tools are attached.
- Deployment:
  - Deployed commit `5752573`.
  - GitHub Actions CI passed.
  - Production deploy passed.
  - `https://sauti.uk/health` returned `UP`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/llm/SpringAiToolCallingLlmProvider.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `backend/src/test/java/com/sauti/llm/SpringAiToolCallingLlmProviderContextTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.SpringAiToolCallingLlmProviderContextTest --tests com.sauti.llm.ConversationOrchestratorTest`
  - `.\gradlew.bat :backend:test`

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

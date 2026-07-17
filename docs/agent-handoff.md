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

1. A human maintainer or separately authorized source-control automation reviews, commits, and pushes to `main`. Coding agents leave changes uncommitted and do not push.
2. GitHub Actions runs CI:
   - backend Gradle tests/build
   - dashboard typecheck/build
3. Deploy workflow SSHes into the VPS.
4. Server checks out the exact commit that passed CI under `/opt/sauti/source`.
5. Server builds Docker images locally and runs `docker compose up -d`.
6. Health check verifies `https://sauti.uk/health`.

Release policy:

- Production releases happen only through `.github/workflows/ci.yml` followed by `.github/workflows/deploy.yml`.
- Coding agents must not commit, push, open PRs, manually dispatch/bypass deployment, SSH to production to release code, run `deploy/deploy.sh` directly, run production Docker Compose commands, or copy application files to the server.
- When asked to deploy, a coding agent verifies the change and hands the uncommitted working tree to the maintainer. After an external push, the agent may perform read-only CI/CD monitoring and public health verification.

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
CALENDLY_REDIRECT_URI=https://sauti.uk/api/v1/integrations/calendly/callback
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

### 2026-07-17 - Canonical calendar prompt settings and structured selectors

- Fixed stale appointment prompts that still rendered `Set up later` after Google Calendar was connected. Runtime prompt resolution now treats the agent's calendar provider and routing policy as canonical, overriding obsolete onboarding-variable values.
- Google Calendar enablement and startup reconciliation now synchronize all three representations together: agent settings, prompt variables, and calendar tool credentials. Google Calendar selects `Fixed calendar` routing automatically; disconnecting the active Google destination resets both settings to `Set up later`.
- Internal structured-variable updates now update the corresponding agent fields as well as the visible variable value, preventing future drift regardless of which settings screen initiated the change.
- Replaced free-text calendar destination and meeting-routing inputs in Agent Studio personalisation with accessible selected option cards. Calendar choices automatically select the valid routing default, use clearer customer-facing labels, and collapse to one column on small screens.
- Shared the structured option definitions between the Agent Studio drawer and the dedicated business-details screen, and made both screens initialize from canonical agent settings. The calendar readiness link now targets the correct `google_calendar` provider.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/AgentVariableService.java`
  - `backend/src/main/java/com/sauti/integration/IntegrationService.java`
  - `backend/src/test/java/com/sauti/agent/AgentVariableServiceTest.java`
  - `backend/src/test/java/com/sauti/integration/IntegrationServiceTest.java`
  - `dashboard/features/agents/domain/structured-agent-settings.ts`
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `dashboard/features/agents/AgentVariables/AgentVariablesPage.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - targeted `AgentVariableServiceTest` and `IntegrationServiceTest` - passed.
  - `.\gradlew.bat :backend:test` - passed.
  - `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
  - `git diff --check` - passed before the handoff update.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: visual browser automation was unavailable in the coding environment. The production build validates the component and CSS, but the option-card layout should receive a quick signed-in desktop/mobile smoke test after CI/CD deployment.

### 2026-07-17 - Unified Google Calendar agent integration state

- Fixed the Google Calendar inconsistency where the integration marketplace could show a workspace connection enabled for an agent while Agent Studio reported `Not connected`. Agent Studio now reads the same tenant-scoped agent-integration binding used by the marketplace instead of independently inferring connection state from legacy tool fields.
- Enabling Google Calendar for an agent now also links that workspace's encrypted Calendar credential to all Calendar booking tools (`check_availability`, `book_slot`, `reschedule_booking`, and `cancel_booking`) and sets the agent's active calendar provider. Disabling or disconnecting it clears those runtime tool links, so the displayed state and actual call behavior cannot diverge.
- Added an application-start reconciliation for bindings created before these two state paths were synchronized. Valid enabled bindings are repaired automatically; bindings without a usable connected workspace credential are safely disabled and logged instead of remaining falsely enabled.
- Kept connection lookup tenant-scoped when building integration responses.
- The Agent Studio `Manage`/`Connect` action now opens the selected agent's Google Calendar configuration in the marketplace.
- Files touched:
  - `backend/src/main/java/com/sauti/integration/IntegrationRepositories.java`
  - `backend/src/main/java/com/sauti/integration/IntegrationService.java`
  - `backend/src/test/java/com/sauti/integration/IntegrationServiceTest.java`
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/integrations/IntegrationsPage/IntegrationsPage.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests "com.sauti.integration.IntegrationServiceTest"` - passed.
  - `.\gradlew.bat :backend:test` - passed.
  - `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: a legacy enabled binding that has no usable tenant Calendar credential will be disabled at startup and must be reconnected through Google OAuth. A real Google account should still be used to test availability and event creation before recording the verification demo.

### 2026-07-17 - Transcript-gated voice turns and deterministic silence handling

- Fixed unsolicited consecutive agent turns in OpenAI Realtime and OpenAI + Cartesia hybrid calls. Realtime sessions now set `create_response=false`; Sauti requests `response.create` only after a usable final caller transcript is received. Empty VAD/transcription failures no longer advance the conversation.
- Added a shared backend caller-transcript guard for blank, punctuation-only, and explicit non-speech captions while retaining valid short replies such as “Oui”, “Mhm”, and phone numbers. The browser Realtime client applies the equivalent guard before requesting a response.
- Raised Realtime VAD thresholds. Telephony barge-in now requires recognized transcript content (streaming delta or accepted final transcript), while browser calls use a 180 ms sustained-speech debounce. Provider VAD noise can no longer immediately interrupt playback; validated speech cancels the current OpenAI response and Cartesia stream in a deterministic order before the next response is requested.
- Fixed phone silence tracking. Raw inbound media frames—including Telnyx's configured idle-silence frames—no longer count as caller activity. The silence clock resets only for accepted caller transcripts, DTMF input, and agent playback boundaries, and it does not advance while the agent is speaking.
- Standardized the default unattended-call flow: prompt once after 30 seconds without an accepted reply, then end after a further 30 seconds. New agents use 30/60-second defaults; Flyway `V32` migrates only agents still using the original untouched 10/600-second default pair.
- Applied the same accepted-transcript and 30-second minimum reminder policy to cascaded browser voice sessions. Browser partial transcripts can participate in barge-in timing without resetting the silence clock.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/Agent.java`
  - `backend/src/main/java/com/sauti/call/CallerTranscriptGuard.java`
  - `backend/src/main/java/com/sauti/call/DefaultTwilioMediaStreamService.java`
  - `backend/src/main/java/com/sauti/call/OpenAiRealtimeService.java`
  - `backend/src/main/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProvider.java`
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `backend/src/main/resources/db/migration/V32__default_silence_turn_policy.sql`
  - affected backend voice-runtime tests
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - browser/test call presentation and default-setting files
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test` - passed.
  - targeted telephony turn-order and media-stream tests after the final concurrency adjustment - passed.
  - `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow; production must apply Flyway `V32` through that workflow.
- Known follow-up/risk: provider VAD behavior must be validated with a quiet real phone call and with low-volume French/Arabic speech. If a carrier path has unusually low input gain, tune the provider threshold deliberately rather than re-enabling automatic response creation.

### 2026-07-17 - Production Docker build storage hardening

- Fixed the infrastructure failure where Gradle could not write its binary store or `last-build.bin` during the backend Docker build. The application `bootJar` itself remains healthy; the failure occurred in exhausted or unsafe Docker/Gradle build storage.
- The backend Docker build now uses a BuildKit cache mount with locked sharing for Gradle's user home, writes the per-build project cache to disposable `/tmp` storage, and copies sources with explicit `gradle` ownership. This prevents concurrent cache writers and avoids persisting fragile project state under `/workspace/.gradle`.
- Production deployment now reclaims stale BuildKit cache, dangling images, and superseded commit-tagged Sauti images before building. It preserves the currently deployed image for rollback, performs a more aggressive unused-build-cache cleanup only when storage is low, and fails early with a clear message when less than 4 GiB remains.
- After a successful release, cleanup retains both the newly deployed tag and the immediately previous rollback tag.
- Files touched:
  - `Dockerfile`
  - `deploy/deploy.sh`
  - `docs/agent-handoff.md`
- Verification:
  - `bash -n deploy/deploy.sh` - passed.
  - `.\gradlew.bat :backend:bootJar --no-daemon` - passed.
  - `git diff --check` - passed.
  - The Dockerfile could not be executed locally because Docker Desktop was not running; CI/CD must exercise the BuildKit cache mount on the production builder.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: the next CI/CD run will intentionally discard old unused build state and may take longer if dependencies must be downloaded again. If the preflight still reports less than 4 GiB, the VPS filesystem itself must be expanded or non-Docker data must be reviewed by the maintainer rather than weakening the guard.

### 2026-07-17 - Spring test mock annotation migration

- Replaced Spring Boot's deprecated `@MockBean` usages with Spring Framework's supported `@MockitoBean` test-context override annotation. This removes the Spring Boot 3.4+ removal warning without changing the mocked service behavior.
- Files touched:
  - `backend/src/test/java/com/sauti/AuthAgentFlowTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `./gradlew.bat :backend:test --tests "com.sauti.AuthAgentFlowTest"` - passed.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-ups/risks: none for this migration; no deprecated `@MockBean` or `@SpyBean` usage remains under `backend/src/test/java`.

### 2026-07-17 - Google Calendar and Sheets verification readiness

- Closed the verification-critical Google integration gaps without adding Google Drive access or other broader OAuth scopes.
- Calendar availability now uses each agent's configured weekly operating hours, including closed and overnight days, rather than a fixed 09:00-17:00 window.
- Added booking duration persistence and synchronized Google Calendar lifecycle operations: create, reschedule with `PATCH`, and cancel with `DELETE`. Both the REST booking API and voice-agent tools use the same provider-aware path.
- Added `reschedule_booking` and `cancel_booking` voice tools and an application-start backfill so existing agents receive newly introduced default tools. Connected Calendar credentials and enabled Sheets lookup state are propagated to the new tools.
- Added workspace Calendar ID selection (`primary` by default), live Calendar free/busy testing, and an integration dialog for configuring/testing the selected calendar.
- Added real Google Sheets connection testing against the configured spreadsheet/range. The integration Test action now includes the selected agent so OAuth runtime configuration is validated against Google, not only checked for non-empty fields.
- Added a caller-confirmed `update_google_sheet_row` tool. It finds the configured lookup value and replaces the matching A1 row with `USER_ENTERED`; post-call append and during-call lookup remain available.
- Disabling Google Calendar now deactivates its tools. Disconnecting the workspace connection also clears Calendar credentials from affected agent tools so a disconnected agent cannot keep using Google.
- Added clearer Google Sheets configuration placeholders for the spreadsheet ID, A1 range, lookup column, returned columns, and append columns.
- Added Flyway migration `V31__booking_duration.sql`; production deployment must run it through the normal CI/CD release.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/OperatingHoursSchedule.java`
  - `backend/src/main/java/com/sauti/api/BookingController.java`
  - `backend/src/main/java/com/sauti/api/GoogleCalendarIntegrationController.java`
  - `backend/src/main/java/com/sauti/api/IntegrationController.java`
  - `backend/src/main/java/com/sauti/calendar/*`
  - `backend/src/main/java/com/sauti/integration/DuringCallIntegrationFulfillment.java`
  - `backend/src/main/java/com/sauti/integration/IntegrationService.java`
  - `backend/src/main/java/com/sauti/integration/ProviderOAuthService.java`
  - `backend/src/main/java/com/sauti/tool/*`
  - `backend/src/main/resources/db/migration/V31__booking_duration.sql`
  - `backend/src/test/java/com/sauti/AuthAgentFlowTest.java`
  - `backend/src/test/java/com/sauti/agent/OperatingHoursScheduleTest.java`
  - `backend/src/test/java/com/sauti/calendar/GoogleCalendarProviderTest.java`
  - `dashboard/features/dashboard/data/preview-data.ts`
  - `dashboard/features/integrations/IntegrationsPage/IntegrationsPage.tsx`
  - `dashboard/lib/api/bookings.ts`
  - `dashboard/lib/api/integrations.ts`
  - `dashboard/types/api.ts`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test` (successful; 137 tests)
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location` (successful)
  - `Push-Location dashboard; npm.cmd run build; Pop-Location` (successful; 50 routes generated)
  - `git diff --check` (successful)
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-up / risk:
  - A real Google OAuth account was not available to the coding agent, so the new live-test controls must be exercised against a dedicated verification Calendar and Sheet before recording the Google review video.
  - Calendar selection is workspace-connection scoped. Changing it affects agents sharing that Google Calendar credential.
  - Sheets row updates deliberately replace a complete configured row and require explicit caller confirmation. Arbitrary spreadsheet creation/deletion is intentionally not implemented because Sauti does not need it and should not claim it during verification.
  - Keep the declared scopes limited to `calendar.events`, `calendar.freebusy`, and `spreadsheets`; do not add full Google Drive access.

### 2026-07-16 - Persisted agent business identity and personalisation drawer refinement

- Fixed the remaining account-name leak in saved exact greetings. When an older greeting contains the tenant/account business name literally, call startup now replaces it with the agent's filled `business_name`; generated/instruction-based greetings continue to use the same agent-scoped identity.
- Changed the personalisation drawer's primary action from a close-only `Done` button to a real `Save details` action for existing agents. It persists existing and newly added variables, refreshes readiness, reports save failures in the drawer, and only then closes. New-agent details still remain in the creation draft and are saved when the agent is created.
- Redesigned the drawer to match the dark Agent Studio: wider glass panel, stronger hierarchy and progress summary, readable field cards, dark structured service/hours editors, explicit save guidance, responsive mobile layout, and consistent add-variable controls.
- Added regression coverage for a legacy exact greeting such as `Sarah de Tranquil AI` resolving to the agent business `X-Fit`.
- Files touched:
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `dashboard/features/agents/AgentVariables/AddVariableForm.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests "com.sauti.call.CallPipelineServiceTest"` (successful)
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location` (successful)
  - `Push-Location dashboard; npm.cmd run build; Pop-Location` (successful; 50 routes generated)
  - `git diff --check`
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-up / risk: callers created before this correction will receive the right business identity on their next new call; already-persisted call transcripts are historical records and are intentionally unchanged.

### 2026-07-16 - OpenAI Realtime + Cartesia for production phone calls

- Added a server-side OpenAI Realtime WebSocket conversation provider for Twilio, SignalWire, and Telnyx media calls. Phone audio is converted from the existing 16 kHz internal PCM stream to the Realtime API's 24 kHz PCM input, so both μ-law Twilio and L16 Telnyx calls share one provider path.
- Real phone calls with a saved `cartesia:` voice now use OpenAI Realtime for native audio understanding, transcription, server VAD, reasoning, conversation context, and tool calls, then stream short speakable text phrases through the existing Cartesia Sonic connection. This removes Deepgram and chat-completions from the normal phone-turn critical path.
- Kept the previous Deepgram -> OpenAI text LLM -> Cartesia pipeline as an automatic fallback. It is selected when Realtime is disabled/unconfigured, when the initial WebSocket connection fails, or once if an established Realtime WebSocket disconnects.
- Wired agent interruption sensitivity and STT endpointing into phone Realtime server VAD. Caller speech immediately clears queued Twilio/Telnyx media and closes the active Cartesia generation so the agent stops over-talking the caller.
- Preserved caller-before-agent transcript ordering when OpenAI returns response text before the asynchronous input transcription event. Interrupted partial agent responses are persisted but are not restarted through Cartesia.
- Reused the existing tenant/agent-scoped Realtime tool router for live bookings and integrations. DTMF selections are inserted into the same Realtime conversation rather than starting a separate chat-completions turn.
- Added deterministic inbound greetings to phone-call persistence and direct Cartesia playback. The greeting plays while the Realtime and Cartesia sockets warm concurrently, and it is seeded into Realtime conversation context without asking the model to regenerate it.
- Added `OPENAI_REALTIME_WEBSOCKET_URL` and the guarded `OPENAI_REALTIME_TELEPHONY_ENABLED` switch. The switch defaults to `true`; clearing the OpenAI key or setting it to `false` leaves phone calls on the cascade.
- Files touched for this change:
  - `.env.example`
  - `deploy/.env.production.example`
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/java/com/sauti/call/TelephonyRealtimeConversationProvider.java`
  - `backend/src/main/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProvider.java`
  - `backend/src/main/java/com/sauti/call/OpenAiRealtimeService.java`
  - `backend/src/main/java/com/sauti/call/DefaultTwilioMediaStreamService.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/test/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProviderTest.java`
  - `backend/src/test/java/com/sauti/call/OpenAiRealtimeServiceTest.java`
  - `backend/src/test/java/com/sauti/call/DefaultTwilioMediaStreamServiceTest.java`
- Verification:
  - `\.\gradlew.bat :backend:test --tests "com.sauti.call.OpenAiRealtimeServiceTest" --tests "com.sauti.call.OpenAiTelephonyRealtimeConversationProviderTest" --tests "com.sauti.call.DefaultTwilioMediaStreamServiceTest" --tests "com.sauti.call.CallPipelineServiceTest"`
  - `\.\gradlew.bat :backend:test` (126 tests passed)
  - `git diff --check`
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-up / risk: automated transport and routing tests pass, but a real Twilio/Telnyx call is still required after CI/CD to measure end-of-speech to first-audio latency and tune each agent's saved endpointing/interruption values. Production must have funded OpenAI Realtime access and a working Cartesia key; the cascade remains available if Realtime cannot connect.

### 2026-07-15 - Voice picker control typography and focus refinement

- Standardized the voice-engine tabs on the Agent Studio font stack, including consistent title, count, and supporting-copy weights.
- Reworked the voice search focus state into a single restrained teal ring with an illuminated search icon; removed the nested input outline that produced the heavy double border.
- Replaced the accent selector's invisible full-surface overlay with an accessible trigger button containing a vertically aligned label, selected value, and animated chevron.
- Updated Cartesia's engine description to reflect the OpenAI Realtime + Cartesia Sonic hybrid runtime instead of the superseded cascaded STT/LLM/TTS path.
- Files touched:
  - `dashboard/features/agents/AgentCreator/VoicePicker.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build` (50 routes generated successfully)
  - `git diff --check`
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-15 - Pinned homepage feature scrollytelling

- Rebuilt the five homepage product chapters as a pinned scrollytelling sequence: the feature viewport stays fixed while scrolling advances the product panels, then releases naturally into the call-lifecycle section after the final feature.
- Added eased vertical panel transitions with a short reading hold around every feature, an updating `01 / 05` counter, and a scroll-progress rail so the interaction remains understandable.
- Made each transition more noticeable with a restrained overshoot-and-return bounce, a springing product preview, a bouncing feature icon, an active-card glow, and an animated down-arrow scroll cue. Settle animations fire once per newly active feature instead of looping continuously.
- Preserved subtle independent depth movement inside each panel for the copy, product preview, ambient orbit, and light particles.
- Scheduled scroll measurements through `requestAnimationFrame`; inactive panels are removed from keyboard and accessibility navigation while pinned.
- Added normal stacked-layout fallbacks for viewports at or below 900px and for `prefers-reduced-motion`, including cleanup when either condition changes while the page is open.
- Replaced root horizontal `overflow-x: hidden` with `overflow-x: clip` and removed clipping from the homepage container. This preserves horizontal overflow protection without creating the ancestor scroll containers that prevent `position: sticky` from pinning in Chrome.
- Standardized the capability row so Safe workflows and Complete visibility now match the first three cards in height, top alignment, opacity, internal spacing, and hover behavior instead of appearing shorter and vertically offset.
- Files touched:
  - `dashboard/hooks/useRevealMotion.ts`
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.tsx`
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.module.css`
  - `dashboard/styles/reset.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location` (50 routes generated successfully; homepage first-load JS 122 kB)
  - Live Chrome DevTools Protocol scroll inspection at 1440x804: sticky container remained at `top: 0` mid-sequence, feature three was active at the midpoint, feature five was active at the end, and the container released after the feature section.
  - `git diff --check`
- Deployment status: not deployed. Changes are intentionally uncommitted for maintainer review and the normal CI/CD path.
- Known follow-up: full-page screenshot tools may represent pinned scrollytelling sections differently because they stitch multiple scroll positions; verify the interaction through normal viewport scrolling. The live desktop scroll coordinates, responsive CSS fallbacks, and reduced-motion behavior were verified.

### 2026-07-14 - Luminous glass marketing homepage refinement

- Refined the homepage visual system around the actual Sauti product previews rather than introducing fabricated dashboard screenshots or unsupported customer claims.
- Added a layered teal aurora, subtle perspective grid, animated voice signal, live-agent indicator, stronger hero dashboard glow, translucent glass cards, and improved CTA lighting.
- Alternated the feature narrative and product preview placement on desktop so the feature journey has more visual rhythm while retaining a simple stacked order on smaller screens.
- Applied the glass treatment consistently to capability, workflow, use-case, security, outcome, and FAQ surfaces, with reduced-motion fallbacks for the new ambient animation.
- Updated the marketing navigation to use a translucent blurred header that fits the revised homepage while remaining shared across public marketing routes.
- Files touched:
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.tsx`
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.module.css`
  - `dashboard/styles/marketing/foundation.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
  - Local 1440px headless Chrome render of `/` (checked hero, capability band, and initial feature sequence; corrected hero preview clipping found during this pass)
  - `git diff --check`
- Deployment status: not deployed. Changes are intentionally uncommitted for maintainer review and the normal CI/CD path.
- Known follow-up: narrow-mobile visual QA is recommended after maintainer review; the desktop render, production build, and responsive breakpoints pass.

### 2026-07-14 - OpenAI-primary conversational LLM with Gemini fallback

- Changed the Spring AI conversation provider so every live agent turn uses OpenAI first, independent of the agent's former standard/advanced tier value.
- Added automatic Gemini fallback for synchronous turns and for streaming turns that fail before OpenAI emits its first text chunk.
- Deliberately do not replay a streaming turn through Gemini after OpenAI has already emitted text, because doing so would make the voice agent repeat or contradict partially spoken output.
- Added explicit `SAUTI_LLM_PRIMARY_MODEL` and `SAUTI_LLM_FALLBACK_MODEL` settings. Existing installations remain compatible: `SAUTI_LLM_ADVANCED_MODEL` is the OpenAI-primary alias and `SAUTI_LLM_DEFAULT_MODEL` is the Gemini-fallback alias when the new variables are absent.
- Updated defaults and examples to OpenAI `gpt-5.4-mini` primary and Gemini `gemini-3.1-flash-lite` fallback. Both `OPENAI_API_KEY` and `GOOGLE_AI_API_KEY` are required in `spring-ai` mode so failover is actually available.
- Gemini embeddings and non-conversational knowledge retrieval were not changed.
- Files touched:
  - `.env.example`
  - `deploy/.env.production.example`
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/java/com/sauti/llm/SpringAiToolCallingLlmProvider.java`
  - `backend/src/test/java/com/sauti/llm/SpringAiToolCallingLlmProviderContextTest.java`
  - `backend/src/test/java/com/sauti/llm/SpringAiToolCallingLlmProviderFailoverTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests "com.sauti.llm.SpringAiToolCallingLlmProviderContextTest" --tests "com.sauti.llm.SpringAiToolCallingLlmProviderFailoverTest"`
  - `.\gradlew.bat :backend:test`
  - `git diff --check`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-13 - Homepage hierarchy, readability, and conversion pass

- Rebalanced the hero around a wider, deliberately grouped headline and a dashboard preview reduced by roughly 6%, aligned with the headline rather than the eyebrow.
- Standardized every primary and secondary conversion action to `Start free trial` and `Book a demo`; strengthened hero and final-CTA sizing, reassurance copy, and glow treatment.
- Replaced the repeated audience strip and separate four-card business section with one eight-card `Built for every customer-facing team` section covering workflows and industries.
- Reworked all five product chapters to use the same step number, icon, heading, description, benefit label, and preview padding structure; increased muted-text contrast across previews, cards, trust indicators, FAQ, navigation, and footer.
- Prioritized 24/7 availability, multilingual conversations, and real-time actions in the differentiator band; renamed `One view` to `Complete visibility` and visually reduced secondary benefits.
- Added a continuous connector and stronger arrow nodes to the six-step call workflow, ending in `Track results`.
- Expanded and clarified the security band with buyer-facing language: encrypted data, secure isolation, reliable systems, human handoff, and agent controls.
- Removed generic quote-style pseudo-testimonials after repository review found no verified customer identities, logos, or measured outcomes. Replaced them with honest, measurable product outcome categories rather than fabricating social proof.
- Grouped FAQ questions by setup, phone numbers, languages, handoffs, data/privacy, and trial/workflows while retaining two desktop columns and one mobile column.
- Rebuilt the marketing footer into Product, Solutions, Industries, Resources, Company, and Legal columns with documentation, API reference, status, support, and social links.
- Files touched:
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.tsx`
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.module.css`
  - `dashboard/features/marketing/MarketingChrome/MarketingChrome.tsx`
  - `dashboard/styles/marketing/foundation.css`
  - `dashboard/styles/marketing/sections.css`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build` (50 routes generated successfully; homepage first-load JS 121 kB)
  - `git diff --check`
  - Live local desktop and responsive browser captures reviewed at the active breakpoints.
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-13 - Reference-matched homepage visual consolidation

- Consolidated the homepage's accumulated style overrides into one responsive, reference-led system with a stronger split hero, larger product dashboard, clearer product chapters, compact workflow/use-case grids, a security band, FAQ, and final call to action.
- After direct screenshot comparison, widened the desktop canvas from 1280px to 1400px, rebuilt the hero preview with setup progress and launch checks, shortened product showcase rows, increased chapter and embedded-UI typography by roughly 20-30%, and removed much of the excess vertical space between sections. This corrected the initial pass, which still looked too close to the previous homepage and made feature content difficult to read.
- Replaced the product showcase internals after a second live review showed that resizing the old previews was not enough: chapter copy columns are now wider and substantially larger, agent setup includes the current channel warning and two-column fields, calls include filters/timestamps/transcript metadata, and integration cards include real descriptions and actions. Chapter numbering now matches the reference's single-digit guide markers.
- Added the reference's compact audience and capability bands, but used supported product capabilities and customer-facing team categories instead of fabricated customer logos, performance results, testimonials, or compliance certifications.
- Increased the legibility and depth of all code-native product previews while retaining the existing console visual language, subtle reveal motion, animated light sweep, and reduced-motion behavior.
- Aligned desktop and mobile navigation calls to action with the homepage's free-trial path and updated the footer year.
- Files touched:
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.tsx`
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.module.css`
  - `dashboard/features/marketing/MarketingChrome/MarketingChrome.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build` (50 routes generated successfully; homepage first-load JS 120 kB)
  - `git diff --check`
- Visual QA note:
  - Captured the live local homepage at 1440px desktop widths after the proportional correction and compared both the fold and full-page rhythm against the supplied reference.
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-13 - Reference-led homepage composition refinement

- Removed the experimental pinned feature stack after browser review showed that it obscured product surfaces and introduced excessive scroll distance. The five product chapters are again distinct, dense showcase rows matching the supplied reference's readable product-tour structure.
- Increased the split hero's scale, dashboard prominence, typography, atmospheric line work, and visual depth so the product proof point is the dominant element above the fold.
- Removed the substitute capability band and the separate industry/integration sections after visual comparison showed that they changed the reference's overall silhouette. Integration proof remains inside the fifth product chapter.
- Refined product rows with numbered guide markers, stronger copy hierarchy, larger embedded console surfaces, tighter section spacing, richer cards, and a more dimensional security/CTA treatment.
- The final page rhythm now follows the requested hero â†’ five product chapters â†’ call lifecycle â†’ business uses â†’ security â†’ FAQ â†’ CTA structure. Testimonials, customer-logo claims, and marketing metric tiles remain excluded as previously requested.
- Files touched:
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.tsx`
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run build` (50 routes generated successfully; homepage first-load JS 119 kB)
  - `npm.cmd run typecheck`
  - `git diff --check`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-11 - Realtime ElevenLabs model migration and French call termination

- Changed French and Arabic ElevenLabs realtime model examples from `eleven_multilingual_v2` to `eleven_flash_v2_5`; updated the ignored local default from deprecated-equivalent Turbo v2.5 to Flash v2.5.
- Added a guarded CI/CD migration that rewrites missing, Multilingual v2, or Turbo v2.5 realtime model overrides to Flash v2.5 for the base, French, and Arabic settings.
- Why: Sarah's ElevenLabs-backed French call completed STT/LLM but produced no first audio, while Cartesia-backed Amélie worked; Flash v2.5 is ElevenLabs' recommended low-latency realtime/Agents model.
- Expanded conversation-ending recognition with the exact observed French phrase `excellent jour à vous`, normalized/no-accent variants, and the common STT spelling `orevoir`.
- Added regression assertions proving those French closing utterances terminate a call, preventing post-goodbye silence reminders and repeated farewells.
- Files touched:
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `.env.example`
  - `deploy/.env.production.example`
  - `deploy/deploy.sh`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`
  - `bash -n deploy/deploy.sh`
  - `git diff --check`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: after CI/CD, retest Sarah's ElevenLabs voice and confirm Amélie's call closes immediately after the first farewell finishes playing.

### 2026-07-11 - Operational realtime provider defaults

- Changed the application defaults from no-op realtime providers to Deepgram STT and ElevenLabs TTS; no-op providers remain available only when explicitly selected for tests or intentionally silent environments.
- Added `SAUTI_STT_STREAMING_PROVIDER=deepgram` and `SAUTI_TTS_STREAMING_PROVIDER=elevenlabs` to local and production environment templates.
- Updated the local ignored `.env` from `noop` to `deepgram` without exposing or changing provider credentials.
- Added a guarded CI/CD deploy migration that changes a missing/`noop` production streaming provider only when the matching Deepgram or ElevenLabs credential is already nonblank.
- Removed ElevenLabs `auto_mode` from the WebSocket URL because Sauti supplies an explicit chunk schedule and flush protocol; the mixed modes could produce no audio.
- Added ElevenLabs JSON error parsing so provider rejection details reach the existing TTS error path instead of being ignored until the first-audio watchdog fires.
- Why: Amélie could speak through Cartesia but could never hear the caller because STT was explicitly configured as `noop`; Sarah used ElevenLabs and returned no frames under the incompatible/opaque WebSocket configuration.
- Files touched:
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/java/com/sauti/call/ElevenLabsRealtimeTextToSpeechProvider.java`
  - `.env.example`
  - `deploy/.env.production.example`
  - `deploy/deploy.sh`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.call.ElevenLabsRealtimeTextToSpeechProviderTest`
  - `.\gradlew.bat :backend:test`
  - `bash -n deploy/deploy.sh`
  - `git diff --check`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: after CI/CD, confirm the production startup log selects Deepgram/ElevenLabs and run one ElevenLabs-backed and one Cartesia-backed French test call.

### 2026-07-11 - Realtime TTS protocol correction and first-audio state

- Fixed the ElevenLabs realtime WebSocket adapter to decode and forward base64 PCM from JSON `audio` messages; it previously handled only binary frames and final markers, causing silent calls stuck in a speaking state.
- Fixed Cartesia streaming synthesis to reuse one `context_id` across all text fragments in a turn, send `continue=true` for intermediate fragments, close the context on flush, and allocate a new context only for the next turn.
- Changed Web Voice speaking state so the browser receives `speaking=true` only after the first actual PCM frame, rather than when a synthesis request is merely queued.
- Added a versioned six-second first-audio watchdog that cancels a stalled synthesis request and reports a recoverable playback error instead of leaving the panel indefinitely stuck.
- Added a regression test proving ElevenLabs JSON audio frames reach the PCM listener.
- Why: most selected ElevenLabs voices produced no audible browser audio because their JSON audio payload was discarded, while Cartesia voices could speak fragmented/inconsistent content because every streamed text fragment used an unrelated synthesis context.
- Files touched:
  - `backend/src/main/java/com/sauti/call/ElevenLabsRealtimeTextToSpeechProvider.java`
  - `backend/src/main/java/com/sauti/call/CartesiaRealtimeTextToSpeechClient.java`
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `backend/src/test/java/com/sauti/call/ElevenLabsRealtimeTextToSpeechProviderTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.call.ElevenLabsRealtimeTextToSpeechProviderTest --tests com.sauti.llm.SpringAiToolCallingLlmProviderContextTest`
  - `.\gradlew.bat :backend:test`
  - `git diff --check`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: confirm both an ElevenLabs-backed voice and a `cartesia:` voice in Agent Studio after CI/CD; provider/network behavior cannot be fully exercised by unit tests.

### 2026-07-11 - Resilient test-call recognition and spoken-text parity

- Changed Web Voice STT routing so agents whose supported languages are entirely English/French use the configured realtime STT provider (Deepgram in production) instead of being sent to OpenAI merely because they are multilingual.
- Kept OpenAI realtime transcription for agents containing languages outside the Deepgram English/French route.
- Added automatic STT recovery: failed initial connections and runtime transcription errors reconnect through the configured fallback provider, with two recovery attempts before the browser receives a fatal speech-recognition error.
- Normalized Spring AI streaming responses so cumulative provider updates are converted into true incremental suffixes before being sent to TTS; ordinary delta streams continue unchanged.
- Accumulated the exact text submitted to realtime TTS and used that text for the browser `agent_response`, including multi-step tool turns, so the displayed response matches what the caller heard.
- Added a regression test covering cumulative-to-incremental stream normalization.
- Why: French hands-free test calls could become permanently deaf after an OpenAI realtime transcription failure, and streamed/tool-loop speech could differ from the final response shown in the transcript.
- Files touched:
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `backend/src/main/java/com/sauti/llm/SpringAiToolCallingLlmProvider.java`
  - `backend/src/test/java/com/sauti/llm/SpringAiToolCallingLlmProviderContextTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:compileJava`
  - `.\gradlew.bat :backend:test`
  - `git diff --check`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: production provider failover and spoken/displayed parity should be confirmed with a French hands-free call after CI/CD deployment.

### 2026-07-11 - Dark new-agent template selection

- Restyled `/agents/new` template selection to match the dark Agents console instead of rendering the original light composer and cards over a dark shell.
- Increased heading and description contrast, added a teal focus treatment to the role composer, and styled its icon, character count, enabled/disabled generation actions, and generation-progress state.
- Rebuilt the template section heading, category tabs, template cards, badges, icons, descriptions, actions, hover elevation, and blank-agent state with dark navy surfaces and restrained teal accents.
- Added responsive two-column and single-column template layouts with horizontally scrollable filters on narrow screens.
- Why: the page had nearly invisible dark text and large white/grey surfaces that did not match the surrounding console, as shown in the supplied full-page screenshot.
- Files touched:
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
  - `git diff --check`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: none.

### 2026-07-11 - Consistent workspace switcher and integration selector alignment

- Removed the Agents-only sidebar ordering rule that moved the workspace/business switcher below navigation and footer actions; Agents now keeps the switcher directly below the Sauti brand like the other console pages.
- Reset Agents navigation/footer ordering to the shared console order while preserving the Agents dark theme and active navigation treatment.
- Fixed the Integration marketplace agent selector icon collision by restricting legacy absolute SVG positioning to direct legacy field wrappers instead of icons nested inside the Radix `DarkSelect` trigger.
- Hardened the shared `DarkSelect` trigger with explicit icon, value, and chevron grid columns plus component-owned static SVG positioning, so the selected agent name remains vertically aligned with the bot icon regardless of surrounding header styles.
- Tightened the marketplace header height and vertically centered its content, while giving the agent selector a stable 300 px width, 56 px control height, aligned icons, and consistent rounded-console styling.
- Why: the Agents sidebar unexpectedly moved the business identity to the bottom, and the integration agent icon overlapped the selected agent name.
- Files touched:
  - `dashboard/styles/console.css`
  - `dashboard/features/integrations/IntegrationsPage/IntegrationsPage.module.css`
  - `dashboard/components/DarkSelect/DarkSelect.tsx`
  - `dashboard/components/DarkSelect/DarkSelect.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
  - `git diff --check`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: none.

### 2026-07-11 - Realtime Agent Studio test calls and greeting correction

- Corrected the immediate greeting optimization so instruction-style `greetingMessage` values are never spoken verbatim; fixed scripts remain supported, while generation directions now use a deterministic localized agent/business introduction.
- Added a regression test reproducing the instruction text shown in the Agent Studio screenshot.
- Extended authenticated test-call startup responses with a short-lived signed Web Voice token and WebSocket URL tied to the test call and agent.
- Allowed the existing Web Voice session service to host signed `test` calls as well as public `web` calls without weakening tenant access: test tokens are issued only by the authenticated call endpoint and match the persisted agent UUID.
- Migrated the Agent Studio hands-free panel from its serialized MediaRecorder upload path to the same persistent 16 kHz PCM WebSocket used by realtime Web Voice, including partial/final transcripts, streamed PCM playback, barge-in audio clearing, speaking state, and call-ending events.
- Kept typed-message fallback and recording upload behavior, while removing the redundant manual-record control from the hands-free UI.
- Redesigned the active test-call panel with larger readable messages, single-surface conversation bubbles, clearer live/thinking/speaking states, improved spacing, stronger contrast, and a simpler composer.
- Why: the prior greeting optimization exposed greeting-generation instructions, and the Agent Studio panel was still using the old prerecorded STT/full-LLM/full-TTS HTTP loop, so it did not benefit from the realtime latency work and still took 5-6 seconds before playback.
- Files touched:
  - `backend/src/main/java/com/sauti/api/CallController.java`
  - `backend/src/main/java/com/sauti/call/CallDtos.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `dashboard/types/api.ts`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:compileJava`
  - `.\gradlew.bat :backend:test --tests com.sauti.call.CallPipelineServiceTest`
  - `.\gradlew.bat :backend:test`
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
  - `git diff --check`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-ups and risks:
  - Typed messages intentionally retain the request/response fallback; spoken hands-free turns use the realtime WebSocket.
  - Browser and production provider latency still require validation after CI/CD with the existing `Voice latency` logs.

### 2026-07-11 - Realtime voice latency pipeline

- Fixed Deepgram turn delivery so `speech_final=true` immediately flushes the accumulated caller transcript instead of waiting for the later `UtteranceEnd` event; kept ordinary `is_final` fragments buffered so mid-thought segments are not submitted prematurely.
- Raised and runtime-clamped Deepgram `utterance_end_ms` to its supported 1,000 ms minimum while retaining the faster agent-configured endpointing signal as the primary turn trigger.
- Added streaming LLM turns to the provider/orchestrator contract and implemented Gemini token streaming through Spring AI, including tool-enabled turns and confirmed tool-result loops.
- Connected streamed model text directly to the existing ElevenLabs WebSocket for telephone and browser calls, enabled ElevenLabs `auto_mode`, and retained the complete-response fallback for advanced OpenAI turns, alternate providers, and test doubles.
- Replaced per-call LLM-generated browser greetings with immediate deterministic/configured greetings, including localized identity fallbacks for non-default languages.
- Added adaptive OpenAI transcription commits: short turns use a 450 ms silence window while longer dictated turns use 800 ms so callers can pause while providing names, addresses, or numbers.
- Added structured production logs for LLM first text, complete turn latency, and TTS first audio without logging response content.
- Added a Deepgram regression test covering immediate `speech_final` delivery and updated the preferred-language greeting assertion.
- Why: production agents were taking 5-7 seconds to respond because confirmed Deepgram speech was buffered until a later event, greetings invoked an LLM synchronously, and the full LLM response completed before TTS received any text.
- Files touched:
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/main/java/com/sauti/call/DeepgramRealtimeSpeechToTextProvider.java`
  - `backend/src/main/java/com/sauti/call/DefaultTwilioMediaStreamService.java`
  - `backend/src/main/java/com/sauti/call/ElevenLabsRealtimeTextToSpeechProvider.java`
  - `backend/src/main/java/com/sauti/call/OpenAiRealtimeTranscriptionService.java`
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/llm/LlmToolCallingProvider.java`
  - `backend/src/main/java/com/sauti/llm/SpringAiToolCallingLlmProvider.java`
  - `backend/src/main/resources/application.yml`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `backend/src/test/java/com/sauti/call/DeepgramRealtimeSpeechToTextProviderTest.java`
  - `deploy/.env.production.example`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.call.CallPipelineServiceTest --tests com.sauti.call.DefaultTwilioMediaStreamServiceTest --tests com.sauti.call.ElevenLabsRealtimeTextToSpeechProviderTest --tests com.sauti.llm.ConversationOrchestratorTest`
  - `.\gradlew.bat :backend:test`
  - `git diff --check`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-ups and risks:
  - Advanced-tier OpenAI text turns use the compatibility complete-response path; standard Gemini turns use token streaming.
  - Actual carrier/provider latency must be validated after CI/CD using the new `Voice latency` log stages; no local test can reproduce production network geography or provider queues.
  - If streamed model output ever includes formatting despite the voice prompt, add an incremental spoken-text sanitizer before TTS rather than buffering the full response again.

### 2026-07-11 - Routing and numeric hover-state correction

- Added explicit dark default, hover, focus, disabled, and browser-control styling for numeric inputs and suffix fields used by silence reminders, reminder limits, timeouts, and similar configuration values.
- Fully darkened the Routing operating-hours editor: mode tabs, schedule container, day rows, checkboxes, time inputs, closed states, and row hover behavior.
- Fully darkened outside-hours behavior cards with distinct default, hover, selected, radio-indicator, description, and message-field states.
- Why: these wrapper components had independent light backgrounds that were not covered by the generic field theme and reverted to white on interaction.
- Files touched:
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: none.

### 2026-07-11 - Complete Agent Studio surface audit and Radix sliders

- Audited the supplied screenshots across Behavior, Speech, Routing, Integrations, Knowledge, and Post-call and explicitly replaced the remaining light nested surfaces with dark component states.
- Corrected conversation intelligence cards, prompt source/view controls, prompt and retention guidance, disabled transfer inputs, integration icons/status/actions, knowledge statistics/budget/chunks/document cards/upload controls, empty states, and post-call rows.
- Replaced the plain agent-name initial in the studio header with a designed bot identity mark using the studio cyan/teal treatment.
- Added `@radix-ui/react-slider` and replaced native browser range inputs with accessible Radix sliders featuring dark tracks, cyan filled ranges, custom thumbs, keyboard control, focus-visible rings, and hover/drag feedback.
- Why: several nested components retained their own high-specificity light styles after the initial studio theme pass, and browser-native sliders did not match the console.
- Files touched:
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `dashboard/package.json`
  - `dashboard/package-lock.json`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
  - `Push-Location dashboard; npm.cmd audit --audit-level=high; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: `npm audit --audit-level=high` passes but reports two moderate PostCSS findings through Next.js; the proposed forced fix would downgrade Next.js and was not applied.

### 2026-07-11 - Agent Studio dark-surface correction

- Replaced all six remaining native Agent Studio selects with the shared Radix dark selector: primary language, timezone, Web Voice widget language, widget position, vocabulary specialization, and DTMF completion key.
- Added explicit high-specificity dark states for the phone-number selector and voice selector, including default, hover, focus-visible, open, and disabled behavior.
- Fully restyled the phone-number dialog: backdrop, container, header, country selector/menu/search, refresh action, number results, hover/selected rows, radio state, recommendation badge, pricing, empty/error/warning states, footer, and assignment actions.
- Added explicit dark treatment for the voice picker modal and its filters, results, selected voice, inputs, footer, and buttons.
- Corrected the setup/next-step action so its default and hover states no longer render as a white card.
- Why: the initial dark studio pass did not override several older high-specificity light rules, and native selects still opened operating-system menus inconsistent with the console.
- Files touched:
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: the Radix selector adds its route-local UI bundle to Agent Studio, consistent with Calls, Analytics, and Integration Marketplace.

### 2026-07-11 - Unified dark agent configuration studio

- Extended the shared dark console shell to `/agents/new` and `/agents/[id]`, then redesigned the full editing workspace around the supplied Agent Configuration reference.
- Updated the sticky studio header, agent identity, setup/save actions, section navigation, prompt-variable card, scrollable form canvas, inputs, selects, textareas, warnings, toggles, language chips, tier/voice options, knowledge documents, integration cards, operating hours, DTMF controls, and post-call fields.
- Applied the same design system across Main Settings, Behavior & Prompt, Speech & Transcription, Call Behaviour, Routing, Integrations, Knowledge, and Post-call rather than limiting it to the initial tab.
- Rebuilt the idle browser test panel with animated voice-wave contours, drifting cyan/indigo lighting, a pulsing microphone orb, expanding rings, and a stronger test-call action. Active call transcript and controls now use the same dark treatment.
- Added reduced-motion handling that disables all decorative browser-test animation when requested by the operating system.
- Added responsive studio behavior: the preview hides at intermediate widths and the section navigation becomes a sticky horizontal rail on small screens.
- Why: agent editing was still using the older light workspace and the browser test panel lacked the dynamic voice-focused presentation of the supplied reference.
- Files touched:
  - `dashboard/components/AppShell/AppShell.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: visually validate very long knowledge/document lists and dense DTMF mappings at the 1080–1280 px transition where the live preview is hidden.

### 2026-07-11 - Shared dark agent dropdowns

- Replaced the native agent selects on Analytics and the Integration Marketplace with a reusable Radix Select component matching the Calls status dropdown.
- Added a custom dark trigger and portal-positioned menu with provider/agent icons, chevrons, selected checkmarks, hover/highlight states, keyboard navigation, focus-visible treatment, and long-label truncation.
- Why: native operating-system menus rendered bright blue/gray dropdowns that broke the dark console design and differed between browsers.
- Files touched:
  - `dashboard/components/DarkSelect/DarkSelect.tsx`
  - `dashboard/components/DarkSelect/DarkSelect.module.css`
  - `dashboard/features/analytics/presentation/AnalyticsPage.tsx`
  - `dashboard/features/analytics/presentation/AnalyticsPage.module.css`
  - `dashboard/features/integrations/IntegrationsPage/IntegrationsPage.tsx`
  - `dashboard/features/integrations/IntegrationsPage/IntegrationsPage.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: Analytics and Integration Marketplace now carry the Radix Select route bundle, consistent with Calls; reuse this shared component for future dark-console selects.

### 2026-07-11 - Dedicated integration provider icons

- Removed the generic plug fallback from integration marketplace cards and mapped every current catalog provider to a dedicated local SVG asset.
- Added distinct marks for Calendly, Telnyx SMS, Custom Webhook, WhatsApp, Email Alerts, Google Sheets, and M-Pesa; retained the existing Google Calendar, Slack, HubSpot, and Salesforce assets.
- Added descriptive image alt text using each provider name.
- Why: provider cards should be immediately recognizable and must not reuse a default integration icon.
- Files touched:
  - `dashboard/features/integrations/IntegrationsPage/IntegrationsPage.tsx`
  - `dashboard/public/logos/calendly.svg`
  - `dashboard/public/logos/telnyx.svg`
  - `dashboard/public/logos/webhook.svg`
  - `dashboard/public/logos/whatsapp.svg`
  - `dashboard/public/logos/email.svg`
  - `dashboard/public/logos/google-sheets.svg`
  - `dashboard/public/logos/mpesa.svg`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: any newly added catalog provider must add its logo to the explicit `logos` map before release.

### 2026-07-11 - Dark integration marketplace

- Brought `/dashboard/integrations` into the shared dark console shell and redesigned the marketplace header, agent selector, provider search, category/workflow filters, category headings, and responsive provider grid around the supplied reference.
- Restyled provider cards with clearer logos, connection states, capabilities, enablement toggles, delivery feedback, and configuration/test/disconnect actions while preserving all existing OAuth, Meta Embedded Signup, credential, and agent-binding behavior.
- Converted connection and WhatsApp setup dialogs to the same dark interface, including inputs, selects, summaries, validation feedback, and actions.
- Reused the existing component and icon dependencies; no new package was required.
- Why: the integration marketplace still used the older light card system and did not match the upgraded console routes.
- Files touched:
  - `dashboard/components/AppShell/AppShell.tsx`
  - `dashboard/styles/console.css`
  - `dashboard/features/integrations/IntegrationsPage/IntegrationsPage.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: validate long provider descriptions and configuration warnings with every server-side authorization configuration state.

### 2026-07-11 - Dark analytics command center

- Brought `/analytics` into the shared dark console shell and redesigned its header, date/agent controls, six KPI cards, tabs, chart cards, legends, tooltips, empty states, latency summaries, and agent table around the supplied analytics reference.
- Retuned all Recharts grids, axis labels, data labels, funnel labels, and series colors for the navy/cyan interface while preserving existing analytics data and interactions.
- Reused the existing route-scoped Recharts dependency; no new package was required and the analytics bundle remains isolated to `/analytics`.
- Why: the Analytics route still used the older light card system and its chart styling did not match the upgraded Overview, Agents, Calls, and Bookings workspaces.
- Files touched:
  - `dashboard/components/AppShell/AppShell.tsx`
  - `dashboard/styles/console.css`
  - `dashboard/features/analytics/presentation/AnalyticsPage.tsx`
  - `dashboard/features/analytics/presentation/AnalyticsPage.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: validate chart density with a high-volume workspace, especially 90-day labels and long intent names.

### 2026-07-11 - Booking row alignment

- Replaced the independent sticky booking date rail with a row-coupled layout where each date marker and appointment card share the same grid row.
- Increased and normalized the appointment card date/time columns, card minimum height, row spacing, and tablet/mobile column behavior.
- Why: the separate rail and card lists drifted vertically as cards grew, making dates appear associated with the wrong appointment.
- Files touched:
  - `dashboard/features/bookings/presentation/BookingsPage.tsx`
  - `dashboard/features/bookings/presentation/BookingsPage.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: none.

### 2026-07-10 - Dark bookings workspace redesign

- Rebuilt `/bookings` around the supplied dark calendar-operations reference and brought the route into the same dark console shell as Overview, Agents, and Calls.
- Added a four-card summary for upcoming, today, confirmed, and cancelled bookings; strengthened the next-appointment panel; and redesigned appointment cards with clearer date, time, customer, source, agent, sync, and status hierarchy.
- Preserved the existing search, status filters, real booking data, loading/empty/error states, and confirmed cancellation flow. Unsupported open/reschedule actions from the visual reference were intentionally not added.
- Added responsive two-column/tablet and stacked/mobile layouts for the summary, filters, timeline, and cards.
- Why: the previous Bookings route still used the older light dashboard styling and did not visually match the recently upgraded console routes.
- Files touched:
  - `dashboard/components/AppShell/AppShell.tsx`
  - `dashboard/styles/console.css`
  - `dashboard/features/bookings/domain/bookings.ts`
  - `dashboard/features/bookings/presentation/BookingsPage.tsx`
  - `dashboard/features/bookings/presentation/BookingsPage.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted for maintainer review and CI/CD.
- Known follow-up: add real booking detail and rescheduling endpoints before exposing those actions in the interface.

### 2026-07-10 - Accessible call date and option filters

- Replaced the Calls page native date inputs with a React DayPicker range popover, including visible default dates, two-month navigation, range highlighting, and 7/14/30-day and all-time shortcuts.
- The initial range covers 14 days ending at the newest loaded call date, so seeded or historical call data remains visible while both range endpoints filter inclusively.
- Replaced the native status and agent selects with keyboard-accessible Radix Select controls styled to match the dark console, including selected-item indicators and proper overlay positioning.
- Why: the browser-native date fields were not opening reliably and the native status/agent menus did not match the Calls interface.
- Files touched:
  - `dashboard/app/globals.css`
  - `dashboard/features/calls/CallsPage/CallsPage.tsx`
  - `dashboard/features/calls/CallsPage/CallsPage.module.css`
  - `dashboard/package.json`
  - `dashboard/package-lock.json`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted under the maintainer-owned source-control policy.
- Known follow-up: the Calls route now carries the calendar/select UI bundle; monitor route size if more filter primitives are added.

### 2026-07-10 - Working call date range, readable typography, and waveform audio

- Fixed the Calls date filter to compare inclusive normalized local calendar dates instead of timestamp boundaries, keep the range ordered, expose active styling, reset pagination, and provide a clear-range action.
- Increased undersized typography across the Calls table, filters, KPI cards, transcript drawer, details, summaries, pagination, and empty/error states.
- Increased the smallest text across the Overview readiness card, metrics, panel headings, operations, funnel, charts, bookings, usage, and empty states while retaining the compact dashboard layout.
- Replaced the native recording control with WaveSurfer.js, providing an interactive waveform, seeking, progress visualization, play/pause, and elapsed/total duration.
- Why: user reported that the date range did not filter calls and that Calls/Overview text and the recording display were too small/basic.
- Files touched:
  - `dashboard/features/calls/CallsPage/CallsPage.tsx`
  - `dashboard/features/calls/CallsPage/CallsPage.module.css`
  - `dashboard/features/dashboard/DashboardOverview/DashboardOverview.module.css`
  - `dashboard/package.json`
  - `dashboard/package-lock.json`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
  - `Push-Location dashboard; npm.cmd audit --audit-level=high; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted under the maintainer-owned source-control policy.
- Known follow-ups:
  - WaveSurfer decodes the recording client-side to render the waveform, so unusually long recordings should be watched for browser memory pressure.
  - `npm audit --audit-level=high` passes but reports two moderate PostCSS findings inherited through Next.js; the suggested forced fix would downgrade Next.js and was not applied.

### 2026-07-10 - Dark calls workspace and transcript drawer redesign

- Rebuilt `/calls` to match the supplied dark call-operations references while retaining the existing calls, agents, bookings, call-turn, and recording APIs.
- Added real conversation, answered-rate, average-duration, and booking KPI cards; call-type, date, status, agent, and text filters; 20-row pagination; richer channel/date/status cells; and responsive table behavior.
- Restyled the selected-call workspace as a sticky transcript drawer with call details, recording playback, a chronological caller/agent timeline, and an AI summary sourced from persisted analysis fields.
- Corrected transcript rendering within each stored turn so the caller utterance appears before the corresponding agent response; greeting-only turns still begin with the agent.
- Scoped the shared dark console shell to `/calls` while keeping its workspace switcher in the reference's top position.
- Why: user requested that the call list and transcript-detail states be improved to match the two supplied high-fidelity dark references.
- Files touched:
  - `dashboard/components/AppShell/AppShell.tsx`
  - `dashboard/styles/console.css`
  - `dashboard/features/calls/CallsPage/CallsPage.tsx`
  - `dashboard/features/calls/CallsPage/CallsPage.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment: not deployed. Changes remain uncommitted under the maintainer-owned source-control policy.
- Known follow-up: date filters currently use the browser's native date inputs; a shared console date-range popover can replace them when one is introduced.

### 2026-07-10 - Maintainer-owned commits and CI/CD-only deployments

- Updated the standing agent contract so coding agents leave all repository changes uncommitted and never stage, commit, push, or open pull requests.
- Made production deployment explicitly CI/CD-only: a maintainer or authorized source-control automation pushes to `main`, CI verifies the revision, and the deploy workflow releases that exact verified revision.
- Prohibited manual application releases through direct SSH, `deploy/deploy.sh`, production Docker Compose, file copying, manual deploy dispatch, or CI bypasses.
- Allowed agents to perform read-only GitHub Actions monitoring and public health verification only after an external push has initiated CI/CD.
- Why: user requested that agents stop committing and that every deployment happen through CI/CD rather than manual release actions.
- Files touched:
  - `AGENTS.md`
  - `docs/agent-handoff.md`
- Verification:
  - `git diff --check`
- Deployment: not applicable; documentation-only policy change, intentionally left uncommitted under the new rule.
- Known follow-up: maintainers must commit and push this policy change before other fresh agent sessions receive it from the repository.

### 2026-07-10 - Dark operations dashboard redesign

- Rebuilt `/dashboard` around the supplied dark command-center reference while keeping every figure tied to the existing tenant-scoped dashboard response.
- Added a responsive readiness panel with progress ring, five KPI cards with real period deltas, a daily-call line visualization, recent operations, booking funnel, appointments-by-day distribution, upcoming bookings, plan usage, and system-status presentation.
- Reused the established dark console shell on the overview route while preserving the dashboard reference's workspace switcher position and scoping the new dashboard content styles to a CSS module.
- Preserved refresh, loading, error, empty-state, call-detail, analytics, bookings, billing, agent-setup, and mobile behaviors.
- Why: user requested that the main overview dashboard be improved to match the supplied high-fidelity dark operational dashboard reference.
- Files touched:
  - `dashboard/components/AppShell/AppShell.tsx`
  - `dashboard/styles/console.css`
  - `dashboard/features/dashboard/DashboardOverview/DashboardOverview.tsx`
  - `dashboard/features/dashboard/DashboardOverview/DashboardOverview.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment:
  - Deployed in commit `e494434`.
  - GitHub Actions CI run `29116374162` passed.
  - GitHub Actions deploy run `29116469192` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/dashboard` redirected unauthenticated users to `/login?next=%2Fdashboard`.
- Known follow-up: the appointments-by-day panel groups the bookings currently returned by the dashboard endpoint; a dedicated date-range aggregation endpoint would support richer trend selection later.

### 2026-07-10 - Dark agent studio redesign

- Rebuilt the `/agents` index to match the supplied dark voice-agent dashboard reference while retaining Sauti's real agent, call, booking-rate, language, status, and channel data.
- Added a voice-focused hero, four workspace KPI cards, filter/search controls, grid/list switching, richer agent cards, a calls-distribution visualization, ranked agent activity, and a compact creation prompt.
- Scoped the dark navy/cyan shell treatment to the agents index so the rest of the console keeps its existing visual system.
- Preserved agent search, live/draft filtering, configuration links, creation, deletion, preview-mode data, loading states, empty states, and responsive behavior.
- Why: user requested that the agents UI be upgraded to resemble the supplied high-fidelity dark dashboard reference.
- Files touched:
  - `dashboard/components/AppShell/AppShell.tsx`
  - `dashboard/styles/console.css`
  - `dashboard/features/agents/AgentList/AgentList.tsx`
  - `dashboard/features/agents/AgentList/AgentList.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment:
  - Deployed in commit `f0aa206`.
  - GitHub Actions CI run `29110744275` passed.
  - GitHub Actions deploy run `29110860245` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/agents` redirected unauthenticated users to `/login?next=%2Fagents`.
- Known follow-up: validate the information density with a workspace containing many agents; the layout currently shows all matching agents and does not paginate.

### 2026-07-10 - Adaptive browser test turn-taking and stronger detail guardrails

- Made browser test endpointing adaptive: ordinary replies now finalize after 650 ms of silence, while replies to requests for a name, phone number, address, email, spelling, or digits retain a 1300 ms pause so callers can dictate naturally.
- Start capturing after 100 ms of sustained speech instead of discarding the beginning of a caller's utterance; adjusted the minimum accepted clip duration accordingly.
- Hardened automatic barge-in against speaker-to-microphone bleed by requiring a substantially louder signal sustained for at least 360 ms. Manual interruption remains available.
- Prevented the conversation model from treating acknowledgements, thanks, or the agent's own name as caller details; it must repeat the pending request rather than inventing a name/contact and moving forward.
- Ignore the observed short Indonesian transcription noise (`Terima kasih`) during a French call before it reaches language detection or the LLM.
- Why: the latest browser-call transcript showed slow turn finalization, clipped/incorrect caller content, false interruption from playback, and invented contact details.
- Files touched:
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `./gradlew.bat :backend:test --tests com.sauti.llm.ConversationOrchestratorTest --tests com.sauti.call.CallPipelineServiceTest`
  - `./gradlew.bat :backend:test`
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Deployment:
  - Deployed in commit `efd7206`.
  - GitHub Actions CI run `29107957259` passed.
  - GitHub Actions deploy run `29108052599` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Known follow-up: this focuses on browser test calls. If the same pauses occur on Telnyx calls, make the realtime STT commit window similarly context-aware.

### 2026-07-10 - Browser test VAD and information-first call flow

- Lowered the browser test voice threshold so quieter speech is detected without forcing the tester to speak loudly.
- Increased auto endpointing from a 450 ms floor to a 1200 ms floor so natural pauses inside longer sentences are less likely to split the caller's utterance.
- Reduced the sustained-voice and voiced-audio requirements slightly to make normal conversational speech easier to capture.
- Updated the conversation prompt so information requests about hours, services, availability, location, pricing, or policies are answered before collecting personal details.
- Clarified that name/contact collection should start only after a clear booking, callback, transfer, or message-taking intent, and that the agent must not invent a name from an availability/information request.
- Why: user reported that the browser test kept cutting them off during longer French sentences, required speaking too loudly, and the agent jumped to name/contact collection while the caller was only asking for availability information.
- Deployment:
  - Deployed in commit `e3eb115`.
  - GitHub Actions CI run `29103169348` passed.
  - GitHub Actions deploy run `29103292431` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.ConversationOrchestratorTest`
  - `.\gradlew.bat :backend:test`
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Known follow-ups:
  - If users still pause longer than 1.2 seconds mid-sentence, consider making endpointing a visible test-call setting or moving browser tests to a streaming VAD/STT path.

### 2026-07-10 - Business-aware opening greetings and voice fallback

- Opening greeting generation now requires both the agent name and the represented institution/business name.
- Added a deterministic post-check: if the LLM returns a greeting that omits either the agent name or business name, the platform replaces it with a concise localized fallback.
- Updated English, French, Arabic, and legacy Swahili fallback openings to include the business/institution.
- Added compatible-voice fallback for browser test and turn-based public web voice audio. If the configured voice cannot synthesize the call language, the backend tries the first compatible catalog voice instead of returning silence.
- Why: user reported greetings that did not identify the institution, and a Sarah agent that did not speak the greeting.
- Deployment:
  - Deployed in commit `59bf4a7`.
  - GitHub Actions CI run `29101917921` passed.
  - GitHub Actions deploy run `29102046761` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/api/CallController.java`
  - `backend/src/main/java/com/sauti/api/PublicWebVoiceController.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.llm.ConversationOrchestratorTest`
  - `.\gradlew.bat :backend:test --tests com.sauti.call.CallPipelineServiceTest`
  - `.\gradlew.bat :backend:test`
- Known follow-ups:
  - Realtime Telnyx/WebSocket TTS still opens the configured realtime voice directly; if silence appears there with an incompatible voice, add equivalent fallback before opening the realtime TTS session.

### 2026-07-10 - Reject corrupted first browser-test transcripts

- Carried an explicit `acceptedTranscript` flag from `CallPipelineService` through simulated/test turn responses so the browser UI no longer infers transcript acceptance from the latest persisted row.
- Added a first-caller-turn reliability guard for non-English calls. Short mangled first transcripts like `Meli seza kare` now produce a localized repeat request without sending the false text to language detection or the LLM.
- Kept clear short French first turns, such as `Bonjour Amelie`, accepted and processed normally.
- Delayed browser test silence reminders before the first accepted caller turn so the agent does not say `Êtes-vous toujours là ?` immediately after the greeting while the mic/noise floor is settling.
- Prevented rejected-transcript clarification turns from falling back to replaying the latest saved agent audio when inline TTS is unavailable.
- Why: user reported French browser tests getting worse, including false English/Arabic caller transcripts, early reminders, and the LLM treating corrupted STT as names or facts.
- Deployment:
  - Deployed in commit `59bf4a7`.
  - GitHub Actions CI run `29101917921` passed.
  - GitHub Actions deploy run `29102046761` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/api/CallController.java`
  - `backend/src/main/java/com/sauti/call/CallDtos.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/lib/api/calls.ts`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.call.CallPipelineServiceTest`
  - `.\gradlew.bat :backend:test`
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`
- Known follow-ups:
  - Browser tests still depend on prerecorded STT quality. If false long transcripts continue, add an explicit test-call language selector and pass the selected language as a hard transcription hint.

### 2026-07-10 - Browser test noise and off-language transcript guard

- Added browser test microphone noise-floor tracking and sustained-speech requirements so background noise such as a fan is less likely to trigger auto capture.
- Added final auto-capture quality checks for minimum duration, voiced time, and peak RMS before audio is submitted to speech recognition.
- Added a backend guard that ignores one- or two-word off-language noise transcripts like `hi`, `hello`, `yes`, `finally`, and `version` on non-English calls before they reach language detection or the LLM.
- Browser audio test responses now return an empty caller transcript when the backend rejected an audio turn as noise, so false words are not displayed in the transcript.
- Why: user reported French browser tests producing English caller transcripts even though they did not speak English, likely from background fan noise.
- Deployment:
  - Deployed in commit `c7a4afa`.
  - GitHub Actions CI run `29097488320` passed.
  - GitHub Actions deploy run `29097579244` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/api/CallController.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.call.CallPipelineServiceTest`
  - `.\gradlew.bat :backend:test`
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
- Known follow-ups:
  - Consider surfacing a live microphone level/noise warning in Agent Studio if users continue to test in noisy rooms.

### 2026-07-10 - Remove Cal.com integration

- Removed Cal.com from the active integration catalog, OAuth provider map, env examples, production callback helpers, dashboard OAuth provider list, and current callback documentation.
- Kept Calendly as the non-Google calendar OAuth integration.
- Why: user decided to keep Calendly and remove Cal.com.
- Deployment:
  - Deployed in commit `f3c401a`.
  - GitHub Actions CI run `29094043361` passed.
  - GitHub Actions deploy run `29094134110` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `.env.example`
  - `AGENTS.md`
  - `backend/src/main/java/com/sauti/api/IntegrationController.java`
  - `backend/src/main/java/com/sauti/integration/IntegrationCatalog.java`
  - `backend/src/main/java/com/sauti/integration/ProviderOAuthService.java`
  - `backend/src/main/resources/application.yml`
  - `dashboard/features/integrations/IntegrationsPage/IntegrationsPage.tsx`
  - `deploy/.env.production.example`
  - `deploy/bootstrap-server-env.sh`
  - `deploy/update-public-callbacks-env.sh`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`
  - `.\gradlew.bat :backend:test`

### 2026-07-09 - Integration marketplace calendar providers and grouping

- Added Calendly to the backend integration catalog as an OAuth-based calendar connection.
- Normalized Google Calendar into the `Calendar` catalog category so Google Calendar and Calendly appear together.
- Added integration marketplace search, category filters, and grouped provider sections.
- Added Calendar, Messaging, CRM, Data, Notifications, Payments, Developer, During call, Post call, and Connected filters.
- Added connection form labels/placeholders for Calendly event type URIs.
- Why: user asked to improve integrations by adding calendar integrations, grouping calendar integrations, and adding filters.
- Deployment:
  - Deployed in commit `f3c401a`.
  - GitHub Actions CI run `29094043361` passed.
  - GitHub Actions deploy run `29094134110` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `backend/src/main/java/com/sauti/integration/IntegrationCatalog.java`
  - `dashboard/features/integrations/IntegrationsPage/IntegrationsPage.tsx`
  - `dashboard/features/integrations/IntegrationsPage/IntegrationsPage.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; Pop-Location`
  - `Push-Location dashboard; npm.cmd run build; Pop-Location`
  - `.\gradlew.bat :backend:test`
- Known follow-ups:
  - Wire Calendly connections into the live calendar fulfillment path. The current change adds secure configuration and per-agent enablement in the marketplace, but booking execution still needs provider adapters before these can create live bookings.

### 2026-07-09 - Calendly OAuth configuration

- Switched Calendly from per-workspace API-token credentials to the generic provider OAuth flow.
- Added backend OAuth configuration for Calendly, including client ID, client secret, redirect URI, authorization URL, token URL, and optional scope.
- Added production/local env example variables and public callback URL update helpers for Calendly.
- Calendly has defaults for `https://auth.calendly.com/oauth/authorize` and `https://auth.calendly.com/oauth/token`.
- Why: user decided to use OAuth for Calendly.
- Deployment:
  - Deployed in commit `f3c401a`.
  - GitHub Actions CI run `29094043361` passed.
  - GitHub Actions deploy run `29094134110` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `.env.example`
  - `backend/src/main/java/com/sauti/api/IntegrationController.java`
  - `backend/src/main/java/com/sauti/integration/IntegrationCatalog.java`
  - `backend/src/main/java/com/sauti/integration/ProviderOAuthService.java`
  - `backend/src/main/resources/application.yml`
  - `dashboard/features/integrations/IntegrationsPage/IntegrationsPage.tsx`
  - `deploy/.env.production.example`
  - `deploy/bootstrap-server-env.sh`
  - `deploy/update-public-callbacks-env.sh`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`
  - `.\gradlew.bat :backend:test`

### 2026-07-09 - Calls table and transcript drawer UI

- Reworked the `/calls` console page from stacked accordion cards into a denser operations table with segmented filters for all calls, phone calls, and browser tests.
- Added clearer call-type presentation, distinct phone/test icons, status chips, search, incoming phone number, duration, and routed event visibility.
- Loaded bookings alongside calls so the routed event column can show the booking service linked to a call, or explicitly show `No booking` when no booking was produced.
- Added a right-side transcript drawer with details, booked event, recording playback, language/intent metadata, and the caller/agent transcript.
- Why: user shared a reference call-management UI and asked to improve the Sauti call UI/UX while differentiating call types.
- Deployment:
  - Deployed in commit `f3c401a`.
  - GitHub Actions CI run `29094043361` passed.
  - GitHub Actions deploy run `29094134110` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `dashboard/features/calls/CallsPage/CallsPage.tsx`
  - `dashboard/features/calls/CallsPage/CallsPage.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`
- Known follow-ups:
  - Replace approximate transcript timestamps with persisted per-turn timestamps if/when the backend stores them.

### 2026-07-09 - Bookings dashboard feature

- Replaced the placeholder `/bookings` console page with a real bookings dashboard backed by the existing tenant-scoped bookings API.
- Added summary metrics for upcoming, today, and cancelled bookings; search; status/time filters; timeline preview; booking source labels; calendar sync badges; and a cancel action that calls the existing `DELETE /api/v1/bookings/{id}` endpoint.
- Added booking domain helpers for view-model mapping, filtering, summaries, and date/time formatting.
- Exposed `externalEventId` in the dashboard `Booking` type so the UI can show where a booking was synced or created from.
- Why: user asked to implement the bookings feature with modern UI/UX and visibility into where bookings were created.
- Deployment:
  - Deployed in commit `f3c401a`.
  - GitHub Actions CI run `29094043361` passed.
  - GitHub Actions deploy run `29094134110` passed.
  - `https://sauti.uk/health` returned `{"status":"UP"}`.
  - `https://sauti.uk/analytics` redirected unauthenticated users to `/login?next=%2Fanalytics`.
- Files touched:
  - `dashboard/app/(console)/bookings/page.tsx`
  - `dashboard/features/bookings/domain/bookings.ts`
  - `dashboard/features/bookings/presentation/BookingsPage.tsx`
  - `dashboard/features/bookings/presentation/BookingsPage.module.css`
  - `dashboard/features/dashboard/data/preview-data.ts`
  - `dashboard/lib/api/bookings.ts`
  - `dashboard/types/api.ts`
  - `docs/agent-handoff.md`
- Verification:
  - `Push-Location dashboard; npm.cmd run typecheck; npm.cmd run build; Pop-Location`
- Known follow-ups:
  - Add backend pagination/date filters before the bookings table grows large.
  - Add a booking detail drawer with the linked call transcript once product scope is confirmed.

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
### 2026-07-11 - ElevenLabs realtime playback failover

- Added an automatic transport fallback for ElevenLabs voices: when the realtime WebSocket produces no first audio within 1.5 seconds (or reports an error), the same accumulated response is streamed through ElevenLabs' HTTP PCM endpoint.
- Kept the displayed response and spoken response identical by replaying the already-generated text; the fallback does not invoke the LLM again.
- Suppressed late WebSocket audio after failover to prevent duplicate or overlapping speech, and preserved interruption/close behavior.
- Why: Sarah could transcribe and answer correctly, but her ElevenLabs WebSocket produced no playable audio while Cartesia-backed Amélie worked.
- Files touched:
  - `backend/src/main/java/com/sauti/call/ElevenLabsRealtimeTextToSpeechProvider.java`
  - `backend/src/test/java/com/sauti/call/ElevenLabsRealtimeTextToSpeechProviderTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests "com.sauti.call.ElevenLabsRealtimeTextToSpeechProviderTest" --tests "com.sauti.call.WebVoiceSessionServiceTest"`
  - `.\gradlew.bat :backend:test`
- Deployment:
  - Not deployed. Changes are uncommitted and ready for maintainer review and the CI/CD release chain.

### 2026-07-11 - Cartesia-only text-to-speech cutover

- Removed the ElevenLabs realtime adapter, configuration, voice catalog integration, tests, and dashboard/marketing references.
- Made Cartesia Sonic 3.5 the sole realtime and preview TTS engine and the default `SAUTI_TTS_STREAMING_PROVIDER`.
- Added `CARTESIA_DEFAULT_VOICE_ID` as a safe fallback for agents with missing or legacy voice identifiers.
- Added a tenant-scoped Flyway migration that replaces legacy agent voice IDs with an existing Cartesia voice from the same workspace. This automatically moves Sarah to the working Cartesia path when Amélie's Cartesia voice exists in that workspace.
- Updated the CI/CD-owned deployment script to force Cartesia when `CARTESIA_API_KEY` is configured; no manual production deployment is required or permitted.
- Why: Amélie's Cartesia realtime path consistently supported low-latency listening and playback, while Sarah's ElevenLabs path repeatedly failed to produce audio.
- Files touched:
  - Cartesia runtime provider/client configuration and tests
  - Voice catalog service
  - Flyway agent voice migration
  - Environment and CI/CD deployment templates
  - Dashboard voice-provider and marketing copy/assets
  - Removed ElevenLabs runtime and test files
- Verification:
  - `.\gradlew.bat :backend:test`
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-12 - Public privacy policy and terms of service

- Added responsive public `/privacy` and `/terms` pages within the existing Sauti marketing shell, with reusable legal-document presentation, section navigation, trust highlights, and mobile layouts.
- Based the content on the project requirements and implemented architecture: tenant accounts, multilingual AI calls, optional recording, transcripts and analytics, booking workflows, encrypted provider credentials, explicit integrations, Google Calendar availability/event access, and Google Sheets lookup/post-call records.
- The privacy policy expressly covers Google API Limited Use, prohibits selling, advertising use, and generalized AI training with Google user data, and explains access, retention, deletion, revocation, security, and provider sharing.
- The terms cover AI limitations, workspace responsibilities, caller consent and recording rules, connected services, acceptable use, payment confirmation, availability, termination, and liability.
- Added Privacy, Terms, and Contact links to the public footer so the policies are discoverable from the OAuth application homepage.
- Files touched:
  - `dashboard/app/(marketing)/privacy/page.tsx`
  - `dashboard/app/(marketing)/terms/page.tsx`
  - `dashboard/features/marketing/LegalDocument/LegalDocument.tsx`
  - `dashboard/features/marketing/LegalDocument/LegalDocument.module.css`
  - `dashboard/features/marketing/MarketingChrome/MarketingChrome.tsx`
  - `dashboard/styles/marketing/sections.css`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build` (50 routes generated, including static `/privacy` and `/terms`)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-11 - Agent Studio form and Cartesia voice-picker polish

- Replaced the low-contrast white Agent Studio text fields with explicit dark-console inputs, readable values/placeholders, and stronger hover/focus feedback.
- Reworked the Live channels panel into a contained console card with consistently aligned channel icons, descriptions, toggles, dividers, and phone-number action.
- Restyled the Cartesia voice picker search, accent menu, language tabs, result headings, voice cards, selection states, preview controls, scrollbar, and footer as a cohesive dark modal.
- Added responsive voice-picker and channel layout rules for narrow screens.
- Why: agent identity, card description, boosted keyterms, channel configuration, and voice selection had conflicting light/dark styles and insufficient contrast.
- File touched:
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-11 - Remove duplicate agent creation card

- Removed the right-rail `Ready for another voice?` creation card from the Agents page.
- Kept the full-width `Create new agent` card beneath the agent grid as the single creation entry point in that section.
- Removed the unused launch-card styles.
- Files touched:
  - `dashboard/features/agents/AgentList/AgentList.tsx`
  - `dashboard/features/agents/AgentList/AgentList.module.css`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-11 - Official Sauti logo asset and favicon

- Replaced the generated letter-in-a-box brand placeholders with the official user-supplied Sauti JPG artwork.
- The reusable compact mark crops the symbol directly from the official artwork with CSS; it does not redraw or reinterpret the logo.
- Applied the logo to the console sidebar, authentication pages, onboarding, marketing navigation/footer, legacy homepage footer, and dashboard hero artwork.
- Kept workspace switcher and user-profile initials dynamic because those identify the tenant/user rather than the Sauti product.
- Registered the official JPG in the root Next.js metadata as the browser favicon, shortcut icon, and Apple icon.
- Files touched:
  - `dashboard/public/sauti-logo.jpg`
  - `dashboard/components/BrandLogo/BrandLogo.tsx`
  - `dashboard/app/layout.tsx`
  - console, auth, onboarding, marketing, and dashboard brand surfaces
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-11 - Updated logo, agent rename synchronization, and humane silence timing

- Replaced the previous full wordmark source with the latest user-supplied standalone Sauti symbol (`logo.png`) for compact brand marks and favicon metadata.
- When an agent is renamed, fixed greeting text containing the previous agent name is now updated case-insensitively. This prevents a renamed Sarah agent from continuing to introduce itself as Amélie.
- Reset the silence-reminder counter whenever real caller speech (or DTMF input) arrives, for browser and phone calls.
- Enforced at least 15 seconds before a silence reminder and at least 20 additional seconds after the final reminder before hangup. Direct silence termination also cannot occur sooner than the combined reminder/grace window.
- Why: reminder state survived later caller responses, causing the agent to interrupt healthy data collection and terminate after the next short pause.
- Files touched:
  - `dashboard/public/sauti-logo.png`
  - `dashboard/components/BrandLogo/BrandLogo.tsx`
  - `dashboard/app/layout.tsx`
  - `dashboard/app/globals.css`
  - `backend/src/main/java/com/sauti/agent/Agent.java`
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `backend/src/main/java/com/sauti/call/DefaultTwilioMediaStreamService.java`
  - `backend/src/test/java/com/sauti/agent/AgentTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests "com.sauti.agent.AgentTest" --tests "com.sauti.call.DefaultTwilioMediaStreamServiceTest"`
  - `.\gradlew.bat :backend:test`
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-11 - Immediate realtime barge-in and phone-number collection rules

- Added an explicit browser WebSocket interruption command. Local voice activity now clears queued PCM immediately and tells the backend to close the active Cartesia turn instead of waiting for a high-confidence partial transcript.
- Lowered the partial-transcript barge-in confidence threshold for browser and telephony fallback detection while retaining the configured speech grace period.
- Added mandatory conversation-ledger rules so collected names/services are not requested again after phone confirmation.
- Added phone-dictation rules for accumulating short digit fragments, preserving leading zeroes, discarding the previous candidate after restart/correction language, avoiding premature length assumptions, and confirming a completed candidate only once.
- Why: callers could be transcribed while old queued audio continued playing, and fragmented/corrected phone numbers caused repetitive prompts and loss of already collected fields.
- Files touched:
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `backend/src/main/java/com/sauti/call/WebVoiceWebSocketHandler.java`
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `backend/src/main/java/com/sauti/call/DefaultTwilioMediaStreamService.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests "com.sauti.call.DefaultTwilioMediaStreamServiceTest" --tests "com.sauti.llm.ConversationOrchestratorTest"`
  - `.\gradlew.bat :backend:test`
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-11 - Audio-first barge-in cancellation

- Started the browser voice-activity monitor when a realtime test call connects; it previously existed but was never started on the successful connection path.
- Reduced sustained local speech time to roughly 180–250 ms before interruption while retaining a strong echo-resistant energy threshold.
- A recognized partial transcript now clears browser playback immediately as a second client-side interruption path.
- Public Web Voice uses a ref-backed speaking state so its long-lived WebSocket callback can reliably cancel playback instead of reading a stale React state value.
- Made the backend interruption command synchronous rather than queueing it behind the still-running LLM turn.
- Added TTS session generations so late Cartesia frames from a cancelled stream are discarded and cannot restart speech after the clear event.
- Guarded streamed TTS callbacks after cancellation so later LLM deltas are ignored safely.
- Why: the caller audio reached STT, but local cancellation was inactive and server cancellation waited behind sentence generation, allowing the agent to finish talking over the caller.
- Files touched:
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-11 - Single silence controller for realtime test calls

- Disabled the Agent Studio browser's local reminder/farewell timer while a realtime WebSocket session is active.
- Kept the local timer only for legacy prerecorded/fallback calls.
- Realtime reminder and hangup decisions now come exclusively from `WebVoiceSessionService`, preventing the UI and backend from independently adding consecutive agent turns.
- Why: after a normal LLM reply, the client emitted its own reminder and farewell while the backend was also maintaining silence state, producing three agent messages without a caller turn.
- Files touched:
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-11 - Single microphone ingestion path for realtime calls

- Prevented the local voice monitor from starting legacy MediaRecorder turn uploads while the realtime WebSocket is connected.
- Realtime mode now streams microphone PCM only once; the local monitor is used solely for immediate barge-in detection.
- Disabled manual prerecorded capture during an active realtime session; pressing it while the agent speaks now performs only an interruption.
- Why: activating the monitor for barge-in accidentally submitted the same caller utterance through both realtime STT and the legacy audio-turn API, creating duplicate caller transcripts and therefore repeated agent replies.
- Files touched:
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-11 - Authoritative call intake notes and integration fields

- Added `CallIntakeNoteService`, which rebuilds structured notes from the complete persisted call history rather than the LLM's rolling 12-message context.
- Notes currently retain caller name, dictated phone number with leading zeroes, email, address, spoken date of birth when explicitly requested, service/reason, preferred weekday, and preferred time; accepted agent proposals are captured from affirmative caller replies.
- Injected the authoritative notes into every LLM turn with an explicit prohibition on asking again for filled fields.
- Added `collectedDetails` to generic post-call integration payloads.
- HubSpot and Salesforce contact sync now use the captured caller name/phone as fallbacks when browser/test calls do not have a carrier caller number or booking record.
- Google Sheets `callerPhone` and CRM notes now include the collected values as well.
- Why: older details fell outside `MAX_HISTORY_MESSAGES = 12` during longer bookings, so the model asked again for name, phone, and reason even though they remained in the transcript.
- Files touched:
  - `backend/src/main/java/com/sauti/call/CallIntakeNoteService.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/integration/PostCallIntegrationService.java`
  - `backend/src/test/java/com/sauti/call/CallIntakeNoteServiceTest.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests "com.sauti.call.CallIntakeNoteServiceTest" --tests "com.sauti.llm.ConversationOrchestratorTest"`
  - `.\gradlew.bat :backend:test`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-11 - Transcript-driven intake corrections

- Reworked phone-note extraction into a sequential candidate: rejected candidates are cleared, complete repetitions replace them, and short digit-only continuations such as `quatre un` append safely.
- Added French STT normalization for number phrases seen in production-like transcripts, including `cent onze` and `cinq cent septante-cinq`.
- Prevented ordinary words such as `une consultation` from being mistaken for phone digit continuations.
- Added a high-priority allowed-booking-fields block: routine booking collects name, contact, reason, and preferred time only; date of birth is prohibited unless explicitly configured as an extraction field.
- Extended the Web Voice silence reminder minimum to 25 seconds when the latest agent question requests dictated personal/contact information.
- Treat an agent-generated goodbye as a terminal outcome, so a polite `Je vous en prie, au revoir` closes the call instead of inviting another goodbye turn.
- Why: the supplied transcripts showed premature reminders during contact collection, prohibited DOB collection, fragmented phone corrections, and duplicate farewell exchanges.
- Files touched:
  - `backend/src/main/java/com/sauti/call/CallIntakeNoteService.java`
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/call/CallIntakeNoteServiceTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests "com.sauti.call.CallIntakeNoteServiceTest" --tests "com.sauti.llm.ConversationOrchestratorTest" --tests "com.sauti.call.CallPipelineServiceTest"`
  - `.\gradlew.bat :backend:test`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-12 - Repeated phone digits and graceful spoken call closure

- Stopped treating the ordinary French word `a` as a phone digit globally; only repeated STT fragments such as `a a` or `a a a` are normalized to repeated `1` digits while a phone number is actively being dictated.
- Recognize correction language such as `il y a trois un` / `trois fois un` as a repetition hint instead of appending the literal digits `3, 1`; a subsequent restart beginning with zero replaces the rejected candidate.
- Added the repeated-digit interpretation to the LLM's authoritative intake instructions and covered the observed correction sequence with a regression test.
- Accent-normalized natural farewell detection and included `a tres bientot`, allowing a completed booking response to end the realtime session without another caller turn.
- Delayed Agent Studio realtime cleanup until the browser's queued PCM has drained, preventing the final spoken sentence from being cut off when the backend sends `ended` immediately after synthesis delivery.
- Why: the supplied transcript showed repeated ones being confused with French filler/correction phrases, and the final goodbye was displayed but its remaining queued audio was stopped during immediate session cleanup.
- Files touched:
  - `backend/src/main/java/com/sauti/call/CallIntakeNoteService.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/call/CallIntakeNoteServiceTest.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests "com.sauti.call.CallIntakeNoteServiceTest" --tests "com.sauti.call.CallPipelineServiceTest"`
  - `.\gradlew.bat :backend:test`
  - `npm.cmd run typecheck`
  - `npm.cmd run build`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-12 - Authenticated users default to the console

- Updated the central Next.js middleware so a browser with a Sauti session is redirected from public marketing, pricing, resource, legal, login, registration, and verification pages to `/dashboard`.
- Kept `/oauth/callback` session-neutral so Google sign-in can finish writing the browser session, and kept `/call/[publicId]` session-neutral because public agent voice links must remain usable by callers even if they also own a Sauti workspace.
- Preserved the existing unauthenticated console guard and its `next` return path, while tightening prefix matching to route boundaries.
- Files touched:
  - `dashboard/middleware.ts`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build` (50 routes generated successfully)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-12 - Product-led public homepage redesign

- Replaced the generic booking-focused homepage with a product-led narrative that presents Sauti as an operating system for AI conversations across agent design, live voice, business actions, and conversation intelligence.
- Added responsive, code-native product previews based on the real console: command center overview, Agent Studio instructions, live caller/agent transcript with tool execution and interruption status, analytics trends and conversion funnel, and agent-scoped integration topology.
- Reorganized the page around a five-stage operating loop (design, connect, converse, act, improve), concrete appointment/support/qualification use cases, platform controls, and clear registration/demo calls to action.
- Refined the layout against the supplied homepage reference: removed the capability strip that read like a trust/metrics band, kept testimonials and customer-logo claims out, and strengthened the numbered product-chapter rhythm around the real UI previews.
- Extended the shared reveal hook with requestAnimationFrame-throttled scroll progress and parallax variables. The homepage now uses a progressive chapter rail, active numbered markers, preview depth/scale, and subtle hero parallax inspired by GitHub's scroll storytelling, with a reduced-motion fallback.
- Removed the homepage's dependency on generic simulated performance claims and fragile screenshot assets; the visual previews remain sharp at every viewport and reuse the product's dark console language and real provider logos.
- Files touched:
  - `dashboard/features/marketing/HomePage/HomePage.tsx`
  - `dashboard/features/marketing/ProductHome/ProductHome.tsx`
  - `dashboard/features/marketing/ProductHome/ProductHome.module.css`
  - `dashboard/hooks/useRevealMotion.ts`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build` (50 routes generated successfully; homepage first-load JS 120 kB)
  - `git diff --check`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-12 - Reference-matched homepage structural refactor

- Replaced the earlier floating product-story homepage with the composition from the supplied Sauti reference: compact split hero, large dashboard proof point, five numbered full-width product chapters, six-step call lifecycle, four business use cases, security/control band, FAQ, and focused final CTA.
- Built chapter previews for the actual product surfaces: agent configuration and voice preview, calls list and transcript, booking status, analytics and language mix, and the integration marketplace.
- Explicitly excluded customer-logo claims, testimonial cards, and marketing metric tiles as requested.
- Removed the superseded `ProductHome` implementation and its GitHub-inspired scroll progress/parallax behavior. The shared motion hook is back to restrained intersection-based reveal transitions with reduced-motion support in the page styles.
- Kept all previews code-native and responsive so they remain visually aligned with the console without shipping large screenshot assets.
- After reviewing the deployed-scale screenshot, widened the desktop system from 1180px to 1320px, increased hero and preview typography, enlarged every product frame and chapter, tightened excessive vertical gaps, and raised navigation, footer, workflow, security, FAQ, and CTA legibility to match the supplied reference's density.
- Alternated the five product chapters so copy and product canvases switch sides from top to bottom. Added a staggered light sweep and hover depth across each product canvas to keep the stacked feature sequence active without reintroducing scroll-jacking.
- Added a continuously looping, left-to-right integration rail for Google Calendar, Google Sheets, WhatsApp, HubSpot, Salesforce, Slack, Calendly, Zapier, Twilio, and M-Pesa, including moving connection signals and hover-to-pause behavior.
- Added a responsive industry bento section with distinct healthcare, real-estate, commerce, education, wellness, and local-service treatments rather than a uniform row of generic cards.
- Files touched:
  - `dashboard/features/marketing/HomePage/HomePage.tsx`
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.tsx`
  - `dashboard/features/marketing/ReferenceHome/ReferenceHome.module.css`
  - `dashboard/features/marketing/ProductHome/ProductHome.tsx` (removed)
  - `dashboard/features/marketing/ProductHome/ProductHome.module.css` (removed)
  - `dashboard/hooks/useRevealMotion.ts`
  - `dashboard/styles/marketing/foundation.css`
  - `docs/agent-handoff.md`
- Verification:
  - `npm.cmd run typecheck`
  - `npm.cmd run build` (50 routes generated successfully; homepage first-load JS 119 kB)
  - `git diff --check`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-15 - Provider-aware OpenAI Realtime voice path

- Added a native OpenAI Realtime WebRTC path for authenticated Agent Studio test calls and public Web Voice calls. Browser audio now travels directly over the Realtime peer connection after Sauti performs the authenticated server-side SDP exchange; the OpenAI API key is never returned to the browser.
- Kept the existing Deepgram -> OpenAI text LLM -> Cartesia TTS cascade intact for Cartesia voices. Runtime routing is explicit from the stored voice prefix: `openai:*` selects native speech-to-speech, while `cartesia:*` selects the cascade.
- Added the current OpenAI Realtime built-in voices to the catalog, with `openai:marin` as the default for agents without a selected voice. OpenAI and Cartesia are visibly separated in Agent Studio, and each provider uses its own compatible preview endpoint.
- Configured native server VAD for fast end-of-turn detection and automatic response interruption. Caller speech stops Realtime output, allowing true barge-in rather than merely transcribing over agent playback.
- Forwarded Realtime function calls through the existing tenant/agent-scoped `ToolFulfillmentRouter`, preserving booking and integration actions. Caller/agent transcripts are posted back to Sauti and persisted as call turns; mutual goodbyes close the browser call after playback completes.
- Added OpenAI Realtime/TTS configuration placeholders to local and production environment examples. Defaults are `gpt-realtime-1.5`, `gpt-4o-mini-transcribe`, and `gpt-4o-mini-tts`, all overridable through environment variables.
- Added focused tests for provider-separated voice catalog behavior and the server-side multipart Realtime SDP request, including authorization, model, VAD interruption, voice, and transcription settings.
- Files touched:
  - `.env.example`
  - `deploy/.env.production.example`
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/java/com/sauti/api/CallController.java`
  - `backend/src/main/java/com/sauti/api/PublicWebVoiceController.java`
  - `backend/src/main/java/com/sauti/call/CallDtos.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/main/java/com/sauti/call/OpenAiRealtimeService.java`
  - `backend/src/main/java/com/sauti/call/RealtimeDtos.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/voice/VoiceCatalogService.java`
  - `backend/src/test/java/com/sauti/call/OpenAiRealtimeServiceTest.java`
  - `backend/src/test/java/com/sauti/voice/VoiceCatalogServiceTest.java`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/agents/AgentCreator/VoicePicker.tsx`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `dashboard/lib/api/calls.ts`
  - `dashboard/lib/api/client.ts`
  - `dashboard/lib/api/public-web-voice.ts`
  - `dashboard/types/api.ts`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`
  - `.\gradlew.bat :backend:test --tests "com.sauti.voice.VoiceCatalogServiceTest" --tests "com.sauti.call.OpenAiRealtimeServiceTest"`
  - `npm.cmd run typecheck`
  - `npm.cmd run build` (50 routes generated successfully)
  - `git diff --check`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - Native Realtime routing is implemented for browser/WebRTC calls. Telnyx/Twilio phone media continues to use the existing cascade until a provider-native SIP or server WebSocket Realtime bridge is implemented and load-tested.
  - A live OpenAI session was not opened during automated verification; production requires a funded `OPENAI_API_KEY` with Realtime access and browser/network access to WebRTC.

### 2026-07-15 - Realtime playback, completion, picker, and Cartesia latency corrections

- Removed the artificial 180-token OpenAI Realtime response cap. Audio tokens consumed that budget quickly and could stop speech mid-sentence; Realtime now uses an unlimited per-response budget while the conversation prompt continues to enforce concise answers.
- Routed Realtime remote audio through the already user-activated Web Audio context in both Agent Studio and public Web Voice. This avoids delayed `HTMLAudioElement.play()` autoplay rejections and suppresses the false playback-blocked error emitted during call teardown.
- Corrected the voice picker from five to six explicit grid rows after adding the engine selector. Renamed and restyled the engine selector to avoid collision with an older provider-banner class, restored the Best match/English/French/Arabic tabs, and fixed the accent selector's transparent click overlay.
- Reduced Cartesia latency without removing its voice catalog. Sauti now accumulates raw LLM token deltas into short speakable phrases before sending them over the existing long-lived Cartesia WebSocket, then sets `max_buffer_delay_ms=0` because buffering is handled by Sauti. This avoids Cartesia's documented default buffer of up to 3000 ms for punctuation-free token streams.
- Added `CARTESIA_MAX_BUFFER_DELAY_MS=0` to local and production environment examples and a focused test proving that phrase buffering does not lose final words.
- Files touched in this correction:
  - `.env.example`
  - `deploy/.env.production.example`
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/java/com/sauti/call/CartesiaRealtimeTextToSpeechClient.java`
  - `backend/src/main/java/com/sauti/call/OpenAiRealtimeService.java`
  - `backend/src/main/java/com/sauti/call/WebVoiceSessionService.java`
  - `backend/src/test/java/com/sauti/call/OpenAiRealtimeServiceTest.java`
  - `backend/src/test/java/com/sauti/call/WebVoiceSpeechBufferTest.java`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/agents/AgentCreator/VoicePicker.tsx`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test`
  - `.\gradlew.bat :backend:test --tests "com.sauti.call.OpenAiRealtimeServiceTest" --tests "com.sauti.call.WebVoiceSpeechBufferTest"`
  - `npm.cmd run typecheck`
  - `npm.cmd run build` (50 routes generated successfully)
  - `git diff --check`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-up:
  - The next larger Cartesia improvement would replace the separate STT and chat-completions inference stages with either Cartesia Line's managed low-latency audio orchestration/OpenAI WebSocket mode or a custom OpenAI Realtime text-output -> Cartesia Sonic streaming bridge.

### 2026-07-15 - OpenAI Realtime + Cartesia Sonic hybrid voice runtime

- Added a third explicit browser runtime, `hybrid_realtime`, for agents whose saved voice uses the `cartesia:` prefix while both OpenAI Realtime and Cartesia are configured.
- Hybrid calls now send caller audio directly from browser WebRTC to OpenAI Realtime for native audio understanding, transcription, server VAD, turn detection, instructions, and tool calls. OpenAI streams text output instead of synthesizing audio for this mode.
- Added an authenticated `/ws/hybrid-voice/{callSid}` bridge. It keeps one Cartesia Sonic WebSocket warm, buffers OpenAI text deltas into short speakable phrases, streams PCM back to the browser, and flushes the final phrase so long responses are not cut off.
- Kept provider routing explicit and backward compatible: `openai:` voices use native OpenAI speech-to-speech, `cartesia:` voices use the hybrid runtime when available, and the existing Deepgram/LLM/Cartesia cascade remains the fallback when Realtime is not configured.
- Coordinated barge-in across both providers. OpenAI server VAD interrupts only an active model response; the same speech-start event clears queued browser PCM and closes/reopens the Cartesia context so the old sentence cannot continue over the caller. Ordinary caller speech while the agent is idle no longer triggers a spurious response cancellation.
- The transcript shown and persisted for hybrid calls is assembled from the same OpenAI text deltas sent to Cartesia, preventing displayed text and spoken text from diverging.
- Added focused coverage for Cartesia hybrid session configuration, PCM forwarding, phrase completion, and interruption context replacement.
- Files touched for the hybrid runtime:
  - `backend/src/main/java/com/sauti/api/CallController.java`
  - `backend/src/main/java/com/sauti/api/PublicWebVoiceController.java`
  - `backend/src/main/java/com/sauti/call/HybridVoiceSessionService.java`
  - `backend/src/main/java/com/sauti/call/HybridVoiceWebSocketHandler.java`
  - `backend/src/main/java/com/sauti/call/OpenAiRealtimeService.java`
  - `backend/src/main/java/com/sauti/call/WebSocketConfig.java`
  - `backend/src/test/java/com/sauti/call/HybridVoiceSessionServiceTest.java`
  - `backend/src/test/java/com/sauti/call/OpenAiRealtimeServiceTest.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `dashboard/lib/api/public-web-voice.ts`
  - `dashboard/types/api.ts`
- Verification:
  - `.\gradlew.bat :backend:test --tests "com.sauti.call.OpenAiRealtimeServiceTest" --tests "com.sauti.call.HybridVoiceSessionServiceTest" --tests "com.sauti.call.WebVoiceSpeechBufferTest"`
  - `.\gradlew.bat :backend:test --rerun-tasks`
  - `npm.cmd run typecheck`
  - `npm.cmd run build` (50 routes generated successfully)
  - `git diff --check`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - This hybrid path covers browser/Agent Studio WebRTC calls. Telnyx/Twilio phone media still uses the existing server-side cascade until a server-side OpenAI Realtime media bridge or provider SIP path is implemented and load-tested.
  - Automated tests use mocked provider streams; a live environment still needs funded OpenAI Realtime and Cartesia credentials to measure first-audio latency and tune VAD/phrase thresholds under real network conditions.

### 2026-07-16 - Realtime turn ordering and complete hybrid speech

- Buffered agent transcript delivery until the active caller transcription completes. OpenAI Realtime transcription completion events can arrive after response events, so the UI now always inserts the caller turn before its agent response.
- Serialized browser transcript persistence for Agent Studio and public Web Voice calls. Caller and agent writes can no longer overtake each other on separate HTTP requests, which also preserves the correct server-side turn association.
- Serialized every Cartesia phrase and final flush behind one per-session write chain. This prevents the flush from overtaking phrases queued while the Cartesia WebSocket is still connecting, which previously could stop spoken responses mid-sentence.
- Disabled provider-managed response interruption for hybrid Cartesia sessions because Cartesia audio is outside OpenAI's native output channel. Hybrid barge-in now requires 180 ms of sustained caller speech before the browser explicitly cancels the model and Cartesia playback, reducing echo-triggered cutoffs while preserving polite interruption.
- Made hybrid server VAD more patient and selective (`520 ms` silence, `0.55` threshold) while retaining the faster native OpenAI settings (`320 ms`, `0.45`). This avoids prematurely splitting natural pauses such as “Monday in ... three p.m.” into separate turns.
- Files touched:
  - `backend/src/main/java/com/sauti/call/HybridVoiceSessionService.java`
  - `backend/src/main/java/com/sauti/call/OpenAiRealtimeService.java`
  - `backend/src/test/java/com/sauti/call/HybridVoiceSessionServiceTest.java`
  - `backend/src/test/java/com/sauti/call/OpenAiRealtimeServiceTest.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - `\.\gradlew.bat :backend:test --tests "com.sauti.call.OpenAiRealtimeServiceTest" --tests "com.sauti.call.HybridVoiceSessionServiceTest"`
  - `\.\gradlew.bat :backend:test --rerun-tasks` (successful)
  - `npm.cmd run typecheck` (successful)
  - `npm.cmd run build` was attempted twice but the local Next.js process remained at startup until the command timeout; no compile error was reported. A pre-existing long-running dashboard Node process was present, so CI should confirm the clean production build.
  - `git diff --check`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-up / risk:
  - Automated provider streams pass, but a live browser call is still required after CI/CD to tune the 180 ms barge-in debounce and 520 ms hybrid endpoint for the deployment's microphones, speakers, and network conditions.

### 2026-07-16 - Cartesia-only voice surface and masked hybrid startup

- Kept OpenAI Realtime as the internal WebRTC conversation engine for speech understanding, VAD, reasoning, and tools, while removing its built-in voices from the customer-facing catalog. Agent Studio now presents one Cartesia voice library instead of exposing an unreliable native/non-English engine choice.
- Existing `openai:` selections are replaced in the editor with the first compatible Cartesia voice so they can be reviewed and saved. Runtime support for legacy OpenAI selections remains temporarily intact rather than silently changing already-saved production agents outside the editor.
- Added exact-greeting Cartesia audio caching keyed by voice, language, and text. Browser test calls and public Web Voice calls can play that audio immediately while the OpenAI WebRTC handshake and warm Cartesia streaming WebSocket connect concurrently behind it.
- Bounded a greeting cache miss to 1.5 seconds. A slower Cartesia request continues warming the shared cache, while the current call falls back to the existing streamed hybrid greeting instead of blocking startup indefinitely.
- Parallelized microphone audio preparation with call-session creation in Agent Studio, and AudioContext activation with public session creation. Cartesia and OpenAI connections are also established concurrently rather than sequentially.
- Added a reusable `speakGreeting` Realtime client operation for the safe fallback path when cached audio is unavailable or cannot be decoded.
- Files touched:
  - `backend/src/main/java/com/sauti/api/CallController.java`
  - `backend/src/main/java/com/sauti/api/PublicWebVoiceController.java`
  - `backend/src/main/java/com/sauti/call/CallDtos.java`
  - `backend/src/main/java/com/sauti/voice/VoiceCatalogService.java`
  - `backend/src/test/java/com/sauti/voice/VoiceCatalogServiceTest.java`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/agents/AgentCreator/VoicePicker.tsx`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `dashboard/types/api.ts`
  - `docs/agent-handoff.md`
- Verification:
  - `\.\gradlew.bat :backend:test --tests "com.sauti.voice.VoiceCatalogServiceTest" --tests "com.sauti.call.OpenAiRealtimeServiceTest" --tests "com.sauti.call.HybridVoiceSessionServiceTest"`
  - `\.\gradlew.bat :backend:test --rerun-tasks` (successful)
  - `npm.cmd run typecheck` (successful)
  - `npm.cmd run build` (successful; 50 routes generated)
  - `git diff --check`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - A live production-like call is still needed to measure cache-hit and cache-miss time-to-first-audio with real Cartesia/OpenAI credentials and network latency.
  - Saved agents that still carry an `openai:` voice keep the legacy runtime until a maintainer opens, reviews, saves, or explicitly migrates them to a Cartesia voice.

### 2026-07-16 - Agent-scoped business identity and runtime configuration audit

- Made the filled agent variable `business_name` the authoritative identity for instant greetings, generated opening prompts, fallback greetings, system context, and call-screen responses. The tenant/account business name is now only a fallback when the agent has no filled `business_name` variable.
- Added regression coverage proving an agent for `X-Fit` no longer introduces the tenant account `Tranquil AI`, including both instant browser greetings and generated/fallback opening logic.
- Audited Agent Studio configuration against the active runtimes:
  - Main identity, prompt, language, voice, timezone, greeting, static knowledge, safety guardrails, enabled tools, transcript retention, phone recording, operating hours/after-hours behavior, and post-call field selection have active runtime consumers.
  - Phone/cascade calls actively consume agent interruption sensitivity/grace, Deepgram endpointing/vocabulary/keyterms, maximum duration, silence reminders/end timeout, voicemail/call-screen detection, and DTMF settings.
  - Agent Studio browser tests consume maximum duration and silence settings, but the hybrid OpenAI Realtime + Cartesia path currently uses fixed VAD/interruption values instead of the saved sensitivity, grace, and endpointing values.
  - Public hybrid Web Voice does not currently enforce the configured maximum duration/silence reminders, voicemail/call screening, DTMF, or Deepgram-specific vocabulary settings.
  - Static knowledge text is included in Realtime instructions. Uploaded-document semantic retrieval is query-driven in cascaded turns, but is not refreshed per caller turn inside the current Realtime session.
  - Post-call summary/success/sentiment/intent run only when transcript retention is enabled and the configured analysis provider is available; integration jobs are still enqueued when analysis is not required.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/AgentVariableService.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/call/CallPipelineServiceTest.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `\.\gradlew.bat :backend:test --tests "com.sauti.call.CallPipelineServiceTest" --tests "com.sauti.llm.ConversationOrchestratorTest"`
  - `\.\gradlew.bat :backend:test --rerun-tasks` (successful)
  - `git diff --check`
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-up / risk:
  - The next runtime-alignment change should pass saved interruption/VAD and public silence-duration settings into the hybrid Realtime session, then expose uploaded knowledge retrieval as a safe Realtime tool or refreshable context source.

### 2026-07-16 - Architecture hardening foundation

- Removed repository access from REST/WebSocket controllers. `CallQueryService` now owns tenant-checked call-turn reads and TTS latency updates, while `PublicWebVoiceAccessService` owns public-agent and session authorization.
- Added `ApiBoundaryTest`, which fails the backend build if a class in `com.sauti.api` imports a repository.
- Replaced the process-local public Web Voice start limiter and duplicated authentication limiter logic with a shared Redis limiter. Identities are SHA-256 protected in Redis keys, and Lua applies increment plus expiry atomically across replicas.
- Replaced integration disconnect's global agent-integration scan with a tenant-and-connection-scoped repository query.
- Added the production Spring profile and `ProductionSafetyValidator`. Production now fails startup for placeholder secrets, fake providers, H2, development token exposure, insecure origins/WebSockets, or disabled signature verification for the selected phone provider.
- Added bounded Micrometer voice metrics for browser-hybrid and phone runtimes: active/started/ended sessions, stage latency histograms, caller interruptions, provider fallbacks, and failures. No tenant, call, agent, or caller identifiers are used as meter tags.
- Added a non-interactive zero-warning ESLint configuration, enforced it in GitHub Actions, removed dead dashboard components/imports, and corrected hook cleanup/catalog initialization without changing live behavior.
- Rewrote the backend module map and added `docs/architecture-hardening.md` with the prioritized follow-up roadmap and service-extraction criteria.
- Files touched:
  - `.github/workflows/ci.yml`
  - `backend/src/main/java/com/sauti/api/CallController.java`
  - `backend/src/main/java/com/sauti/api/PublicWebVoiceController.java`
  - `backend/src/main/java/com/sauti/auth/AuthRateLimitService.java`
  - `backend/src/main/java/com/sauti/call/CallQueryService.java`
  - `backend/src/main/java/com/sauti/call/DefaultTwilioMediaStreamService.java`
  - `backend/src/main/java/com/sauti/call/HybridVoiceSessionService.java`
  - `backend/src/main/java/com/sauti/call/PublicWebVoiceAccessService.java`
  - `backend/src/main/java/com/sauti/call/PublicWebVoiceRateLimitService.java`
  - `backend/src/main/java/com/sauti/call/VoiceRuntimeMetrics.java`
  - `backend/src/main/java/com/sauti/integration/IntegrationRepositories.java`
  - `backend/src/main/java/com/sauti/integration/IntegrationService.java`
  - `backend/src/main/java/com/sauti/shared/ProductionSafetyValidator.java`
  - `backend/src/main/java/com/sauti/shared/RedisRateLimiter.java`
  - `backend/src/main/resources/application-production.yml`
  - related backend tests under `backend/src/test/java/com/sauti/{architecture,call,shared}`
  - `dashboard/eslint.config.mjs`, `dashboard/package.json`, and lint cleanup in affected dashboard feature/marketing files
  - `deploy/docker-compose.prod.yml`
  - `deploy/.env.production.example`
  - `docs/backend-modules.md`
  - `docs/architecture-hardening.md`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test` (successful)
  - `.\gradlew.bat :backend:test --tests com.sauti.shared.RedisRateLimiterTest` (successful after the final test cleanup)
  - `npm.cmd run lint` (successful, zero warnings)
  - `npm.cmd run typecheck` (successful)
  - `npm.cmd run build` (successful; 50 routes generated)
  - `git diff --check` (successful; Git reported only expected working-tree line-ending notices)
- Deployment:
  - Not deployed. All changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - Durable database-backed worker claiming is the next scaling prerequisite before multiple backend replicas process post-call jobs.
  - `AgentCreator.tsx` and `TestCallPanel.tsx` remain large and should be split behind feature hooks in incremental, behavior-preserving changes.
  - Existing Spring test warnings for deprecated `@MockBean` remain outside this change and should migrate to the replacement test annotation before the next Spring Boot major upgrade.

### 2026-07-16 - Production provider-mode compatibility fix

- Fixed the production startup failure introduced by the safety validator on installations whose existing `/opt/sauti/.env.production` predates `PROVIDER_MODE`.
- The `production` profile now defaults `sauti.providers.mode` to `live` instead of inheriting the development-only `fake` fallback from `application.yml`.
- An explicitly configured non-live `PROVIDER_MODE` is still honored and rejected by `ProductionSafetyValidator`; the guard has not been weakened.
- Files touched:
  - `backend/src/main/resources/application-production.yml`
  - `backend/src/test/java/com/sauti/shared/ProductionSafetyValidatorTest.java`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.shared.ProductionSafetyValidatorTest`
  - `git diff --check`
- Deployment:
  - Not deployed by the coding agent. The fix remains uncommitted for maintainer review and the normal CI/CD chain.
- Follow-up / risk:
  - Maintainers should still add `PROVIDER_MODE=live` to `/opt/sauti/.env.production` during the next environment-maintenance window so the intended mode remains explicit outside the application defaults.

### 2026-07-16 - Optional WhatsApp webhook configuration cleanup

- Kept WhatsApp webhook verification fail-closed when no Meta App Secret is configured; unsigned requests are never accepted in production merely to silence a startup message.
- Changed the expected optional/unconfigured channel message from a warning to an informational disabled-state message.
- Unified webhook verification and Embedded Signup on the same Meta App Secret. `WHATSAPP_APP_SECRET` remains canonical, with `WHATSAPP_EMBEDDED_SIGNUP_APP_SECRET` supported as a fallback for existing installations.
- Clarified the production environment example and added regression coverage for the unconfigured fail-closed behavior.
- Files touched:
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/java/com/sauti/whatsapp/WhatsAppSignatureValidator.java`
  - `backend/src/test/java/com/sauti/whatsapp/WhatsAppSignatureValidatorTest.java`
  - `deploy/.env.production.example`
  - `docs/agent-handoff.md`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.whatsapp.WhatsAppSignatureValidatorTest --tests com.sauti.WhatsAppWebhookSecurityTest`
  - `git diff --check`
- Deployment:
  - Not deployed by the coding agent. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-up / risk:
  - To activate WhatsApp, set `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN`, and the Embedded Signup application/configuration IDs. Keep `WHATSAPP_VALIDATE_SIGNATURE=true`.

### 2026-07-17 - Production provider override and deployment diagnostics

- Diagnosed failed deployment run `29531947805` for commit `db707222711e6d5fc2f0d05290d8871e085b6e0a`: CI passed, the remote deploy exhausted its health window, Caddy could not connect to backend management port `8081`, and the workflow rolled back to the previous healthy revision.
- Fixed the production-mode compatibility gap by setting `PROVIDER_MODE=live` directly in the backend Compose environment. This overrides a stale explicit `PROVIDER_MODE=fake` in long-lived server environment files; the profile-level default alone only handled a missing value.
- Hardened `deploy.sh` failure handling. Failed container startup, Caddy reload, and health checks now print Compose state plus the backend container state, exit code, OOM flag, restart count, image, and 300 lines of timestamped service logs without printing environment values.
- Centralized rollback so all post-start failures restore the last successful image tag and reload Caddy consistently.
- Health polling now stops early when the backend container disappears or leaves the running state instead of emitting Caddy 502 errors for the entire timeout.
- Files touched:
  - `deploy/docker-compose.prod.yml`
  - `deploy/deploy.sh`
  - `docs/agent-handoff.md`
- Verification:
  - `bash -n deploy/deploy.sh` (successful)
  - `docker-compose.exe --env-file deploy/.env.production.example -f deploy/docker-compose.prod.yml config` with `SAUTI_ENV_FILE` pointed at the absolute example path (successful; rendered backend environment contains `PROVIDER_MODE: live`)
  - `git diff --check`
- Deployment:
  - Not deployed by the coding agent. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-up / risk:
  - After the maintainer pushes this change, monitor the next CI-triggered deployment and verify `https://sauti.uk/health`. If startup still fails, the enhanced diagnostics should expose the actual backend exit reason before rollback.

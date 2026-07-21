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

### 2026-07-17 - Complete speech chunks and authoritative calendar availability

- Fixed streamed Cartesia speech segmentation so abbreviations such as `P.M.`, `A.M.`, `e.g.`, and `U.S.` are not mistaken for sentence endings. Short comma fragments are no longer sent as standalone speech, while complete sentences can still begin playback without waiting for the whole response.
- Raised the cascaded LLM output ceiling from 120 to 220 tokens to prevent the provider from cutting a valid response in the middle of a sentence; prompt rules still keep spoken turns brief.
- Added a shared multilingual availability-intent detector for English, French, and Arabic dates/times.
- Made availability checks authoritative across every runtime:
  - cascaded turns buffer the availability decision before speech and require `check_availability`;
  - browser OpenAI Realtime and hybrid OpenAI + Cartesia sessions force the specific function through per-response `tool_choice`;
  - phone OpenAI Realtime + Cartesia sessions apply the same forced tool choice before responding.
- Exposed whether `check_availability` is configured in browser/public session start responses so non-booking agents are not forced to call a missing tool.
- Enriched `check_availability` results with operating windows, requested time, exact-match status, nearby slots, and next open business windows. The result now distinguishes closed hours, a fully booked calendar, an unavailable requested time, and an available requested time.
- Preserved exact spoken times such as `3 P.M.` as `15:00`; the agent is explicitly forbidden from silently changing them to another slot.
- Made the local calendar preview obey the agent's operating hours instead of returning fixed sample times on closed days.
- Added operating-hours context and anti-hallucination rules to the conversation prompt so agents do not invent classes, services, treatments, prices, or schedules.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/OperatingHoursSchedule.java`
  - `backend/src/main/java/com/sauti/api/{CallController,PublicWebVoiceController}.java`
  - `backend/src/main/java/com/sauti/calendar/LocalCalendarProvider.java`
  - `backend/src/main/java/com/sauti/call/{CallDtos,OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider,SentenceChunker,WebVoiceDtos,WebVoiceSessionService}.java`
  - `backend/src/main/java/com/sauti/llm/{AvailabilityIntentDetector,ConversationOrchestrator,LlmToolTurnContext,SpringAiToolCallingLlmProvider}.java`
  - `backend/src/main/java/com/sauti/tool/{DefaultToolSeeder,SautiCalendarFulfillment}.java`
  - related tests under `backend/src/test/java/com/sauti/{agent,calendar,call,llm,tool}`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `dashboard/lib/api/public-web-voice.ts`
  - `dashboard/types/api.ts`
  - `docs/agent-handoff.md`
- Verification:
  - targeted backend calendar, speech-buffer, orchestrator, and Realtime tests (successful)
  - `.\gradlew.bat :backend:test` (successful; 153 tests)
  - `npm.cmd run lint` (successful; zero warnings)
  - `npm.cmd run typecheck` (successful)
  - `npm.cmd run build` (successful; 50 routes generated)
  - `git diff --check` (successful; only expected line-ending notices)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - A live call with real Google Calendar credentials is still required to verify actual provider availability and measure time-to-first-audio; automated tests cover business-hour filtering, exact-time matching, forced tool selection, and runtime routing.
  - The OpenAI developer-docs MCP server was added to the local Codex configuration during implementation and requires a Codex restart before its tools become available in a future session.

### 2026-07-17 - Prevent structured tool payloads and duplicate Realtime speech

- Fixed a production-facing Realtime regression where a provider could return required calendar arguments as ordinary JSON text. Structured payloads are now detected before TTS or transcript persistence, removed from the Realtime conversation, converted into the intended `check_availability` call, and followed by one natural tool-backed response.
- Added the same structured-output guard to the cascaded orchestrator and the transcript persistence boundary so provider protocol data cannot become caller-facing speech or contaminate later conversation history.
- Added an explicit expected-response gate in browser and phone Realtime sessions. Unsolicited provider responses are cancelled, preventing the agent from speaking twice without a new caller turn or tool result.
- Refreshes Realtime session instructions after each accepted caller transcript with the latest authoritative intake notes. This helps the agent retain corrected names, phone digits, service, date, and time instead of reverting to an older candidate.
- Removed a duplicate transcript write for typed browser-test turns.
- Improved phone-number restart handling so a caller-requested full restart replaces the rejected candidate and later digit fragments append to the replacement. The transcript regression now preserves `01115653441` instead of reusing the model's earlier `7`.
- Added exact localized time normalization for French `midi`/`minuit` and English `noon`/`midnight`, so `demain a midi` checks 12:00 rather than only checking the day.
- Reinforced voice prompts to keep tool actions silent, return one concise answer, and remain in the active caller language.
- Files touched:
  - `backend/src/main/java/com/sauti/api/{CallController,PublicWebVoiceController}.java`
  - `backend/src/main/java/com/sauti/call/{CallIntakeNoteService,CallPipelineService,OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider,RealtimeDtos,VoiceOutputGuard}.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/tool/SautiCalendarFulfillment.java`
  - related regression tests under `backend/src/test/java/com/sauti/{call,llm,tool}`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `dashboard/lib/api/{calls,public-web-voice}.ts`
  - `docs/agent-handoff.md`
- Verification:
  - focused structured-output, phone-Realtime, intake-note, orchestrator, and calendar tests (successful)
  - `.\gradlew.bat :backend:test` (successful)
  - `npm.cmd run lint` (successful; zero warnings)
  - `npm.cmd run typecheck` (successful)
  - `npm.cmd run build` (successful; 50 routes generated)
  - `git diff --check` (successful; only expected line-ending notices)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - A live Cartesia browser test and a live Telnyx phone call with real Google Calendar credentials should validate the provider-specific event ordering and time-to-first-audio. Automated tests reproduce and block the leaked `{"date": ..., "time_preference": "midi"}` path.

### 2026-07-17 - Realtime protocol compliance and calendar failure safety

- Fixed the two Realtime protocol errors observed after structured availability recovery:
  - partial `session.update` events now include `session.type = realtime`, as required by the current OpenAI Realtime schema;
  - recovered function-call IDs are restricted to the provider's 32-character limit.
- Calendar tool failures no longer return to the model for an improvised response. Browser and phone hybrid runtimes now speak a deterministic, localized statement that the live calendar could not be confirmed and that the requested time is not booked.
- Successful availability results receive turn-specific constraints: preserve the exact requested date/time, use only the tool output, and never claim that availability means a booking, hold, or callback.
- Ordinary Realtime responses now reiterate that configured facts are authoritative and prohibit invented classes, services, examples, actions, or altered caller details.
- Extended authoritative intake notes to retain English relative days and exact 12/24-hour clock values. `Tomorrow 08:00 p.m.` is stored as `tomorrow` at `20:00`, preventing later substitution with another time.
- New Google Calendar OAuth connections now run a live free/busy probe before being marked connected or attached to agent tools. Failed scopes, disabled API access, or unusable credentials roll back instead of appearing connected until a live call.
- Added safe server-side logging for failed Realtime tool execution without logging credentials or tool arguments.
- Files touched:
  - `backend/src/main/java/com/sauti/calendar/GoogleCalendarIntegrationService.java`
  - `backend/src/main/java/com/sauti/call/{CallIntakeNoteService,OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider,VoiceOutputGuard}.java`
  - related regression tests under `backend/src/test/java/com/sauti/call`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `docs/agent-handoff.md`
- Verification:
  - focused Realtime protocol, intake-note, calendar fulfillment/provider, and integration synchronization tests (successful)
  - `npm.cmd run lint` (successful; zero warnings)
  - `npm.cmd run typecheck` (successful)
  - `npm.cmd run build` (successful; 50 routes generated)
  - `.\gradlew.bat :backend:test` (successful)
  - `git diff --check` (successful; only expected line-ending notices)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - Existing Google Calendar connections are not re-authorized by this code change. After CI deployment, use the marketplace's `Test live connection` action once for the selected agent. If it fails, reconnect Google Calendar so the new OAuth validation runs and the refresh token/scopes are renewed.

### 2026-07-18 - Deterministic availability decisions and resilient Google Calendar calls

- Fixed the main failure shown in the French and English transcripts: natural Realtime arguments such as `demain à midi`, `Monday at 5 p.m.`, missing date fields, and dotted A.M./P.M. values are normalized into the strict calendar contract before tool routing. The normalizer also recovers the latest accepted caller transcript when the model emits incomplete tool arguments.
- Reordered availability evaluation so configured business hours are authoritative and evaluated before Google Calendar. Closed days and requested appointments that would extend past closing time are answered immediately without a provider request.
- Converted Google/provider availability outages into a successful, structured `calendar_temporarily_unavailable` decision. This prevents an upstream error from entering the model as an open-ended failure while still making it explicit that no appointment was booked.
- Added deterministic caller-safe availability speech in English, French, Arabic, and Swahili. Browser OpenAI audio, browser OpenAI + Cartesia, phone OpenAI + Cartesia, and cascaded production providers now use that response directly instead of asking the model to reinterpret dates, times, alternatives, or failure state.
- Preserved the local heuristic provider's follow-up pass because it uses that pass to persist its booking draft; production model providers remain on the lower-latency deterministic path.
- Added a one-time OAuth refresh and retry when Google rejects an apparently unexpired access token with HTTP 401.
- Added controlled missing-date clarification instead of exposing a tool error when no usable date can be resolved.
- Files touched:
  - `backend/src/main/java/com/sauti/calendar/GoogleCalendarApiClient.java`
  - `backend/src/main/java/com/sauti/call/{OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider}.java`
  - `backend/src/main/java/com/sauti/llm/{ConversationOrchestrator,LlmToolCallingProvider,LocalToolCallingLlmProvider}.java`
  - `backend/src/main/java/com/sauti/tool/{AvailabilityRequestNormalizer,AvailabilitySpeechRenderer,SautiCalendarFulfillment}.java`
  - related tests under `backend/src/test/java/com/sauti/{call,llm,tool}`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `docs/agent-handoff.md`
- Verification:
  - focused normalizer, calendar fulfillment, Realtime service, phone Realtime, orchestrator, and end-to-end auth/agent-flow tests (successful)
  - `\.\gradlew.bat :backend:test --no-daemon` (successful; 169 tests, up to date after the completed full run)
  - `npm.cmd run typecheck` (successful)
  - `npm.cmd run build` (successful; 50 routes generated)
  - `git diff --check` (successful; only expected line-ending notices)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - Automated tests cover normalization, business-hours-first behavior, deterministic speech, provider degradation, and Realtime routing. A live Google call still requires real credentials and cannot be simulated locally.
  - After CI deployment, run `Test live connection` for Google Calendar on each affected agent. Any pre-existing OAuth grant that lacks `calendar.freebusy` or has an invalid refresh token must be reconnected once; the new runtime retry cannot add scopes to an old grant.

### 2026-07-18 - Fix asynchronous Realtime calendar credential resolution

- Investigated the live French calendar failure after the user disconnected and reconnected Google Calendar.
- Confirmed through GitHub Actions that production commit `2381866e20b41ea0fa646c38d56b72ae46d17cd0` deployed successfully and remained healthy.
- Ran the existing read-only `Production diagnostics` workflow as run `29620248471` for the preceding two hours. It confirmed the affected French Cartesia sessions at 22:58, 22:59, and 23:01 UTC without a voice-provider error. The workflow's current grep filter does not include calendar warnings, so it could not expose the underlying exception text.
- Identified a runtime-specific persistence defect: Realtime tool fulfillment runs asynchronously after the repository transaction closes, while `CalendarProviderFactory.forTool` dereferenced `toolConfig.getAgent().getTenant()` through a lazy JPA association. The synchronous OAuth free/busy probe could therefore succeed during reconnect while every later asynchronous availability lookup degraded as unavailable.
- Added a tenant-scoped provider resolution overload that accepts the authoritative tenant ID from the active call and never dereferences the detached agent association. Availability and booking tool paths now use it.
- Kept `forAgent` transaction-scoped for non-call booking operations that resolve a provider from an agent tool.
- Added a regression test that makes any `AgentTool.getAgent()` access fail and verifies Google provider resolution still succeeds using the explicit tenant ID.
- Files touched:
  - `backend/src/main/java/com/sauti/tool/CalendarProviderFactory.java`
  - `backend/src/main/java/com/sauti/tool/SautiCalendarFulfillment.java`
  - `backend/src/test/java/com/sauti/tool/{CalendarProviderFactoryTest,SautiCalendarFulfillmentTest}.java`
  - `docs/agent-handoff.md`
- Verification:
  - focused calendar factory, fulfillment, browser Realtime, and phone Realtime tests (successful)
  - `\.\gradlew.bat :backend:test --no-daemon` (successful)
  - `git diff --check` (successful; only expected line-ending notices)
- Deployment:
  - Not deployed by the coding agent. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - Extend the existing production diagnostics workflow filter to include `Live calendar availability failed`, `Realtime tool failed`, and Google Calendar messages so future runtime failures can be diagnosed without direct server access.

### 2026-07-18 - Enforce agent business identity, role fidelity, and single-response turns

- Analyzed the supplied Alec and Sarah transcripts as three independent runtime failures: workspace identity leakage, loss of the configured business role/capabilities, and duplicate Realtime final-transcription events that could trigger identical consecutive answers.
- Removed the tenant/workspace business name as a voice-agent fallback. An explicit `business_name` remains authoritative; older agents can recover a customer-facing business name from clear saved-prompt declarations such as `virtual assistant for X-Fit` or a structured `Name:` field. If no agent business is configured, the greeting uses the agent name only instead of saying `Tranquil AI`.
- Strengthened shared browser and phone Realtime instructions so an agent cannot abandon its configured business, direct the caller to contact the same business elsewhere, deny booking/trial capabilities granted by its prompt, invent class examples, or ask more than one question in a reply.
- Added turn-specific business-role constraints. Booking intent reinforces the configured workflow, while business-hours questions are answered from structured operating hours or explicit saved-prompt hours.
- Separated business-hours language such as `When are you available?` and `Quelles sont vos horaires ?` from appointment-slot availability. Those questions no longer force a Google Calendar tool call; a dated or booking-context availability request still does.
- Deduplicated final caller transcription events in both browser WebRTC and phone Realtime runtimes by provider item ID and a short normalized-text window. This prevents one caller utterance from requesting two identical agent responses while still allowing a deliberate repetition after the window.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/{AgentBusinessIdentity,AgentVariableService}.java`
  - `backend/src/main/java/com/sauti/call/{CallPipelineService,OpenAiTelephonyRealtimeConversationProvider}.java`
  - `backend/src/main/java/com/sauti/llm/{AvailabilityIntentDetector,ConversationOrchestrator}.java`
  - related backend regression tests
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `docs/agent-handoff.md`
- Verification:
  - focused identity, call-pipeline, hours-intent, Realtime telephony, and orchestration tests (successful)
  - `\.\gradlew.bat :backend:test --no-daemon` (successful)
  - `npm.cmd run typecheck` (successful after the final browser-runtime change)
  - `npm.cmd run build` (successful; 50 routes generated before the final type-safe hours-intent refinement)
  - `git diff --check` (successful; only expected line-ending notices)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - Sarah's live-calendar failure depends on the separately documented asynchronous tenant-resolution fix reaching production through CI/CD. Reconnecting OAuth alone cannot repair code that has not yet been deployed.
  - Prompt inference supports older agents, but the durable source of truth is still the agent's explicit `business_name` variable. Configure that value for every production agent rather than relying indefinitely on prompt inference.

### 2026-07-18 - Repair booking-tool activation and interrupted hybrid responses

- Traced Alec's refusal to create a new appointment to a capability-state mismatch: template-created agents could save `bookingEnabled=true` while their seeded `check_availability` and `book_slot` tools remained inactive. `DefaultToolSeeder` now synchronizes tool activation after creation, on agent updates, and during the existing startup backfill, so existing booking agents are repaired without disconnecting their Google credential.
- Kept calendar credentials and provider type intact while toggling booking capability. Added regression coverage for both enabling and disabling tools with an attached Google credential.
- Strengthened shared conversation instructions for new appointments. A new booking must not ask for a booking ID or ordinary duration, must not claim booking is unsupported when `book_slot` exists, and must proceed through missing name/contact/service/date/time, availability, confirmation, and booking.
- Fixed an interruption race in browser hybrid voice, public Web Voice, and phone OpenAI + Cartesia sessions. Cancelling a response now marks its response ID as discarded; late text/done events cannot reopen Cartesia, duplicate the interrupted sentence, contaminate the next response, or save a broken completion.
- Added a production warning when a booking-enabled agent starts a Realtime call without both required booking tools. Extended the read-only production diagnostics workflow to include calendar/tool execution failures and this readiness warning.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/AgentService.java`
  - `backend/src/main/java/com/sauti/call/{OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider}.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/tool/DefaultToolSeeder.java`
  - `backend/src/test/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProviderTest.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `backend/src/test/java/com/sauti/tool/DefaultToolSeederTest.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `.github/workflows/production-diagnostics.yml`
  - `docs/agent-handoff.md`
- Verification:
  - focused booking-tool, telephony Realtime, and conversation-orchestrator tests (successful)
  - `.\gradlew.bat :backend:test --no-daemon` (successful)
  - `npm.cmd run typecheck` (successful)
  - `npm.cmd run build` (successful; 50 routes generated)
- Deployment:
  - Not deployed. All changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - The startup backfill takes effect only after this revision is reviewed, committed, and deployed through CI/CD. Until then, affected live agents can continue to start calls without the booking tools.
  - After deployment, retest Alec or Sarah and run the `Production diagnostics` workflow for the call window if Google still rejects availability or event creation. The expanded filter will expose whether the remaining issue is tool readiness, credential refresh/scope, free/busy, or event insertion.

### 2026-07-18 - Require real Google event insertion and enforce prompt-defined hours

- Analyzed Alec and Amélie's latest booking transcripts. The generated Sauti confirmation code did not prove a Google event existed because a `book_slot` tool left on `noop_calendar` could silently use the local provider even when the agent selected Google Calendar.
- Changed default-tool reconciliation to propagate any connected Google credential to every calendar action (`check_availability`, `book_slot`, `reschedule_booking`, and `cancel_booking`). The startup backfill now repairs partially connected legacy agents.
- Made Google Calendar booking fail closed. When the agent selects Google Calendar, `book_slot` must carry a Google credential and the provider must return a non-local external event ID. Otherwise the caller receives an explicit not-booked response instead of a false confirmation.
- Added deterministic post-insertion booking speech. A confirmation code is spoken only after `BookingService` returns from the real provider event creation path.
- Added an effective-hours resolver for older/template agents whose structured schedule remained `always` while the saved prompt contained explicit hours. Calendar availability, Google free/busy windows, local availability, after-hours checks, and conversation context now share the same recovered schedule.
- Added natural calendar speech formatting. Whole French hours are rendered as `15 heures` rather than `15:00`/`zéro zéro`; English whole hours use natural day-period phrases. Availability alternatives, next openings, and successful booking confirmations use the formatter.
- Expanded business-hours intent handling for questions such as "what other days are you open?", "are you open Saturday?", and "travaillez-vous le samedi ?". The next response is constrained to the effective schedule and may not invent appointment availability.
- Files touched in addition to the preceding entry:
  - `backend/src/main/java/com/sauti/agent/{Agent,OperatingHoursSchedule}.java`
  - `backend/src/main/java/com/sauti/calendar/{GoogleCalendarProvider,LocalCalendarProvider}.java`
  - `backend/src/main/java/com/sauti/call/VoiceOutputGuard.java`
  - `backend/src/main/java/com/sauti/llm/{AvailabilityIntentDetector,ConversationOrchestrator}.java`
  - `backend/src/main/java/com/sauti/tool/{AvailabilitySpeechRenderer,BookingSpeechRenderer,DefaultToolSeeder,SautiCalendarFulfillment,SpokenDateTimeFormatter}.java`
  - related tests under `backend/src/test/java/com/sauti/{agent,llm,tool}`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `docs/agent-handoff.md`
- Verification:
  - focused operating-hours, availability, Google binding, speech-rendering, intent, orchestration, and telephony Realtime tests (successful; 41 tests)
  - `.\gradlew.bat :backend:test --no-daemon` (successful)
  - `npm.cmd run typecheck` (successful)
  - `npm.cmd run build` (successful; 50 routes generated)
- Deployment:
  - Not deployed. All changes remain uncommitted for maintainer review and CI/CD deployment.
- Follow-ups / risks:
  - A Google event can still fail because of revoked OAuth, missing scopes, provider quota, or a Google API error. Those failures now roll back the Sauti booking and are spoken as not booked; they can be diagnosed with the expanded production diagnostics workflow after deployment.
  - Prompt-hours recovery is a compatibility path. The structured weekly schedule should remain the long-term source of truth for newly configured agents.

### 2026-07-18 - Prevent general opening-hours questions from producing midnight slots

- Investigated Amélie's live transcript where `Quels jours vous êtes disponible ?` produced midnight, 00:30, and 01:00 slots and the agent then falsely claimed the business operated throughout the night.
- Confirmed the preceding calendar and operating-hours revision had already passed CI and deployed successfully. The new failure was an uncovered intent phrase: the word `disponible` caused the turn to be treated as appointment-slot availability even though the caller had not supplied a date.
- Expanded the shared browser/backend business-hours detector to recognize natural English and French variations including `Quels jours...`, `Quels autres jours...`, singular/plural `disponible(s)`, and `travaillez jusqu'à...`.
- Added a server-side defense independent of the model. When the actual caller transcript asks about operating days or hours, `AvailabilityRequestNormalizer` removes any model-invented date/time and tags the request as a business-hours question.
- Added deterministic calendar fulfillment for that tag. It returns the agent's effective configured schedule as natural speech and never queries Google or another calendar for appointment slots, so fabricated midnight arguments cannot reach callers.
- Added regression coverage using the exact reported French phrases, an invented midnight tool payload, prompt-defined MediCare-style hours, and verification that the calendar provider receives no request.
- Files touched:
  - `backend/src/main/java/com/sauti/llm/AvailabilityIntentDetector.java`
  - `backend/src/main/java/com/sauti/tool/{AvailabilityRequestNormalizer,SautiCalendarFulfillment}.java`
  - `backend/src/test/java/com/sauti/llm/AvailabilityIntentDetectorTest.java`
  - `backend/src/test/java/com/sauti/tool/{AvailabilityRequestNormalizerTest,SautiCalendarFulfillmentTest}.java`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `docs/agent-handoff.md`
- Verification:
  - focused availability detector, request normalizer, and calendar fulfillment tests (successful; 16 tests)
  - `.\gradlew.bat :backend:test --no-daemon` (successful)
  - `npm.cmd run typecheck` (successful)
  - `npm.cmd run build` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - General day/hour questions intentionally return published operating hours, not live multi-day appointment inventory. After the caller supplies a specific date or time, the normal Google free/busy lookup remains mandatory.

### 2026-07-18 - Reset operational data and rebuild templates and booking workflows

- Added the explicitly requested one-time V33 operational reset. It preserves authentication identity (`tenants`, `app_users`, and refresh-token state) while deleting agents, templates, calls, bookings, tools, integrations/credentials, knowledge, post-call work, provider event records, and usage counters. This migration is intentionally destructive and will run once when the reviewed revision reaches an environment through CI/CD.
- Replaced the previous ten templates with six layered version-2 templates: medical, dental, fitness, salon/wellness, legal intake, and general business. Every template receives a shared business/persona/routing/after-hours/booking/knowledge/compliance field layer plus vertical variables, prompt behavior, required booking fields, and owner-notification defaults.
- Removed the English/French availability keyword detector and request normalizer documented in the preceding handoff entry. The multilingual model now decides when to call `get_business_hours`, `check_availability`, `book_slot`, `reschedule_booking`, or `cancel_booking` from meaning; server tools still enforce normalized dates/times, business hours, required data, tenant ownership, and confirmation state.
- Generalized agent/template/onboarding language validation to BCP 47 codes. The studio includes common English, French, Arabic, Swahili, Spanish, German, Portuguese, and Italian options plus an arbitrary valid language-code control.
- Added per-agent booking intake and notification configuration. Booking agents always retain the core caller name, phone, service, and appointment fields and can add up to 21 vertical fields. Owners choose dashboard and/or email alerts and may override the recipient email.
- Made booking persistence local-first. A Google outage or missing credential no longer loses the request or falsely confirms a Google event: Sauti saves a `pending_confirmation` booking, assigns a public `SAT-...` booking number, records the calendar-sync error, publishes the dashboard event, and sends the selected owner email after transaction commit. A successful provider insertion records the external event and `synced` status.
- Added booking-number lookup and caller tools for rescheduling/cancellation. Added owner API and dashboard controls for editing, cancelling, and permanently deleting tenant-scoped bookings, including customer/contact/service/time fields and visible calendar follow-up state.
- Added structured-output guards to both cascaded and phone Realtime paths. If a model emits tool arguments as JSON text, the payload is held back from speech and recovered only when its schema maps unambiguously to an available tool; this does not rely on caller-language keyword lists.
- Updated calendar provider resolution so an agent configured for Google does not silently fall back to the local calendar during create/update/cancel operations when its Google credential is unavailable.
- Main files touched:
  - `backend/src/main/resources/db/migration/V33__reset_operational_data_and_booking_workflows.sql`
  - `backend/src/main/java/com/sauti/agent/{Agent,AgentDtos,AgentService,AgentTemplateService,AgentDraftGenerationService,OnboardingCompletionService,SystemAgentTemplateSeeder}.java`
  - `backend/src/main/java/com/sauti/calendar/{Booking,BookingDtos,BookingRepository,BookingService,BookingNotificationService}.java`
  - `backend/src/main/java/com/sauti/api/BookingController.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/call/{OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider}.java`
  - `backend/src/main/java/com/sauti/tool/{CalendarProviderFactory,DefaultToolSeeder,SautiCalendarFulfillment}.java`
  - deleted language-specific `AvailabilityIntentDetector` and `AvailabilityRequestNormalizer` production/tests
  - `dashboard/features/agents/AgentCreator/*`
  - `dashboard/features/bookings/{domain,presentation}/*`
  - `dashboard/features/onboarding/OnboardingFlow/OnboardingFlow.tsx`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/lib/api/bookings.ts`, `dashboard/types/api.ts`
  - `docs/agent-templates.md` and related regression tests
- Verification:
  - focused template, calendar fulfillment, orchestrator, and phone Realtime tests (successful; 29 tests after the vertical-field case was added)
  - `.\gradlew.bat :backend:test --no-daemon` (successful; 183 tests)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
  - `git diff --check` (successful; expected line-ending warnings only)
- Deployment:
  - Not deployed. All changes remain uncommitted for maintainer review and must use the existing CI/CD chain.
- Follow-ups / risks:
  - V33 deletes all non-authentication product data, including OAuth/calendar connections. Export anything that must be retained before a maintainer approves and deploys this revision; owners must reconnect providers and create agents from the new templates afterward.
  - Dashboard notification delivery is the existing realtime dashboard event. Email delivery is best-effort and logged on provider failure so it cannot roll back a captured booking.
  - The built-in heuristic language detector can recognize Arabic script plus English/French/Swahili markers and otherwise keeps the active/default configured language. Adding arbitrary BCP 47 configuration is now supported, but reliable mid-call switching for every language should ultimately consume provider-reported STT language metadata rather than grow keyword lists.

### 2026-07-18 - Enforce identity readbacks, remove onboarding, and rebuild Bookings UI

- Made identity confirmation a server-enforced booking prerequisite instead of a prompt-only preference. `book_slot` now requires explicit name-spelling and phone-digit confirmation flags, plus a separate email-spelling flag whenever an email is supplied. Calendar fulfillment returns `identity_confirmation_required` without writing a booking when a flag is absent or false.
- Updated the live conversation contract to read phone numbers one digit at a time and spell names/email addresses character by character with the NATO phonetic alphabet before booking. The local heuristic provider now models this as a two-step flow and cannot treat a booking confirmation as identity confirmation.
- Made opening greetings capability-aware. The opening-generation prompt receives only the agent's active tools, asks the agent to mention the two most useful supported actions naturally, and then asks how it can help; it must not advertise inactive capabilities.
- Removed onboarding as an agent-creation path. Login and Google registration route workspaces without agents to `/agents`, the legacy `/onboarding` page redirects there, the onboarding UI/client and backend creation endpoint/service were deleted, and Dashboard/Agents now present a direct first-agent call to action. The read-only onboarding-status endpoint remains as the existing workspace-readiness contract.
- Rebuilt the Bookings screen around a compact operations list inspired by the supplied design: five summary cards, working search/date range/agent/status filters, removable filter chips, list/calendar views, date-grouped rows, phone links, reschedule/edit, cancel, delete, calendar-sync state, and responsive layouts. The default view shows the next seven days and makes all active filters visible.
- Main files touched:
  - `backend/src/main/java/com/sauti/llm/{ConversationOrchestrator,LocalToolCallingLlmProvider}.java`
  - `backend/src/main/java/com/sauti/session/BookingDraft.java`
  - `backend/src/main/java/com/sauti/tool/{DefaultToolSeeder,SautiCalendarFulfillment}.java`
  - `backend/src/main/java/com/sauti/api/TenantController.java`
  - deleted backend onboarding completion DTO/service/test
  - `dashboard/features/bookings/presentation/{BookingsPage.tsx,BookingsPage.module.css}`
  - `dashboard/features/{agents,auth,dashboard}` signed-in journey components
  - deleted dashboard onboarding feature and client
  - related backend regression tests and `dashboard/types/api.ts`
- Verification:
  - focused calendar fulfillment, orchestrator, and end-to-end auth/agent/call/booking flow tests (successful)
  - `.\gradlew.bat :backend:test --no-daemon` (successful)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. All changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - The legacy `/onboarding` route is intentionally retained only as a redirect so old links do not break. No onboarding creation UI or API remains.
  - The supplied image was used as the layout reference; automated in-app visual inspection was unavailable in this session, so maintainers should perform one responsive browser review before committing.

### 2026-07-18 - Replace the native booking date inputs with a themed range picker

- Replaced the browser/operating-system date inputs that produced an unstyled grey calendar with a dedicated Sauti date-range popover built on the already-installed Radix Popover, React DayPicker, and date-fns dependencies.
- Added accessible month navigation, contiguous range highlighting, start/end summaries, Today/Next 7 days/Next 30 days/This month/All dates presets, reset/cancel/apply actions, collision-aware portal positioning, responsive mobile layout, and concise range formatting in the toolbar trigger.
- Kept date changes as a draft until the owner presses Apply, so closing or cancelling the picker cannot accidentally change the booking results.
- Files touched:
  - `dashboard/features/bookings/presentation/BookingsPage.tsx`
  - `dashboard/features/bookings/presentation/BookingDateRangePicker.tsx`
  - `dashboard/features/bookings/presentation/BookingDateRangePicker.module.css`
  - `dashboard/features/bookings/presentation/BookingsPage.module.css`
- Verification:
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated with no CSS compatibility warning)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and CI/CD.

### 2026-07-18 - Expand the system template catalog across appointments, support, and sales

- Expanded the layered system-template catalog from 6 to 22 production blueprints. Added Auto Repair Advisor, Restaurant Reservation Host, Veterinary Clinic Receptionist, Real Estate Showing Scheduler, Home Services Dispatcher, Photography and Event Studio Booking, Tutoring and Education Center, General Support Helpdesk, Order Status and Returns Desk, IT and SaaS Technical Support Tier 1, Property Management Tenant Support, Utility and Telecom Customer Service, Real Estate Lead Qualifier, Insurance Sales Intake, B2B SaaS Demo Booking SDR, and E-commerce Cart Recovery Specialist.
- Each addition defines real vertical behavior rather than card copy alone: explicit intake fields, booking or action requirements, tool truthfulness rules, emergency/escalation paths, privacy and compliance boundaries, owner-notification defaults, supported languages, and a focused conversation prompt layered over the shared business/persona/routing/booking/knowledge configuration.
- Added an explicit `Category` template field and validated it against `Appointments`, `Support`, and `Sales`. Runtime grouping now uses authored category metadata instead of relying on fragile industry-name inference; legacy templates retain a fallback mapping for compatibility.
- Expanded industry icon metadata and the dashboard icon registry so the larger catalog remains quickly scannable in the template grid.
- Files touched:
  - `docs/agent-templates.md`
  - `backend/src/main/java/com/sauti/agent/SystemAgentTemplateSeeder.java`
  - `backend/src/test/java/com/sauti/agent/SystemAgentTemplateSeederTest.java`
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
- Verification:
  - `.\gradlew.bat :backend:test --tests com.sauti.agent.SystemAgentTemplateSeederTest` (successful)
  - `.\gradlew.bat :backend:test` (successful)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-ups / risks:
  - E-commerce cart recovery supports outbound behavior only when an authorized outbound campaign supplies a consented contact; selecting the template does not itself start outbound calling.
  - Support templates define safe ticket/intake behavior, but actual ticket creation still depends on the owner connecting and enabling a compatible integration or custom webhook for that agent.

### 2026-07-18 - Distinguish required personalisation fields in red

- Changed unfilled required-field badges in the agent personalisation form and drawer from the shared amber treatment to a red danger treatment. Optional fields remain amber, completed fields remain teal, and invalid values use the same red visual language as required attention states.
- Files touched:
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
- Verification:
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-18 - Keep calls and bookings calendars inside the visible viewport

- Made both date-range popovers respect Radix's available viewport height, keep their action footers visible, and scroll only the calendar content when the browser is too short to display the complete picker at once.
- Corrected the Calls calendar's React DayPicker sizing by setting both day-cell and day-button dimensions. This removes the unexpectedly oversized two-month layout while preserving readable dates and quick-range actions.
- Added a compact short-viewport layout to the Bookings picker so its header, selected-range summary, calendar, and Apply/Cancel controls remain usable at the viewport height shown in the supplied screenshots.
- Files touched:
  - `dashboard/features/calls/CallsPage/CallsPage.tsx`
  - `dashboard/features/calls/CallsPage/CallsPage.module.css`
  - `dashboard/features/bookings/presentation/BookingDateRangePicker.module.css`
- Verification:
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - The commands were run sequentially because concurrent execution races on Next's generated `.next/types` directory.
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-18 - Make timezone guided and calendar configuration integration-owned

- Replaced the free-text Business timezone variable in Personalise with the existing IANA timezone selector, including readable UTC offsets and location names. It updates the agent's authoritative scheduling timezone and is persisted immediately when saved for an existing agent.
- Removed Calendar system from template personalisation. Calendar provider and routing values are now system-managed from the agent integration state, so owners cannot enter a value that disagrees with the provider actually enabled.
- Added a tenant-scoped timezone patch endpoint and server-side `ZoneId` validation. Full agent create/update requests now receive the same validation, preventing invalid timezones from reaching booking or operating-hours code.
- Added backward compatibility for existing agents: legacy `business_timezone`, `calendar_system`, `calendar_provider`, and `routing_policy` variables are hidden, ignored by readiness checks, protected from owner API updates, and resolved from the current agent/integration state when an older saved prompt still references them.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/{Agent,AgentDtos,AgentService,AgentVariableService,SystemAgentTemplateSeeder}.java`
  - `backend/src/main/java/com/sauti/api/AgentController.java`
  - `backend/src/test/java/com/sauti/agent/{AgentVariableServiceTest,SystemAgentTemplateSeederTest}.java`
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/lib/api/agents.ts`
- Verification:
  - focused template and agent-variable regression tests (successful)
  - `.\gradlew.bat :backend:test` (successful)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-18 - Replace native personalisation time pickers

- Replaced the operating-system `time` inputs in the Personalise weekly-hours editor with a Sauti-themed Radix Select control. The menu now matches the dark agent studio instead of opening the browser's grey multi-column time dialog.
- Added quarter-hour choices across the full day, readable 12-hour labels, selected-state and keyboard focus styling, scroll controls, viewport-aware placement, and a compact responsive layout. The editor still stores canonical 24-hour `HH:mm` values, so prompts, validation, and booking-hour enforcement remain compatible.
- Files touched:
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
- Verification:
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-18 - Unify booking intake fields and refine Main settings

- Removed the duplicate owner-editable `required_booking_fields` and `notification_channels` entries from template personalisation. They are now system-managed runtime variables resolved from the agent's authoritative booking workflow, including backward compatibility for existing agents that still have stale saved variables.
- Replaced the comma-separated booking-field input in Main settings with a structured multi-select editor. The four platform-required fields are visible and locked, each template's vertical-specific fields are recommended automatically, owners can browse a reusable field catalog, and custom human-readable fields are normalized into safe booking keys. Selection is capped at the backend-supported 25 fields.
- Confirmed that additional configured fields flow through the booking tool's `customer_details` payload and are persisted in each booking's structured `capturedData`; no database migration was required.
- Refined only Main settings into compact, bordered studio cards matching the supplied design direction. Identity, channels, languages, capabilities, privacy, and greeting now have clearer grouping and denser responsive spacing without changing the other configuration tabs.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/AgentVariableService.java`
  - `backend/src/main/java/com/sauti/agent/SystemAgentTemplateSeeder.java`
  - `backend/src/test/java/com/sauti/agent/AgentVariableServiceTest.java`
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
- Verification:
  - focused `AgentVariableServiceTest` and `SystemAgentTemplateSeederTest` (successful)
  - `.\gradlew.bat :backend:test` (successful)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-18 - Add durable booking notifications and polish Main settings inputs

- Replaced the decorative header bell with a tenant-scoped notification inbox. Dashboard-enabled booking alerts are persisted after the booking transaction commits, delivered live over the existing dashboard WebSocket, and refreshed by a 30-second polling fallback.
- Added an unread badge, readable confirmed/follow-up booking cards, appointment and booking-reference context, mark-as-read on open, mark-all-read, empty/loading/error states, responsive positioning, and a direct Bookings link. Read state is stored server-side and survives refreshes and devices.
- Added authenticated notification list/read/read-all endpoints, tenant-scoped repository queries, a Flyway `V34` notification table and indexes, and a focused booking-notification service test. Email notification delivery remains independently controlled by the agent's Email channel.
- Restyled the Main settings language-code field as a unified focusable control with a proper teal action button and responsive stacked state. Restyled the booking notification email with a rounded dark input, clear optional-override badge, focus/hover states, and explicit workspace-owner fallback copy.
- Files touched:
  - `backend/src/main/java/com/sauti/api/WorkspaceNotificationController.java`
  - `backend/src/main/java/com/sauti/calendar/{BookingNotificationService,BookingService}.java`
  - `backend/src/main/java/com/sauti/notification/*`
  - `backend/src/main/resources/db/migration/V34__workspace_notifications.sql`
  - `backend/src/test/java/com/sauti/notification/WorkspaceNotificationServiceTest.java`
  - `dashboard/components/AppShell/AppShell.tsx`
  - `dashboard/features/agents/AgentCreator/{AgentCreator.tsx,AgentCreator.css}`
  - `dashboard/features/notifications/presentation/{NotificationMenu.tsx,NotificationMenu.module.css}`
  - `dashboard/lib/api/notifications.ts`
  - `dashboard/types/api.ts`
- Verification:
  - `.\gradlew.bat :backend:test` (successful)
  - focused `WorkspaceNotificationServiceTest` after its final cleanup (successful)
  - `.\gradlew.bat :backend:compileJava` after the final listener resilience change (successful)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-18 - Refine custom booking fields and Analytics agent scope

- Reworked the Main settings custom booking-field entry into one cohesive input/action surface with a shared focus ring, clearer placeholder contrast, an active teal Add field action, a restrained disabled state, and a compact normalized-key preview.
- Rebuilt the Analytics agent selector as a compact integrated scope control. Its selected agent, icon, and dropdown affordance now read as one vertically centered component without the crowded floating label and secondary hint from the first iteration.
- Added an optional trigger styling hook to the shared Radix-based `DarkSelect`, allowing feature-specific composition without weakening the shared dropdown behavior or duplicating select logic.
- Files touched:
  - `dashboard/components/DarkSelect/DarkSelect.tsx`
  - `dashboard/features/agents/AgentCreator/AgentCreator.css`
  - `dashboard/features/analytics/presentation/AnalyticsPage.tsx`
  - `dashboard/features/analytics/presentation/AnalyticsPage.module.css`
- Verification:
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-18 - Make personalised business hours authoritative at runtime

- Fixed the split source of truth that caused agents to report unrestricted hours after owners configured a weekly schedule. Saving `business_hours` now updates the agent's runtime operating-hours setting used by call admission, business-hours tools, local/Google availability, and booking validation.
- Added call-start reconciliation for browser tests, public Web Voice, inbound phone calls, and WhatsApp so existing agents with a populated business-hours variable are repaired automatically before availability is evaluated; owners do not need to recreate those agents.
- Extended schedule parsing to accept the compact UI representation (`Mon-Fri 09:00-17:00` and per-day segments), including non-zero-padded hours, while retaining structured JSON, weekday presets, closed days, and overnight support.
- Corrected variable input classification so only `business_hours` renders a weekly schedule. `after_hours_behavior` now renders explicit Answer normally / Collect a message / Announce closure choices and synchronizes with the runtime call behavior instead of being mistaken for another set of opening hours.
- Prompt resolution now derives business-hours and after-hours descriptions from authoritative runtime settings, preventing stale personalisation values from contradicting calendar tools.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/{AgentVariableService,OperatingHoursSchedule}.java`
  - `backend/src/main/java/com/sauti/call/CallPipelineService.java`
  - `backend/src/test/java/com/sauti/{AgentTemplateApiTest.java,agent/AgentVariableServiceTest.java,agent/OperatingHoursScheduleTest.java}`
  - `dashboard/features/agents/AgentCreator/AgentCreator.tsx`
  - `dashboard/features/agents/AgentVariables/AgentVariablesPage.tsx`
  - `dashboard/features/agents/domain/structured-agent-settings.ts`
- Verification:
  - focused `AgentTemplateApiTest`, `OperatingHoursScheduleTest`, and `AgentVariableServiceTest` (successful)
  - `.\gradlew.bat :backend:test` (successful; 189 tests)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-18 - Make booking capture complete and database-first

- Added a runtime conversation context containing every populated owner-configured business field, whether required or optional. Authoritative business-hours and after-hours values are rendered from the agent runtime settings, empty values and system-managed variables are excluded, and the context is appended to the agent prompt so relevant business facts are available throughout the call.
- Made the `book_slot` tool schema agent-specific. Platform booking fields and every configured required field are exposed to the model, custom required fields are enforced inside `customer_details`, and additional relevant details volunteered by the caller can be retained in the booking's structured captured data without forcing the agent to ask unnecessary questions.
- Reworked booking creation into a database-first workflow. A confirmed Sauti booking is now inserted and committed in a new transaction before any external calendar call is attempted. External calendar synchronization then runs after that commit and updates the booking in a second transaction as `synced`, `not_configured`, or `pending_owner_action`.
- A missing external integration is now treated as a valid local Sauti booking instead of a fabricated provider event. When an integration is configured but event creation fails, the local booking remains confirmed, the provider error is sanitized, and the business owner receives an actionable unread dashboard notification even if ordinary dashboard booking notifications were disabled. Email also reports the calendar issue when the agent's Email notification channel is enabled.
- Updated call-tool responses so callers are told the truth: fully synced bookings, locally saved bookings, and bookings awaiting calendar follow-up have distinct outcomes. The agent must provide the Sauti booking number and cannot claim the external calendar was updated when it was not.
- Updated the notification menu so calendar-sync failures use the warning treatment rather than the successful-booking icon.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/AgentVariableService.java`
  - `backend/src/main/java/com/sauti/calendar/{Booking,BookingNotificationService,BookingService}.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/notification/WorkspaceNotificationService.java`
  - `backend/src/main/java/com/sauti/tool/{AgentToolLoader,SautiCalendarFulfillment}.java`
  - `backend/src/test/java/com/sauti/AuthAgentFlowTest.java`
  - `backend/src/test/java/com/sauti/agent/AgentVariableServiceTest.java`
  - `backend/src/test/java/com/sauti/calendar/BookingServiceTest.java`
  - `backend/src/test/java/com/sauti/notification/WorkspaceNotificationServiceTest.java`
  - `backend/src/test/java/com/sauti/tool/AgentToolLoaderTest.java`
  - `dashboard/features/notifications/presentation/NotificationMenu.tsx`
- Verification:
  - focused booking, tool-schema, notification, and agent-variable tests (successful)
  - `\.\gradlew.bat :backend:test` (successful; 195 tests)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-19 - Make identity readback the agent's responsibility

- Corrected the booking-verification direction exposed by the Ailsa transcript. Callers now provide names, phone numbers, and email addresses naturally; agents must never require callers to translate their own details into NATO phonetics or digit-by-digit dictation.
- Strengthened the shared conversation contract, browser OpenAI Realtime response instructions, and phone OpenAI Realtime response instructions so the agent performs the phonetic name/email readback and digit-by-digit phone readback. The caller is asked only to confirm the agent's readback or provide a correction.
- Made the server-enforced `identity_confirmation_required` tool response include deterministic `agentReadback` values. Latin letters are converted to standard NATO words, email punctuation is spoken explicitly, and phone digits are separated for reliable speech. This gives every model an exact readback to speak rather than an ambiguous instruction it could delegate to the caller.
- Clarified the default booking tool and confirmation-flag descriptions so newly seeded agents follow the same direction.
- Added regression assertions covering the shared prompt prohibition, exact Zachary/phone readbacks, email phonetics, and the tool instruction that the agent—not the caller—performs verification.
- Files touched:
  - `backend/src/main/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProvider.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/tool/{DefaultToolSeeder,SautiCalendarFulfillment}.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `backend/src/test/java/com/sauti/tool/SautiCalendarFulfillmentTest.java`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
- Verification:
  - focused `ConversationOrchestratorTest` and `SautiCalendarFulfillmentTest` (successful)
  - `\.\gradlew.bat :backend:test` (successful)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-19 - Defer verification to one final booking review

- Corrected the timing of the identity readback after clarification from the owner. Agents no longer confirm names, phone numbers, email addresses, services, or other booking fields individually as they are collected.
- The required conversation sequence is now: collect every configured field, check live availability, present one consolidated final review, receive one confirmation, save the booking, and close the call. The review includes the agent-produced NATO name/email spelling, digit-by-digit phone number, service, date/time, duration, and configured custom booking details.
- Added `final_booking_review_confirmed` to both newly seeded and dynamically loaded `book_slot` schemas, so existing agents receive the rule without recreation. Separate earlier name/phone flags are insufficient: calendar fulfillment returns `identity_confirmation_required` until the caller has confirmed the complete final review.
- The final-review tool response now returns both deterministic `agentReadback` values and a structured `bookingReview`, allowing browser and phone Realtime models to speak one complete summary without inventing or omitting saved fields.
- Updated the local heuristic booking path to set the new confirmation state after its end-of-intake readback.
- Added regression coverage proving that early individual confirmations cannot create a booking and that the final review contains the service, appointment time, duration, and custom booking details.
- Files touched:
  - `backend/src/main/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProvider.java`
  - `backend/src/main/java/com/sauti/llm/{ConversationOrchestrator,LocalToolCallingLlmProvider}.java`
  - `backend/src/main/java/com/sauti/tool/{AgentToolLoader,DefaultToolSeeder,SautiCalendarFulfillment}.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `backend/src/test/java/com/sauti/tool/{AgentToolLoaderTest,SautiCalendarFulfillmentTest}.java`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
- Verification:
  - focused conversation, dynamic tool-schema, and calendar-fulfillment tests (successful)
  - `\.\gradlew.bat :backend:test` (successful)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-19 - Make the late-call readback non-blocking

- Supersedes the mandatory confirmation-gate design documented immediately above. The supplied Ailsa transcript showed that exposing spelling-confirmation booleans caused the model to repeatedly demand that the caller spell their own name.
- Removed `caller_name_spelling_confirmed`, `caller_phone_digits_confirmed`, `caller_email_spelling_confirmed`, and `final_booking_review_confirmed` from new booking-tool schemas. The dynamic tool loader also strips these legacy properties and requirements from existing agents at runtime, so agents do not need to be recreated.
- Removed `identity_confirmation_required` from calendar fulfillment. A booking now depends only on the real configured booking fields; spelling and formal confirmation phrases are not API prerequisites.
- Strengthened browser, phone, and shared conversation instructions: callers always provide names, emails, and phone numbers naturally and must never be asked to spell them in any form. Toward the end of intake, the agent spells its own interpretation and reads the phone number back once so the caller can correct an error. That accuracy check is not performed after every field and does not require special confirmation wording.
- Kept the local heuristic provider's natural end-of-intake agent readback, but removed all confirmation flags from its eventual booking call.
- Added regression coverage proving legacy confirmation fields are removed from model-visible schemas and that normal bookings succeed without any spelling/confirmation booleans.
- Files touched:
  - `backend/src/main/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProvider.java`
  - `backend/src/main/java/com/sauti/llm/{ConversationOrchestrator,LocalToolCallingLlmProvider}.java`
  - `backend/src/main/java/com/sauti/tool/{AgentToolLoader,DefaultToolSeeder,SautiCalendarFulfillment}.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `backend/src/test/java/com/sauti/tool/{AgentToolLoaderTest,SautiCalendarFulfillmentTest}.java`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
- Verification:
  - focused conversation, tool-schema, calendar-fulfillment, and authenticated call-flow tests (successful after correcting one wording assertion)
  - `\.\gradlew.bat :backend:test` (successful)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-19 - Make booking intake sequential and literal

- Corrected the intake failures shown by the latest Ailsa transcript. Agents now request exactly one missing booking value per reply instead of combining service, staff, name, phone, and email into a single question.
- Made the configured required-field list a private model checklist rather than caller-facing copy. When `book_slot` is attempted before intake is complete, calendar fulfillment now exposes only `nextMissingField` plus a remaining count; it no longer returns the complete missing-field list that encouraged bundled questions.
- Neutral acknowledgements such as `okay`, `no problem`, and `just a second` are no longer treated as booking values or silently converted to `any staff`. An any-staff preference is recorded only when the caller explicitly says they have no preference.
- Added a dedicated pause rule: when a caller asks for a moment, the agent says only a short equivalent of `Take your time` and waits without repeating the pending question or collected details.
- Prevented contradictory comprehension responses such as `I didn't catch that` immediately before using the caller's correctly understood name.
- Unclear service transcripts are now clarified with one short question instead of being silently repaired into a plausible service. Spoken dates and times must use natural localized wording and must not expose ISO dates or combine 24-hour notation with AM/PM.
- Applied the same response constraints to the shared orchestrator, browser OpenAI Realtime runtime, and phone OpenAI Realtime runtime.
- Files touched:
  - `backend/src/main/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProvider.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/tool/SautiCalendarFulfillment.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `backend/src/test/java/com/sauti/tool/SautiCalendarFulfillmentTest.java`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
- Verification:
  - focused `ConversationOrchestratorTest`, `SautiCalendarFulfillmentTest`, and `AgentToolLoaderTest` (successful)
  - `.\gradlew.bat :backend:test` (successful)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.

### 2026-07-19 - Make configured knowledge and final booking review authoritative

- Fixed the broader configuration gap exposed by the salon transcript. Every populated required or optional agent variable is now injected into the live conversation even when the template prompt omitted its placeholder. Customer-facing facts are separated from private operating rules so agents can answer exact services, prices, hours, policies, locations, memberships, classes, packages, insurance, staff, and vertical-specific facts without exposing transfer destinations or escalation instructions.
- Structured service-style catalog values into exact entries and explicitly preserve service/price pairs. This applies across the current appointment, support, and sales templates rather than only the salon template.
- Replaced prompt-only late-call verification with a server-enforced two-step `book_slot` protocol. The first valid call returns a deterministic consolidated review and a private token bound to every value that will be saved. The review includes the agent-spelled NATO name and email, digit-by-digit phone, service, naturally spoken date/time, duration, preferred staff, and all configured custom booking details. A correction changes the payload and invalidates the old token; only a later call with unchanged values and the matching token can create the booking.
- Preserved explicit `any available staff` as a real booking value and included it in saved custom details and the final review, so the agent must not ask for staff again after the caller has already expressed no preference.
- Fixed a call-lifecycle defect where the review-only `book_slot` result was incorrectly classified as `booking_made`. The call now remains active while the customer reviews or corrects details, and only an actually persisted booking can produce the booking outcome.
- Serialized browser and phone OpenAI Realtime `response.create` requests. Tool-result speech now waits for the preceding response to finish, provider-busy errors preserve and retry the pending response, and deterministic tool text is synthesized through the native voice path rather than being injected as mismatched transcript text. This addresses the `Conversation already has an active response in progress` race.
- Added regression coverage for optional business facts and exact pricing, private operating rules, review-token invalidation after a correction, NATO/digit readback, duration and custom fields, Realtime response serialization/retry, and the authenticated review-to-booking flow.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/AgentVariableService.java`
  - `backend/src/main/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProvider.java`
  - `backend/src/main/java/com/sauti/llm/{ConversationOrchestrator,LocalToolCallingLlmProvider}.java`
  - `backend/src/main/java/com/sauti/session/BookingDraft.java`
  - `backend/src/main/java/com/sauti/tool/{AgentToolLoader,BookingReviewRenderer,DefaultToolSeeder,SautiCalendarFulfillment}.java`
  - `backend/src/test/java/com/sauti/AuthAgentFlowTest.java`
  - `backend/src/test/java/com/sauti/agent/AgentVariableServiceTest.java`
  - `backend/src/test/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProviderTest.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `backend/src/test/java/com/sauti/tool/{AgentToolLoaderTest,SautiCalendarFulfillmentTest}.java`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
- Verification:
  - focused agent-variable, conversation, calendar-fulfillment, tool-loader, telephony-Realtime, and authenticated-flow tests (successful)
  - `.\gradlew.bat :backend:test` (successful; 196 tests)
  - `npm.cmd run typecheck` in `dashboard` (successful)
  - `npm.cmd run build` in `dashboard` (successful; 50 routes generated)
- Deployment:
  - Not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD chain.
- Follow-up risk:
  - The consolidated review uses deterministic localized framing for the platform's current English, French, Swahili, and Arabic language set while spelling identity characters with the standard NATO alphabet. Any newly supported caller language should add localized framing tests before launch; the payload and review-token enforcement are language-independent.

### 2026-07-20 - Lossless booking corrections, natural turn behavior, and local conflict protection

- Corrected the failures shown in the Ailsa booking transcript. Final reviews now introduce NATO readback clearly (for example, `Z for Zulu, A for Alfa`) instead of speaking only code words with no explanation.
- Replaced the irreversible review hash with a signed, call-bound review snapshot. When a caller changes one value, the server can compare the old and new booking payloads and return a focused correction readback instead of repeating the entire name, phone, service, date, and duration. The preceding private token must be retained for a correction; the latest token is reused only after approval.
- Made caller phone capture lossless at the booking boundary. The server reads the latest accepted caller transcript, preserves leading zeroes, replaces rather than merges corrected digit sequences, and carries the exact reviewed phone value into the final create call even if the model reconstructs different digits afterward.
- Connected calendar fulfillment to authoritative call-intake notes for the initial caller name/email/phone. Literal names such as `Akari` are no longer silently replaced by familiar names such as `Zachary`; name extraction also stops before a following phrase such as `and I want to book...`.
- Strengthened browser, phone, and shared Realtime instructions to react briefly and naturally, vary acknowledgements, stay in the caller's substantial current language, never repeat the same sentence/summary twice, treat names as opaque values, and reconfirm only the corrected field.
- Added per-turn language detection before generating updated Realtime instructions. A substantial English turn now overrides stale language state while single noisy words cannot switch the call language.
- Made Sauti's booking database part of live availability. Confirmed or pending local bookings are removed from provider-returned slots, so an occupied requested time becomes `requested_time_unavailable` and the existing availability response offers nearby returned alternatives.
- Added a second overlap check immediately before the database insert. A stale review or concurrent booking attempt cannot knowingly create another appointment that overlaps an existing non-cancelled Sauti booking.
- Files touched:
  - `backend/src/main/java/com/sauti/calendar/{BookingRepository,BookingService}.java`
  - `backend/src/main/java/com/sauti/call/{CallIntakeNoteService,OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider}.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/tool/{AgentToolLoader,BookingReviewRenderer,DefaultToolSeeder,SautiCalendarFulfillment}.java`
  - corresponding backend regression tests
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `docs/agent-handoff.md`
- Verification:
  - focused calendar fulfillment, booking service, language/runtime, call-intake, orchestrator, tool-schema, and authenticated flow tests - passed.
  - `.\gradlew.bat :backend:test` - passed; 202 tests.
  - `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
  - `git diff --check` - passed before the handoff update.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: actual microphone/carrier testing is still required to validate STT segmentation around spoken digit groups and expressive delivery for the selected Cartesia voice. Provider controls were intentionally not forced to one static emotion because Sonic 3.5 derives emotion from transcript context; mismatched forced emotion can degrade delivery.

### 2026-07-20 - Preserve personalized facts in every Realtime response

- Fixed the exact cause of the salon price hallucination where the configured `men hairstyle: $5` became `$30`. Browser and phone Realtime paths were adding shortened `response.create.instructions` to ordinary replies. OpenAI applies those instructions as a response-only override, so the complete session prompt containing owner-configured service/price facts was discarded for that turn.
- Normal caller replies now send a bare `response.create` and inherit the transcript-aware session instructions installed immediately beforehand. This keeps every populated personalization value, exact service-price pair, caller-name preservation rule, and booking-intake rule active.
- Availability-tool requests and tool-result follow-ups now override only `tool_choice`, not instructions. The post-availability reply therefore retains the rule to ask for exactly one missing value instead of bundling phone number and email in one question.
- Kept response-specific instructions only for deliberately deterministic speech such as the configured opening greeting and server-rendered booking review text.
- Added phone-Realtime regression coverage proving normal and tool-result responses contain no response-level instruction override.
- Files touched:
  - `backend/src/main/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProvider.java`
  - `backend/src/test/java/com/sauti/call/OpenAiTelephonyRealtimeConversationProviderTest.java`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `docs/agent-handoff.md`
- Verification:
  - focused `AgentVariableServiceTest`, `ConversationOrchestratorTest`, and `OpenAiTelephonyRealtimeConversationProviderTest` - passed.
  - `.\gradlew.bat :backend:test` - passed.
  - `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: verify one browser call and one phone call after CI/CD using the saved `$5` catalog. Exact model adherence still depends on receiving the current session update, but the response-level override that deterministically removed those facts is now eliminated.

### 2026-07-20 - Enforce a typed tool/speech boundary before audio

- Supersedes the earlier prefix-streaming mitigation. Prefix inspection was not a sufficient production boundary because protocol could appear after natural-looking text, and native provider audio could already be playing before its parallel transcript was validated.
- Tool execution now has exactly one entry point: provider-native `function_call` events. Text or JSON in a message is never parsed, repaired, or inferred into a tool call. Native call IDs are retained for the full Realtime session so a replayed event cannot repeat a side effect.
- Realtime output items are tracked by provider item ID and type. Only `message` items can be considered for speech. Message text is held through `response.done`; if the same response also contains a `function_call`, all accompanying message text stays silent and only the native tool event runs.
- `VoiceOutputGuard` validates the complete message, removes accidental caller-facing wrappers such as `assistant:`, and rejects private roles, channel routing, function namespaces, code, and structured tool arguments even when they occur after a natural sentence. The same guard is applied again at transcript persistence and the server-side hybrid TTS WebSocket.
- Browser Realtime is now text-first only. Unexpected WebRTC output-audio tracks are stopped immediately and cannot bypass the guarded Cartesia TTS channel. Both saved `cartesia:` voices and legacy `openai:` voices use hybrid text-first mode when OpenAI Realtime and Cartesia are configured; a legacy OpenAI voice requires `CARTESIA_DEFAULT_VOICE_ID` because provider-native audio is intentionally no longer an audible path.
- The shared cascaded orchestrator also buffers complete provider turns. It discards any text returned beside structured tool calls, rejects textual tool arguments, and emits only the validated final message to TTS/history.
- Realtime instructions now state the contract directly: ordinary replies are bare natural speech without speaker labels, and tools are used only through native function calls.
- Added regression coverage for the exact `assistant: Hi Walker...` transcript, split role labels, protocol after a natural prefix, textual JSON that must not execute, non-message text events, combined message-plus-tool responses, duplicate native call IDs, server-side hybrid TTS validation, and sanitized transcript storage.
- Files touched:
  - `backend/src/main/java/com/sauti/api/{CallController,PublicWebVoiceController}.java`
  - `backend/src/main/java/com/sauti/call/{CallPipelineService,HybridVoiceSessionService,OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider,VoiceOutputGuard}.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/call/{CallPipelineServiceTest,HybridVoiceSessionServiceTest,OpenAiRealtimeServiceTest,OpenAiTelephonyRealtimeConversationProviderTest,VoiceOutputGuardTest}.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - focused protocol-boundary, telephony Realtime, hybrid TTS, transcript persistence, Realtime configuration, and orchestrator tests - passed.
  - `.\gradlew.bat :backend:test` - passed; 215 tests.
  - `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: complete-response validation deliberately trades some first-audio latency for a hard pre-speech boundary. Run one browser call and one carrier call after CI/CD to measure the resulting pause. Before deploying with any legacy `openai:` voice, confirm `CARTESIA_DEFAULT_VOICE_ID` is configured or migrate that agent to a `cartesia:` voice.

### 2026-07-20 - Make interruption cancellation generation-safe and speech exactly-once

- Fixed the repeated post-interruption response shown by the booking-review transcript. The root problem was not prompt wording: cancellation stopped the active OpenAI response and audible buffer, but asynchronous caller preparation and tool promises remained eligible to enqueue or speak after the caller had moved to a newer turn.
- Browser Realtime now assigns every caller turn, response request, tool execution, deferred transcript, and speech message to an output generation. Sustained caller speech advances the generation, cancels the provider response, purges queued work, and makes every late completion from the prior generation speech-ineligible. Async caller-instruction preparation also rechecks its generation after the HTTP wait.
- Replaced the browser-to-hybrid `tts_delta`/`tts_complete` pair with one validated `speak` message carrying a generation and speech ID. Speech and transcript persistence now cross the boundary together, after pending caller transcription is resolved, instead of TTS being sent while the corresponding transcript was deferred.
- Added exactly-once guards using both speech identity and normalized content. Different provider response IDs or tool-call IDs can no longer make the same booking review audible twice within one caller turn.
- Semantically identical tool calls in one turn now share one execution, including when JSON property order differs. Realtime sessions disable parallel tool calls, and one generation can schedule at most one deterministic tool speech or one model follow-up. Tool outputs are still returned to the provider, but a tool that finishes after interruption cannot speak or enqueue a stale response.
- Hybrid and phone Cartesia output now serialize complete utterance contexts. A second valid utterance waits for the first Cartesia `onComplete`; contexts are no longer allowed to multiplex PCM chunks and produce broken, interleaved speech.
- Applied the same generation-scoped response queue, stale async-tool suppression, speech deduplication, atomic complete-message delivery, and queued-response purge to telephony Realtime. DTMF text turns also advance the generation before requesting a response.
- Files touched:
  - `backend/src/main/java/com/sauti/call/{DefaultTwilioMediaStreamService,HybridVoiceSessionService,OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider}.java`
  - `backend/src/test/java/com/sauti/call/{DefaultTwilioMediaStreamServiceTest,HybridVoiceSessionServiceTest,OpenAiRealtimeServiceTest,OpenAiTelephonyRealtimeConversationProviderTest}.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - focused hybrid, phone-media, Realtime configuration, and telephony Realtime regression tests - passed.
  - `.\gradlew.bat :backend:test` - passed; 223 tests.
  - `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
  - `git diff --check` - passed before the handoff update.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: run one browser test call and one carrier call after CI/CD, interrupting both an ordinary answer and the final booking review before Cartesia emits its first frame and again mid-playback. The automated coverage controls both timing windows, but real microphone echo/VAD and carrier buffering still require an end-to-end production check.

### 2026-07-21 - Separate Realtime response phases and suppress standalone output markers

- Fixed the exact transcript leak where the model spoke `ANSWER` instead of a natural reply. The previous complete-message guard recognized labels such as `answer:` but allowed a standalone response-section heading with no colon.
- Expanded the shared backend and browser speech guards to handle standalone and Markdown-style `ANSWER`, `FINAL ANSWER`, `RESPONSE`, assistant, and agent headings. A heading wrapped around real text is removed before speech; a heading-only response is rejected and recovered without being spoken. Natural sentences such as `The answer is five dollars` and names such as `Answer Salon` remain unchanged.
- Added equivalent protection for standalone private sections such as `ANALYSIS`, `COMMENTARY`, tool/function call/result headings, and presentation-only separators so protocol formatting cannot cross the caller-facing TTS boundary.
- Made browser and phone Realtime completion handling phase-aware. When `response.done.response.output` contains phase metadata, only `final_answer` message content can reach transcripts or Cartesia; commentary stays silent, and any function-call item keeps the whole response on the tool path. Older Realtime responses without phases retain the existing complete streamed-text fallback.
- Strengthened the Realtime prompt contract to forbid response-section headings in ordinary speech.
- Why: tool events and model commentary are protocol, while caller-facing speech is a validated final message. Treating every textual Realtime output as equivalent allowed presentation/channel markers to become audio and would remain unsafe as phased Realtime models are introduced.
- Files touched:
  - `backend/src/main/java/com/sauti/call/{OpenAiTelephonyRealtimeConversationProvider,VoiceOutputGuard}.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/call/{OpenAiTelephonyRealtimeConversationProviderTest,VoiceOutputGuardTest}.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `docs/agent-handoff.md`
- Verification:
  - focused voice-output guard, telephony Realtime, and conversation-instruction tests - passed.
  - `.\gradlew.bat :backend:test` - passed; 228 tests.
  - `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: after CI/CD, run one browser test call and one carrier call against both the current configured Realtime model and any future `gpt-realtime-2` upgrade. Automated tests cover the exact marker leak, legacy no-phase output, commentary-only output, and mixed commentary/final output; live provider event ordering and model behavior still need an end-to-end check.

### 2026-07-21 - Make complete Cartesia speech atomic and completion-safe

- Fixed the broken/choppy voice and indefinitely loading `Agent is speaking` state at the TTS/playback boundary. The guarded LLM paths already had a complete, validated reply, but then split it back into sentence fragments, sent every fragment as a Cartesia continuation, and finalized the context with an empty transcript. Complete replies now use one Cartesia context and one final generation request, preserving sentence-level prosody and eliminating avoidable continuation seams.
- Applied the atomic generation contract consistently to hybrid browser calls, public Web Voice, cascaded phone media, and DTMF-triggered replies. Removed the obsolete phone text-fragment state that could no longer be reached after complete-response validation.
- Added a hybrid TTS completion watchdog. It is refreshed by every PCM frame and terminates a stalled response after eight seconds of inactivity. Provider stream/connect failures now always clear the active utterance, pending speech, and caller-facing speaking state, close the failed Cartesia session, and allow the next caller turn to open a fresh connection without replaying partially heard text.
- Cartesia error events are now handled before their `done` flag, because provider error payloads may also be terminal. Abnormal WebSocket closure is also reported as a failure instead of leaving the session waiting forever.
- Browser PCM playback now uses an 80 ms preroll when starting or recovering from an underrun, schedules later chunks contiguously, and defers the listening-state transition until already queued PCM has drained. This absorbs ordinary WebSocket scheduling jitter and keeps the UI state aligned with audible playback.
- Added regressions for atomic complete-text synthesis, serialized utterances, provider-error cleanup and reconnect, terminal Cartesia errors, abnormal socket closure, and updated phone-media framing expectations.
- Files touched:
  - `backend/src/main/java/com/sauti/call/{CartesiaRealtimeTextToSpeechClient,DefaultTwilioMediaStreamService,HybridVoiceSessionService,WebVoiceSessionService}.java`
  - `backend/src/test/java/com/sauti/call/{CartesiaRealtimeTextToSpeechClientTest,DefaultTwilioMediaStreamServiceTest,HybridVoiceSessionServiceTest}.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - focused hybrid, phone-media, and Cartesia WebSocket regression tests - passed.
  - `.\gradlew.bat :backend:test` - passed.
  - `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
  - `git diff --check` - passed before the handoff update.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: after CI/CD, run one browser test call, one public Web Voice call, and one carrier call. Interrupt an ordinary reply and a booking review mid-sentence, then allow a later reply to finish without interruption. Automated coverage verifies generation ordering and terminal state, but microphone echo, browser audio scheduling, and carrier buffering still require live end-to-end confirmation.

### 2026-07-21 - Make booking state participant-aware and playback buffering adaptive

- Fixed the wife-booking transcript where the caller identified himself as Zachary but the appointment was for Alexandra. The old design used `caller_name` for both the person speaking and the person receiving the service, so the authoritative intake pass overwrote Alexandra with Zachary during final review.
- The model-facing `book_slot` contract now exposes `appointment_name` for the service recipient. The fulfillment boundary translates it to the legacy persisted `caller_name` booking field, preserving database/API compatibility while removing the ambiguity from every LLM provider and language. Existing legacy `caller_name` tool payloads remain accepted.
- Authoritative call state now tracks the speaker, third-party relationship, appointment recipient, selected service, phone, day, and time separately. A third-party intent such as `book for my wife` clears any implicit self-booking name until the recipient's name is collected. The live prompt receives this compact current state on every caller turn.
- The booking server now treats the review token as the durable booking snapshot. On a clear natural approval it restores all reviewed top-level and configured custom fields before saving, so missing or reconstructed model arguments cannot alter the confirmed appointment. Corrections still produce a focused new review. Questions, confusion, or unrelated acknowledgements cannot authorize a save.
- Realtime local datetimes without an explicit UTC offset are normalized in the configured business timezone. Invalid dates/times return structured intake validation instead of being announced as calendar-provider failures. Genuine failures now tell the caller that Sauti retained the details for one retry.
- This follows OpenAI's current Realtime guidance: the session conversation is stateful, while complex flows benefit from explicit conversation states and dynamic `session.update` instructions. Sauti keeps the provider conversation and reinforces it with server-owned booking state rather than relying on model recollection alone.
- Browser Cartesia playback now starts with 160 ms of PCM preroll and increases the target by 40 ms after a detected underrun, up to 320 ms for that utterance. A completed or interrupted utterance resets the target. This absorbs real WebSocket/main-thread jitter without permanently adding the maximum latency to every response.
- Added transcript-shaped regressions for the exact Zachary/Alexandra sequence, third-party bookings without a recipient name, model-facing `appointment_name`, local datetime normalization, confused non-approval, natural approvals, and restoring the reviewed recipient after the model supplies a wrong or incomplete final payload.
- Files touched:
  - `backend/src/main/java/com/sauti/call/{CallIntakeNoteService,VoiceOutputGuard}.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/tool/{AgentToolLoader,DefaultToolSeeder,SautiCalendarFulfillment}.java`
  - `backend/src/test/java/com/sauti/call/CallIntakeNoteServiceTest.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `backend/src/test/java/com/sauti/tool/{AgentToolLoaderTest,SautiCalendarFulfillmentTest}.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `docs/agent-handoff.md`
- Verification:
  - focused intake-state, booking-fulfillment, tool-schema, conversation-prompt, and authenticated end-to-end flow tests - passed.
  - `.\gradlew.bat :backend:test` - passed; 237 tests.
  - `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
  - `git diff --check` - passed before the handoff update.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: after CI/CD, repeat the Zachary/Alexandra browser call and one carrier call, including `yes, all of those details are correct`, a correction after the review, and a confused question before approval. For audio, test on both a stable and deliberately throttled connection; the adaptive jitter target is bounded at 320 ms, but only real browser scheduling, microphone echo, and carrier buffering can validate the final perceived smoothness.

### 2026-07-21 - Make Realtime turns self-healing and browser PCM continuous

- Fixed the browser call failure where an interruption could leave the UI on Agent is speaking, require another utterance to recover, or fail to recognize a short caller reply. The Realtime lifecycle now treats both response.done and response.cancelled as terminal, does not let a late terminal event from an old response finish a newer response, and clears a cancellation rejected with no active response instead of leaving the response queue blocked.
- Replaced the scalar pending-transcription counter with item-scoped caller-turn tracking in both browser WebRTC and phone WebSocket Realtime paths. Every VAD turn has its own terminal watchdog, overlapping or noisy turns cannot leak the pending count, and missing speech_stopped or transcription terminal events self-release. A usable short transcript that finishes before the barge-in debounce now still advances the generation and interrupts external Cartesia playback before the next response is prepared.
- Replaced per-WebSocket-frame AudioBufferSourceNode scheduling with a continuous AudioWorklet PCM renderer for Agent Studio test calls, public Web Voice, and the cascaded browser fallback. It resamples Cartesia 16 kHz PCM to the actual browser audio rate, starts with a 280 ms jitter buffer, raises the rebuffer target by 120 ms after a real underrun up to 800 ms, and keeps all samples on one audio clock instead of creating audible seams between provider frames.
- Added an audible-drain watchdog at the playback boundary. If audio runs dry for 1.2 seconds without a provider completion, the browser finishes the local state and sends playback_stalled; hybrid and cascaded servers close the stale TTS context, clear queued audio, emit speaking=false, and open a clean Cartesia context for the next turn.
- Corrected the supplied Zachary and Alexandra intake sequence. Book for my wife, Alexandra is now captured as a named third-party recipient, a service answer followed by a price question retains only the service, and unclear speech after a phone-number request cannot be acknowledged as a phone or advance to date and time. The authoritative prompt requires the agent to answer any caller question or request the phone again.
- This follows the current OpenAI Realtime VAD and interruption contract: caller turns are driven by input_audio_buffer.speech_started and speech_stopped, and cancellation is a distinct terminal lifecycle that clients must handle.
- Files touched:
  - backend/src/main/java/com/sauti/call/{CallIntakeNoteService,HybridVoiceSessionService,OpenAiTelephonyRealtimeConversationProvider,WebVoiceWebSocketHandler}.java
  - backend/src/test/java/com/sauti/call/{CallIntakeNoteServiceTest,HybridVoiceSessionServiceTest,OpenAiTelephonyRealtimeConversationProviderTest,WebVoiceWebSocketHandlerTest}.java
  - dashboard/features/agents/AgentCreator/TestCallPanel.tsx
  - dashboard/features/voice-runtime/{openaiRealtime,pcmStreamPlayer}.ts
  - dashboard/features/web-voice/WebVoiceCall.tsx
  - dashboard/public/pcm-stream-player.js
  - docs/agent-handoff.md
- Verification:
  - focused intake-state, hybrid playback recovery, and telephony Realtime lifecycle tests - passed.
  - gradlew :backend:test - passed.
  - npm.cmd run typecheck in dashboard - passed.
  - npm.cmd run build in dashboard - passed; 50 routes generated.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: after CI/CD, repeat this exact browser transcript and interrupt both a short and a long agent response. Test once on a stable connection and once with throttling. The worklet removes main-thread scheduling gaps and can rebuffer up to 800 ms, but the final perceived latency and quality still needs a real microphone, browser audio device, and network path.

### 2026-07-21 - Preserve PCM sample boundaries and measure audible turn latency

- Traced the remaining symptom after conversational state and booking behavior were corrected. The Cartesia Java WebSocket listener reassembled fragmented text messages but forwarded every binary fragment immediately. A WebSocket fragment can split a little-endian 16-bit PCM sample between callbacks, so an odd fragment could be rejected by the browser or shift sample decoding and produce intermittent breakup or a stalled speaking state.
- Reassembled complete binary WebSocket messages before forwarding audio and carried an unmatched PCM byte across both binary messages and base64 JSON chunk events. A provider `done` event with an incomplete sample now follows the existing TTS error/recovery path instead of silently corrupting playback. The browser also aligns odd PCM buffers defensively before decoding them.
- Removed the oversized buffer that had been compensating for corruption. Continuous worklet playback now starts at 160 ms, increases by 40 ms only after a real underrun, and is capped at 320 ms. The worklet module URL is versioned so browsers cannot keep using an older cached processor after deployment.
- Added end-to-end hybrid timing and underrun telemetry. Each caller generation now measures transcript-to-validated-speech, speech-ready-to-first-Cartesia-audio, transcript-to-first-audio, speech-ready-to-audible-playback, and transcript-to-audible-playback. `sauti.voice.playback.underruns` counts actual browser buffer starvation by channel.
- Kept the complete-message speech/tool safety boundary. OpenAI's voice-agent guidance identifies direct speech-to-speech as the lowest-latency architecture, while Sauti intentionally uses a chained text/Cartesia path for explicit control. The new stage metrics make that architectural cost visible without weakening the guard that prevents tool protocol or commentary from becoming speech.
- Added regressions for fragmented binary PCM, odd sample bytes across provider messages, odd base64 PCM chunks, and hybrid latency/underrun metrics.
- Files touched:
  - `backend/src/main/java/com/sauti/call/{CartesiaRealtimeTextToSpeechClient,HybridVoiceSessionService,VoiceRuntimeMetrics}.java`
  - `backend/src/test/java/com/sauti/call/{CartesiaRealtimeTextToSpeechClientTest,HybridVoiceSessionServiceTest}.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/voice-runtime/{openaiRealtime,pcmStreamPlayer}.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `dashboard/public/pcm-stream-player.js`
  - `docs/agent-handoff.md`
- Verification:
  - focused Cartesia framing and hybrid session tests - passed.
  - `.\gradlew.bat :backend:test` - passed.
  - `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
  - `node --check public/pcm-stream-player.js` in `dashboard/` - passed.
- Deployment status: not deployed. Changes remain uncommitted for maintainer review and the normal CI/CD workflow.
- Known follow-up/risk: after CI/CD, repeat a long booking review and several short replies in Agent Studio and public Web Voice. Inspect `sauti.voice.latency` by stage and `sauti.voice.playback.underruns`: a high `transcript_to_speech_ready` isolates prompt/model/tool latency, a high `speech_ready_to_first_audio` isolates Cartesia, and underruns isolate provider/network delivery. Live audio-device and network behavior cannot be proven by mocked streams alone.

### 2026-07-21 - Gate interruptions on recognized speech and answer business hours deterministically

- Fixed the Agent Studio state where a caller transcript was accepted but the UI remained on `Hearing you` and no answer followed. Raw server VAD and a second local microphone-energy monitor could treat echo, background noise, or an empty audio turn as a real interruption, invalidate the valid response generation, and then leave no recognized transcript from which to request a replacement response.
- Raw audio start/stop events now update only the listening/processing UI and pending-transcription guard. Only meaningful streaming or final transcription can cancel a response or interrupt Cartesia. Empty, failed, and watchdog-expired turns restore the idle listening state. The duplicate local raw-energy interruption path is disabled for Realtime calls.
- Applied the same recognition gate to phone Realtime. `speech_started` no longer marks a valid phone response interrupted; the interruption becomes authoritative only after transcript content is accepted. Empty VAD turns therefore preserve and release the valid deferred answer instead of silently discarding it.
- Made caller-response preparation fail open after 1.2 seconds. A slow or hung transcript/instruction request can no longer prevent `response.create` or poison the serialized preparation chain for later turns; the current safe session prompt is used and a late instruction update is retained only for a still-current generation.
- Added a deterministic, server-owned answer for broad opening-hours questions in browser, public Web Voice, and phone Realtime. Configured hours are spoken once without asking the caller to choose a day or morning/afternoon first; date- or time-specific availability remains owned by the live calendar tool. The default weekday answer is shortened to one natural sentence to reduce latency and TTS exposure.
- Strengthened the shared conversation policy for every agent/channel: direct customer questions must receive the known answer first, published business hours are distinct from live slots, and broad `when are you available` questions must include both days and opening/closing times.
- Added browser turn-gate tests to CI plus backend regressions for the reported availability phrasings, specific-slot exclusion, exact direct phone speech, and a valid response completing during an empty/noise VAD turn.
- This design intentionally goes beyond raw VAD semantics. OpenAI documents `speech_started` as the normal interruption signal, but Sauti's external Cartesia path and open microphone can produce false acoustic starts; with provider auto-interruption disabled, Sauti requires recognized speech before destructive cancellation.
- Files touched:
  - `.github/workflows/ci.yml`
  - `backend/src/main/java/com/sauti/agent/OperatingHoursSchedule.java`
  - `backend/src/main/java/com/sauti/api/{CallController,PublicWebVoiceController}.java`
  - `backend/src/main/java/com/sauti/call/{OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider,RealtimeDtos}.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/test/java/com/sauti/agent/OperatingHoursScheduleTest.java`
  - `backend/src/test/java/com/sauti/call/{OpenAiRealtimeServiceTest,OpenAiTelephonyRealtimeConversationProviderTest}.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `dashboard/features/agents/AgentCreator/TestCallPanel.tsx`
  - `dashboard/features/voice-runtime/{openaiRealtime,realtimeTurnGate,realtimeTurnGate.test}.ts`
  - `dashboard/features/web-voice/WebVoiceCall.tsx`
  - `dashboard/lib/api/{calls,public-web-voice}.ts`
  - `dashboard/{package.json,tsconfig.json}`
  - `docs/agent-handoff.md`
- Verification:
  - focused Realtime service, telephony lifecycle, operating-hours, and conversation-policy tests - passed.
  - `.\gradlew.bat :backend:test :backend:build` - passed.
  - `npm.cmd run test:voice` in `dashboard/` - passed; 3 turn-gate regressions.
  - `npm.cmd run lint` and `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
- Deployment status: not deployed. All changes remain uncommitted for maintainer review and the normal GitHub Actions CI/CD workflow.
- Known follow-up/risk: after CI/CD, repeat the exact Agent Studio call from the screenshot, one broad-hours question, one date-specific availability question, and one carrier call. Test silence/echo during a pending answer and a deliberate spoken interruption. Automated tests cover event ordering and deterministic replies, but real microphone acoustic echo, browser scheduling, carrier buffering, and provider latency still require live validation. If these remain unstable after the lifecycle fix, migrate the media/turn-taking layer to a dedicated voice runtime rather than adding more local VAD heuristics.

### 2026-07-21 - Make the booking flow continuous and honor caller withdrawal

- Fixed the latest Zachary transcript as a workflow problem rather than another phrasing exception. Structured weekly schedules with identical hours are now grouped for speech, so the default salon schedule is announced as Monday through Friday, 9 in the morning to 5 in the evening, with the weekend closed instead of reading every weekday separately.
- Removed the model-controlled gap between a successful availability check and the booking review. When the requested slot is available and server-owned intake state confirms an active booking with recipient, service, and phone collected, the availability tool authorizes `book_slot` as the one required next tool. Browser Realtime, phone Realtime, and the turn-based orchestrator execute that transition silently; they cannot insert a promise to prepare the details, ask the caller to hold, or wait for an unnecessary `OK` before the server-generated review.
- Kept information-only availability questions separate from booking intake. The automatic review transition is not returned unless the authoritative intake state says the caller is actively booking and the required pre-review identity/contact details are present.
- Added deterministic withdrawal handling for explicit deferred stops such as `don't book yet` plus `I will call you back later`. No booking tool is called, and every runtime now reassures the caller that nothing will be booked, thanks them, and closes warmly. The matcher is intentionally narrow so a correction such as `don't book Friday; book Saturday instead` remains an active booking change.
- Reinforced the shared prompt and intake notes so the same behavior applies to every agent and conversation runtime, while business facts and booking state remain server-owned.
- Files touched:
  - `backend/src/main/java/com/sauti/agent/OperatingHoursSchedule.java`
  - `backend/src/main/java/com/sauti/call/{BookingConversationPolicy,CallIntakeNoteService,OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider}.java`
  - `backend/src/main/java/com/sauti/llm/ConversationOrchestrator.java`
  - `backend/src/main/java/com/sauti/tool/SautiCalendarFulfillment.java`
  - `backend/src/test/java/com/sauti/agent/OperatingHoursScheduleTest.java`
  - `backend/src/test/java/com/sauti/call/{BookingConversationPolicyTest,CallIntakeNoteServiceTest,OpenAiRealtimeServiceTest,OpenAiTelephonyRealtimeConversationProviderTest}.java`
  - `backend/src/test/java/com/sauti/llm/ConversationOrchestratorTest.java`
  - `backend/src/test/java/com/sauti/tool/SautiCalendarFulfillmentTest.java`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `docs/agent-handoff.md`
- Verification:
  - focused transcript-shaped operating-hours, intake-state, withdrawal, calendar fulfillment, phone Realtime, and orchestrator tests - passed; 86 tests.
  - `.\gradlew.bat :backend:test` - passed.
  - `npm.cmd run test:voice` in `dashboard/` - passed; 3 turn-gate regressions.
  - `npm.cmd run lint` and `npm.cmd run typecheck` in `dashboard/` - passed.
  - `npm.cmd run build` in `dashboard/` - passed; 50 routes generated.
  - `git diff --check` - passed before the handoff update.
- Deployment status: not deployed. All changes remain uncommitted for maintainer review and the normal GitHub Actions CI/CD workflow.
- Known follow-up/risk: after CI/CD, repeat this exact call in Agent Studio and on one carrier call. Confirm that the hours are grouped, Friday at 3 p.m. moves directly into one booking review, and `don't book yet; I will call back later` produces an explicit no-booking reassurance. Automated tests cover the server transition and all three runtime paths, but live Realtime model argument quality and external provider event ordering still need an end-to-end call.

### 2026-07-21 - Replace phrase matching with a multilingual semantic turn boundary

- Superseded the narrow phrase-matching portions of the preceding booking revision. Production model-backed agents now must call the internal `update_conversation_state` function before any caller-facing reply on every accepted turn. The model interprets meaning from the complete multilingual conversation; it no longer depends on an English/French regex list for broad hours, withdrawal, identity, or corrections.
- Kept model understanding separate from application authority. The internal function emits typed field changes, `booking_subject`, `booking_intent`, the caller-facing response, and at most one requested configured business tool. A server reducer validates and persists those changes in the Redis-backed call session. Calendar, webhook, transfer, payment, and other configured tools remain separate side-effect boundaries.
- Added deterministic participant invariants. A corrected speaker name updates a self-booking recipient, never invents the old misheard name as another person, and never overwrites an explicitly different recipient. Moving from self to an unnamed third party clears the stale self name. The same reducer supports configured vertical fields and explicit field withdrawal without adding language-specific sentences.
- Made review authorization turn-scoped. Semantic approval is cleared automatically on the next turn, and `book_slot` reads the typed review decision instead of matching an approval phrase. A paused intent blocks booking in both the semantic router and calendar fulfillment, even if a stale model call still reaches the calendar boundary.
- Applied the boundary platform-wide through `AgentToolLoader`, so it is present for every agent rather than one salon configuration. Browser/public Web Voice, phone Realtime, and the turn-based orchestrator force the semantic tool before speech. Text accompanying a function call remains silent; an authorized business lookup chains without a spoken preamble.
- Removed the redundant second model response from the Cartesia phone path. A validated semantic/tool reply is seeded into Realtime history once and sent directly to external TTS, reducing reply latency and eliminating another opportunity for duplicated or changed speech. Browser preparation failures/timeouts and phone preparation failures now fall back to the required semantic tool instead of an unclassified response.
- Retained the old transcript parser only as a compatibility fallback for the explicitly non-semantic local heuristic provider and historical sessions with no semantic-state revision. It is not on the production Spring AI/Realtime turn path.
- Files touched:
  - `backend/src/main/java/com/sauti/call/{BookingConversationPolicy,CallIntakeNoteService,OpenAiRealtimeService,OpenAiTelephonyRealtimeConversationProvider,RealtimeDtos}.java`
  - `backend/src/main/java/com/sauti/llm/{ConversationOrchestrator,LlmToolCallingProvider,LocalToolCallingLlmProvider}.java`
  - `backend/src/main/java/com/sauti/session/{CallSession,CallSessionStore,ConversationState,RedisCallSessionStore}.java`
  - `backend/src/main/java/com/sauti/tool/{AgentToolLoader,AgentToolService,ConversationStateTool,SautiCalendarFulfillment,ToolFulfillmentRouter}.java`
  - corresponding focused backend tests, including removal of `BookingConversationPolicyTest`
  - `dashboard/features/voice-runtime/openaiRealtime.ts`
  - `dashboard/lib/api/{calls,public-web-voice}.ts`
  - `docs/agent-handoff.md`
- Verification:
  - focused semantic-state, agent-tool loading, intake, Realtime, orchestrator, and calendar suite - passed; 90 tests.
  - `.\gradlew.bat :backend:test --rerun-tasks` - passed from a forced full rerun.
  - `npm.cmd run test:voice` - passed; 3 turn-gate regressions.
  - `npm.cmd run lint` and `npm.cmd run typecheck` - passed.
  - `npm.cmd run build` - passed; 50 routes generated.
  - `git diff --check` - passed before this handoff update.
- Deployment status: not deployed. All changes remain uncommitted for maintainer review and the normal GitHub Actions CI/CD workflow.
- Known follow-up/risk: model semantic quality still needs live evaluation across the supported languages, accents, interruptions, ambiguous references, corrections, and each agent's custom fields. After CI/CD, run a multilingual transcript matrix plus one browser and one carrier call, and measure transcript-final-to-first-audio latency. Do not respond to a failure by adding another sentence matcher; improve the semantic schema/prompt, model evaluation set, or deterministic reducer invariant instead.

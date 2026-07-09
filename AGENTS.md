# Sauti Agent Operating Instructions

These instructions are for coding agents working in this repository. Treat this file as the standing contract for how to continue the project without breaking prior work.

## Current project shape

Sauti is a Spring Boot + Next.js AI voice-agent platform.

- `backend/`: Spring Boot modular monolith.
- `dashboard/`: Next.js App Router dashboard, not Flutter.
- `deploy/`: production Docker Compose, Caddy, backup, migration, and server helper scripts.
- `docs/`: plans, architecture notes, implementation notes, and agent handoff history.
- Production domain: `https://sauti.uk`.
- GitHub repository: `https://github.com/Zacle/Sauti`.
- Production host: OVH VPS reached by CI/CD over SSH. Do not commit server secrets or private keys.

The old Flutter clean-architecture guidance does not apply to the current dashboard. If a Flutter app is introduced later, add a separate section for it.

## Required agent workflow

Before changing code:

1. Read this file.
2. Read [docs/agent-handoff.md](docs/agent-handoff.md).
3. Check `git status --short`.
4. Preserve user changes and untracked files unless the user explicitly asks otherwise.
5. Prefer a small, complete change over a broad rewrite.

After every code or deployment change:

1. Run verification proportional to the change.
2. Update [docs/agent-handoff.md](docs/agent-handoff.md) with:
   - date,
   - what changed,
   - why,
   - files touched,
   - verification run,
   - deployment status if applicable,
   - known follow-ups or risks.
3. Commit only intentional project files. Never commit `.env`, `secrets/`, private keys, local screenshots, build outputs, or unrelated user files.

If you deploy:

1. Push to `main`.
2. Confirm GitHub Actions CI success.
3. Confirm production deploy success.
4. Verify `https://sauti.uk/health`.
5. Record the deployed commit in [docs/agent-handoff.md](docs/agent-handoff.md).

## Coding architecture rules

### Backend

- Keep the backend as a modular Spring Boot monolith.
- Use package-level feature boundaries under `backend/src/main/java/com/sauti/*`.
- Controllers stay in `com.sauti.api`.
- Business logic belongs in feature services, not controllers.
- DTOs should live with their feature package unless there is an existing convention.
- Repository queries must be tenant-scoped for customer data.
- Do not expose secrets in API responses, logs, or test output.
- Prefer portable JPQL/Java aggregation unless PostgreSQL-specific behavior is required and tested.
- Keep H2/local test compatibility unless intentionally changing the test database strategy.

### Dashboard

- The dashboard is Next.js, not Flutter.
- Use feature folders under `dashboard/features/<feature>`.
- Keep boundaries similar to clean architecture:
  - `domain/`: pure view/domain helpers, no API calls or React side effects.
  - `presentation/`: React components and CSS modules.
  - `dashboard/lib/api/*`: API clients.
  - `dashboard/types/api.ts`: shared API response/request types.
- Pages under `dashboard/app/.../page.tsx` should be thin wrappers around feature components.
- Do not call `fetch` directly inside deeply nested components when a `lib/api` client is appropriate.
- Prefer CSS modules for feature-specific styles.
- Keep UI consistent with existing console cards, rounded inputs, and token styling.

### Integrations

- Workspace/provider credentials must be encrypted at rest.
- API responses should expose metadata and status, not secrets.
- Connections belong to workspaces/tenants; enablement and behavior belong to agents.
- During-call tools must be explicit and safe. Payment tools must require confirmation.
- Post-call jobs must be durable, retryable, and idempotent.
- Test calls are valid analytics/post-call inputs and must remain clearly marked as tests where payloads expose that distinction.

## Production architecture

Production runs on a single Docker host with Caddy in front.

- Docker Compose file: `deploy/docker-compose.prod.yml`.
- Deploy script: `deploy/deploy.sh`.
- CI workflow: `.github/workflows/ci.yml`.
- Deploy workflow: `.github/workflows/deploy.yml`.
- Production builds images locally on the VPS from the exact commit that passed CI.
- Caddy serves `sauti.uk` and redirects `www.sauti.uk` to the apex domain.
- Public health endpoint: `https://sauti.uk/health`.

Managed external services currently used:

- Neon PostgreSQL.
- Upstash Redis.
- Resend SMTP.

The production compose file intentionally does not run local Postgres or Redis services anymore. Old local Docker volumes may still exist on the server for rollback safety; do not delete them without explicit approval.

## Production callback URLs

Provider dashboards should use `sauti.uk`, not localhost or temporary tunnels.

OAuth/callback URLs:

- Google auth: `https://sauti.uk/api/v1/auth/oauth/google/callback`
- Google Calendar: `https://sauti.uk/api/v1/integrations/google-calendar/callback`
- Google Sheets: `https://sauti.uk/api/v1/integrations/google_sheets/callback`
- HubSpot: `https://sauti.uk/api/v1/integrations/hubspot/callback`
- Salesforce: `https://sauti.uk/api/v1/integrations/salesforce/callback`
- Cal.com: `https://sauti.uk/api/v1/integrations/cal_com/callback`
- Calendly: `https://sauti.uk/api/v1/integrations/calendly/callback`

Telnyx:

- Call Control webhook URL: `https://sauti.uk/webhooks/telnyx/call-control`
- Media WebSocket base URL is not entered in Telnyx. It belongs in Sauti env:
  `TELNYX_MEDIA_WEBSOCKET_BASE_URL=wss://sauti.uk/ws/telnyx/media`

Other public base URLs:

- `PUBLIC_BASE_URL=https://sauti.uk`
- `DASHBOARD_BASE_URL=https://sauti.uk`
- `SAUTI_WEB_VOICE_WEBSOCKET_URL=wss://sauti.uk`
- `SAUTI_CORS_ALLOWED_ORIGINS=https://sauti.uk`

## Environment handling

- Real secrets live in `.env` locally and `/opt/sauti/.env.production` on the server. Do not commit them.
- `.env.example` and `deploy/.env.production.example` may contain placeholder values only.
- Use `deploy/configure-external-services-env.sh` to derive Spring/Compose values from provider-style external service URLs on the server.
- Use `deploy/update-public-callbacks-env.sh` to rewrite public callback URLs to a production domain.
- Use `deploy/test-external-services.sh` to validate Neon, Upstash, and Resend connectivity from the server.
- Use `deploy/migrate-postgres-to-neon.sh` only for the guarded local-to-Neon migration flow. It refuses to restore over a Neon database with existing public tables.

## Verification commands

Use the smallest relevant set, but for broad backend/dashboard changes run:

```powershell
.\gradlew.bat :backend:test
Push-Location dashboard
npm.cmd run typecheck
npm.cmd run build
Pop-Location
```

For dashboard-only changes:

```powershell
Push-Location dashboard
npm.cmd run typecheck
npm.cmd run build
Pop-Location
```

For deployment verification:

```powershell
curl.exe -i https://sauti.uk/health
curl.exe -I https://sauti.uk/analytics
```

`/analytics` should redirect unauthenticated users to `/login?next=%2Fanalytics`.

## Current important decisions

- Recharts is the chosen dashboard charting library.
- Analytics charts are scoped to `/analytics`; the larger bundle is acceptable there but should not be imported globally.
- The analytics backend currently aggregates calls in Java for portability across local tests and PostgreSQL.
- The analytics empty state must be explicit when all metrics are zero; do not render empty chart labels.
- The agent dropdown and other filters should match the larger rounded console control style.
- The integration marketplace uses OAuth where appropriate; do not replace OAuth providers with raw API-key shortcuts.
- Slack currently uses incoming webhooks. Improve message formatting with Block Kit before changing the auth model.
- OAuth token refresh for Google Sheets, HubSpot, and Salesforce remains a high-priority follow-up if not already implemented.
- M-Pesa Daraja uses consumer key/secret/passkey, which is correct for that API.

## Documentation discipline

The handoff file is part of the product. If you change behavior, deployment, environment variables, architecture, UI patterns, provider configuration, or operational workflow, update [docs/agent-handoff.md](docs/agent-handoff.md) in the same commit.

Do not leave future agents guessing.

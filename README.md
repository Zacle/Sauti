# Sauti

Sauti is a multi-tenant AI voice-receptionist platform. Businesses configure an
agent in the dashboard, connect a phone number, and the agent answers calls,
books appointments, sends SMS, and syncs with calendars, CRMs, and webhooks.

- **Voice:** live calls run on Telnyx-managed AI Assistants (Telnyx handles
  speech recognition, the model, synthesis, and interruption). Sauti owns the
  business logic: tool policy, confirmation before side effects, fulfillment,
  call records, billing, and analytics.
- **WhatsApp:** handled by Sauti's own conversation orchestrator.
- **Data:** PostgreSQL (Neon in production), Redis (Upstash in production),
  email through Resend SMTP.

## Repository layout

```text
backend/     Spring Boot modular monolith (Java 17, Gradle)
dashboard/   Next.js 15 App Router console and marketing site
deploy/      Production Docker Compose, Caddy, and server helper scripts
infra/local/ Local Postgres, Redis, and Mailpit
docs/        Architecture, runbooks, setup guides, and agent handoff history
```

Start with [AGENTS.md](AGENTS.md) and [docs/agent-handoff.md](docs/agent-handoff.md).
Product specs: `Sauti_SRS.pdf` and `Sauti_Flows.pdf`. Backend module boundaries
are in [docs/backend-modules.md](docs/backend-modules.md).

## Local development

```bash
cp .env.example .env
docker compose -f infra/local/docker-compose.yml up -d   # postgres, redis, mailpit
./gradlew :backend:bootRun                               # http://localhost:8080
cd dashboard && npm ci && npm run dev                    # http://localhost:8088
```

Or run everything in containers with `docker compose up --build`.
Mailpit is at `http://localhost:8025`, Swagger at `/swagger-ui.html`.
Without `PROVIDER_MODE=live` no external telephony or speech calls are made.
Verification codes are returned in API responses only when
`AUTH_EXPOSE_DEV_TOKENS=true`; keep it `false` in production.

## Checks

```bash
./gradlew :backend:test
cd dashboard && npm run typecheck && npm run lint && npm run build
```

## Production

Pushes to `main` run `.github/workflows/ci.yml`; `deploy.yml` then deploys the
exact commit that passed CI to a single Docker host behind Caddy. See
[docs/deployment.md](docs/deployment.md) for provisioning, secrets, DNS, and
backups, and `deploy/bootstrap-vps.sh` to prepare a fresh Ubuntu server.

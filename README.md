# Sauti

Sauti is a greenfield MVP implementation of the multilingual AI voice-agent platform described in `Sauti_SRS.pdf`.

The current implementation targets the first live-call MVP:

- Spring Boot backend with tenant auth, agent configuration, Twilio webhook entrypoints, call logs, and a fake STT/LLM/TTS pipeline.
- AI agent turn orchestration with structured booking decisions and configurable webhook integrations for external LLM/calendar workflows.
- Next.js dashboard with agent creation, activation, usage summary, and call-log views.
- Local infrastructure for PostgreSQL and Redis under `infra/local/docker-compose.yml`.

## Structure

```text
backend/      Spring Boot modular monolith
dashboard/    Next.js operational dashboard
infra/local/  Infrastructure-only Docker Compose services
docs/         Implementation notes
```

## Backend

The complete local stack can be started from the repository root:

```powershell
Copy-Item .env.example .env
# Add GOOGLE_AI_API_KEY to .env for live Standard-model responses.
docker compose up --build -d
docker compose ps
```

Open the dashboard at `http://localhost:8088`, the API at `http://localhost:8080`, Swagger at `http://localhost:8080/swagger-ui.html`, and Mailpit at `http://localhost:8025`.

Use `docker compose logs -f backend dashboard` to follow application logs and `docker compose down` to stop the stack. PostgreSQL and Redis data remain in named volumes; use `docker compose down -v` only when you intentionally want to erase local data.

The code uses fake telephony and no-op speech providers by default. Gemini-backed text turns require `GOOGLE_AI_API_KEY`. Twilio, Deepgram, and ElevenLabs credentials are only required when their corresponding live providers are enabled.
Auth email HTML is rendered in the backend with Thymeleaf templates under `backend/src/main/resources/templates/email`. Verification and password reset codes are stored in Redis with a TTL. The app uses one SMTP-backed mail service. Local delivery uses Mailpit on `localhost:1025`; open the Mailpit inbox at `http://localhost:8025`. Production can use Resend SMTP by setting `SPRING_MAIL_HOST=smtp.resend.com`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME=resend`, `SPRING_MAIL_PASSWORD=<resend-api-key>`, `SPRING_MAIL_SMTP_AUTH=true`, and `SPRING_MAIL_SMTP_STARTTLS_ENABLE=true`. Verification and password reset codes are returned in API responses only when `AUTH_EXPOSE_DEV_TOKENS=true`, which is the local default. Disable it in production.
The dashboard handles `/verify-email`, `/forgot-password`, and `/reset-password` by collecting the email code sent by the backend.
Twilio webhook signature validation is available through `TWILIO_VALIDATE_SIGNATURE=true`; local development leaves it disabled by default. Auth rate limits are stored in Redis.

Call turns are routed through per-agent tool definitions stored in `agent_tools`. Local development uses `SAUTI_LLM_PROVIDER=heuristic`; production can set `SAUTI_LLM_PROVIDER=webhook` and `SAUTI_LLM_WEBHOOK_URL` to call an external AI workflow. The webhook should return:

```json
{
  "responseText": "Done. Your appointment is confirmed.",
  "bookingIntent": {
    "callerName": "Fatou",
    "serviceType": "Consultation",
    "appointmentAt": "2030-01-15T10:00:00Z"
  }
}
```

Agent tools are seeded inactive when an agent is created. Activate the relevant tools through `/api/v1/agents/{agentId}/tools` before expecting the LLM to call them.

Bookings are synced through a `CalendarProvider`. Local development uses `SAUTI_CALENDAR_PROVIDER=local`; production can set `SAUTI_CALENDAR_PROVIDER=webhook` and `SAUTI_CALENDAR_WEBHOOK_URL` to call Google Calendar, Calendly, n8n, or another scheduling workflow. The calendar webhook should return `{ "externalEventId": "..." }`.

Backend module boundaries are documented in [docs/backend-modules.md](docs/backend-modules.md).
OpenAPI UI is available at `/swagger-ui.html` when the backend is running.

## Production deployment

Production runs from immutable Docker images using Docker Compose and Caddy.
Pushes to `main` deploy only after the backend and dashboard CI jobs pass;
the VPS builds the exact commit that passed CI.
See [docs/deployment.md](docs/deployment.md) for server provisioning, GitHub
secrets, DNS, TLS, and backup requirements.

## Dashboard

For a host-based dashboard run, use `npm ci` and `npm run dev` inside `dashboard/`. Override the backend proxy with `SAUTI_API_BASE_URL` when necessary.

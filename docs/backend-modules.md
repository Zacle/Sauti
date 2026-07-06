# Backend Modules

The backend is a Spring Boot modular monolith. It builds as one JAR, but package boundaries map to the SRS module table.

| SRS Module | Java Package | Responsibility |
| --- | --- | --- |
| module-auth | `com.sauti.auth` | JWT auth, registration, Redis-backed email verification codes, refresh-token rotation, logout, password reset, future OAuth |
| module-tenant | `com.sauti.tenant` | Tenant account management, onboarding state |
| module-agent | `com.sauti.agent` | Agent language, voice, prompt, greeting, schedule, escalation config |
| module-call | `com.sauti.call` | Twilio webhooks, WebSocket media stream, turn loop |
| module-nlp | `com.sauti.nlp` | Language detection and future STT/TTS routing |
| module-llm | `com.sauti.llm` | Agent reasoning, prompt context, structured AI turn decisions |
| module-calendar | `com.sauti.calendar` | Booking lifecycle, local/webhook calendar sync, future Google Calendar/Calendly integrations |
| module-analytics | `com.sauti.analytics` | Call aggregation and outcome metrics |
| module-billing | `com.sauti.billing` | Plan usage and future Lemon Squeezy webhooks |
| module-api | `com.sauti.api` | REST controllers, CORS, OpenAPI configuration |

Rules:

- Controllers stay in `com.sauti.api`.
- Business logic stays in the owning module package.
- Cross-module access should go through services or narrow repository use from orchestration services.
- New provider integrations should be hidden behind interfaces in their owning module.

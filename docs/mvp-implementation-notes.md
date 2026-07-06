# MVP Implementation Notes

## Implemented

- Monorepo scaffold with Spring Boot backend and Flutter Web dashboard.
- Auth flow: register, login, access JWT, refresh-token persistence.
- Full auth lifecycle: email verification, resend verification, login blocked before verification, refresh rotation, logout, forgot password, reset password, and refresh-token revocation after password reset.
- Backend-rendered Thymeleaf HTML templates for verification and password reset emails.
- Redis-backed verification and password reset code storage with TTL.
- Auth email delivery through one SMTP-backed service: Mailpit for local development and Resend via SMTP configuration for production.
- Flutter auth pages for `/verify-email`, `/forgot-password`, and `/reset-password`.
- Tenant model and tenant-scoped agent/call APIs.
- Agent CRUD, fake Twilio number provisioning, activate/deactivate.
- Twilio inbound webhook endpoint returning TwiML with a media stream URL.
- WebSocket endpoint for Twilio media sessions, including Twilio JSON frame parsing and media-event responses.
- Fake STT, language detection, fake LLM, and fake TTS provider interfaces.
- Structured AI agent reasoning provider for call turns, including booking-intent output.
- Webhook-backed AI reasoning integration for external LLM/agent workflows.
- Calendar provider abstraction with local and webhook-backed event creation.
- Call and turn persistence for transcript/debugging.
- Onboarding status endpoint for the business setup flow.
- Billing usage endpoint with plan limit, remaining minutes, and limit status.
- Booking table and tenant-scoped booking list/get/create/cancel APIs.
- Agent booking flags, timezone, and configurable escalation phrases.
- Call-pipeline handling for human-transfer and voicemail edge cases.
- Terminal call outcomes close the Twilio media session and tenant minute usage is incremented once on completion.
- Twilio webhook signature validation is implemented and can be enabled with `TWILIO_VALIDATE_SIGNATURE=true`.
- Redis-backed rate limiting protects login, forgot-password, verify-email, and resend-verification endpoints.
- Tenant-scoped simulated call-turn lookup by Twilio SID.
- Dashboard operational shell for demo auth, agent setup, activation, and call logs.
- Flutter clean-architecture package layout with `core`, `feature_auth`, `feature_agents`, and `feature_calls`.

## Flow PDF Notes

`Sauti_Flows.pdf` was reviewed, but the available local PDF extraction tool only exposed the section headings, not the visual node labels inside the diagrams. Backend updates were aligned to the extracted flow headings:

- Business Onboarding Flow
- Inbound Call Pipeline
- Booking Flow Detail
- Language Detection & Routing
- Multi-Tenancy & Data Isolation
- Billing & Usage Flow
- Call Escalation & Edge Cases
- Dashboard App Navigation

## Remaining Production Work

- Add real Twilio number provisioning.
- Add real Deepgram and streaming TTS adapters.
- Add first-party Gemini/OpenAI adapter if webhook-based AI workflow is not enough.
- Move active conversation state to Redis.
- Complete production domain configuration.
- Add CI, Dockerfile, and deployment workflow.

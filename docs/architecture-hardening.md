# Architecture hardening roadmap

Date: 2026-07-16

This document records the debt-prevention decisions made during the production architecture review and the remaining work in priority order.

## Implemented foundation

- HTTP adapters no longer read call, turn, agent, or public Web Voice repositories directly. Narrow feature services own lookup, tenant checks, and session authorization.
- A build-time boundary test prevents repository imports from returning to `com.sauti.api`.
- Integration disconnect queries are tenant-scoped instead of scanning every agent integration.
- Authentication and public Web Voice session creation use one Redis-backed limiter. Its key contains a protected identity, and Lua makes increment plus expiry atomic across application replicas.
- The production Spring profile and startup validator reject development secrets, fake providers, H2, insecure public origins, non-TLS WebSockets, and disabled telephony signature validation.
- Browser and phone voice paths publish bounded Micrometer metrics for active sessions, first-response stages, interruptions, fallbacks, and failures.
- Dashboard linting is non-interactive, warning-free, and required by CI alongside type checking and the production build.

## Next priorities

1. **Durable worker claiming.** Add database-backed claim/lease fields to post-call and integration delivery jobs, claim batches transactionally, and test concurrent workers. This is required before running multiple backend replicas.
2. **Break up the agent studio.** Split `AgentCreator.tsx` and `TestCallPanel.tsx` into feature slices with explicit hooks for configuration, media transport, transcripts, and persistence. Keep provider-independent state separate from WebSocket/audio adapters.
3. **Introduce typed application ports.** Define narrow interfaces for telephony media, STT, LLM streaming, TTS streaming, bookings, and post-call sinks. Provider implementations should translate vendor errors into stable domain outcomes.
4. **Strengthen tenant guarantees.** Prefer repository methods that require `tenantId`; add integration tests proving cross-tenant IDs return not-found for every customer-data aggregate.
5. **Trace voice turns end to end.** Add OpenTelemetry spans around carrier ingress, STT finalization, first LLM token, first TTS byte, first outbound audio, and tool calls. Propagate an internal correlation ID without exposing caller data.
6. **Define SLOs and alerts.** Track p50/p95 time to first audio, interruption stop time, turn failure rate, provider fallback rate, queue age, and job retry exhaustion separately for browser tests and each phone provider.
7. **Contract-test providers.** Add recorded, secret-free fixtures for Telnyx/Twilio media events, Deepgram transcripts, OpenAI streaming deltas, Cartesia audio frames, and OAuth token refresh.
8. **Separate read models where useful.** Dashboard and analytics queries should use explicit projections instead of returning mutable domain entities. Keep the monolith until scaling evidence justifies service extraction.

## Extraction rule

Do not split the monolith merely to create more deployables. Extract a module only when it has a stable interface, independent scaling or reliability requirements, isolated data ownership, and enough observability to operate it safely.

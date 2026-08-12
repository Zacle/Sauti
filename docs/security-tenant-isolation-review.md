# Phase 4 security and tenant-isolation review

Review date: 2026-08-12

This review is the code and configuration evidence for the Phase 4 security
gate. It does not replace a penetration test, provider verification, or legal
review, and it does not automatically approve general availability.

## Boundaries reviewed

- Spring Security defaults every route to authenticated unless it is an
  explicitly public authentication, catalog, provider webhook, public demo, or
  health endpoint. Platform administration requires `ROLE_PLATFORM_ADMIN` and
  the admin Caddy host rejects webhook and WebSocket paths.
- Tenant-facing agents, calls, bookings, analytics, integrations, inbox, and
  notifications derive the tenant from the signed `AuthenticatedUser`; their
  service/repository lookups include that tenant. Cross-workspace resources are
  returned as not found rather than disclosing ownership.
- Provider callbacks were checked for signature or callback-credential
  verification. Telnyx, WhatsApp, Whop, Lemon Squeezy, and 2Checkout already
  validate provider proof before state changes. M-Pesa was remediated in this
  review as described below.
- Integration API responses expose connection metadata and a
  `credentialConfigured` boolean, not decrypted credentials. OAuth state is
  signed, time-bounded, and restricts its return path. Password-reset codes are
  verified once, then deleted, and active refresh tokens are revoked after a
  password reset.
- Production startup already rejects development providers, placeholder
  secrets, open registration, exposed development tokens, H2, non-PostgreSQL
  storage, unsigned provider callbacks, and non-HTTPS CORS configuration.

## Findings remediated

1. Public rate limits previously parsed the first raw `X-Forwarded-For` value,
   which could let a client select its rate-limit identity. Client address
   resolution now uses `HttpServletRequest.getRemoteAddr()` after Tomcat's
   trusted-proxy processing. Production requires native forwarded-header
   handling.
2. WebSocket origins defaulted to `*` outside explicit configuration. The
   production profile, startup validator, deployment example, and launch gate
   now require explicit HTTPS WebSocket origins. The admin host continues to
   reject WebSockets entirely.
3. The notifications WebSocket put the normal API bearer token in its URL. It
   now obtains a purpose-bound dashboard ticket that expires after 60 seconds;
   the socket handler verifies its issuer, purpose, signature, and tenant.
4. M-Pesa callback state changes were based only on connection and checkout
   identifiers. New STK requests now receive a callback URL containing an HMAC
   token bound to the exact connection, and unsigned or mismatched callbacks
   are rejected before payment state changes.

## Regression evidence

- `ClientAddressResolverTest` proves a supplied forwarded-for chain cannot
  replace the container-resolved address.
- `ProductionSafetyValidatorTest` proves wildcard WebSocket origins and an
  unsafe forwarded-header mode block production startup.
- `DashboardSocketTicketServiceTest` proves tickets are tenant- and
  issuer-bound.
- `MpesaCallbackTokenServiceTest` and `MpesaWebhookControllerTest` prove a
  callback token cannot be reused for another connection and that missing
  proof cannot reach payment fulfillment.
- Existing API acceptance coverage includes platform-admin denial for tenant
  owners and cross-tenant `404` behavior for tenant-owned agent templates.
  Tenant-scoped repository/service tests cover agents, bookings, calendar
  credentials, integrations, calls, WhatsApp conversations, and knowledge
  documents.

## Operational acceptance still required

- Deploy only through CI/CD and confirm the production startup validator passes.
- Confirm normal login, dashboard notification delivery, and polling fallback
  from `https://sauti.uk`; confirm `admin.sauti.uk/ws/...` remains unavailable.
- Start one M-Pesa sandbox STK request after deployment and confirm its signed
  callback updates only the originating request. Requests created before this
  change do not have the new callback token and should be allowed to expire.
- Run an authenticated two-workspace smoke test against agents, calls,
  bookings, integrations, and inbox resources using IDs from the other
  workspace; each request must return `404` or `403` without data.
- Retain the test/deployment evidence, then a platform administrator may mark
  the security review complete in the launch-readiness screen. Do not approve
  general availability until the other Phase 4 gates are also complete.

## Deferred defense-in-depth work

- Arrange an independent penetration test before expanding beyond the limited
  launch cohort.
- Review access-token revocation requirements if Sauti adds immediate user
  suspension; current signed access tokens remain valid only until their short
  expiry while refresh tokens can be revoked.
- Add provider-specific replay storage if M-Pesa introduces a stable callback
  event identifier. Payment state transitions are currently idempotent and the
  callback proof is connection-bound, but no provider event ID is available in
  the current payload contract.

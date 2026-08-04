# Sauti production launch phases

This roadmap supersedes the original June 2026 voice-pipeline plan where the
current Telnyx-managed architecture has already replaced the proposed custom
Twilio/STT/TTS pipeline.

## Phase 0 — controlled acquisition (accepted 2026-08-04)

- Public workspace registration is closed.
- Prospects request a tailored demo without creating paid resources.
- The homepage offers an isolated, one-minute Sauti-only voice demo with
  enforced quotas and no tenant tools or customer data.
- The production browser demo has passed live acceptance.

## Phase 1 — controlled pilot onboarding (in progress)

Goal: approve selected demo leads and activate pilot workspaces without
reopening public registration or provisioning paid resources automatically.

### Slice 1: private workspace invitations

- An operator can issue an invitation for a stored demo request through an
  operator-key-protected endpoint.
- Invitation tokens are random, delivered in a URL fragment so they do not
  enter server access logs, stored only as SHA-256 hashes, expire after 72
  hours, and are accepted once under a database lock.
- The invitation fixes the workspace business name, email, and country to the
  reviewed demo request. The prospect chooses only a password and still
  verifies the invited email before login.
- Public registration and unknown Google-account creation remain disabled.
- Activating an invitation creates only the Sauti tenant/user records. It does
  not buy a phone number, start a call, or create another paid provider
  resource.

Operator endpoint:

```text
POST /api/v1/operator/demo-requests/{requestId}/invitation
X-Sauti-Operator-Key: <server-configured secret>
```

Production requires a strong `SAUTI_OPERATOR_API_KEY` GitHub Actions secret.
If the secret is absent, operator endpoints fail closed with `401`.

### Remaining Phase 1 slices

1. Complete the new Sauti Admin console with audit views. Platform totals,
   the demo-request approval queue, searchable read-only workspace/customer
   directories, platform time-series, provider cost evidence, unpriced usage,
   reconciliation state, and observed provider health are now available on the
   isolated `admin.sauti.uk` origin.
2. Extend demo operations with explicit rejection, invitation delivery state,
   safe resend/revoke, assignment, notes, and an audit trail.
3. Add pilot budgets and provisioning approvals so number purchases, live
   calling, SMS, and WhatsApp cannot consume funds before operator approval.
4. Add a pilot readiness checklist covering agent setup, number ownership,
   calendar sync, messaging, test calls, and support contacts.
5. Run one invited-workspace acceptance journey in production and record the
   evidence before Phase 1 is marked complete. The equivalent admin approval,
   invitation preview, one-time activation, and database-persistence journey is
   now covered by an automated integration test.

## Later phases

- Phase 2: operational reliability, alerting, backup/restore drills, provider
  retry visibility, and incident runbooks.
- Phase 3: billing-provider lifecycle acceptance and reviewed enforcement.
- Phase 4: security/privacy review, Google verification completion, legal
  readiness, and controlled general availability.

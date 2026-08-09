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

## Phase 1 — controlled pilot onboarding (completed 2026-08-09)

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

### Completed Phase 1 administration slices

- The Sauti Admin console includes platform totals,
   the demo-request approval queue, searchable read-only workspace/customer
   directories, platform time-series, provider cost evidence, unpriced usage,
   reconciliation state, and observed provider health are now available on the
   isolated `admin.sauti.uk` origin.
- Demo operations include explicit rejection, SMTP-provider delivery state,
  token-rotating resend, revoke, assignment, internal notes, and immutable
  platform-admin audit history.
- Invited workspaces now start with a zero-budget, deny-by-default provider
  policy. Platform administrators explicitly approve phone numbers, live
  calling, SMS, and WhatsApp within a monthly ledger-backed ceiling.
- Demo requesters receive request-received, approval/invitation, and rejection
  lifecycle emails. A verified first-time workspace receives the onboarding
  welcome email once.
- Platform administrators now have an evidence-backed pilot readiness review
  for agent setup, owned phone numbers, optional calendar sync, approved
  messaging channels, completed browser test calls, and escalation contacts.
  Required checks cannot be bypassed by the final launch-approval control.

The invited-workspace production acceptance journey is complete. Phase 1 is
closed; later fixes to invitation UX or administration remain normal product
maintenance and do not reopen the phase.

## Phase 2 — operational reliability (in progress)

Goal: detect, communicate, diagnose, and recover from production failures
without relying on a customer to report them first.

### Slice 1: reliability incidents and operator alerts

- Evaluate stored provider connection and delivery evidence on a schedule
  without making billable provider health requests.
- Persist deduplicated open/resolved incidents, email support on first detection
  and recovery, and show the incident history in platform analytics.
- Keep alerting disabled by default outside production and make the recipient,
  evidence window, and polling interval configurable.

### Slice 2: off-site backup verification and guarded recovery

- Produce atomic PostgreSQL custom dumps, validate their catalogs, and attach a
  SHA-256 checksum before treating a backup as complete.
- Optionally replicate each completed dump to any Restic-supported encrypted
  off-site repository and verify the latest off-site snapshot daily once the
  repository variable is explicitly enabled.
- Restore only into an empty, explicitly named disposable database after
  comparing it with the production identity. Record non-sensitive recovery
  evidence covering required tables, Flyway history, and aggregate row counts.
- Implementation and local safety tests are complete. Live acceptance remains
  gated on configuring off-site storage and an isolated restore database, then
  recording one successful `restore_offsite` workflow run.
- Live acceptance is explicitly deferred during the limited-funds pilot. The
  dormant tooling creates no storage account or provider charge by itself and
  does not block the remaining Phase 2 reliability work.

### Slice 3: durable queue and retry visibility

- Aggregate post-call processing, integration delivery, calendar sync, custom
  webhooks, billing events, cost reconciliation, and recording reconciliation
  through a contributor-based operational queue boundary.
- Expose pending, retrying, terminally exhausted, and oldest-active-item state
  to platform administrators without exposing payloads, endpoints, customer
  fields, or credentials.
- Show queue state in Admin Analytics and make total exhausted work a top-level
  operational KPI so silent background failures are visible without database
  access.

### Slice 4: measurable pilot SLOs and automatic incidents

- Evaluate every durable queue against a five-minute warning and thirty-minute
  critical oldest-item target. Any terminally exhausted work is critical
  immediately, regardless of age.
- Evaluate completed production call failure rate and stored non-zero
  LLM-plus-TTS response time over a rolling fifteen-minute window. Browser test
  calls are excluded, and
  the targets stay `insufficient_data` until at least five production calls or
  ten production turns exist, preventing a single pilot call from raising a
  platform incident.
- Open, deduplicate, notify, and resolve SLO incidents through the same durable
  reliability incident model as provider failures. Show actual values,
  thresholds, sample sufficiency, and evidence details in Admin Analytics.
- Do not infer call-start-to-first-audible-audio from turn timings. Browser
  measurement is added in Slice 5; phone measurement remains provider-gated.

### Slice 5: measured browser first audio

- Persist Telnyx's provider-measured browser greeting latency once per session
  for authenticated agent tests, the public Sauti demo, and customer Web Voice.
  Session credentials or tenant ownership protect every ingestion endpoint;
  impossible values are rejected and repeated SDK events keep the first sample.
- Evaluate browser first-audio latency over the same rolling window as other
  voice SLOs. Require five samples, warn at three seconds, and become critical
  at seven seconds by default. Breaches use the existing incident and operator
  notification path.
- Show phone first audio as `unavailable`, not zero or healthy. Telnyx currently
  documents only conversation-ended and insights webhooks for AI Assistant
  start; `call.speak.started` belongs to the separate Speak command and cannot
  truthfully measure an AI greeting.

### Slice 6: incident runbooks and safe reliability drills

- Provide a platform-admin-only synthetic incident that exercises persistence,
  support notification, acknowledgement, recovery notification, and admin audit
  evidence without calling providers or touching tenant data and durable jobs.
- Enforce one active drill and the ordered `Detected` to `Acknowledged` to
  `Resolved` lifecycle. The operator cannot acknowledge before detection email
  delivery is recorded or resolve before acknowledgement; scheduled monitoring
  cannot auto-resolve an operator-controlled drill.
- Publish the signal-specific triage, containment, escalation, and drill runbook
  at `docs/runbooks/reliability-incidents.md`.
- Implementation and automated verification are complete. Live acceptance
  requires one post-deployment production drill with detection, acknowledgement,
  recovery, and audit timestamps.

### Remaining Phase 2 slices

1. Configure off-site storage and execute the documented restore drill against
   an isolated database; retain the generated evidence without customer rows or
   credentials.
2. Add phone first-audio measurement only when the provider exposes an AI
   playback event or Sauti owns the phone media stream.
3. After deployment, execute the documented production reliability drill and
   retain its database/audit evidence. No provider or customer operation is part
   of the drill.

## Later phases

- Phase 3: billing-provider lifecycle acceptance and reviewed enforcement.
- Phase 4: security/privacy review, Google verification completion, legal
  readiness, and controlled general availability.

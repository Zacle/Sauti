# Reliability incident and drill runbook

## Purpose

Use this runbook to respond to Sauti reliability incidents and to rehearse the
response path without disrupting customers. Platform administrators perform the
steps from `https://admin.sauti.uk/admin/analytics`. Support receives detection
and recovery email at the configured `SAUTI_RELIABILITY_ALERT_EMAIL` address.

Synthetic drills do not call a provider, change customer data, consume a queued
job, or place a voice call. They exercise Sauti's database, email, admin UI, and
audit trail only.

## Severity and first response

| Signal | Warning | Critical | First action |
| --- | --- | --- | --- |
| Provider health | Retrying or degraded stored evidence | Failed delivery or connection attention | Open Provider health and identify the affected integration |
| Durable queue | Oldest active item at least 5 minutes | Oldest item at least 30 minutes or any exhausted item | Record the queue, counts, and oldest-item age before changing anything |
| Production calls | Failure rate at least 10% | Failure rate at least 25% | Confirm the sample size and separate provider failures from busy/no-answer outcomes |
| Agent response | Average LLM + TTS at least 2.5 seconds | Average at least 5 seconds | Compare provider health and recent call evidence; do not infer first audio |
| Browser first audio | Average greeting at least 3 seconds | Average at least 7 seconds | Reproduce once on the homepage and once in an authenticated agent test |

An `insufficient_data` target is not healthy or unhealthy; it means the minimum
sample has not been reached. Phone first audio is `unavailable` until the phone
provider exposes an audible-playback event or Sauti owns the phone media path.

## Incident response

1. Open Admin Analytics and confirm the incident key, severity, first-detected
   time, sample size, and supporting queue/provider/SLO evidence.
2. Acknowledge ownership outside customer-facing channels. During the pilot,
   the support mailbox owner is the incident lead unless another owner is
   explicitly assigned.
3. Check `/health` and the latest GitHub Actions deployment before assuming the
   provider is at fault. Do not manually deploy, restart production containers,
   alter credentials, or replay mutating jobs from this runbook.
4. Contain impact using an already-approved reversible control. Examples include
   disabling an affected agent channel in the dashboard or asking a customer to
   use a known-good channel. Record the action and time.
5. Verify recovery from stored evidence. The monitor resolves ordinary incidents
   only after the breached signal disappears. Confirm the recovery email and
   retain the incident and admin-audit records.
6. Escalate if the incident is still critical after 15 minutes, repeats twice in
   24 hours, affects tenant isolation, risks data loss, or involves payments or
   credentials. Preserve diagnostics without copying transcripts, phone numbers,
   tokens, webhook payloads, or secrets into tickets.

## Safe production drill

Run this only after the reviewed revision has passed CI and deployed through the
normal GitHub Actions chain.

1. Sign in to `admin.sauti.uk` with a platform-admin allowlisted email.
2. Open **Analytics & provider health**, then **Reliability drill**.
3. Select **Start synthetic drill** and accept the confirmation. Only one active
   drill is allowed.
4. Confirm all detection evidence:
   - the drill shows `Detected`;
   - a critical `drill:<UUID>` incident appears;
   - the support mailbox receives the detection email;
   - the UI records **Detection email** instead of `Pending`;
   - Admin Audit contains `reliability.drill.started`.
5. Select **Acknowledge alert**. The action remains disabled until notification
   delivery is recorded. Confirm the operator and timestamp and the
   `reliability.drill.acknowledged` audit event.
6. Select **Resolve and send recovery**. Confirm:
   - the drill and incident show `Resolved`;
   - the support mailbox receives the recovery email;
   - the UI records **Recovery email**;
   - Admin Audit contains `reliability.drill.resolved`.
7. Record the deployed commit, drill UUID, detection/acknowledgement/recovery
   timestamps, and pass/fail result in the release evidence. The database and
   audit entries are the source evidence; screenshots are optional.

## If the drill does not complete

- **Detection email stays pending:** verify Resend/SMTP connectivity and
  `SAUTI_RELIABILITY_ALERT_EMAIL`. Do not acknowledge until delivery is stored.
- **Alert email arrives but UI stays pending:** inspect backend mail-listener logs
  for the drill UUID; do not start a second drill.
- **Acknowledgement is rejected:** refresh Admin Analytics. Another operator may
  already have transitioned the drill.
- **Recovery email stays pending:** keep the resolved record, verify email
  connectivity, and document the partial drill. Do not reopen or edit the
  incident directly in PostgreSQL.
- **Unexpected customer/provider activity:** stop the drill procedure and treat
  it as a real critical incident. The synthetic implementation itself performs
  no provider or tenant operation.

## Acceptance record

Phase 2 operational-drill acceptance is complete only after one production
drill has all three timestamps (detection email, acknowledgement, recovery
email) and the three corresponding admin-audit events. Automated tests prove the
state machine but do not replace this live email-delivery acceptance.

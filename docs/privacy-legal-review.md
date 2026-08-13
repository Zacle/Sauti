# Phase 4 privacy and legal product-control review

Review date: 2026-08-12

This document records the product behavior that can be verified in code. It is
not legal advice, does not select Sauti's launch jurisdictions, and does not
automatically satisfy the human privacy/legal attestation in the platform
launch-readiness gate.

## Implemented controls

- Each workspace controls identifiable conversation-content retention using a
  bounded 30, 90, 180, or 365-day setting. The default is 90 days.
- Each workspace controls recording retention using a bounded 7, 30, or 90-day
  setting. The default is 30 days and cannot exceed conversation retention.
- The daily tenant-scoped retention job redacts caller phone numbers,
  transcript turns, call summaries, sentiment, intent, archived conversation
  state, failure detail, and transfer detail after the conversation period.
- Aggregate operational fields such as call timestamps, duration, outcome,
  language, interruption, and latency may remain for service measurement.
- Local browser recordings are deleted from Sauti storage. Telnyx-hosted
  recordings are deleted with the provider recording API. A failed provider
  deletion remains unmarked and is retried by a later run.
- An agent with **Save transcript** disabled may use transient live state to
  conduct the call, but durable transcript turns and derived conversation
  content are cleared at call completion. The transient session is deleted
  only after the database transaction commits.
- If any agent records calls, saving retention settings requires a current
  acknowledgement that the workspace is responsible for applicable AI and
  recording notices and consent.
- The public Privacy Policy discloses the available periods, retained aggregate
  fields, external-system boundaries, and verified-rights request channel.
- The public Terms describe workspace consent duties, acceptable use, billing
  cancellation timing, end-of-access behavior, pending plan changes, add-on
  separation, and the refund baseline.

## Deletion boundaries

The conversation-retention job does not automatically delete bookings,
customer records, billing evidence, audit records, legal holds, backups, or
records already written to Google Calendar, Google Sheets, a CRM, or another
provider. Those records have separate product, provider, contractual, or legal
lifecycles. The Settings screen and Privacy Policy state this boundary so a
workspace is not led to believe that one retention setting deletes every copy.

Full access, export, restriction, correction, or workspace-deletion requests
are routed to `support@sauti.uk` for identity and authority verification. A
separate reviewed operational procedure is still required before general
availability to define responder roles, verification evidence, provider
follow-up, backup treatment, legal holds, and completion deadlines.

## Required qualified review before launch attestation

Qualified counsel or an appropriately authorized privacy professional must
review the actual initial launch countries and customer verticals and record at
least:

1. controller/processor roles and the lawful bases used for workspace,
   prospect, caller, employee, and website analytics data;
2. required AI, automated-call, transcription, and recording notices and the
   consent standard for inbound, outbound, browser, SMS, and WhatsApp channels;
3. marketing consent, opt-out, do-not-call, quiet-hour, and suppression rules;
4. required processor agreements, international-transfer safeguards, privacy
   contacts, breach notification, and data-subject response deadlines;
5. whether the available retention periods and the separate booking, audit,
   billing, provider, and backup lifecycles are appropriate;
6. cancellation, renewal, tax, refund, consumer-right, and pricing disclosures;
7. prohibited or restricted verticals and any additional health, financial,
   employment, children, biometric, or other sensitive-data obligations; and
8. the governing law, dispute process, business identity, and any mandatory
   company-registration disclosures that must appear in the Terms.

Any required policy or product change must be implemented and re-verified
before an administrator checks the privacy/legal attestation. The attestation
must identify the reviewer, jurisdictions, reviewed policy version, date, and
location of non-sensitive evidence; it must never contain customer data or
legal privileged material that should not be stored in the application.

## Operational acceptance after deployment

1. In one test workspace, save each allowed period and confirm invalid periods
   and recording periods longer than conversation retention are rejected.
2. Enable recording on one agent and confirm retention cannot be saved without
   the notice/consent acknowledgement.
3. Create non-customer test calls with expired timestamps and verify the job
   redacts content only inside that workspace while preserving aggregate
   metrics.
4. Verify local and Telnyx test recordings are removed, and simulate one
   provider failure to prove it remains retryable rather than appearing
   deleted.
5. Complete a call with **Save transcript** disabled and verify no transcript,
   summary, sentiment, intent, or archived conversation is visible afterward.
6. Confirm the effective Privacy Policy and Terms are reachable from Settings
   and the public website.

Retain identifiers and timestamps for purpose-built test records only. Do not
copy real caller content, provider credentials, access tokens, or legal advice
into the launch-readiness evidence field.

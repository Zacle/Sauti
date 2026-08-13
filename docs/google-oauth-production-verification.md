# Phase 4 Google OAuth production verification

Review date: 2026-08-13

This is the engineering and submission checklist for Sauti's production Google
authorization flows. It contains no credentials and does not mark the platform
launch-readiness attestation complete. That attestation is checked only after
Google accepts the production branding/data-access submission and the retained
production evidence has been reviewed by a platform administrator.

## Implemented application contract

| User-facing feature | Exact scopes requested | Production callback |
| --- | --- | --- |
| Sign in with Google | `openid email profile` | `https://sauti.uk/api/v1/auth/oauth/google/callback` |
| Google Calendar | `https://www.googleapis.com/auth/calendar.events` and `https://www.googleapis.com/auth/calendar.freebusy` | `https://sauti.uk/api/v1/integrations/google-calendar/callback` |
| Google Sheets | `https://www.googleapis.com/auth/spreadsheets` | `https://sauti.uk/api/v1/integrations/google_sheets/callback` |

Calendar and Sheets authorization is contextual: neither integration scope is
requested during ordinary Google sign-in. The callback now refuses a partial
grant before any OAuth token or enabled binding is persisted.

The Calendar scopes are narrow for Sauti's implemented behavior:

- `calendar.freebusy` returns busy ranges without requiring event titles or
  descriptions and is used when finding availability;
- `calendar.events` is required to create, reschedule, and cancel the selected
  Sauti-created booking event. Read-only or free/busy access cannot perform
  those mutations.

The Sheets integration works with an existing spreadsheet ID supplied by the
workspace and can initialize tabs, read configured ranges, append call/customer
rows, and perform caller-confirmed updates. `spreadsheets` is therefore the
narrowest currently compatible Sheets scope. `spreadsheets.readonly` cannot
write. `drive.file` would require Sauti to introduce a Google Picker or create
the file through Sauti before per-file access applies; that is not the current
product flow. Sauti requests no full Google Drive or Google Photos scope.

Google currently classifies `spreadsheets` as sensitive and requires public
apps using sensitive scopes to complete data-access verification. The Google
Cloud Console remains authoritative for the classification shown at submission
time.

## Product controls verified in code

- OAuth client secrets remain server-only and workspace/provider tokens are
  encrypted at rest.
- OAuth state is signed, tenant- and agent-bound, non-predictable, and expires
  after ten minutes.
- Tokens are stored only after required-scope validation and a live Calendar
  or configured Sheets probe.
- API responses expose connection status and configuration, never decrypted
  credentials.
- Every Calendar/Sheets runtime lookup is tenant- and agent-scoped.
- The integration screen displays an in-product disclosure before redirecting
  to Google. It explains the data accessed, purpose, encryption, training
  prohibition, disconnection, and Privacy Policy/Terms links.
- Disconnecting immediately disables all affected agent tools and permanently
  deletes Sauti's encrypted token copy. Google revocation is intentionally
  project-aware: revoking one token invalidates all scopes granted to every
  client in the same Google Cloud project. Automatic revocation is therefore
  disabled by default and the UI directs the owner to Google Account
  permissions for complete project-wide revocation.

## Required Google Cloud project layout

Google requires separate projects for development/testing and production.
Sauti should additionally use isolated production projects for these three
authorization surfaces:

1. **Sauti Sign-in** — `openid email profile` only.
2. **Sauti Calendar** — Calendar API and the two Calendar scopes only.
3. **Sauti Sheets** — Sheets API and `spreadsheets` only.

This separation prevents an integration disconnect from revoking sign-in or a
different integration for the same Google user. It also gives each sensitive
scope submission a precise reviewer story. Configure the Calendar-specific
client with `GOOGLE_CALENDAR_OAUTH_CLIENT_ID` and
`GOOGLE_CALENDAR_OAUTH_CLIENT_SECRET`; the old sign-in credentials remain a
backward-compatible fallback only. Sheets already has separate environment
variables.

After the three production projects and clients are confirmed isolated, set
`GOOGLE_REVOKE_ON_DISCONNECT=true`. Until then, keep it `false` and rely on
local token deletion plus the user's Google Account permissions page. Never
enable the flag merely to make a verification recording pass.

Each production project must use:

- app name: `Sauti`;
- homepage: `https://sauti.uk/`;
- Privacy Policy: `https://sauti.uk/privacy`;
- Terms: `https://sauti.uk/terms`;
- user support email: `support@sauti.uk`;
- authorized domain: `sauti.uk`;
- current developer contacts controlled by the maintainers;
- only the exact HTTPS redirect URI belonging to that project/client.

Verify `sauti.uk` in Google Search Console using an account that is an Owner or
Editor of each production Cloud project. Do not put localhost, preview hosts,
wildcards, or tunnel URLs in production clients. Keep separate non-production
projects and clients for development and staging.

## Copy-ready scope justifications

### Calendar events

Sauti is an AI voice-agent platform that lets a business synchronize caller-
confirmed appointments with a Google calendar selected by the workspace owner.
It uses `calendar.events` only to create confirmed Sauti booking events and to
update or delete those Sauti-created events when a caller reschedules or
cancels. A read-only or free/busy scope cannot provide this user-facing event
management feature. Sauti does not manage calendar sharing, ACLs, calendars, or
unrelated Google events.

### Calendar free/busy

Before offering an appointment, Sauti uses `calendar.freebusy` to retrieve only
busy time ranges for the calendar selected by the workspace owner. It combines
those ranges with Sauti business hours and tenant-scoped bookings to avoid a
conflict. This narrower scope prevents Sauti from reading event titles,
descriptions, attendees, or unrelated event content for availability checks.

### Google Sheets

Sauti uses `spreadsheets` only after a workspace owner connects Google Sheets,
provides an existing spreadsheet ID, configures ranges, and enables the tool on
an agent. The integration initializes missing Customers and Calls tabs, reads
configured customer ranges, appends configured post-call/customer fields, and
performs caller-confirmed row updates. `spreadsheets.readonly` cannot perform
the required writes. The current product does not create the spreadsheet or
use Google Picker, so `drive.file` would not authorize an arbitrary existing
spreadsheet pasted by its owner. No Google Drive or Photos scope is requested.

## Submission sequence

1. Create or select the three isolated production projects and enable only the
   APIs required by each.
2. Configure Branding with the exact Sauti identity, verified domain, public
   homepage, Privacy Policy, Terms, support email, and developer contacts.
3. Configure the External audience. Remove obsolete OAuth clients from the
   production projects.
4. Add the exact scopes above in Data Access. Do not add future-use scopes.
5. Create Web application clients with the exact callbacks above and install
   their IDs/secrets through production secret management.
6. Use separate test projects while validating. Once the production app is
   ready, publish its branding/audience and open the Verification Center.
7. Submit each sensitive scope justification and up to three relevant
   documentation links.
8. Upload one unlisted YouTube demonstration that meets the recording checklist
   below, or separate Calendar/Sheets recordings if Google requests one per
   project.
9. Monitor every project owner/editor, support, and developer-contact mailbox
   and answer reviewer questions promptly. Google states that sensitive-scope
   review can take up to ten days; schedule launch accordingly.
10. After approval, retain the approval email/status, project IDs, exact scope
    list, video URL, and acceptance date without storing client secrets or test
    passwords in the repository or launch-readiness note.

## End-to-end demonstration recording

Use an English Google UI and only synthetic test records. Keep the browser
address bar visible on every Google consent screen so the reviewer can see the
OAuth client ID. Never expose credentials, tokens, real calls, or customer data.

1. Start at `https://sauti.uk/`, show the product description, footer Privacy
   Policy and Terms links, and the same app name/logo used in Cloud Branding.
2. Sign in to a dedicated reviewer workspace and open one saved appointment
   agent's **Integrations** screen.
3. Select Google Calendar. Show Sauti's pre-authorization disclosure, continue
   to Google, set the consent language to English, expand the full permissions,
   and keep both exact Calendar scopes readable.
4. Return to Sauti, select the synthetic calendar, run **Test live connection**,
   check an available and a busy slot, create one confirmed booking, reschedule
   it, cancel it, and show the corresponding Google event lifecycle.
5. Disconnect Calendar and show that Sauti marks it disconnected. If isolated
   project revocation is enabled, show successful reconnection is required. If
   it is not enabled, show the Google Account permissions removal path and state
   that local encrypted tokens were deleted immediately.
6. Repeat from the Google Sheets card. Show the Sauti disclosure and complete
   consent screen with only `spreadsheets`.
7. Enter the synthetic spreadsheet ID, use **Create tabs and headers**, show
   Customers and Calls, run the live connection test, perform one synthetic
   lookup, and perform one confirmed update/append. Explain that Sauti remains
   the system of record for calls and bookings.
8. Disconnect Sheets and show the disabled agent tool/status.
9. End on the Privacy Policy's Google data section. State only the data-path and
   AI-provider facts confirmed for the deployed environment. Google Workspace
   data must not be used to train or improve generalized models; verify every
   enabled AI provider's contract/account setting before recording that claim.

Reviewer credentials must be delivered only through Google's approved private
submission channel. The account must not require phone verification, payment,
or an invitation step that the reviewer cannot complete.

## External acceptance still required

The engineering slice is complete when automated tests pass. The platform
Google verification attestation remains unchecked until:

- the three production project/client boundaries and domain ownership are
  confirmed;
- the deployed flows pass the synthetic Calendar and Sheets journey;
- AI provider retention/training settings are confirmed for the exact deployed
  voice/model path;
- Google accepts branding and every sensitive scope in the production
  Verification Center; and
- a platform administrator records non-sensitive evidence in
  `/admin/launch-readiness`.

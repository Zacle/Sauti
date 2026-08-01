# Google Calendar OAuth verification

This is the submission checklist for the production Sauti Google Calendar
integration. It contains no client secret or other credential.

## Current application contract

- Production app name: `Sauti`
- Homepage: `https://sauti.uk/`
- Privacy policy: `https://sauti.uk/privacy`
- Terms: `https://sauti.uk/terms`
- Authorized domain: `sauti.uk`
- Calendar callback:
  `https://sauti.uk/api/v1/integrations/google-calendar/callback`
- Google sign-in callback used by the same production web client:
  `https://sauti.uk/api/v1/auth/oauth/google/callback`
- Calendar scopes requested by the application:
  - `https://www.googleapis.com/auth/calendar.events`
  - `https://www.googleapis.com/auth/calendar.freebusy`

Do not add Google Drive, full Calendar, Google Sheets, or future-use scopes to
the Calendar authorization request. Google Sheets uses a separate, contextual
Sauti authorization flow documented in
[`google-sheets-oauth-verification.md`](google-sheets-oauth-verification.md).

Sauti is the booking system of record. Confirmed creates, reschedules, detail
updates, and cancellations are committed to Sauti before Google Calendar is
updated by the durable background synchronizer. Direct edits made to the Google
event are intentionally not imported into the Sauti booking. Each generated
event tells the workspace owner to manage the booking in Sauti.

Agents connected to the same Google Calendar credential share one capacity-one
availability scope even though each agent retains exclusive ownership of the
bookings it creates. Pending Sauti bookings block that shared scope before the
Google event is written, and the final database insert locks and rechecks the
scope to prevent simultaneous double booking.

## Google Cloud configuration

Use the Google Cloud project and OAuth web client whose client ID and secret are
already installed in the production Sauti environment. A different client would
not demonstrate the production application.

1. Enable the Google Calendar API.
2. Configure Google Auth Platform branding:
   - app name `Sauti`;
   - an owner-controlled support email;
   - the Sauti homepage, privacy policy, and terms URLs above;
   - authorized domain `sauti.uk`;
   - current developer contact addresses.
3. Set the audience to `External`.
4. In Data Access, declare exactly the two Calendar scopes above, plus only the
   basic identity scopes already used by Google sign-in when the same project
   contains that client.
5. In Clients, use a `Web application` OAuth client and register both exact
   redirect URIs above. Do not add wildcards, localhost, preview domains, or
   temporary tunnels to the production client.
6. While the app is in testing, add only the accounts required to verify and
   record the flow.
7. Verify ownership of `sauti.uk` in Google Search Console with an account that
   is also an Owner or Editor of the Google Cloud project.
8. When the live flow passes, publish the audience to production and prepare
   the sensitive-scope verification submission.

The Google Cloud Console is authoritative for scope classification. If either
scope is unexpectedly classified as restricted, stop before submitting and
reassess it; restricted scopes can require an independent security assessment.

## Scope justification text

### `calendar.events`

Sauti is an AI voice-agent platform used by a workspace owner to manage
appointments requested by callers. After the owner explicitly connects Google
Calendar to an appointment-enabled Sauti agent, Sauti uses this scope only to
create confirmed appointment events in the calendar selected by the owner and
to update or delete those same Sauti-created events when the caller reschedules
or cancels. Sauti does not manage calendar sharing, calendars, or unrelated
events. A read-only or free/busy scope cannot create, reschedule, or cancel the
appointment events required by this user-facing feature.

### `calendar.freebusy`

Before offering or confirming an appointment, Sauti queries only busy time
ranges for the calendar selected by the workspace owner. It uses those ranges
with the agent's configured opening hours and existing Sauti bookings to avoid
double booking. This endpoint does not require Sauti to read event titles,
attendees, descriptions, or other event content. A narrower non-Calendar scope
cannot provide the live availability required by this feature.

## Verification video

Record one continuous video using the production application and an English
Google consent screen. Do not reveal client secrets, access tokens, server
environment values, or unrelated calendar data.

1. Show `https://sauti.uk/`, the Sauti product name, privacy policy, and terms.
2. Sign in to a test workspace and open an appointment-enabled agent.
3. Open `/dashboard/integrations` and select that agent.
4. Click `Connect Google Calendar`.
5. Show the entire English Google consent screen, including the exact requested
   Calendar permissions, and approve it with the test account.
6. Return to Sauti, show the connected state, select the test calendar, and run
   `Test live connection`.
7. Make a browser test call that asks for an available time and confirms a
   booking.
8. Show the resulting Sauti booking, wait for its Calendar status to become
   synced, and show the corresponding event in the selected Google Calendar.
9. Reschedule the booking in Sauti, wait for synchronization, and show the same
   Google event move without blocking the caller on the Google write.
10. Cancel it in Sauti, wait for synchronization, and show the event removed.
11. Reconnect if needed for the recording, then demonstrate `Disconnect` and
    show that the Sauti connection and agent enablement are removed.

Upload the video as an unlisted link accessible to Google's reviewers. The app
name, domain, branding, OAuth client, consent scopes, and production behavior
shown in the video must match the submitted project.

## Pre-submission evidence

Keep the following internal evidence with the submission:

- screenshot of the production OAuth client redirect URIs;
- screenshot of the two declared Calendar scopes;
- Search Console ownership for `sauti.uk`;
- successful `Test live connection` result;
- booking ID and Google event ID from the demo;
- evidence that reschedule and cancellation affected that same event;
- the unlisted demo video URL;
- date and Google account used for the test.

Do not submit until the production OAuth flow and all three event operations
(create, reschedule, cancel) pass against a dedicated verification calendar.

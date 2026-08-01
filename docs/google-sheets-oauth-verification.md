# Google Sheets OAuth verification

This is the production setup and submission checklist for Sauti's Google Sheets
integration. It contains no OAuth client secret, token, spreadsheet content, or
other credential.

## Production application contract

- Application name: `Sauti`
- Homepage: `https://sauti.uk/`
- Privacy policy: `https://sauti.uk/privacy`
- Terms: `https://sauti.uk/terms`
- Authorized domain: `sauti.uk`
- OAuth callback:
  `https://sauti.uk/api/v1/integrations/google_sheets/callback`
- Scope requested:
  `https://www.googleapis.com/auth/spreadsheets`

The Sheets authorization flow requests only the Sheets scope. It does not add
Google Drive or Calendar access. Sauti requests offline access because enabled
agents can perform approved lookups during calls and durable post-call appends
when the workspace owner is not present.

OAuth access and refresh tokens are encrypted at rest. Connections belong to a
workspace; each agent must be enabled separately and receives its own configured
spreadsheet ranges. API responses expose connection metadata and test status,
not tokens.

## Google Cloud configuration

1. Enable the Google Sheets API in the same production Google Cloud project
   used for the Sauti verification submission.
2. Configure Google Auth Platform branding with the Sauti name, homepage,
   privacy policy, terms, support email, developer contacts, and authorized
   domain above.
3. Set the audience to `External`.
4. Add exactly the `spreadsheets` scope for this feature. Do not add Google
   Drive access merely to read a Sheet URL supplied by the owner.
5. Use a Web application OAuth client and add the exact callback above. The
   same production OAuth client may be used for Calendar and Sheets when all
   exact callbacks are registered; set `GOOGLE_SHEETS_OAUTH_CLIENT_ID` and
   `GOOGLE_SHEETS_OAUTH_CLIENT_SECRET` to that production client's values.
6. While publishing status is `Testing`, add the recording account as a test
   user. Google refresh tokens for an external app in Testing commonly expire
   after seven days, so publish before relying on unattended production access.
7. Verify `sauti.uk` ownership in Search Console using a project Owner or Editor.

Production must contain:

```text
GOOGLE_SHEETS_OAUTH_CLIENT_ID=<production web client id>
GOOGLE_SHEETS_OAUTH_CLIENT_SECRET=<production web client secret>
GOOGLE_SHEETS_REDIRECT_URI=https://sauti.uk/api/v1/integrations/google_sheets/callback
```

Keep the values only in `/opt/sauti/.env.production` or the approved secret
store. Never put them in the repository or a verification video.

## Scope justification

Sauti is an AI voice-agent platform. A workspace owner explicitly connects a
Google account, selects an agent, and configures the exact spreadsheet ID and
ranges that agent may use. During a call, the agent can look up a row by a value
in the owner-configured column and receives only the configured return columns.
The agent may replace a matching configured row only after explicit caller
confirmation. When the call creates a confirmed Sauti booking, that same
durable delivery also upserts the booking customer by normalized phone: a
missing customer is appended, while an existing row receives only missing name
or email values. After a call is analysed, Sauti appends the owner-selected call
fields to a separate configured log range.

Read-only access is insufficient because the user-facing integration includes
confirmed row updates and owner-enabled post-call appends. Google Drive access
is unnecessary because the owner supplies the spreadsheet ID directly and
Sauti uses only the Sheets values API.

## Prepare a safe demonstration spreadsheet

Create a dedicated spreadsheet containing synthetic data only:

1. Create an empty spreadsheet. Copy its ID from between `/d/` and `/edit` in
   the Google Sheets URL.
2. In Sauti enter that ID and keep the safe defaults:
   - spreadsheet ID: the ID between `/d/` and `/edit` in the Sheet URL;
   - lookup range: `Customers!A:C`;
   - lookup column: `0`;
   - customer name column: `1`;
   - customer email column: `2`;
   - return columns: `0, 1, 2`;
   - post-call append range: `Calls!A:F`;
   - append columns: `callId, startedAt, callerPhone, outcome, summary, sentiment`.
3. Click `Create tabs and headers`. Sauti creates missing `Customers` and
   `Calls` tabs and initializes these headers only when row 1 is empty:
   - `Customers`: `Phone`, `Name`, `Email`;
   - `Calls`: `Call ID`, `Started At`, `Caller Phone`, `Outcome`, `Summary`,
     `Sentiment`.
   Existing tabs and non-empty header rows are preserved and never replaced.
4. Leave the `Customers` tab with headers only. The end-to-end booking test will
   prove that Sauti creates the synthetic customer automatically.
5. Click `Save and test`. Sauti must show the connection as connected before
   recording the review video.

The call ID is always included in post-call writes so a delivery retry does not
append the same call twice. Customer retries match the normalized booking phone
before writing, so they do not blindly append another customer. Existing
non-empty customer name and email cells are never replaced by automatic sync.
Lookup and append ranges are separate so customer records are not mixed with
call logs.

## End-to-end verification video

Record one continuous production video in English. Do not reveal secrets,
tokens, unrelated Google files, or real customer data.

1. Show the Sauti homepage, Privacy Policy, and Terms.
2. Sign in, open `/dashboard/integrations`, and select the demonstration agent.
3. Click the Google Sheets connect action.
4. Show the complete Google consent screen, including the Sauti name and Sheets
   permission, then approve it.
5. On return to Sauti, enter the demonstration spreadsheet ID and show the
   explanation of the `Customers` and `Calls` business uses.
6. Click `Create tabs and headers`, then show both initialized tabs in Google
   Sheets. Return to Sauti, click `Save and test`, and show connected status.
7. Run a browser test call that creates a confirmed booking for a synthetic
   caller and phone number.
8. End the call, wait for post-call analysis/delivery, and show exactly one new
   customer row in `Customers` and exactly one new call row in `Calls`.
9. Run a lookup for that synthetic phone and show that the agent returns only
   the configured fields. Ask to update the row, show the agent's explicit
   confirmation request, confirm it, and show the changed row in Google Sheets.
10. Show Sauti's latest successful Google Sheets delivery status.
11. Disconnect Google Sheets and show that the selected agent is disabled from
    using its Sheets tools.

Upload the recording as an unlisted link accessible to Google reviewers. The
domain, branding, OAuth client, consent scope, callback, and production behavior
shown must match the submitted project.

## Submission evidence

Keep these items with the verification submission:

- screenshot of the production OAuth client's exact redirect URIs;
- screenshot of the enabled Google Sheets API and declared scope;
- Search Console domain ownership;
- successful Sauti live-test timestamp;
- synthetic spreadsheet ID and ranges used in the demo;
- one automatic confirmed-booking customer upsert, successful lookup,
  confirmed update, and post-call delivery record;
- the unlisted video URL and recording account;
- confirmation that the app audience is published rather than left in Testing.

Do not submit until OAuth reconnect, token refresh, automatic customer upsert,
lookup, confirmed update, post-call append, duplicate-delivery protection, and
disconnect all pass against the dedicated production test spreadsheet.

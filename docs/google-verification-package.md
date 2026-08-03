# Google API verification package

Prepared: 2026-08-03

This document is a reviewer-facing draft based on the repository implementation. It is not a substitute for confirming the enabled scopes, provider contracts, account tier, retention settings, and test credentials in the deployed staging environment.

## Scope inventory

The application has three separate Google OAuth flows:

| Feature | OAuth scopes requested by the code | Data use |
| --- | --- | --- |
| Google sign-in | `openid email profile` | Authenticate a Sauti user and read the basic verified profile. No Workspace or Photos data is accessed. |
| Google Calendar | `https://www.googleapis.com/auth/calendar.events` and `https://www.googleapis.com/auth/calendar.freebusy` | Read free/busy availability and create/update/cancel events for the calendar explicitly selected by the workspace owner. |
| Google Sheets | `https://www.googleapis.com/auth/spreadsheets` | Read configured ranges and update configured rows for the enabled Google Sheets agent tool. |

The Calendar and Sheets scopes are requested only from their respective integration actions; they are not included in the basic sign-in flow. No Google Drive or Google Photos scope was found in the application source.

Before recording, compare this table character-for-character with the OAuth consent-screen configuration and the verification request in Google Cloud Console. The recording must show the complete expanded scope list by selecting “Show all services.”

## Google data flow

Google Calendar data is used to answer availability questions and synchronize confirmed booking changes. Google Sheets data is limited to the spreadsheet, range, columns, and row operation configured by the workspace owner. OAuth access and refresh tokens are encrypted at rest; API responses expose connection metadata rather than tokens.

The repository contains no Google Workspace or Photos model-training pipeline. However, a Google Sheets lookup result is returned to the conversation/tool layer and may become part of an AI turn. This is a material policy point: the deployed staging configuration and each AI provider’s current terms must be verified before claiming that Workspace data is never transmitted to an AI service. If that isolation cannot be demonstrated, disable the Sheets AI tool for the verification account or re-architect the flow so Workspace values are handled without generalized-model processing.

## AI and model-provider inventory

The codebase references the following providers or model services:

| Provider/service | Role observed in code | Plan/tier and training/retention setting to confirm before submission |
| --- | --- | --- |
| Google Gemini / Google GenAI | LLM fallback/default, embeddings, and post-call analysis | `[confirm project, API product, plan, data-use and retention settings]` |
| OpenAI | Advanced LLM turns and speech transcription fallback | `[confirm API plan and zero-training/data-retention configuration]` |
| Deepgram | Speech-to-text | `[confirm plan and audio retention/training configuration]` |
| ElevenLabs | Realtime text-to-speech | `[confirm plan and data-use/retention configuration]` |
| Telnyx managed voice assistant | Live telephony media, speech recognition, synthesis, and tool webhook calls | `[confirm plan and whether any call content is used for model training]` |
| Cartesia | Optional realtime text-to-speech provider | `[confirm whether enabled in the staging account, plan, and data-use configuration]` |

No multi-model aggregator or gateway was identified in the repository. If production configuration routes through one, add the platform, every downstream model, endpoint, payload flags, zero-data-retention setting, and dashboard control here before replying.

## Reviewer test account and navigation

Replace bracketed values with a dedicated staging account. Do not send credentials in this repository or in a public video description.

```text
Application: https://sauti.uk
Environment: staging OAuth configuration / verification account
Username: [reviewer account]
Password: [send through Google’s approved secure channel]
Workspace: [workspace name]
Agent: [agent name]
```

1. Open `https://sauti.uk` and sign in with the supplied Sauti test account.
2. Open **Agents**, select `[agent name]`, then open **Integrations**.
3. Choose **Google Calendar** and select **Connect**. The browser redirects to Google’s OAuth consent screen. Expand **Show all services** and keep every requested permission readable on screen.
4. Approve the request and return to Sauti. Select the test calendar and use **Test connection**.
5. In the agent’s booking tools, ask the test agent to check availability for the prepared test date. Confirm that the result reflects the connected calendar.
6. Ask the agent to book a prepared test slot. Confirm the booking only after the agent reads back the details, then verify the event in the selected test calendar.
7. For Sheets, return to **Integrations**, choose **Google Sheets**, and connect the prepared test spreadsheet. Show the requested scope screen in a separate staging run if necessary.
8. Configure only the prepared `Customers` range and use **Test connection**. Demonstrate a lookup using synthetic data. Demonstrate an update only after explicit caller confirmation.
9. Disconnect the integration and show that the connection status changes to disconnected.

The test account must have no phone verification, payment, organization approval, or other authentication blocker. Use synthetic calendar and spreadsheet records only.

## Demonstration video script

1. Start with the Sauti staging URL and state that this is a test environment. Do not show secrets, real customer records, or production traffic.
2. Show Google Cloud Console’s OAuth configuration and the exact scopes submitted for verification.
3. Start the Google Calendar connection from the Sauti UI. On the consent screen, click **Show all services** and pause long enough for both full Calendar scopes to be read.
4. Complete the connection, select the calendar, test availability, and create one confirmed synthetic booking.
5. Start the Google Sheets connection in a separate run. Expand the consent screen and pause on the complete `spreadsheets` permission.
6. Return to Sauti, show the configured range, perform a synthetic lookup, and perform a confirmed row update.
7. Show the integration status and disconnect action.
8. End with the AI data-handling statement: Workspace data is used only for the enabled calendar or spreadsheet feature; it is not sold or used to train generalized AI models. Only say this after the provider contracts and staging data path have been verified and the statement is technically true.

## Reply template to Google

Hello Google Verification Team,

Thank you for the feedback. We prepared a new staging demonstration that shows the OAuth consent flow with all requested scopes expanded and readable.

The scopes in the application and Google Cloud Console are:

- Sign-in: `openid email profile`
- Google Calendar: `https://www.googleapis.com/auth/calendar.events`, `https://www.googleapis.com/auth/calendar.freebusy`
- Google Sheets: `https://www.googleapis.com/auth/spreadsheets`

No Google Drive or Google Photos scopes are requested.

Demo video: [secure video link]

Test credentials and navigation instructions: [secure credential delivery / instructions link]

Third-party AI providers and configurations:

- [provider, plan/tier, endpoint, model, retention/training configuration]
- [provider, plan/tier, endpoint, model, retention/training configuration]

We do not use Google Workspace or Photos user data to train, improve, or sell generalized AI models. [Add the precise, verified description of the technical isolation used for Google Calendar and Google Sheets data.] We do not use an aggregator or multi-model gateway. [If this is not true, replace this sentence with the complete platform and downstream-model inventory.]

The application remains in production publishing status. New verification testing is isolated to the staging configuration and does not expose unverified scopes to production users.

Regards,
[Name]
[Company]

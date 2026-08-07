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

Sauti does not train, fine-tune, or improve a generalized AI model using Google user data. The live voice agent is a Telnyx AI Assistant managed through the Telnyx API. Sauti supplies the agent instructions and routes authorized tool calls back to Sauti; Telnyx supplies the hosted AI conversation runtime.

| Provider/service | Role in the verification flow | Disclosure required |
| --- | --- | --- |
| Telnyx AI Assistant | Primary live voice agent, including hosted conversation runtime, voice, and managed telephony | Telnyx plan/tier; configured model `moonshotai/Kimi-K2.6`; confirm Telnyx model-improvement opt-out is enabled for the account. |
| Deepgram through Telnyx | Transcription model configured on the managed assistant as `deepgram/nova-3` | Confirm this is the exact Telnyx transcription route for the verification assistant and disclose Telnyx's applicable retention/training controls. |
| Google Gemini / Google GenAI | Separate optional Sauti LLM, embeddings, and post-call-analysis adapters present in the codebase | State whether disabled in the verification environment. If enabled, disclose the exact model, API plan, and data-use setting. |
| OpenAI | Separate optional Sauti LLM and transcription fallback adapters present in the codebase | State whether disabled in the verification environment. If enabled, disclose the exact model, API plan, and data-use setting. |
| ElevenLabs and Cartesia | Optional separate Sauti text-to-speech adapters | State whether disabled in the verification environment. If enabled, disclose the exact plan and data-use setting. |

Telnyx is also the relevant managed AI platform/gateway for this flow. Telnyx's AI Services Addendum lists possible downstream providers including OpenAI, Anthropic, Google, Groq, Deepgram, Azure, AWS, ElevenLabs, and Minimax. The reviewer response must identify only the downstream providers and models actually used by the Telnyx account, not merely every provider listed in Telnyx's general terms. Confirm this in the Telnyx portal or with Telnyx support before submitting.

Important: Telnyx's current AI Services Terms say that model-improvement use is permitted unless the customer opts out through the Telnyx portal or by written notice. Enable and document that opt-out before telling Google that Workspace data is not used for generalized model training. The Sauti provisioner currently sets Telnyx assistant recording retention to enabled; that is a storage setting and is separate from model training.

Reference: [Telnyx AI Services Terms](https://telnyx.com/legal/ai-services-terms).

## What Google's AI specification means

Google is not asking whether Sauti uses AI in general. Google is asking whether data obtained through Google Workspace or Google Photos is sent to an AI provider that may use that data to train or improve a general-purpose model.

For this review, answer four separate questions:

1. Which AI providers can receive data in the application? List every provider, the plan or tier, the endpoint or product, and the model used.
2. Can Google Calendar or Google Sheets values enter an AI request? In the current implementation, a Google Sheets lookup result can enter the conversation/tool result used by an agent turn. Do not describe this as complete isolation unless the deployed configuration has been changed or verified.
3. Do those providers use API customer data to train generalized models? Confirm this from the current provider contract and account settings. Do not infer it from marketing language.
4. If any provider does train on the data, what technical boundary prevents Workspace data from reaching it? If there is no auditable boundary, disable the affected Workspace feature for the verification account or change the architecture before submitting.

The important distinction is: “Sauti does not train its own model on Google data” is not enough. The reviewer also needs to know that Telnyx is the hosted AI provider, which Telnyx downstream models are used, and that the Telnyx model-improvement opt-out is enabled for the account.

## Manual reviewer setup: create an agent first

The reviewer account starts with an empty workspace, so the instructions must not assume that an agent already exists. Use the following exact sequence with the supplied test account:

1. Open `https://sauti.uk` and sign in with the supplied Sauti test credentials.
2. Open **Agents** in the left navigation.
3. Select **Create agent**. The page title is **How should your agent help callers?**.
4. Select the **Appointment Booker** template. This supplies a simple booking scenario for the Calendar demonstration. The reviewer may instead select **Blank agent**, but the remaining steps assume Appointment Booker.
5. In the draft studio, open **Main** if it is not already selected. Set:
   - Agent name: `Google Verification Agent`
   - Short description: `Books a synthetic test appointment using the connected Google Calendar.`
   - Language: `English`
   - Greeting: `Hello, this is the Google Verification Agent. How may I help?`
6. Open **Speech & voice** and choose any available supported voice. A voice must be selected before a test call can be prepared.
7. Leave phone-call recording disabled and use synthetic test data only. Keep transcript saving enabled only if the reviewer needs to inspect the test conversation.
8. Click **Save draft** in the upper-right corner. The page must show a saved agent before integrations can be connected; the integrations panel explicitly requires a saved agent ID.
9. Open **Tools & integrations** from the setup navigation. Connect Google Calendar or Google Sheets from there, depending on the part of the demonstration being reviewed.
10. After connecting an integration, return to **Agents**, open `Google Verification Agent`, and use **Test** or the test-call panel to demonstrate the configured behavior.

If the reviewer wants to test both Google integrations, use the same saved agent and connect each integration one at a time. Use a synthetic calendar and a synthetic spreadsheet, and disconnect each provider after its demonstration.

## Copy-ready AI specification response

Use this wording only after replacing the bracketed provider settings with facts confirmed in the deployed staging account:

```text
AI/data-use clarification

Sauti does not train or fine-tune any generalized AI model. The live voice agent in this verification environment is a Telnyx AI Assistant:

- Telnyx AI Assistant — [Telnyx plan/tier], model `moonshotai/Kimi-K2.6`, with Telnyx model-improvement opt-out enabled for this account.
- Telnyx transcription route — `deepgram/nova-3`, subject to the Telnyx account's configured downstream-provider controls.

Sauti does not use Google Workspace or Google Photos user data to train, improve, or sell a generalized or foundational AI model. Sauti does not control or train the underlying Telnyx models; Telnyx processes the assistant input to provide the hosted AI service. The Telnyx account used for this review has model-improvement opt-out enabled, so the input and output are not used for generalized model training under the configured account setting.

Google Calendar data is used only to check availability and synchronize a confirmed booking in the calendar selected by the workspace owner. Google Sheets data is limited to the spreadsheet and range configured by the workspace owner and is used only for the enabled lookup or confirmed update action.

The Google Sheets feature can return a configured row to the Telnyx agent's tool/conversation layer. Therefore, Workspace data may be included in a Telnyx AI request only when the workspace owner has enabled that agent tool. In the verification environment, the Telnyx model-improvement opt-out is enabled for the account, and the downstream provider/model route shown above is the only AI route used for the demonstration. Telnyx assistant recording retention is configured separately and is disclosed as [retained / not retained] for this account.

OAuth tokens are encrypted at rest. Sauti does not request Google Drive or Google Photos scopes. Telnyx is the managed AI platform for the live assistant, and the downstream model configuration is disclosed above. Sauti does not use a separate multi-model aggregator or gateway outside Telnyx. The Google Sheets tool is enabled only for the synthetic verification spreadsheet.
```

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

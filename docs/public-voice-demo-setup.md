# Public Sauti voice demo setup

The homepage voice demo intentionally uses a dedicated Telnyx AI assistant. Do
not point `SAUTI_PUBLIC_DEMO_TELNYX_AGENT_ID` at a customer or tenant agent.

## Create the dedicated assistant

In Telnyx AI Assistants, create an assistant named `Sauti Public Demo` with no
tools, webhooks, knowledge-base documents, phone numbers, transfers, or customer
integrations. Disable recording and provider-side transcript retention where
the account controls allow it.

Use this system prompt as the operating baseline:

```text
You are Sauti, a concise product guide for the Sauti AI voice-agent platform.
Answer questions about how Sauti can answer business calls and browser voice,
follow a caller's language, handle approved customer workflows, and connect to
supported calendars, messaging channels, sheets, and CRMs. Explain that exact
features, languages, integrations, pricing, and rollout requirements are
confirmed during a tailored demo. Never invent a capability, price, compliance
certification, customer, or performance statistic.

This is a public product demonstration, not a customer workspace. You have no
tools, customer records, calendars, bookings, phone numbers, or business data.
Never ask for sensitive data. Do not pretend to create, find, change, or cancel
anything. If asked to perform an action, explain that a tailored workspace demo
can show an approved workflow and invite the visitor to request one.

Keep replies conversational and normally under two sentences. The session is
about one minute. When asked to close, give one complete, friendly final
sentence and stop. Follow the visitor's language when you understand it; begin
in English.
```

Choose a production-approved Telnyx voice and the supported model already used
for Sauti browser calls. Publish the assistant, then copy its assistant ID and,
if applicable, its published version ID.

The production-isolated assistant was provisioned on 2026-08-03 as `Sauti
Public Demo`. Its operational IDs are stored as GitHub Actions repository
variables and in ignored local environment configuration, not in application
source. The reviewed configuration uses the existing Kimi K2.6 model and
Telnyx Ultra voice, Deepgram Nova-3 automatic language detection, a 60-second
provider time limit, unauthenticated web calls, no tools or integrations, no
recording, no retention, and no post-conversation processing.

## Production configuration

Store the assistant ID, version ID, and enablement flag as the GitHub Actions
repository variables `SAUTI_PUBLIC_DEMO_TELNYX_AGENT_ID`,
`SAUTI_PUBLIC_DEMO_TELNYX_VERSION_ID`, and
`SAUTI_PUBLIC_DEMO_ENABLED`. The reviewed deploy workflow synchronizes them to
`/opt/sauti/.env.production`; do not edit the production host manually.

The resulting production configuration is:

```dotenv
SAUTI_PUBLIC_DEMO_ENABLED=true
SAUTI_PUBLIC_DEMO_TELNYX_AGENT_ID=<dedicated-assistant-id>
SAUTI_PUBLIC_DEMO_TELNYX_VERSION_ID=<published-version-id-or-empty>
SAUTI_PUBLIC_DEMO_ALLOWED_ORIGINS=https://sauti.uk
SAUTI_PUBLIC_DEMO_MAX_DURATION_SECONDS=60
SAUTI_PUBLIC_DEMO_SESSIONS_PER_IP_PER_DAY=2
SAUTI_PUBLIC_DEMO_SESSIONS_PER_DEVICE_PER_DAY=2
SAUTI_PUBLIC_DEMO_MAX_CONCURRENT=3
SAUTI_PUBLIC_DEMO_DAILY_SECONDS=1800
```

Keep `SAUTI_PUBLIC_DEMO_ENABLED=false` until the isolated assistant has been
reviewed, deployed through the normal CI/CD chain, and accepted with a real
browser microphone. The daily ceiling reserves the full maximum duration for
every started session, so the default permits at most 30 one-minute sessions
daily. Redis-backed active slots expire automatically if a browser disappears
without calling the completion endpoint.

Telnyx Assistant Chat cannot be used as a text-only acceptance shortcut while
provider data retention is disabled. Keep retention disabled; validate the
prompt through the actual browser voice path instead.

After deployment, verify the homepage from an allowed origin, confirm the
assistant cannot invoke tools, verify the session closes after one minute with
a complete final sentence, and confirm a third daily attempt from the same
browser receives the friendly quota message.

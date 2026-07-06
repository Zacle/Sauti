# Sauti Agent Studio Reference Plan

This plan synthesizes UI and product patterns from the provided OmniDimension, PolyAI, Rinng, and Zeeg screenshots.

## What The References Teach

### OmniDimension
- Uses a dark, operations-heavy console with a persistent sidebar.
- Starts agent creation with a freeform description: "describe the assistant you want."
- Adds use-case category filters and concrete templates such as appointments, site visits, table reservations, consultations.
- Separates setup from operations: assistants, clone voice, files, integrations, phone numbers, WhatsApp, call logs, analytics, campaigns, billing, API.

### PolyAI
- Uses a guided setup path before exposing configuration.
- Starts with business qualification: company size and business type.
- Collects a website/knowledge source so the agent can be grounded in public business information.
- Uses a chat-like builder where the AI researches, asks setup questions, and produces an agent plan.
- Requires review/approval before publish.
- Includes testing before deployment: generated conversation scenarios, happy-path simulations, and automated test runs.
- Separates lifecycle states clearly: sandbox, draft, publish, test.

### Rinng
- Uses a lightweight onboarding wizard with progress and big selectable cards.
- Separates assistant types: outbound, inbound, webcall.
- Separates prompt complexity: single prompt for simple workflows, multi-prompt for complex workflows.
- Provides industry templates and blank templates.
- Agent creation includes basics, primary/secondary language, voice selection, call context, and custom variables.
- Main app navigation includes assistants, campaigns, knowledge base, numbers, history, analytics, alerts, integrations, STT, settings.

### Zeeg
- Strongest reference for booking and calendar setup.
- Begins with Google Calendar permission consent and explicit scopes.
- Onboarding asks product bundle, industry, services offered, meeting routing type, and bookable scheduling pages.
- Agent tasks are explicit toggles: answer questions, book appointments, transfer to human, take voicemail.
- Agent prompt starts from a generated greeting and optional extra instructions, not an empty prompt.
- Uses clean two-panel onboarding screens: choices on the left, product preview/illustration on the right.

## Sauti Product Structure

### Primary Navigation
- Overview
- Agent Studio
- Conversations
- Calls
- Calendar & Booking
- Knowledge
- Phone Numbers
- Campaigns
- Analytics
- Integrations
- Settings

### Agent Studio Sections
- Agents list
- Create agent
- Templates
- Voice library
- Test lab
- Publish/deploy

### Agent Setup Flow
1. Business context
   - Business type: local business, startup, agency, enterprise, personal/test
   - Industry: healthcare, salon/beauty, real estate, finance, restaurant, education, consulting, ecommerce, other
   - Website or knowledge source URL

2. Use case and channel
   - Appointment booking
   - Customer support
   - Lead qualification
   - Call routing
   - Reminders/outbound
   - Custom workflow
   - Channel: inbound phone, outbound campaign, webcall, WhatsApp later

3. Services and booking model
   - Services offered
   - Bookable services
   - Meeting style: one host, round robin, group event, resource/room
   - Calendar provider: Google Calendar, Calendly, webhook, future Outlook
   - Timezone, operating hours, buffer time, no-show/reminder behavior

4. Agent identity
   - Agent name
   - Role/personality
   - Greeting
   - Primary language, supported languages, auto-detect toggle
   - Voice provider/voice/tone
   - Fallback contact and escalation rules

5. Knowledge and tools
   - Website crawl/files/FAQ sources
   - Tool permissions: check availability, book slot, send SMS, transfer, end call, create lead, webhook actions
   - Required variables: caller name, phone, service, date/time, reason, custom fields

6. Prompt and behavior
   - Generated prompt draft from setup answers
   - Extra instructions field
   - Guardrails: never book before availability check, confirm details before booking, do not answer outside scope
   - Escalation behavior

7. Test lab
   - Generated test scenarios
   - Simulated call transcript
   - Voice preview
   - Tool trace preview
   - Pass/fail checks before publish

8. Publish
   - Draft checklist
   - Assign phone number
   - Enable webhooks/calendar
   - Run test call
   - Publish to live

## Required Backend Features

- Agent templates by industry/use case.
- Agent draft state with wizard progress.
- Knowledge source ingestion records.
- Calendar credential OAuth flow and provider permissions.
- Service/scheduling page model for bookable offerings.
- Agent tool permissions and required variable schema.
- Test scenario generation and result storage.
- Publish checklist and deployment state.
- Phone number assignment and channel configuration.
- Conversation/call test sandbox separate from live calls.

## Required Frontend Screens

1. App shell with persistent sidebar and workspace switcher.
2. Overview dashboard with setup checklist and recent calls.
3. Agents list with status, type, language, channel, call count, and search/filter.
4. Guided create-agent wizard.
5. Template picker with industry/use-case filters.
6. Agent builder detail page with tabs:
   - Summary
   - Behavior
   - Voice & language
   - Booking
   - Knowledge
   - Tools
   - Test lab
   - Publish
7. Calendar connection and booking configuration page.
8. Conversations/call logs with transcript, tool trace, recording, outcome.
9. Analytics page with booking rate, transfers, qualification, duration, sentiment, usage.
10. Integrations page for Google Calendar, Twilio, Deepgram, ElevenLabs, OpenAI/Gemini, Resend/Mailpit, Lemon Squeezy, webhooks.

## Implementation Order

1. App shell and dashboard foundation.
2. Agents list and create-agent wizard UI.
3. Agent draft backend model and APIs.
4. Templates and generated prompt draft.
5. Calendar/service booking configuration.
6. Knowledge source setup.
7. Tool permission UI and backend schema.
8. Test lab with transcript/tool-trace simulation.
9. Publish checklist and phone number assignment.
10. Live call logs and analytics refinement.

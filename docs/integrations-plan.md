# Sauti Integrations Plan

## Framing

Sauti integrations fall into two categories — matching the model established by platforms
like OmniDimension:

| Category | When it runs | Purpose |
|---|---|---|
| **During Call** | Inside an active conversation turn | The AI uses a tool mid-call to fetch data, book, or act |
| **Post Call** | After the call ends | Notifications, CRM sync, follow-up messages |

**Built-in (not integrations):** SMS via Telnyx is a first-class built-in tool — the
same `TELNYX_API_KEY` used for voice powers SMS. It is exposed as the `send_sms` tool
in `SautiSmsFulfillment`, not as a third-party integration.

---

## During-Call Integrations

These are tool fulfillments that the LLM can call mid-conversation. They run inside
`ConversationOrchestrator`'s tool loop (max 4 iterations).

### 1. Calendar / Booking — `fulfillmentType: sauti_calendar` ✅ Done
Real-time appointment booking via Google Calendar (OAuth) or local availability slots.
`SautiCalendarFulfillment` is already implemented. Agent exposes a `book_appointment` tool.

### 2. Webhook (Bring Your Own Backend) — `fulfillmentType: webhook` ✅ Done
Agent owners POST to any HTTPS URL with auth options: Bearer, API key, HMAC-SHA256.
`WebhookToolFulfillment` is already implemented. Covers M-Pesa STK push, loyalty
lookups, order status, custom CRM queries — anything reachable via HTTP.

### 3. SMS Confirmation — `fulfillmentType: sauti_sms` ✅ Done (Telnyx)
Send a confirmation SMS mid-call (booking reference, OTP, order summary).
`SautiSmsFulfillment` calls `POST https://api.telnyx.com/v2/messages`.
Required env: `TELNYX_API_KEY`. Agent must have a provisioned Telnyx number as `from`.

### 4. WhatsApp Message — `fulfillmentType: webhook` (via Meta Cloud API)
Send a WhatsApp template message during the call — e.g. share a payment link or PDF.
Implementation: tenant configures a webhook tool pointing at a thin wrapper, or Sauti
provides a first-class `WhatsAppNotifyFulfillment` that calls Meta's send-message API.

**Required config per tenant:**
```
WHATSAPP_ACCESS_TOKEN
WHATSAPP_PHONE_NUMBER_ID
```

**Effort:** 1 day for a dedicated `WhatsAppNotifyFulfillment`; zero days via webhook tool.

### 5. Live Data Lookup (Google Sheets / Airtable)
Read a row from a spreadsheet to answer product/price/inventory questions mid-call.
**Current path:** webhook tool to a Google Apps Script URL (zero Sauti code needed).
**Future:** native `GoogleSheetsFulfillment` for tenant convenience.

---

## Post-Call Integrations

These run after `CallPipelineService` marks the call complete. The extension point is
a `PostCallHook` interface (to be extracted from the current analytics/summary flow).

### 1. WhatsApp Follow-up ⭐ Priority
After the call ends, send a WhatsApp voice note or text with the booking confirmation,
order summary, or support ticket number.

**Architecture:**
```
CallCompletedEvent
    → WhatsAppPostCallNotifier
        → Download/generate summary
        → POST to Meta Cloud API: /messages (template or text)
```

**Limitations:** Meta's 24-hour window applies. For cold follow-ups outside the window,
use an approved WhatsApp template message.

**Effort:** 2 days.

### 2. CRM Sync — HubSpot / Salesforce
Create or update a contact and associate the call transcript and summary.

**Architecture:** `CrmSyncService` calls HubSpot/Salesforce REST API after call ends.
Tenant connects their CRM API key in the dashboard.

For MVP: expose a post-call webhook (`sauti.webhooks.*` already exists) so tenants can
connect their own Zapier/Make.com automation. No Sauti code for HubSpot itself needed
at launch.

**Effort:** Webhook path is done. Native HubSpot: 3 days.

### 3. Slack / Email Notification
Alert the business owner when a new call completes (transcript, sentiment, booking made).

**Architecture:** `NotificationService` listens for `CallAnalysed` event.

- Email: `JavaMailSender` is already wired (`spring.mail.*` in application.yml).
- Slack: webhook URL stored per tenant preference → HTTP POST after call ends.

**Effort:** Email: 1 day. Slack: 1 day.

### 4. Google Sheets Row Append
Log every completed call (date, caller, duration, outcome, summary) to a Google Sheet.

**Architecture:** `GoogleSheetsPostCallLogger` → Sheets API v4 via service account or
OAuth credential stored in `CalendarCredential` table (already handles Google OAuth).

**Effort:** 2 days.

### 5. M-Pesa STK Push (Post-Call Payment Request)
After a booking call, send a Daraja STK push to the caller's number.

**Architecture:** Post-call webhook (tenant's own Daraja integration, zero Sauti code)
OR a first-class `MpesaPaymentNotifier` that calls Daraja API.

**Effort:** Webhook path: 0 days. Native: 2 days.

---

## Integration Marketplace — Dashboard UI

The integration marketplace should mirror OmniDimension's pattern:

```
Integrations tab (in AgentCreator sidebar)
├── During Call
│   ├── Calendar Booking     [Connected / Connect]
│   ├── Send SMS             [Active — Telnyx built-in]
│   ├── WhatsApp Message     [Connect]
│   ├── Webhook (Custom)     [+ Add Webhook]
│   └── Live Data Lookup     [+ Add Webhook → Sheets URL]
└── Post Call
    ├── WhatsApp Follow-up   [Connect]
    ├── Email Alert          [Configure]
    ├── Slack Alert          [Connect]
    ├── CRM Sync             [Connect HubSpot / Salesforce]
    └── Google Sheets Log    [Connect]
```

Each integration card shows:
- Name + icon
- One-line description
- Status badge: Active / Not configured
- Configure button → modal with auth fields

**Dashboard effort:** 3–4 days for the marketplace shell + individual cards.

---

## Priority Order (African market focus)

| # | Integration | Why priority |
|---|---|---|
| 1 | SMS (Telnyx built-in) | Booking confirmations, OTPs — universal |
| 2 | WhatsApp Post-Call follow-up | 80%+ penetration in Sub-Saharan Africa |
| 3 | Calendar (done) | Core booking use case |
| 4 | Webhook (done) | M-Pesa, loyalty, custom |
| 5 | Email alert | Business owner notifications |
| 6 | Slack alert | SME owners often use WhatsApp instead — low priority |
| 7 | Google Sheets | Light CRM for businesses without HubSpot |
| 8 | HubSpot / Salesforce | Enterprise tier only |

---

## Environment Variables Summary

```
# SMS + Voice (same key)
TELNYX_API_KEY=...
TELNYX_PUBLIC_KEY=...          # Ed25519 for webhook verification (Phase 3)

# WhatsApp
WHATSAPP_ACCESS_TOKEN=...
WHATSAPP_PHONE_NUMBER_ID=...
WHATSAPP_VERIFY_TOKEN=...

# Google Calendar / Sheets (shared OAuth client)
GOOGLE_OAUTH_CLIENT_ID=...
GOOGLE_OAUTH_CLIENT_SECRET=...

# Email (already configured)
SPRING_MAIL_HOST=...
SPRING_MAIL_PORT=587
```

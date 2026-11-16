/**
 * Sample data for the dev-only console preview (NEXT_PUBLIC_SAUTI_PREVIEW=true).
 * Loaded lazily from lib/api/client.ts, so it never ships in production builds.
 * Everything here is fictional.
 */

import { ApiError } from "@/lib/api/client";

const DAY = 86_400_000;
const now = Date.now();
const iso = (t: number) => new Date(t).toISOString();
const dayKey = (offset: number) => iso(now - offset * DAY).slice(0, 10);

const agentDefaults = {
  twilioPhoneNumberSid: null,
  phoneNumberOrderId: null,
  phoneNumberAssignedAt: null,
  greetingMessage: "Hello, thanks for calling Bright Smile Dental. How can I help you today?",
  systemPrompt: "You are the friendly front desk for Bright Smile Dental in Nairobi. Book cleanings and check-ups, answer questions about opening hours and prices, and take a message when you can't help.",
  ttsVoiceId: "nia",
  humanTransferNumber: "+254 20 765 4000",
  escalationPhrases: ["speak to a person", "manager"],
  knowledgeBase: "Open Mon–Fri 8:00–18:00, Sat 9:00–13:00. Cleaning from KES 3,500.",
  operatingHours: null,
  afterHoursBehavior: "take_message",
  afterHoursMessage: "We're closed right now. Leave your name and number and we'll call you back.",
  maxCallDurationSeconds: 600,
  saveTranscript: true,
  recordCalls: false,
  bargeInSensitivity: 0.7,
  bargeInGraceMs: 250,
  endCallOnSilenceSeconds: 300,
  reminderAfterSilenceSeconds: 30,
  maxReminders: 2,
  detectVoicemail: true,
  handleCallScreening: true,
  dtmfEnabled: false,
  dtmfTerminationKey: "#",
  dtmfInputTimeoutSeconds: 5,
  dtmfMaxDigits: 12,
  dtmfDigitMappings: {},
  sttEndpointingMs: 300,
  sttVocabularyDomain: "dental",
  sttBoostedKeywords: "cleaning, whitening, filling",
  safetyGuardrails: [],
  postCallExtractionFields: ["caller_name", "reason"],
  bookingRequiredFields: ["name", "phone"],
  bookingNotificationChannels: ["console", "email"],
  bookingNotificationRecipient: null,
  businessType: "dental_clinic",
  primaryUseCase: "appointment_booking",
  businessWebsite: "https://brightsmile.example",
  bookableServices: ["Dental cleaning", "General check-up", "Whitening consultation", "Filling"],
  calendarProvider: "google",
  routingPolicy: null,
  voiceProfile: "warm",
  webVoiceEnabled: true,
  webVoicePublicId: "preview-web-voice",
  webVoiceAllowedOrigins: [],
  webVoiceRequireConsent: true,
  whatsappEnabled: false,
  whatsappPhoneNumberId: null,
};

const agents = [
  {
    id: "agent-front-desk",
    name: "Front desk",
    description: "Books cleanings and check-ups, answers opening hours and pricing questions.",
    active: true,
    defaultLanguage: "en",
    supportedLanguages: ["en", "sw"],
    timezone: "Africa/Nairobi",
    twilioPhoneNumber: "+254 20 765 4321",
    phoneNumberStatus: "active",
    phoneNumberProvider: "telnyx",
    bookingEnabled: true,
    defaultBookingDurationMinutes: 30,
    llmTier: "standard",
    createdAt: iso(now - 64 * DAY),
    updatedAt: iso(now - 2 * DAY),
  },
  {
    id: "agent-after-hours",
    name: "After hours",
    description: "Takes messages and books callbacks when the clinic is closed.",
    active: true,
    defaultLanguage: "en",
    supportedLanguages: ["en", "fr"],
    timezone: "Africa/Nairobi",
    twilioPhoneNumber: null,
    phoneNumberStatus: null,
    phoneNumberProvider: null,
    bookingEnabled: true,
    defaultBookingDurationMinutes: 30,
    llmTier: "standard",
    createdAt: iso(now - 30 * DAY),
    updatedAt: iso(now - 5 * DAY),
  },
  {
    id: "agent-billing",
    name: "Billing questions",
    description: "Draft agent for insurance and invoice questions.",
    active: false,
    defaultLanguage: "en",
    supportedLanguages: ["en"],
    timezone: "Africa/Nairobi",
    twilioPhoneNumber: null,
    phoneNumberStatus: null,
    phoneNumberProvider: null,
    bookingEnabled: false,
    defaultBookingDurationMinutes: 30,
    llmTier: "standard",
    createdAt: iso(now - 3 * DAY),
    updatedAt: iso(now - 1 * DAY),
  },
].map((agent) => ({ ...agentDefaults, ...agent }));

const outcomes = ["booking", "faq_answered", "completed", "booking", "transferred", "no_answer", "faq_answered", "voicemail", "booking", "completed"];
const intents = ["book_cleaning", "opening_hours", "reschedule", "book_checkup", "billing_question", null, "pricing", null, "book_whitening", "directions"];
const callers = ["+254 712 004 118", "+254 733 218 904", "+254 701 552 310", "+33 6 12 44 81 09", "+254 722 908 441", "+255 754 118 220", "+254 790 334 017"];

const calls = Array.from({ length: 48 }, (_, i) => {
  const started = now - i * 0.37 * DAY - (i % 6) * 2_700_000;
  const outcome = outcomes[i % outcomes.length];
  const duration = outcome === "no_answer" ? 0 : 38 + ((i * 41) % 240);
  const agentId = i % 4 === 3 ? "agent-after-hours" : "agent-front-desk";
  return {
    id: `call-${1000 + i}`,
    agentId,
    twilioCallSid: `v3:${i}`,
    callerNumber: callers[i % callers.length],
    direction: i % 9 === 0 ? "test" : "inbound",
    languageDetected: i % 5 === 0 ? "sw" : i % 11 === 0 ? "fr" : "en",
    startedAt: iso(started),
    endedAt: iso(started + duration * 1000),
    durationSeconds: duration,
    outcome,
    transcript: null,
    recordingUrl: null,
    failureReason: null,
    callSummary: outcome === "booking"
      ? "Caller asked for a cleaning this week and accepted the first morning slot. Confirmation SMS sent."
      : outcome === "transferred"
        ? "Caller had a question about an invoice and asked to speak to someone; transferred to reception."
        : null,
    sentiment: i % 4 === 0 ? "positive" : i % 13 === 0 ? "negative" : "neutral",
    intent: intents[i % intents.length],
    transferStatus: outcome === "transferred" ? "completed" : null,
    afterHours: agentId === "agent-after-hours",
  };
});

const services = ["Dental cleaning", "General check-up", "Whitening consultation", "Follow-up visit", "Filling"];
const names = ["Amani Kariuki", "Wanjiru Mwangi", "Otieno Juma", "Fatuma Ali", "Brian Odhiambo", "Aisha Noor", "Claire Dubois", "Peter Njoroge"];
const bookingCalls = calls.filter((call) => call.outcome === "booking");

const bookings = Array.from({ length: 16 }, (_, i) => {
  const appointment = now + (i - 5) * 0.75 * DAY;
  const at = new Date(appointment);
  at.setHours(8 + ((i * 3) % 9), i % 2 ? 30 : 0, 0, 0);
  return {
    id: `booking-${i + 1}`,
    bookingReference: `BSD-${2041 + i}`,
    agentId: i % 5 === 4 ? "agent-after-hours" : "agent-front-desk",
    callId: bookingCalls[i]?.id ?? null,
    callerName: names[i % names.length],
    callerPhone: callers[i % callers.length],
    callerEmail: null,
    serviceType: services[i % services.length],
    bookedAt: iso(now - (16 - i) * 0.6 * DAY),
    appointmentAt: at.toISOString(),
    durationMinutes: i % 3 === 0 ? 45 : 30,
    externalEventId: null,
    status: i === 6 ? "cancelled" : "confirmed",
    confirmationSent: true,
    capturedData: {},
    calendarSyncStatus: i === 9 ? "failed" : "synced",
    calendarSyncError: i === 9 ? "Calendar token expired" : null,
  };
});

const turns = [
  { turnIndex: 1, callerTranscript: "Habari, nataka ku-book a cleaning this week.", agentResponse: "Sawa. Thursday at 9:00 or Friday at 10:30 are open. Which suits you?", language: "sw", interrupted: false },
  { turnIndex: 2, callerTranscript: "Thursday at nine, please.", agentResponse: "Done — you're booked for Thursday at 9:00 with Dr. Achieng. I'll text a confirmation.", language: "en", interrupted: false },
  { turnIndex: 3, callerTranscript: "Asante sana.", agentResponse: "Karibu! See you Thursday.", language: "sw", interrupted: false },
];

const delta = (value: number, previous: number) => ({
  value,
  previousValue: previous,
  percentChange: previous ? Math.round(((value - previous) / previous) * 1000) / 10 : 0,
});

const summary = {
  totalCalls: 412,
  attemptedCalls: 412,
  connectedCalls: 389,
  completedCalls: 352,
  faqAnsweredCalls: 121,
  transferredCalls: 18,
  voicemailCalls: 9,
  bookingCalls: 164,
  totalDurationSeconds: 49_850,
  connectRate: 94.4,
  averageDurationSeconds: 128,
  avgTurnsPerCall: 6.1,
  avgSttLatencyMs: 182,
  avgLlmLatencyMs: 410,
  avgTtsLatencyMs: 205,
  totalCallsDelta: delta(412, 361),
  connectRateDelta: delta(94.4, 92.1),
  totalDurationSecondsDelta: delta(49_850, 47_100),
  averageDurationSecondsDelta: delta(128, 136),
  bookingCallsDelta: delta(164, 139),
  transferredCallsDelta: delta(18, 22),
};

const daysBack = (n: number, fn: (i: number) => Record<string, unknown>) =>
  Array.from({ length: n }, (_, i) => ({ date: dayKey(n - 1 - i), ...fn(i) }));

const volume = (i: number) => 18 + Math.round(9 * Math.sin(i / 1.7) + (i % 7 === 5 || i % 7 === 6 ? -8 : 4) + i * 0.4);

const routes: Record<string, unknown> = {
  "GET /agents": agents,
  "GET /agents/readiness": agents.map((agent) => ({
    agentId: agent.id,
    businessDetailsComplete: true,
    calendarRequired: true,
    calendarConfigured: agent.id !== "agent-billing",
    phoneNumberConfigured: Boolean(agent.twilioPhoneNumber),
    webVoiceConfigured: true,
    whatsappConfigured: false,
    channelConfigured: agent.id !== "agent-billing",
    active: agent.active,
    readyToActivate: agent.id !== "agent-billing",
    nextStep: agent.active ? "ready" : "connect_calendar",
    missingRequiredVariables: [],
  })),
  "GET /agents/stats": [
    { agentId: "agent-front-desk", totalCalls: 318, bookingCalls: 141, bookingRate: 44.3 },
    { agentId: "agent-after-hours", totalCalls: 94, bookingCalls: 23, bookingRate: 24.5 },
    { agentId: "agent-billing", totalCalls: 0, bookingCalls: 0, bookingRate: 0 },
  ],
  "GET /calls": calls,
  "GET /bookings": bookings,
  "GET /tenant/onboarding-status": { registered: true, emailVerified: true, hasAgent: true, hasActiveAgent: true, hasProvisionedNumber: true, nextStep: "ready" },
  "GET /analytics/summary": summary,
  "GET /analytics/daily": daysBack(14, (i) => ({ callCount: volume(i) })),
  "GET /analytics/outcomes-by-day": daysBack(30, (i) => {
    const v = volume(i % 14);
    return { completed: Math.round(v * 0.78), transferred: i % 5 === 0 ? 1 : 0, voicemail: i % 7 === 0 ? 1 : 0, noAnswer: Math.round(v * 0.05), busy: 0, failed: i % 11 === 0 ? 1 : 0, afterHours: Math.round(v * 0.18) };
  }),
  "GET /analytics/connect-rate-by-day": daysBack(30, (i) => {
    const attempts = volume(i % 14);
    const connected = Math.round(attempts * (0.9 + (i % 4) * 0.02));
    return { attempts, connected, rate: Math.round((connected / attempts) * 1000) / 10 };
  }),
  "GET /analytics/funnel": { attempted: 412, connected: 389, completed: 352 },
  "GET /analytics/by-language": [
    { language: "en", callCount: 268 },
    { language: "sw", callCount: 117 },
    { language: "fr", callCount: 27 },
  ],
  "GET /analytics/by-channel": [
    { channel: "phone", totalCalls: 341, connectedCalls: 322, completedCalls: 290, bookingCalls: 139, connectRate: 94.4 },
    { channel: "web", totalCalls: 71, connectedCalls: 67, completedCalls: 62, bookingCalls: 25, connectRate: 94.4 },
  ],
  "GET /analytics/top-intents": [
    { intent: "book_cleaning", callCount: 96 },
    { intent: "opening_hours", callCount: 71 },
    { intent: "reschedule", callCount: 48 },
    { intent: "pricing", callCount: 39 },
    { intent: "book_checkup", callCount: 34 },
    { intent: "billing_question", callCount: 18 },
  ],
  "GET /analytics/sentiment-by-day": daysBack(30, (i) => {
    const analysedCalls = volume(i % 14);
    return { analysedCalls, averageScore: 0.42 + (i % 5) * 0.03, positive: Math.round(analysedCalls * 0.46), neutral: Math.round(analysedCalls * 0.44), negative: Math.round(analysedCalls * 0.06), mixed: Math.round(analysedCalls * 0.04) };
  }),
  "GET /analytics/by-agent": [
    { agentId: "agent-front-desk", agentName: "Front desk", totalCalls: 318, connectedCalls: 302, bookingCalls: 141, connectRate: 95, avgDurationSeconds: 131 },
    { agentId: "agent-after-hours", agentName: "After hours", totalCalls: 94, connectedCalls: 87, bookingCalls: 23, connectRate: 92.6, avgDurationSeconds: 117 },
  ],
  "GET /analytics/after-hours": {
    totalCalls: 94,
    connectedCalls: 87,
    completedCalls: 80,
    behaviors: [
      { behavior: "answer", callCount: 61 },
      { behavior: "take_message", callCount: 26 },
      { behavior: "closed", callCount: 7 },
    ],
  },
  "GET /analytics/integration-events": [
    { provider: "google_calendar", attempted: 164, delivered: 161, failed: 1, retrying: 2 },
    { provider: "webhook", attempted: 352, delivered: 350, failed: 2, retrying: 0 },
  ],
  "GET /billing/usage": { plan: "growth", status: "active", monthlyMinutesLimit: 750, minutesUsedThisCycle: 512, remainingMinutes: 238, usagePercent: 68, limitReached: false },
  "GET /billing/account": {
    id: "billing-preview",
    status: "active",
    enforcementMode: "observe",
    billingCurrency: "USD",
    monthlySpendingLimit: 150,
    lowBalanceThreshold: 10,
    communicationBalances: { USD: 42.6 },
    paidResourcesAllowed: true,
    subscription: { provider: "lemon_squeezy", providerReference: "sub_preview", plan: "growth", interval: "monthly", status: "active", renewsAt: iso(now + 12 * DAY), manageUrl: null },
    pendingPlanChange: null,
    addOns: [{ addOn: "number", quantity: 1, status: "active", manageUrl: null }],
    costTotals: [
      { costBasis: "provider_confirmed", currency: "USD", amount: 38.4 },
      { costBasis: "rate_card", currency: "USD", amount: 6.15 },
    ],
    unpricedUsage: [],
    reconciliation: { pending: 2, retrying: 0, reconciled: 318, estimated: 14, unavailable: 0 },
    recentEntries: [
      { id: "le-1", direction: "debit", category: "voice_minutes", quantity: 142, unit: "minute", amount: 11.36, currency: "USD", costBasis: "provider_confirmed", externalReference: null, description: "Inbound voice minutes", createdAt: iso(now - DAY) },
      { id: "le-2", direction: "debit", category: "sms", quantity: 64, unit: "message", amount: 2.56, currency: "USD", costBasis: "rate_card", externalReference: null, description: "Booking confirmation SMS", createdAt: iso(now - 2 * DAY) },
      { id: "le-3", direction: "credit", category: "top_up", quantity: 1, unit: "credit", amount: 25, currency: "USD", costBasis: "credit", externalReference: null, description: "Communication balance top-up", createdAt: iso(now - 9 * DAY) },
    ],
  },
  "GET /whatsapp/conversations": [
    { id: "wa-1", agentId: "agent-front-desk", customerNumber: "+254 712 004 118", customerName: "Amani Kariuki", mode: "ai", status: "open", unreadCount: 2, lastMessagePreview: "Can I move my cleaning to Friday?", lastMessageAt: iso(now - 6 * 60_000) },
    { id: "wa-2", agentId: "agent-front-desk", customerNumber: "+254 733 218 904", customerName: "Wanjiru Mwangi", mode: "human", status: "open", unreadCount: 0, lastMessagePreview: "Thank you, see you then!", lastMessageAt: iso(now - 2 * 3_600_000) },
    { id: "wa-3", agentId: "agent-after-hours", customerNumber: "+33 6 12 44 81 09", customerName: "Claire Dubois", mode: "ai", status: "open", unreadCount: 0, lastMessagePreview: "Do you accept AXA insurance?", lastMessageAt: iso(now - DAY) },
  ],
  "GET /integrations/catalog": [
    ["google_calendar", "Google Calendar", "Calendar", "Check free slots during the call and create the event when the caller books.", true, false],
    ["calendly", "Calendly", "Calendar", "Offer times from your Calendly event types.", true, false],
    ["whatsapp", "WhatsApp", "Messaging", "Answer WhatsApp messages and send booking confirmations.", true, true],
    ["telnyx_sms", "SMS", "Messaging", "Text callers their booking details and reminders.", false, true],
    ["hubspot", "HubSpot", "CRM", "Create or update the contact and log the call summary.", false, true],
    ["salesforce", "Salesforce", "CRM", "Look up the caller and log the call against the record.", true, true],
    ["google_sheets", "Google Sheets", "Data", "Look up customers and add a row after every call.", true, true],
    ["email", "Email", "Notifications", "Email the team a summary when a booking comes in.", false, true],
    ["slack", "Slack", "Notifications", "Post new bookings and missed calls to a channel.", false, true],
    ["mpesa", "M-Pesa", "Payments", "Request a deposit by STK push during the call.", true, false],
    ["custom_webhook", "Webhook", "Developer", "Send a signed JSON event to your own endpoint after each call.", false, true],
  ].map(([provider, name, category, description, duringCall, postCall]) => ({
    provider, name, category, description, duringCall, postCall, requiresConnection: !["telnyx_sms", "email"].includes(provider as string),
    configurationFields: provider === "custom_webhook" ? ["webhookUrl", "authType"] : [],
    credentialFields: provider === "custom_webhook" ? ["authToken", "apiKey", "hmacSecret"] : [],
    authorizationConfigured: true,
  })),
  "GET /integrations/connections": [
    { id: "conn-cal", provider: "google_calendar", displayName: "Clinic calendar", status: "connected", credentialConfigured: true, configuration: {}, externalAccountId: "reception@brightsmile.example", lastTestedAt: iso(now - 3 * DAY), lastError: null, createdAt: iso(now - 60 * DAY) },
    { id: "conn-wa", provider: "whatsapp", displayName: "WhatsApp Business", status: "connected", credentialConfigured: true, configuration: {}, externalAccountId: "+254 20 765 4321", lastTestedAt: iso(now - 8 * DAY), lastError: null, createdAt: iso(now - 40 * DAY) },
  ],
  "GET /voices": {
    enabledProviders: ["telnyx"],
    voices: [
      ["nia", "Nia", "Warm, clear and unhurried. Good for clinics.", ["en", "sw"], { accent: "kenyan", gender: "female", age: "young adult" }],
      ["baraka", "Baraka", "Calm and reassuring with a steady pace.", ["en", "sw"], { accent: "kenyan", gender: "male", age: "middle aged" }],
      ["amara", "Amara", "Bright and friendly front-desk voice.", ["en"], { accent: "nigerian", gender: "female", age: "young adult" }],
      ["claire", "Claire", "Polished and precise.", ["fr", "en"], { accent: "parisian", gender: "female", age: "middle aged" }],
      ["james", "James", "Relaxed conversational tone.", ["en"], { accent: "british", gender: "male", age: "young adult" }],
    ].map(([id, name, description, languages, traits]) => ({
      provider: "telnyx", id, name, description, category: "conversational", previewUrl: null, languages, traits, owned: false,
    })),
  },
  "GET /billing/checkout/status": { provider: "lemon_squeezy", environment: "sandbox", configured: true, addOnsConfigured: true },
  "GET /tenant/workspace-profile": { businessName: "Bright Smile Dental", ownerEmail: "owner@brightsmile.example", countryCode: "KE", timezone: "Africa/Nairobi", defaultBookingDurationMinutes: 30 },
  "GET /tenant/call-defaults": { saveTranscript: true, recordCalls: false, bargeInSensitivity: 0.6 },
  "GET /tenant/notification-preferences": { consoleBookingNotificationsEnabled: true, emailBookingNotificationsEnabled: true },
  "GET /tenant/privacy-retention": { conversationRetentionDays: 90, recordingRetentionDays: 30, recordingEnabledForAnyAgent: false, policyVersion: "2026-09" },
  "GET /tenant/webhook": { webhookUrl: null, secretConfigured: false },
  "GET /notifications": {
    unreadCount: 2,
    notifications: [
      { id: "n1", type: "booking.confirmed", title: "New booking", message: "Amani Kariuki booked a dental cleaning for Thursday 9:00.", href: "/bookings", resourceType: "booking", resourceId: "booking-6", payload: {}, createdAt: iso(now - 40 * 60_000), readAt: null },
      { id: "n2", type: "booking.follow_up_required", title: "Calendar sync failed", message: "BSD-2050 couldn't be written to Google Calendar.", href: "/bookings", resourceType: "booking", resourceId: "booking-10", payload: {}, createdAt: iso(now - 3 * 3_600_000), readAt: null },
      { id: "n3", type: "booking.confirmed", title: "New booking", message: "Claire Dubois booked a whitening consultation.", href: "/bookings", resourceType: "booking", resourceId: "booking-7", payload: {}, createdAt: iso(now - DAY), readAt: iso(now - 20 * 3_600_000) },
    ],
  },
};

function withoutQuery(path: string) {
  return path.split("?")[0];
}

/** Resolve a console API call against the sample data. */
export function previewResponse(path: string, method = "GET", body?: BodyInit | null): unknown {
  const clean = withoutQuery(path);
  const verb = method.toUpperCase();
  const exact = routes[`${verb} ${clean}`];
  if (exact !== undefined) return structuredClone(exact);

  const call = clean.match(/^\/calls\/([^/]+)(\/turns)?$/);
  if (call && verb === "GET") return call[2] ? turns : calls.find((item) => item.id === call[1]) ?? null;

  const readiness = clean.match(/^\/agents\/([^/]+)\/readiness$/);
  if (readiness && verb === "GET") return (routes["GET /agents/readiness"] as Array<{ agentId: string }>).find((item) => item.agentId === readiness[1]) ?? null;

  if (/^\/agents\/[^/]+\/integrations$/.test(clean) && verb === "GET") {
    const binding = (provider: string, name: string, connectionStatus: string, connectionId: string | null) => ({
      provider, name, enabled: true, connectionId, connectionStatus, configuration: {},
      lastDelivery: { id: `d-${provider}`, callId: "call-1003", provider, status: "delivered", attempts: 1, responseCode: 200, lastError: null, deliveredAt: iso(now - 3_600_000), createdAt: iso(now - 3_600_000) },
    });
    return [
      binding("google_calendar", "Google Calendar", "connected", "conn-cal"),
      binding("whatsapp", "WhatsApp", "connected", "conn-wa"),
      binding("telnyx_sms", "SMS", "built_in", null),
    ];
  }

  if (/^\/agents\/[^/]+\/(variables|knowledge-documents)$/.test(clean) && verb === "GET") return [];

  if (/^\/agents\/[^/]+\/available-numbers$/.test(clean) && verb === "GET") {
    const country = new URLSearchParams(path.split("?")[1] ?? "").get("countryCode") ?? "KE";
    if (country !== "KE") return [];
    return [
      ["+254 20 765 4401", "local", "Nairobi", "Nairobi County", "8"],
      ["+254 20 765 4418", "local", "Nairobi", "Nairobi County", "8"],
      ["+254 41 222 1093", "local", "Mombasa", "Mombasa County", "8"],
      ["+254 800 720 115", "toll_free", "", "", "22"],
    ].map(([phoneNumber, type, locality, region, monthlyCost]) => ({ phoneNumber, type, locality, region, upfrontCost: "0", monthlyCost, currency: "USD" }));
  }

  const agent = clean.match(/^\/agents\/([^/]+)$/);
  if (agent && verb === "GET") {
    const found = agents.find((item) => item.id === agent[1]);
    if (!found) throw new ApiError("We couldn’t find that. It may have been moved or deleted.", 404);
    return found;
  }
  if (agent && verb === "PUT") {
    // Like the real API: the stored agent with the submitted changes applied.
    const found = agents.find((item) => item.id === agent[1]);
    if (!found) throw new ApiError("We couldn’t find that. It may have been moved or deleted.", 404);
    const changes = typeof body === "string" ? (JSON.parse(body) as Record<string, unknown>) : {};
    Object.assign(found, changes);
    return structuredClone(found);
  }

  const whatsapp = clean.match(/^\/whatsapp\/conversations\/([^/]+)\/(messages|read|assignment)$/);
  if (whatsapp) {
    const conversations = routes["GET /whatsapp/conversations"] as Array<Record<string, unknown>>;
    const conversation = conversations.find((item) => item.id === whatsapp[1]) ?? conversations[0];
    if (whatsapp[2] === "messages" && verb === "GET") return whatsappThread(whatsapp[1]);
    if (whatsapp[2] === "messages") {
      const text = typeof body === "string" ? (JSON.parse(body) as { text?: string }).text ?? "" : "";
      return { id: `wa-msg-${Date.now()}`, providerMessageId: null, direction: "outbound", type: "text", body: text, mediaId: null, mediaMimeType: null, status: "sent", failureReason: null, createdAt: new Date().toISOString() };
    }
    if (whatsapp[2] === "read") return { ...conversation, unreadCount: 0 };
    const mode = typeof body === "string" ? (JSON.parse(body) as { mode?: string }).mode : undefined;
    return { ...conversation, mode: mode ?? conversation.mode };
  }

  // Writes: echo the submitted JSON so optimistic UIs keep working.
  if (verb !== "GET") {
    if (typeof body === "string") {
      try {
        return JSON.parse(body);
      } catch {
        return {};
      }
    }
    return {};
  }
  return [];
}


function whatsappThread(conversationId: string) {
  const message = (offsetMinutes: number, direction: "inbound" | "outbound", text: string) => ({
    id: `${conversationId}-${offsetMinutes}`, providerMessageId: null, direction, type: "text", body: text,
    mediaId: null, mediaMimeType: null, status: direction === "outbound" ? "read" : "received", failureReason: null,
    createdAt: new Date(Date.now() - offsetMinutes * 60_000).toISOString(),
  });
  return [
    message(42, "inbound", "Hi, I have a cleaning booked for Thursday at 9."),
    message(41, "outbound", "Hello! Yes, I can see your cleaning on Thursday at 9:00 with Dr. Achieng. How can I help?"),
    message(9, "inbound", "Something came up at work."),
    message(6, "inbound", "Can I move my cleaning to Friday?"),
    message(5, "outbound", "Of course. Friday has 10:30 or 14:00 free. Which would you prefer?"),
  ];
}

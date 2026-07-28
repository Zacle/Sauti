export type IntegrationIconKey =
  | "activity"
  | "bot"
  | "briefcase"
  | "calendar"
  | "code"
  | "database"
  | "lock"
  | "message"
  | "phone"
  | "route"
  | "shield"
  | "sparkles"
  | "waveform"
  | "webhook";

export type IntegrationStep = {
  icon: IntegrationIconKey;
  label: string;
  detail: string;
};

export type IntegrationCapability = {
  title: string;
  description: string;
};

export type MarketingIntegration = {
  slug: string;
  label: string;
  shortLabel: string;
  cardDescription: string;
  accent: string;
  icon: IntegrationIconKey;
  heroPrefix: string;
  heroHighlight: string;
  heroSuffix: string;
  description: string;
  proof: string[];
  providers: Array<{ name: string; logo?: string }>;
  flow: {
    eyebrow: string;
    title: string;
    description: string;
    steps: IntegrationStep[];
    result: string;
    resultDetail: string;
  };
  value: {
    eyebrow: string;
    title: string;
    description: string;
    capabilities: IntegrationCapability[];
  };
  safeguards: string[];
  finalTitle: string;
  finalDescription: string;
};

export const marketingIntegrations: MarketingIntegration[] = [
  {
    slug: "voice-infrastructure",
    label: "Voice infrastructure",
    shortLabel: "Voice",
    cardDescription: "Route live calls through one managed Telnyx conversation layer.",
    accent: "#36e6d7",
    icon: "phone",
    heroPrefix: "One voice layer. ",
    heroHighlight: "Every live call.",
    heroSuffix: "",
    description:
      "Telnyx carries the conversation while Sauti supplies the agent, business context, safe tools, and durable call outcomes.",
    proof: ["Inbound and outbound", "Native call control", "Authoritative callbacks"],
    providers: [{ name: "Telnyx", logo: "/logos/telnyx.svg" }],
    flow: {
      eyebrow: "A call in motion",
      title: "From the first ring to a recorded outcome.",
      description:
        "The carrier owns the live media path. Sauti owns the business decisions and the record your team works from.",
      steps: [
        { icon: "phone", label: "Call arrives", detail: "Telnyx validates and answers the live call." },
        { icon: "waveform", label: "Conversation runs", detail: "Speech, interruption, and turn-taking stay native." },
        { icon: "route", label: "Agent acts", detail: "Sauti supplies approved tools and tenant context." },
        { icon: "activity", label: "Outcome lands", detail: "Status, duration, recording, and result are persisted." },
      ],
      result: "Call lifecycle complete",
      resultDetail: "Connected · handled · outcome recorded",
    },
    value: {
      eyebrow: "Clear responsibility",
      title: "A simpler voice architecture is easier to trust.",
      description:
        "Live audio does not bounce between an improvised chain of vendors. Every boundary has one owner and one source of truth.",
      capabilities: [
        { title: "Carrier-grade routing", description: "Handle inbound, outbound, transfer, and hang-up through one call-control provider." },
        { title: "Managed assistants", description: "Keep speech and interruption behavior close to the live media path." },
        { title: "Durable outcomes", description: "Persist the final state even when a browser or dashboard is not open." },
      ],
    },
    safeguards: ["Signed provider webhooks", "Server-only credentials", "Idempotent callback handling"],
    finalTitle: "Bring your real phone workflow into one controlled call path.",
    finalDescription: "Start with one number, one agent, and one measurable call outcome.",
  },
  {
    slug: "speech-and-voice",
    label: "Speech & voice",
    shortLabel: "Speech",
    cardDescription: "Let callers speak naturally across languages, accents, and interruptions.",
    accent: "#6ad7ff",
    icon: "waveform",
    heroPrefix: "Speech that keeps up with ",
    heroHighlight: "real callers.",
    heroSuffix: "",
    description:
      "Use managed transcription and natural Telnyx voices without turning language support into a pile of provider-specific prompts.",
    proof: ["Multilingual speech", "Natural interruption", "Voice preview"],
    providers: [
      { name: "Telnyx", logo: "/logos/telnyx.svg" },
      { name: "Deepgram", logo: "/logos/deepgram.svg" },
    ],
    flow: {
      eyebrow: "One conversational turn",
      title: "Listen, understand, and answer without breaking the rhythm.",
      description:
        "The agent receives structured speech events, keeps the caller's language, and responds with the selected voice.",
      steps: [
        { icon: "waveform", label: "Hear", detail: "Streaming speech captures the caller's current turn." },
        { icon: "message", label: "Transcribe", detail: "Language-aware recognition turns audio into text." },
        { icon: "bot", label: "Respond", detail: "The agent reasons over the same conversation state." },
        { icon: "sparkles", label: "Speak", detail: "The chosen voice delivers the answer naturally." },
      ],
      result: "Conversation stays fluid",
      resultDetail: "Caller language retained · interruption respected",
    },
    value: {
      eyebrow: "Caller experience",
      title: "Language is a runtime choice, not a hard-coded workflow.",
      description:
        "Sauti keeps business operations language-neutral while the speech layer handles the caller's voice and pronunciation.",
      capabilities: [
        { title: "Language continuity", description: "Carry the detected language through prompts, tools, summaries, and confirmations." },
        { title: "Barge-in aware turns", description: "Stop speaking when the caller interrupts and continue from the new intent." },
        { title: "Voice consistency", description: "Preview and assign supported voices per agent without exposing provider credentials." },
      ],
    },
    safeguards: ["No browser API keys", "Explicit language policy", "Provider-native media handling"],
    finalTitle: "Give every caller a voice experience that feels immediate and familiar.",
    finalDescription: "Test your real language mix, accents, and interruption patterns before rollout.",
  },
  {
    slug: "ai-models",
    label: "AI models",
    shortLabel: "Models",
    cardDescription: "Use model intelligence without giving it unchecked control of business actions.",
    accent: "#a993ff",
    icon: "bot",
    heroPrefix: "Model intelligence with ",
    heroHighlight: "business boundaries.",
    heroSuffix: "",
    description:
      "Sauti gives the model the right context and tools, then keeps confirmations, tenant rules, and factual outcomes authoritative on the server.",
    proof: ["Tool-calling models", "Tenant context", "Server-authorized actions"],
    providers: [
      { name: "OpenAI", logo: "/logos/openai.svg" },
      { name: "Gemini" },
    ],
    flow: {
      eyebrow: "A controlled decision",
      title: "The model proposes. The platform verifies. The tool reports fact.",
      description:
        "Conversation quality and operational safety are separated so changing a model does not rewrite your business rules.",
      steps: [
        { icon: "message", label: "Context", detail: "Agent instructions and conversation state are assembled." },
        { icon: "bot", label: "Decision", detail: "The model chooses a response or an allowed tool." },
        { icon: "shield", label: "Policy", detail: "Sauti checks confirmation, identity, and tenant boundaries." },
        { icon: "activity", label: "Fact", detail: "The tool result becomes the only source of action truth." },
      ],
      result: "Action remains accountable",
      resultDetail: "No false success · no model-supplied authorization",
    },
    value: {
      eyebrow: "Provider flexibility",
      title: "Change the intelligence layer without changing the operating contract.",
      description:
        "Prompts, tools, and state are provider-neutral so the business workflow stays stable as models evolve.",
      capabilities: [
        { title: "Structured tool use", description: "Give models precise schemas instead of asking them to improvise integrations." },
        { title: "Authoritative state", description: "Retain confirmations and pending actions outside the model's conversational memory." },
        { title: "Factual responses", description: "Announce success only when the underlying tool reports that the action happened." },
      ],
    },
    safeguards: ["Confirmation gates", "Tenant-scoped tools", "Provider-neutral state"],
    finalTitle: "Use capable models without outsourcing your operating rules.",
    finalDescription: "Pilot one bounded workflow and inspect every proposed and completed action.",
  },
  {
    slug: "calendars",
    label: "Calendars",
    shortLabel: "Calendars",
    cardDescription: "Check availability first, save the booking, then synchronize calendars.",
    accent: "#59e39d",
    icon: "calendar",
    heroPrefix: "A booking is only real when ",
    heroHighlight: "the system agrees.",
    heroSuffix: "",
    description:
      "Sauti records the booking as the source of truth, applies availability rules, and synchronizes external calendars when they are configured.",
    proof: ["Availability before promise", "Database-first booking", "External synchronization"],
    providers: [
      { name: "Google Calendar", logo: "/logos/google-calendar.svg" },
      { name: "Calendly", logo: "/logos/calendly.svg" },
    ],
    flow: {
      eyebrow: "Booking transaction",
      title: "Check, confirm, persist, then sync.",
      description:
        "The caller never hears a confident promise that depends on an integration silently succeeding later.",
      steps: [
        { icon: "calendar", label: "Check", detail: "Evaluate opening hours, duration, and conflicting bookings." },
        { icon: "message", label: "Confirm", detail: "Review the exact service, time, and caller details." },
        { icon: "database", label: "Save", detail: "Persist the Sauti booking as the primary record." },
        { icon: "route", label: "Sync", detail: "Create or update the configured external calendar event." },
      ],
      result: "Booking confirmed",
      resultDetail: "Sauti record saved · calendar synchronization tracked",
    },
    value: {
      eyebrow: "Reliable scheduling",
      title: "External calendars extend the booking system; they do not define it.",
      description:
        "Your phone agent can still produce a durable booking while integration delivery remains observable and retryable.",
      capabilities: [
        { title: "Live availability", description: "Combine tenant hours, service duration, and calendar conflicts before offering a time." },
        { title: "Change lifecycle", description: "Keep reschedules and cancellations tied to the same booking identity." },
        { title: "Delivery visibility", description: "Show connection health and the last synchronization result to operators." },
      ],
    },
    safeguards: ["Tenant-scoped calendar access", "Encrypted OAuth tokens", "Retryable synchronization"],
    finalTitle: "Make phone bookings dependable before adding another calendar.",
    finalDescription: "Connect one calendar, test create/reschedule/cancel, and verify the stored booking each time.",
  },
  {
    slug: "business-tools",
    label: "Business tools",
    shortLabel: "Business tools",
    cardDescription: "Send useful call outcomes to the systems your team already checks.",
    accent: "#ffb463",
    icon: "briefcase",
    heroPrefix: "The call ended. ",
    heroHighlight: "The work should continue.",
    heroSuffix: "",
    description:
      "Turn bookings, qualified leads, support outcomes, and follow-up requests into structured records and notifications.",
    proof: ["CRM-ready outcomes", "Team notifications", "Durable delivery"],
    providers: [
      { name: "HubSpot", logo: "/logos/hubspot.svg" },
      { name: "Salesforce", logo: "/logos/salesforce.svg" },
      { name: "Slack", logo: "/logos/slack.svg" },
      { name: "Google Sheets", logo: "/logos/google-sheets.svg" },
    ],
    flow: {
      eyebrow: "After-call delivery",
      title: "Translate the conversation into the next piece of work.",
      description:
        "Teams receive the outcome they need instead of another raw transcript to interpret.",
      steps: [
        { icon: "activity", label: "Outcome", detail: "The call finishes with a structured disposition." },
        { icon: "database", label: "Prepare", detail: "Sauti builds a tenant-safe business payload." },
        { icon: "route", label: "Deliver", detail: "The event routes to each enabled workspace connection." },
        { icon: "briefcase", label: "Follow up", detail: "The team sees a CRM record, row, or notification." },
      ],
      result: "Team workflow updated",
      resultDetail: "Lead captured · owner notified · delivery visible",
    },
    value: {
      eyebrow: "Operational follow-through",
      title: "Integrate the outcome, not the noise.",
      description:
        "Each provider receives a purposeful payload shaped for the action its users need to take next.",
      capabilities: [
        { title: "CRM handoff", description: "Upsert contacts and attach useful call context without creating blind duplicates." },
        { title: "Team visibility", description: "Notify the right channel when a booking, escalation, or high-value lead needs attention." },
        { title: "Simple records", description: "Append structured rows for teams that operate from shared spreadsheets." },
      ],
    },
    safeguards: ["Encrypted workspace credentials", "Idempotent deliveries", "Observable retries"],
    finalTitle: "Make every valuable call visible in the system your team already uses.",
    finalDescription: "Choose one outcome and connect only the destination responsible for the next action.",
  },
  {
    slug: "developer-tools",
    label: "Developer tools",
    shortLabel: "Developers",
    cardDescription: "Extend Sauti through explicit APIs, signed events, and controlled agent tools.",
    accent: "#50b9ff",
    icon: "code",
    heroPrefix: "Build the custom workflow. ",
    heroHighlight: "Keep the contract clear.",
    heroSuffix: "",
    description:
      "Use REST APIs, OpenAPI schemas, signed webhooks, and tenant tools without placing internal systems directly in the model's hands.",
    proof: ["REST and OpenAPI", "Signed webhooks", "Explicit tool contracts"],
    providers: [
      { name: "Webhooks", logo: "/logos/webhook.svg" },
      { name: "Zapier", logo: "/logos/zapier.svg" },
    ],
    flow: {
      eyebrow: "Custom tool execution",
      title: "A narrow contract between the conversation and your system.",
      description:
        "The agent asks for one named operation. Your service returns one factual result. Sauti keeps the boundary auditable.",
      steps: [
        { icon: "code", label: "Define", detail: "Describe the operation with an explicit request schema." },
        { icon: "lock", label: "Authorize", detail: "Apply tenant policy and server-side authentication." },
        { icon: "webhook", label: "Execute", detail: "Send the signed request to the configured endpoint." },
        { icon: "activity", label: "Record", detail: "Persist delivery status and return the factual result." },
      ],
      result: "Custom action completed",
      resultDetail: "Signed request · validated response · audit trail",
    },
    value: {
      eyebrow: "Developer experience",
      title: "Extend the platform without creating an invisible integration maze.",
      description:
        "Contracts are documented, workspace-scoped, and observable from configuration through delivery.",
      capabilities: [
        { title: "Documented APIs", description: "Use stable request and response models instead of scraping dashboard behavior." },
        { title: "Signed events", description: "Verify that outbound call events originated from Sauti." },
        { title: "Controlled tools", description: "Expose only the business operations an agent is allowed to perform." },
      ],
    },
    safeguards: ["HTTPS validation", "HMAC or bearer authentication", "Secret-free API responses"],
    finalTitle: "Connect the system only your business has—without weakening the call boundary.",
    finalDescription: "Start with one narrow operation, one idempotency rule, and one observable outcome.",
  },
];

export function marketingIntegrationFor(slug: string) {
  return marketingIntegrations.find((integration) => integration.slug === slug);
}

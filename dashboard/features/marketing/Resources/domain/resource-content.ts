export type ResourceSlug =
  | "documentation"
  | "api-reference"
  | "blog"
  | "case-studies"
  | "faqs"
  | "security";

export type ResourceIconKey =
  | "activity"
  | "analytics"
  | "api"
  | "article"
  | "book"
  | "calendar"
  | "calls"
  | "check"
  | "clock"
  | "code"
  | "database"
  | "document"
  | "faq"
  | "globe"
  | "key"
  | "lock"
  | "route"
  | "search"
  | "shield"
  | "sparkles"
  | "users"
  | "webhook";

export type ResourceNavItem = {
  slug: ResourceSlug;
  label: string;
  shortDescription: string;
  icon: ResourceIconKey;
};

export const resourceNavItems: ResourceNavItem[] = [
  { slug: "documentation", label: "Documentation", shortDescription: "Guides and how-tos", icon: "document" },
  { slug: "api-reference", label: "API Reference", shortDescription: "REST APIs and webhooks", icon: "api" },
  { slug: "blog", label: "Blog", shortDescription: "Product updates and insights", icon: "article" },
  { slug: "case-studies", label: "Case Studies", shortDescription: "Implementation patterns", icon: "analytics" },
  { slug: "faqs", label: "FAQs", shortDescription: "Answers to common questions", icon: "faq" },
  { slug: "security", label: "Security", shortDescription: "Policies and architecture", icon: "lock" },
];

export const audienceOptions = [
  { id: "owner", label: "Business owner", icon: "users" as ResourceIconKey },
  { id: "builder", label: "Builder", icon: "code" as ResourceIconKey },
  { id: "security", label: "Security review", icon: "shield" as ResourceIconKey },
] as const;

export type AudienceId = (typeof audienceOptions)[number]["id"];

export const searchSuggestions: Record<AudienceId, string[]> = {
  owner: [
    "How do I connect a booking calendar?",
    "How does Sauti answer from my FAQs?",
    "How can I review calls and outcomes?",
  ],
  builder: [
    "How are signed webhooks verified?",
    "Which API creates an outbound call?",
    "How do I configure agent tools?",
  ],
  security: [
    "How is tenant data isolated?",
    "Where are credentials encrypted?",
    "How are recordings and consent controlled?",
  ],
};

export const launchSteps = [
  "Connect a booking calendar",
  "Ground answers with FAQ/RAG",
  "Configure your agent behavior",
  "Make test calls and refine",
  "Go live and monitor",
  "Optimize from outcomes",
];

export const popularJourneys = [
  {
    title: "Book appointments",
    description: "Connect calendars and let your agent schedule with confidence.",
    meta: "5 guides · 20–30 min",
    href: "/resources/documentation#bookings",
    icon: "calendar" as ResourceIconKey,
    tone: "cyan",
  },
  {
    title: "Ground answers in FAQs",
    description: "Use approved business knowledge for accurate, current responses.",
    meta: "4 guides · 15–25 min",
    href: "/resources/documentation#knowledge",
    icon: "faq" as ResourceIconKey,
    tone: "amber",
  },
  {
    title: "Review call analytics",
    description: "Understand conversations, outcomes, and where to improve.",
    meta: "3 guides · 15–20 min",
    href: "/resources/documentation#analytics",
    icon: "analytics" as ResourceIconKey,
    tone: "violet",
  },
];

export const trustLinks = [
  { title: "Tenant isolation", description: "Customer data is scoped to its workspace.", icon: "shield" as ResourceIconKey, href: "/resources/security#tenant-isolation" },
  { title: "Encrypted credentials", description: "Provider secrets are encrypted at rest.", icon: "lock" as ResourceIconKey, href: "/resources/security#credentials" },
  { title: "Signed webhooks", description: "Provider events are authenticated before use.", icon: "webhook" as ResourceIconKey, href: "/resources/security#webhooks" },
  { title: "Consent & recordings", description: "Recording behavior follows explicit controls.", icon: "calls" as ResourceIconKey, href: "/resources/security#recordings" },
  { title: "Public health status", description: "Check the live Sauti service health endpoint.", icon: "activity" as ResourceIconKey, href: "/health" },
];

export type ResourceSection = {
  id: string;
  eyebrow: string;
  title: string;
  description: string;
  icon: ResourceIconKey;
  bullets: string[];
};

export type ResourcePageContent = {
  slug: ResourceSlug;
  label: string;
  eyebrow: string;
  title: string;
  description: string;
  icon: ResourceIconKey;
  accent: "cyan" | "violet" | "mint" | "amber";
  primaryLabel: string;
  primaryHref: string;
  readingTime: string;
  audience: string;
  sections: ResourceSection[];
  related: ResourceSlug[];
};

export const resourcePages: Record<ResourceSlug, ResourcePageContent> = {
  documentation: {
    slug: "documentation",
    label: "Documentation",
    eyebrow: "Build the operating foundation",
    title: "Launch a multilingual voice agent with a clear path from setup to live calls.",
    description: "Follow Sauti’s core concepts, configure the knowledge and actions your agent may use, test the complete call path, and launch with visible outcomes.",
    icon: "document",
    accent: "cyan",
    primaryLabel: "Start the launch guide",
    primaryHref: "#agent-setup",
    readingTime: "30–45 min",
    audience: "Business owners and operators",
    sections: [
      { id: "agent-setup", eyebrow: "01 · Agent setup", title: "Give the agent a role, voice, greeting, and operating boundaries.", description: "Start with the business context and caller outcome the agent is responsible for—not a generic prompt.", icon: "sparkles", bullets: ["Choose supported languages and a compatible managed voice", "Set the greeting, business identity, hours, and escalation path", "Keep required personalisation complete before activation"] },
      { id: "knowledge", eyebrow: "02 · Knowledge", title: "Ground answers in business-approved information.", description: "Use structured FAQs and uploaded reference material so the agent answers from what the business actually knows.", icon: "book", bullets: ["Upload supported knowledge documents", "Keep policies, services, prices, and hours current", "Test retrieval with realistic caller questions"] },
      { id: "bookings", eyebrow: "03 · Business actions", title: "Connect calendars and explicit agent tools.", description: "Enable only the actions the agent should complete during a call, with confirmation where the consequence requires it.", icon: "calendar", bullets: ["Connect Google Calendar or a supported booking path", "Configure duration, routing, required fields, and confirmations", "Use webhooks or integrations for downstream delivery"] },
      { id: "analytics", eyebrow: "04 · Operate", title: "Test, launch, and learn from every call outcome.", description: "Use test calls before activation, then review transcripts, outcomes, bookings, and trends after launch.", icon: "analytics", bullets: ["Run browser or phone test calls", "Confirm multilingual prompts, tools, and transfers end to end", "Review analytics and call records after launch"] },
    ],
    related: ["api-reference", "faqs", "security"],
  },
  "api-reference": {
    slug: "api-reference",
    label: "API Reference",
    eyebrow: "Build on explicit contracts",
    title: "Connect agents, calls, bookings, analytics, and signed events without guessing.",
    description: "Browse Sauti’s authenticated REST surface and webhook boundaries by the business object you need to read, configure, or operate.",
    icon: "api",
    accent: "violet",
    primaryLabel: "Browse core endpoints",
    primaryHref: "#agents-api",
    readingTime: "Reference",
    audience: "Developers and integration teams",
    sections: [
      { id: "auth-api", eyebrow: "Authentication", title: "/api/v1/auth", description: "Register, authenticate, rotate refresh tokens, and end sessions through the public authentication boundary.", icon: "key", bullets: ["POST /register and /login", "POST /refresh with refresh-token rotation", "POST /logout and password recovery flows"] },
      { id: "agents-api", eyebrow: "Agents", title: "/api/v1/agents", description: "Create and configure tenant-scoped voice agents, upload knowledge, activate them, and start controlled test calls.", icon: "sparkles", bullets: ["GET and POST agent collections", "GET and PUT an agent configuration", "Activate, test, and upload knowledge documents"] },
      { id: "calls-api", eyebrow: "Calls and analytics", title: "/api/v1/calls and /analytics", description: "Read call records and outcomes or initiate an outbound call from an authorized agent.", icon: "calls", bullets: ["Filter calls by date, agent, outcome, or language", "Read transcript and outcome detail", "Retrieve summary, language, outcome, and peak-hour analytics"] },
      { id: "bookings-api", eyebrow: "Bookings and webhooks", title: "/api/v1/bookings and signed callbacks", description: "Read or cancel bookings and verify provider events before they affect workspace state.", icon: "webhook", bullets: ["List and inspect tenant-scoped bookings", "Cancel through the authenticated booking boundary", "Verify callback signatures and handle retries idempotently"] },
    ],
    related: ["documentation", "security", "blog"],
  },
  blog: {
    slug: "blog",
    label: "Blog",
    eyebrow: "Patterns from the product",
    title: "Practical notes for designing voice workflows people can trust.",
    description: "Read product updates and implementation guidance drawn from the operating decisions behind multilingual calls, booking tools, knowledge, and reliable delivery.",
    icon: "article",
    accent: "amber",
    primaryLabel: "Read the latest note",
    primaryHref: "#latest",
    readingTime: "5–8 min each",
    audience: "Operators, product teams, and builders",
    sections: [
      { id: "latest", eyebrow: "Product architecture", title: "Why voice tools should report facts, not imply success.", description: "A reliable voice workflow separates what the model proposes, what Sauti authorizes, and what an external system confirms.", icon: "route", bullets: ["Keep action policies explicit", "Treat provider responses as the source of execution truth", "Record durable outcomes for later inspection"] },
      { id: "multilingual", eyebrow: "Multilingual calls", title: "Design for the caller’s language without losing business rules.", description: "Language, tone, and voice should adapt while workflow constraints, required fields, and safe escalation remain consistent.", icon: "globe", bullets: ["Prepare each supported language deliberately", "Test names, numbers, dates, and business-specific vocabulary", "Keep handoff and emergency behavior unambiguous"] },
      { id: "knowledge-article", eyebrow: "Grounded answers", title: "Turn business knowledge into useful call answers.", description: "The best knowledge experience starts with clear, current source material and tests the questions callers actually ask.", icon: "book", bullets: ["Use approved source documents", "Prefer direct answers before unnecessary data collection", "Review gaps from real call transcripts"] },
      { id: "operations-article", eyebrow: "Operations", title: "Use call outcomes to improve the workflow, not just the prompt.", description: "Analytics reveal where calls complete, transfer, or stall—and whether the underlying tools and policies need refinement.", icon: "analytics", bullets: ["Review outcomes alongside transcripts", "Separate test calls from customer operations", "Tune the whole workflow around recurring friction"] },
    ],
    related: ["documentation", "case-studies", "faqs"],
  },
  "case-studies": {
    slug: "case-studies",
    label: "Case Studies",
    eyebrow: "Reference implementation patterns",
    title: "See how common call journeys map to safe, measurable Sauti workflows.",
    description: "These scenario playbooks show implementation patterns, not claimed customer results. Use them to shape a pilot around the calls your team already receives.",
    icon: "analytics",
    accent: "mint",
    primaryLabel: "Explore the booking pattern",
    primaryHref: "#clinic-booking",
    readingTime: "10 min each",
    audience: "Teams planning a pilot",
    sections: [
      { id: "clinic-booking", eyebrow: "Reference scenario · Healthcare", title: "Appointment booking with clear clinical boundaries.", description: "A clinic agent answers approved front-desk questions, collects booking details, checks availability, and escalates clinical or urgent requests.", icon: "calendar", bullets: ["Ground responses in clinic-approved services and policies", "Create bookings only after caller confirmation", "Transfer sensitive or emergency cases to the configured path"] },
      { id: "service-support", eyebrow: "Reference scenario · Services", title: "After-hours support that captures intent and routes safely.", description: "A service business keeps routine answers and appointment intake available while staff are offline without inventing a resolution.", icon: "clock", bullets: ["Answer known hours, coverage, and service questions", "Collect structured callback or appointment details", "Escalate hazards and high-risk issues immediately"] },
      { id: "lead-qualification", eyebrow: "Reference scenario · Sales", title: "Lead qualification that ends in a visible next step.", description: "A multilingual agent captures intent and fit, then books a consultation or sends the agreed handoff record to the team.", icon: "users", bullets: ["Ask only the fields required for the next action", "Avoid unsupported product, price, or ROI claims", "Record the confirmed booking, callback, or transfer outcome"] },
      { id: "support-desk", eyebrow: "Reference scenario · Support", title: "Grounded issue intake with safe human handoff.", description: "A support agent answers from approved playbooks, captures diagnostic context, and stops before destructive or security-sensitive steps.", icon: "shield", bullets: ["Never request passwords or security codes", "Apply security-incident routing before routine troubleshooting", "Pass a concise context summary to the human queue"] },
    ],
    related: ["documentation", "blog", "faqs"],
  },
  faqs: {
    slug: "faqs",
    label: "FAQs",
    eyebrow: "Answers before you launch",
    title: "Get direct answers about setup, languages, integrations, calls, and controls.",
    description: "Start with the questions teams ask most often while evaluating Sauti, then follow the relevant guide when you need implementation depth.",
    icon: "faq",
    accent: "cyan",
    primaryLabel: "Browse setup answers",
    primaryHref: "#setup-faqs",
    readingTime: "Quick answers",
    audience: "Buyers, operators, and builders",
    sections: [
      { id: "setup-faqs", eyebrow: "Setup", title: "How quickly can I create a first agent?", description: "You can create an agent from a template, complete the required business details, choose a compatible voice, connect the needed tools, and run a test call before activation.", icon: "sparkles", bullets: ["Templates provide operating structure", "Readiness highlights missing required details", "Activation follows successful configuration and testing"] },
      { id: "language-faqs", eyebrow: "Languages and voice", title: "Which languages can an agent use?", description: "Language support belongs to each saved agent and its managed provider binding. Sauti prepares compatible voice and speech behavior for every enabled language.", icon: "globe", bullets: ["Configure only languages the workflow is ready to serve", "Use language-appropriate managed voices", "Test real names, numbers, and caller phrasing"] },
      { id: "integration-faqs", eyebrow: "Integrations", title: "Can Sauti book appointments and update other systems?", description: "Yes. Agents can use explicitly enabled calendar, CRM, webhook, or provider tools within workspace-scoped policies and confirmation rules.", icon: "calendar", bullets: ["Connections belong to the workspace", "Tool enablement belongs to the agent", "Consequential actions require the configured confirmation"] },
      { id: "security-faqs", eyebrow: "Security and data", title: "How does Sauti protect workspace data?", description: "Customer records are tenant-scoped, provider credentials are encrypted at rest, and signed public callbacks are validated before processing.", icon: "shield", bullets: ["No API response exposes stored secrets", "Recordings follow explicit consent controls", "Operational events remain traceable"] },
    ],
    related: ["documentation", "security", "api-reference"],
  },
  security: {
    slug: "security",
    label: "Security",
    eyebrow: "Trust through enforceable boundaries",
    title: "Protect live call operations from workspace access to provider callbacks.",
    description: "Sauti combines tenant-scoped data access, encrypted integration credentials, authenticated public events, explicit call controls, and durable operational records.",
    icon: "shield",
    accent: "mint",
    primaryLabel: "Review the security model",
    primaryHref: "#tenant-isolation",
    readingTime: "15 min review",
    audience: "Security, engineering, and operations",
    sections: [
      { id: "tenant-isolation", eyebrow: "Workspace boundary", title: "Tenant isolation is part of every customer data query.", description: "Authenticated services resolve the current workspace and repositories scope customer records by tenant before returning or changing data.", icon: "database", bullets: ["Agents, calls, bookings, analytics, and integrations are tenant-scoped", "Role and workspace context are enforced at service boundaries", "API responses expose authorized metadata, never another tenant’s records"] },
      { id: "credentials", eyebrow: "Secrets and connections", title: "Provider credentials stay encrypted and server-side.", description: "Integration secrets and OAuth tokens are encrypted at rest. Customer-facing APIs return connection status and safe metadata rather than secret values.", icon: "lock", bullets: ["Encryption happens before persistence", "Secrets are excluded from logs and API responses", "Connections are workspace-scoped and agent enablement remains explicit"] },
      { id: "webhooks", eyebrow: "Public callbacks", title: "Signed events are verified before business logic runs.", description: "Telephony, billing, and managed tool callbacks use provider signatures or a dedicated server-only secret, with bounded and idempotent processing where retries are expected.", icon: "webhook", bullets: ["Reject invalid or missing authentication", "Acknowledge provider timing requirements safely", "Make post-call and external delivery retries idempotent"] },
      { id: "recordings", eyebrow: "Calls and recordings", title: "Consent, retention, and action policies remain explicit.", description: "Recording behavior follows workspace consent settings, while agent tools and payment-related actions use narrowly defined authorization and confirmation rules.", icon: "calls", bullets: ["Do not record without the configured consent path", "Require confirmation for consequential actions", "Keep test calls marked as tests in operational data"] },
    ],
    related: ["documentation", "api-reference", "faqs"],
  },
};

export function resourceFor(slug: string) {
  return resourcePages[slug as ResourceSlug] ?? null;
}

export const resourceSlugs = Object.keys(resourcePages) as ResourceSlug[];

import {
  BarChart3,
  BookOpen,
  Bot,
  BriefcaseBusiness,
  Building2,
  CalendarCheck,
  Code2,
  FileQuestion,
  FileText,
  GraduationCap,
  Headphones,
  HeartPulse,
  Landmark,
  Languages,
  LockKeyhole,
  MessageSquareText,
  Network,
  PhoneCall,
  PlugZap,
  Radio,
  Rocket,
  Route,
  Scissors,
  Stethoscope,
  UsersRound,
  Webhook,
  Workflow,
  Zap,
} from "lucide-react";

export const menuGroups = [
  {
    label: "Solutions",
    href: "/solutions",
    eyebrow: "Business outcomes",
    headline: "Agents designed around the calls your team receives.",
    description: "Deploy focused workflows for booking, support, qualification, escalation, and after-hours coverage.",
    items: [
      ["appointment-booking", "Appointment Booking", "Check availability and book calendar slots during calls.", CalendarCheck],
      ["customer-service", "Customer Service", "Answer common questions and escalate complex calls.", Headphones],
      ["lead-qualification", "Lead Qualification", "Capture caller intent, urgency, budget, and contact details.", MessageSquareText],
      ["human-transfer", "Human Transfer", "Route sensitive calls to the right person with context.", UsersRound],
      ["multilingual-call-handling", "Multilingual Call Handling", "Detect language and respond naturally across supported languages.", Languages],
      ["after-hours-answering", "After-Hours Answering", "Keep booking and support available when the team is offline.", PhoneCall],
    ],
  },
  {
    label: "Integrations",
    href: "/integrations",
    eyebrow: "Connected stack",
    headline: "Connect calls, calendars, models, and business systems.",
    description: "Use trusted providers for voice, speech, AI, calendars, CRM, and developer workflows.",
    items: [
      ["voice-infrastructure", "Voice Infrastructure", "Twilio call handling, media streams, status callbacks, and carrier-grade routing.", PhoneCall],
      ["speech-and-voice", "Speech & Voice", "Deepgram, Cartesia, Intron, and provider routing for multilingual calls.", Radio],
      ["ai-models", "AI Models", "OpenAI, Gemini, Spring AI, prompt construction, and tool calling.", Bot],
      ["calendars", "Calendars", "Google Calendar, Calendly, and custom booking webhooks.", CalendarCheck],
      ["business-tools", "Business Tools", "HubSpot, Salesforce, Slack, Pipedrive, Notion, and team workflows.", BriefcaseBusiness],
      ["developer-tools", "Developer Tools", "REST APIs, OpenAPI docs, signed webhooks, and tenant tools.", Code2],
    ],
  },
  {
    label: "Industries",
    href: "/industries",
    eyebrow: "Use cases",
    headline: "AI phone agents for teams where every call matters.",
    description: "Shape the agent prompt, tools, routing, and compliance stance around your operating environment.",
    items: [
      ["clinics-healthcare", "Clinics & Healthcare", "Book appointments, answer front-desk questions, and escalate sensitive calls.", Stethoscope],
      ["salons-beauty", "Salons & Beauty", "Handle booking requests, reschedules, reminders, and service questions.", Scissors],
      ["real-estate", "Real Estate", "Qualify buyers, capture property interest, and schedule viewings.", Building2],
      ["professional-services", "Professional Services", "Route inquiries, book consultations, and collect intake details.", Landmark],
      ["education", "Education", "Answer admissions questions, schedule callbacks, and route department inquiries.", GraduationCap],
      ["local-businesses", "Local Businesses", "Never miss after-hours calls, new leads, or appointment requests.", Rocket],
    ],
  },
  {
    label: "Resources",
    href: "/resources",
    eyebrow: "Learn and evaluate",
    headline: "Everything buyers and builders need to trust the platform.",
    description: "Documentation, API references, security notes, case studies, and answers for teams evaluating Sauti.",
    items: [
      ["documentation", "Documentation", "Platform concepts, setup guides, agent configuration, and deployment notes.", BookOpen],
      ["api-reference", "API Reference", "REST endpoints, DTOs, OpenAPI docs, and webhook contracts.", FileText],
      ["blog", "Blog", "Product updates, voice AI patterns, booking automation, and implementation guides.", MessageSquareText],
      ["case-studies", "Case Studies", "How teams use Sauti to answer more calls and convert more bookings.", BarChart3],
      ["faqs", "FAQs", "Answers about compliance, billing, integrations, customization, and support.", FileQuestion],
      ["security", "Security", "Tenant isolation, encrypted credentials, signature validation, and operational controls.", LockKeyhole],
    ],
  },
] as const;

export const pricingPage = {
  section: "pricing",
  slug: "pricing",
  label: "Pricing",
  eyebrow: "Plans",
  title: "Pricing built around call volume, agents, and outcomes.",
  description:
    "Start with a pilot, measure call minutes and booked outcomes, then scale agents across teams, locations, and workflows.",
  icon: Zap,
};

export type MenuGroup = (typeof menuGroups)[number];
export type MenuItem = MenuGroup["items"][number];

export function sectionPathFor(group: MenuGroup) {
  return group.label.toLowerCase();
}

function groupMatches(entry: MenuGroup, section: string) {
  return entry.label.toLowerCase() === section || sectionPathFor(entry) === section;
}

export function pageFor(section: string, slug: string) {
  const group = menuGroups.find((entry) => groupMatches(entry, section));
  const item = group?.items.find(([itemSlug]) => itemSlug === slug);
  if (!group || !item) {
    return null;
  }
  const [itemSlug, title, description, icon] = item;
  return {
    section,
    slug: itemSlug,
    group,
    eyebrow: group.eyebrow,
    title,
    description,
    icon,
    href: `/${section}/${itemSlug}`,
  };
}

export function groupFor(section: string) {
  return menuGroups.find((entry) => groupMatches(entry, section)) ?? null;
}

export function paramsFor(section: string) {
  const group = menuGroups.find((entry) => groupMatches(entry, section));
  return group ? group.items.map(([slug]) => ({ slug })) : [];
}

export function allDestinations() {
  return menuGroups.flatMap((group) =>
    group.items.map(([slug, title, description, icon]) => ({
      section: sectionPathFor(group),
      slug,
      title,
      description,
      icon,
      href: `/${sectionPathFor(group)}/${slug}`,
    })),
  );
}

export const destinationAccents = {
  solutions: "service",
  integrations: "webhook",
  industries: "lead",
  resources: "transfer",
  pricing: "ops",
} as const;

export const destinationVisualIcons = [Workflow, Network, Webhook, Route];

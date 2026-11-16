import {
  Building2,
  CalendarCheck,
  GraduationCap,
  Headphones,
  Landmark,
  Languages,
  MessageSquareText,
  PhoneCall,
  Rocket,
  Scissors,
  Stethoscope,
  UsersRound,
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
] as const;

export type MenuGroup = (typeof menuGroups)[number];

function group(label: MenuGroup["label"]) {
  const found = menuGroups.find((entry) => entry.label === label);
  if (!found) throw new Error(`Unknown menu group ${label}`);
  return found;
}

/** Top-level marketing navigation, in display order. */
export const primaryNav: Array<{ kind: "group"; group: MenuGroup } | { kind: "link"; label: string; href: string }> = [
  { kind: "group", group: group("Solutions") },
  { kind: "group", group: group("Industries") },
  { kind: "link", label: "Integrations", href: "/integrations" },
  { kind: "link", label: "Pricing", href: "/pricing" },
];
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

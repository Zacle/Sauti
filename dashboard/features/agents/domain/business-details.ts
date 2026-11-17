/**
 * How each business detail (prompt variable) is edited.
 *
 * Values are always stored as readable text because they are inserted into the
 * prompt as {{key}}; this module only decides which control to show, and how
 * to read and write that text. Lists stay comma-separated because the server
 * splits service catalogues on commas, semicolons and new lines.
 */

export type DetailKind =
  | "text"
  | "longtext"
  | "url"
  | "phone"
  | "address"
  | "hours"
  | "languages"
  | "language"
  | "choice"
  | "multi"
  | "list"
  | "faq"
  | "number"
  | "structured";

export type DetailGroup =
  | "basics"
  | "hours"
  | "voice"
  | "bookings"
  | "calls"
  | "catalog"
  | "policies"
  | "other";

export type DetailSpec = {
  kind: DetailKind;
  group: DetailGroup;
  /** Ready-made answers for choice and multi kinds. */
  options?: string[];
  /** Whether a custom answer may be typed in addition to the options. */
  allowOther?: boolean;
  unit?: string;
  /** Hint shown inside list inputs. */
  itemPlaceholder?: string;
};

export const DETAIL_GROUPS: Array<{ id: DetailGroup; title: string; description: string }> = [
  { id: "basics", title: "Business basics", description: "Who you are and how callers can reach you." },
  { id: "hours", title: "Hours and availability", description: "When you’re open and what happens when you’re not." },
  { id: "voice", title: "Languages and tone", description: "How the agent speaks to callers." },
  { id: "bookings", title: "Bookings", description: "Rules the agent follows when it books." },
  { id: "catalog", title: "Services and team", description: "What you offer, so the agent never invents it." },
  { id: "calls", title: "Transfers and escalation", description: "When and how calls reach a person." },
  { id: "policies", title: "Policies and answers", description: "Approved wording the agent may repeat." },
  { id: "other", title: "Other details", description: "Anything else this agent uses." },
];

const SPECS: Record<string, DetailSpec> = {
  business_name: { kind: "text", group: "basics" },
  business_description: { kind: "longtext", group: "basics" },
  business_address: { kind: "address", group: "basics" },
  business_phone: { kind: "phone", group: "basics" },
  business_website: { kind: "url", group: "basics" },
  booking_link: { kind: "url", group: "bookings" },

  business_hours: { kind: "hours", group: "hours" },
  after_hours_behavior: { kind: "structured", group: "hours" },

  supported_languages: { kind: "languages", group: "voice" },
  fallback_language: { kind: "language", group: "voice" },
  tone: {
    kind: "choice", group: "voice", allowOther: true,
    options: ["Warm and efficient", "Friendly and casual", "Calm and reassuring", "Formal and polished", "Upbeat and energetic"],
  },
  greeting_style: {
    kind: "choice", group: "voice", allowOther: true,
    options: ["Warm and concise", "Friendly and upbeat", "Formal and polished", "Calm and reassuring"],
  },

  appointment_lead_time: {
    kind: "choice", group: "bookings", allowOther: true,
    options: ["No minimum", "30 minutes", "1 hour", "2 hours", "4 hours", "1 day", "2 days"],
  },
  appointment_buffer: {
    kind: "choice", group: "bookings", allowOther: true,
    options: ["No gap", "5 minutes", "10 minutes", "15 minutes", "30 minutes"],
  },
  cancellation_policy: {
    kind: "choice", group: "bookings", allowOther: true,
    options: [
      "Cancel or reschedule at least 24 hours ahead",
      "Cancel or reschedule at least 48 hours ahead",
      "Free cancellation at any time",
      "Late cancellations lose their deposit",
    ],
  },
  confirmation_channels: {
    kind: "choice", group: "bookings",
    options: ["SMS", "Email", "SMS and email", "WhatsApp", "No confirmation"],
  },
  large_party_threshold: { kind: "number", group: "bookings", unit: "people" },

  transfer_number: { kind: "phone", group: "calls" },
  transfer_rules: {
    kind: "multi", group: "calls", allowOther: true,
    options: ["Caller asks for a person", "Urgent or emergency issue", "Complaint", "Billing or payment question", "Agent can’t help"],
  },
  escalation_triggers: {
    kind: "multi", group: "calls", allowOther: true,
    options: ["Caller asks for a person", "Caller is upset or angry", "Safety or emergency risk", "Request outside what the agent handles", "Agent keeps misunderstanding"],
  },
  transfer_retry_policy: {
    kind: "choice", group: "calls", allowOther: true,
    options: [
      "Ring for 20 seconds, then take a message",
      "Ring for 30 seconds, then take a message",
      "Ring for 20 seconds, then offer a callback",
      "Don’t retry; take a message",
    ],
  },

  faq: { kind: "faq", group: "policies" },
  prohibited_statements: {
    kind: "multi", group: "policies", allowOther: true,
    options: [
      "Never diagnose or give medical advice",
      "Never give legal advice",
      "Never give financial advice",
      "Never guarantee results",
      "Never quote prices that aren’t listed",
      "Never share other customers’ details",
    ],
  },
};

const LIST_KEYS = new Set([
  "services", "services_and_prices", "treatments", "bookable_services", "classes", "memberships", "packages",
  "products_and_plans", "service_categories", "service_types", "session_types", "subjects_and_levels",
  "supported_products", "veterinary_services", "coverage_types", "dining_options", "delivery_modes",
  "practice_areas", "staff", "dentists", "accepted_insurance", "maintenance_categories", "authorized_offers",
  "species_served", "jurisdictions", "service_area", "listing_sources",
]);

const LIST_FIELD_KEYS = /(_fields|_details_required|_requirements)$/;
const POLICY_KEYS = /(_policy|_policies|_rules|_instruction|_instructions|_disclaimer|_offer|_boundaries|_playbooks|_wording)$/;

export function detailSpec(key: string): DetailSpec {
  if (SPECS[key]) return SPECS[key];
  if (LIST_KEYS.has(key)) return { kind: "list", group: "catalog", itemPlaceholder: listPlaceholder(key) };
  if (LIST_FIELD_KEYS.test(key)) return { kind: "list", group: "bookings", itemPlaceholder: "Add a detail to collect" };
  if (POLICY_KEYS.test(key)) return { kind: "longtext", group: "policies" };
  if (key.includes("phone")) return { kind: "phone", group: "basics" };
  if (key.includes("address") || key.includes("location")) return { kind: "address", group: "basics" };
  if (key.includes("website") || key.includes("url") || key.endsWith("_link")) return { kind: "url", group: "basics" };
  if (key.endsWith("_threshold") || key.endsWith("_count")) return { kind: "number", group: "bookings" };
  return { kind: "text", group: "other" };
}

function listPlaceholder(key: string) {
  if (key === "staff" || key === "dentists") return "Name – specialty";
  if (key.includes("price")) return "Service – price";
  if (key === "accepted_insurance") return "Insurer or payment option";
  if (key === "service_area") return "Area, city or postcode";
  return "Add an item";
}

/* ---------- Lists ---------- */

/** Splits on commas, semicolons and new lines, but keeps thousands separators ("3,500") together. */
export function parseList(value: string) {
  return value
    .split(/\r?\n|;|,(?!\d{3}(?!\d))/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function serializeList(items: string[]) {
  return items.map((item) => item.trim()).filter(Boolean).join(", ");
}

/* ---------- FAQ ---------- */

export type FaqPair = { question: string; answer: string };

export function parseFaq(value: string): FaqPair[] {
  const text = value.trim();
  if (!text) return [];
  if (/^Q:/im.test(text)) {
    return text
      .split(/\n\s*\n|(?=^Q:)/m)
      .map((block) => {
        const question = block.match(/^Q:\s*(.*)$/im)?.[1]?.trim() ?? "";
        const answer = block.match(/^A:\s*([\s\S]*)$/im)?.[1]?.trim() ?? "";
        return { question, answer };
      })
      .filter((pair) => pair.question || pair.answer);
  }
  // Older free text: "Parking: available behind the building" per line or per sentence group.
  return text.split(/\r?\n|;\s*/).map((line) => line.trim()).filter(Boolean).map((line) => {
    const index = line.indexOf(":");
    return index > 0
      ? { question: line.slice(0, index).trim(), answer: line.slice(index + 1).trim() }
      : { question: "", answer: line };
  });
}

export function serializeFaq(pairs: FaqPair[]) {
  return pairs
    .filter((pair) => pair.question.trim() || pair.answer.trim())
    .map((pair) => `Q: ${pair.question.trim()}\nA: ${pair.answer.trim()}`)
    .join("\n\n");
}

/* ---------- Weekly hours ---------- */

export const WEEK_DAYS = [
  ["Mon", "Monday"], ["Tue", "Tuesday"], ["Wed", "Wednesday"], ["Thu", "Thursday"],
  ["Fri", "Friday"], ["Sat", "Saturday"], ["Sun", "Sunday"],
] as const;

export type DayKey = (typeof WEEK_DAYS)[number][0];
export type ScheduleDay = { enabled: boolean; open: string; close: string };
export type WeeklySchedule = Record<DayKey, ScheduleDay>;

export function emptySchedule(): WeeklySchedule {
  return Object.fromEntries(WEEK_DAYS.map(([day]) => [day, { enabled: false, open: "09:00", close: "17:00" }])) as WeeklySchedule;
}

function normalizeTime(value: string) {
  const [hour, minute = "00"] = value.split(":");
  return `${hour.padStart(2, "0")}:${minute.padStart(2, "0")}`;
}

/** Reads "Mon 09:00-17:00; Tue …" and ranges such as "Mon-Fri 09:00-17:00", like the server does. */
export function parseSchedule(value: string): WeeklySchedule {
  const schedule = emptySchedule();
  const order = WEEK_DAYS.map(([day]) => day);
  for (const segment of value.split(";").map((item) => item.trim()).filter(Boolean)) {
    const match = segment.match(/^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)(?:\s*-\s*(Mon|Tue|Wed|Thu|Fri|Sat|Sun))?\s+(\d{1,2}(?::\d{2})?)\s*-\s*(\d{1,2}(?::\d{2})?)$/i);
    if (!match) continue;
    const first = order.findIndex((day) => day.toLowerCase() === match[1].toLowerCase());
    const last = match[2] ? order.findIndex((day) => day.toLowerCase() === match[2].toLowerCase()) : first;
    for (let step = 0; step < 7; step += 1) {
      const index = (first + step) % 7;
      schedule[order[index]] = { enabled: true, open: normalizeTime(match[3]), close: normalizeTime(match[4]) };
      if (index === last) break;
    }
  }
  return schedule;
}

export function serializeSchedule(schedule: WeeklySchedule) {
  return WEEK_DAYS
    .filter(([day]) => schedule[day].enabled)
    .map(([day]) => `${day} ${schedule[day].open}-${schedule[day].close}`)
    .join("; ");
}

/* ---------- Validation ---------- */

export function detailError(key: string, value: string): string {
  const text = value.trim();
  if (!text) return "";
  const { kind } = detailSpec(key);
  if (kind === "phone") {
    const digits = text.replace(/\D/g, "");
    if (digits.length < 7 || digits.length > 15) return "Enter a phone number with 7 to 15 digits.";
  }
  if (kind === "url") {
    try {
      const url = new URL(text);
      if (url.protocol !== "https:" && url.protocol !== "http:") throw new Error("protocol");
    } catch {
      return "Enter a full web address starting with https://";
    }
  }
  if (kind === "hours") {
    const days = Object.values(parseSchedule(text));
    if (!days.some((day) => day.enabled)) return "Choose at least one open day, or clear the hours.";
    if (days.some((day) => day.enabled && day.open === day.close)) return "Opening and closing times can’t be the same.";
  }
  if (kind === "number" && !/^\d+$/.test(text)) return "Enter a whole number.";
  return "";
}

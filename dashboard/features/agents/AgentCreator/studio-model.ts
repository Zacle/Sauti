
import type { AgentVariableDefinition, AgentTemplate as StoredAgentTemplate } from "@/types/api";
import type { LucideIcon } from "lucide-react";
import { DEFAULT_BOOKING_FIELDS, DEFAULT_BOOKING_NOTIFICATIONS } from "@/features/agents/domain/agent-form";
export { DEFAULT_BOOKING_FIELDS, DEFAULT_BOOKING_NOTIFICATIONS, type AfterHoursBehavior } from "@/features/agents/domain/agent-form";
import {
BookOpen,
Bot,
BriefcaseBusiness,
CalendarCheck,
Camera,
Car,
Check,
Clock3,
GraduationCap,
Headphones,
History as HistoryIcon,
House,
Languages,
LifeBuoy,
MessageSquareText,
PackageSearch,
PawPrint,
PhoneCall,
Plug,
Presentation,
RadioTower,
Route,
Settings2,
ShieldCheck,
ShoppingCart,
Sparkles,
UsersRound,
Utensils
} from "lucide-react";


export type Template = {
  id: string;
  name: string;
  category: string;
  group: "Appointments" | "Support" | "Sales" | "Custom";
  description: string;
  icon: LucideIcon;
  greeting: string;
  prompt: string;
  bookingEnabled: boolean;
  source: "stored" | "custom";
  defaultLanguage: string;
  supportedLanguages: string[];
  escalationPhrases: string[];
  variables: AgentVariableDefinition[];
  defaultBookingDurationMinutes: number;
  bookingRequiredFields: string[];
  bookingNotificationChannels: string[];
};

export const SYSTEM_MANAGED_TEMPLATE_VARIABLES = new Set([
  "business_timezone",
  "calendar_system",
  "calendar_provider",
  "routing_policy",
  "required_booking_fields",
  "notification_channels",
]);

export type WeekDayKey = "monday" | "tuesday" | "wednesday" | "thursday" | "friday" | "saturday" | "sunday";
export type DayHours = { enabled: boolean; start: string; end: string };
export type WeeklyHours = Record<WeekDayKey, DayHours>;

export const OPERATING_WEEK_DAYS: Array<{ key: WeekDayKey; label: string }> = [
  { key: "monday", label: "Monday" },
  { key: "tuesday", label: "Tuesday" },
  { key: "wednesday", label: "Wednesday" },
  { key: "thursday", label: "Thursday" },
  { key: "friday", label: "Friday" },
  { key: "saturday", label: "Saturday" },
  { key: "sunday", label: "Sunday" },
];

export const CORE_BOOKING_FIELDS = new Set(DEFAULT_BOOKING_FIELDS);
export const BOOKING_FIELD_CATALOG = [
  "caller_email", "reason_for_visit", "date_of_birth", "patient_date_of_birth", "preferred_staff",
  "location", "party_size", "seating_preference", "dietary_accessibility_notes", "pet_name",
  "pet_species", "pet_breed", "pet_age", "property_reference", "buyer_or_renter", "financing_status",
  "move_timeline", "service_address", "issue_description", "urgency_level", "vehicle_make_model",
  "vehicle_year", "vehicle_registration", "student_name", "subject", "grade_level", "learning_goal",
  "order_number", "account_number", "company_name", "company_size", "budget_range", "notes",
] as const;

/**
 * The neutral starting point. Templates come only from the backend catalog
 * (SystemAgentTemplateSeeder plus workspace templates), so there is one source of truth.
 */
export const BLANK_TEMPLATE: Template = {
  id: "blank",
  name: "Blank agent",
  category: "Custom",
  group: "Custom",
  description: "Start without predefined behavior.",
  icon: Bot,
  greeting: "Open naturally in the caller's language. Introduce yourself as {{agent_name}} once, and ask one simple question about what the caller needs.",
  prompt: "",
  bookingEnabled: false,
  source: "custom",
  defaultLanguage: "en",
  supportedLanguages: ["en"],
  escalationPhrases: ["speak to a person", "talk to a human", "human agent"],
  variables: [],
  defaultBookingDurationMinutes: 60,
  bookingRequiredFields: [],
  bookingNotificationChannels: ["dashboard"],
};

export const iconMap: Record<string, LucideIcon> = {
  stethoscope: Headphones,
  smile: Headphones,
  scissors: Sparkles,
  scale: ShieldCheck,
  building: UsersRound,
  dumbbell: Clock3,
  wrench: Settings2,
  utensils: CalendarCheck,
  sparkles: Sparkles,
  landmark: ShieldCheck,
  car: Car,
  restaurant: Utensils,
  veterinary: PawPrint,
  property: House,
  camera: Camera,
  education: GraduationCap,
  helpdesk: LifeBuoy,
  orders: PackageSearch,
  technical: Settings2,
  telecom: RadioTower,
  insurance: ShieldCheck,
  demo: Presentation,
  commerce: ShoppingCart,
  sales: BriefcaseBusiness,
  bot: Bot,
};

export function mapStoredTemplate(template: StoredAgentTemplate): Template {
  let bookingEnabled = false;
  let group: Template["group"] = "Appointments";
  let icon: LucideIcon = Bot;
  let escalationPhrases = ["speak to a person", "talk to a human", "human agent"];
  let variables: AgentVariableDefinition[] = [];
  let defaultBookingDurationMinutes = 60;
  let bookingRequiredFields = DEFAULT_BOOKING_FIELDS;
  let bookingNotificationChannels = DEFAULT_BOOKING_NOTIFICATIONS;
  try {
    const configuration = JSON.parse(template.configurationJson) as {
      bookingEnabled?: boolean;
      group?: Template["group"];
      icon?: string;
      escalationPhrases?: string[];
      variables?: AgentVariableDefinition[];
      bookingDurationMinutes?: number;
      bookingRequiredFields?: string[];
      bookingNotificationChannels?: string[];
    };
    bookingEnabled = Boolean(configuration.bookingEnabled);
    group = configuration.group ?? group;
    icon = iconMap[configuration.icon ?? "bot"] ?? Bot;
    escalationPhrases = configuration.escalationPhrases ?? escalationPhrases;
    variables = Array.isArray(configuration.variables)
      ? configuration.variables.filter((variable) => !SYSTEM_MANAGED_TEMPLATE_VARIABLES.has(variable.key))
      : [];
    defaultBookingDurationMinutes = Number.isInteger(configuration.bookingDurationMinutes)
      && Number(configuration.bookingDurationMinutes) >= 5
      && Number(configuration.bookingDurationMinutes) <= 480
      ? Number(configuration.bookingDurationMinutes)
      : 60;
    bookingRequiredFields = Array.isArray(configuration.bookingRequiredFields)
      ? configuration.bookingRequiredFields
      : bookingRequiredFields;
    bookingNotificationChannels = Array.isArray(configuration.bookingNotificationChannels)
      ? configuration.bookingNotificationChannels
      : bookingNotificationChannels;
  } catch {
    bookingEnabled = false;
  }
  return {
    id: template.id,
    name: template.name,
    category: template.category,
    group,
    description: template.description,
    icon,
    greeting: template.greetingMessage,
    prompt: template.systemPrompt,
    bookingEnabled,
    source: "stored",
    defaultLanguage: template.defaultLanguage,
    supportedLanguages: template.supportedLanguages,
    escalationPhrases,
    variables,
    defaultBookingDurationMinutes,
    bookingRequiredFields,
    bookingNotificationChannels,
  };
}

export const studioSections = [
  { id: "main", label: "Main settings", description: "Name, purpose, voice & more", icon: Settings2 },
  { id: "behavior", label: "Prompt", description: "Instructions and conversation rules", icon: MessageSquareText },
  { id: "speech", label: "Speech & transcription", description: "Voice and recognition", icon: Languages },
  { id: "calls", label: "Call behaviour", description: "Silence, voicemail & DTMF", icon: PhoneCall },
  { id: "routing", label: "Routing", description: "Transfers and operating hours", icon: Route },
  { id: "integrations", label: "Integrations", description: "Connected business tools", icon: Plug },
  { id: "knowledge", label: "Knowledge", description: "Documents and approved answers", icon: BookOpen },
  { id: "postcall", label: "Post-call", description: "Outcomes and extraction", icon: Check },
  { id: "history", label: "Versions", description: "Changes and restore", icon: HistoryIcon },
] as const;

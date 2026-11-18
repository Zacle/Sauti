"use client";

import "./AgentCreator.css";
import "./AgentStudio.css";
import "./AgentStudioLayout.css";
import "./StudioOverlays.css";
import Link from "next/link";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import type { LucideIcon } from "lucide-react";
import { toast } from "sonner";
import { Button, buttonVariants } from "@/components/ui/button";
import { Alert, Textarea } from "@/components/ui/field";
import { Badge, Checkbox, RadioCard, RadioDot, RadioGroup, Tabs, TabsList, TabsTrigger, Tooltip } from "@/components/ui/primitives";
import { cn } from "@/lib/utils";
import { IntegrationLogo } from "@/features/integrations/presentation/IntegrationLogo";
import {
  Chip,
  ChipGroup,
  Field,
  FieldGrid,
  Meta,
  Note,
  NumberField,
  RowList,
  SliderField,
  StudioGroup,
  StudioInput,
  StudioSection,
  SwitchRow,
  studioSelectClass,
} from "./studio-kit";
import { ApiError } from "@/lib/api/client";
import { LoadFailure } from "@/features/errors/ConsoleErrors";
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Bot,
  BriefcaseBusiness,
  CalendarCheck,
  Camera,
  Car,
  Check,
  ChevronDown,
  CircleAlert,
  ExternalLink,
  FileText,
  FileUp,
  Clock3,
  Headphones,
  House,
  GraduationCap,
  Languages,
  LifeBuoy,
  LoaderCircle,
  MessageSquareText,
  Phone,
  PhoneCall,
  Play,
  Plug,
  Plus,
  PawPrint,
  PackageSearch,
  Presentation,
  RadioTower,
  RefreshCw,
  Route,
  Settings2,
  ShieldCheck,
  ShoppingCart,
  Sparkles,
  UsersRound,
  WandSparkles,
  Trash2,
  Utensils,
  X,
  ArrowUpRight,
  Copy,
  Eye,
  PencilLine,
  PanelRightClose,
} from "lucide-react";
import {
  activateAgent,
  createAgent,
  createAgentVariable,
  createAgentFromTemplate,
  deleteAgent,
  deleteKnowledgeDocument,
  generateAgentFromBrief,
  getAgent,
  getAgentReadiness,
  listAvailableAgentNumbers,
  listAgentVariables,
  listAgentTemplates,
  listKnowledgeDocuments,
  provisionAgentNumber,
  refreshAgentPhoneNumber,
  updateAgent,
  updateAgentTimezone,
  updateAgentVariables,
  uploadKnowledgeDocument,
} from "@/lib/api/agents";
import { loadWorkspaceCallDefaults, loadWorkspaceProfile } from "@/lib/api/tenant";
import type { Agent, AgentReadiness, AgentTemplate as StoredAgentTemplate, AgentVariableDefinition, AvailablePhoneNumber, CreateAgentVariable, KnowledgeDocument } from "@/types/api";
import { useAuth } from "@/hooks/useAuth";
import { COUNTRIES } from "@/lib/countries";
import { VoicePicker } from "./VoicePicker";
import { TestCallPanel } from "./TestCallPanel";
import { PersonalisationDrawer } from "./Personalisation";
import { LANGUAGE_OPTIONS, TIMEZONE_OPTIONS, bookingDurationLabel, bookingDurationOptions } from "../domain/agent-options";
import { getAgentIntegrations, getIntegrationCatalog, type AgentIntegration, type IntegrationCatalogEntry } from "@/lib/api/integrations";
import { DeleteAgentDialog } from "@/features/agents/DeleteAgentDialog/DeleteAgentDialog";
import { Select } from "@/components/ui/select";
import { listCountryCallingCodes, type CountryCallingCode } from "@/lib/api/phone-numbers";

type Template = {
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

const SYSTEM_MANAGED_TEMPLATE_VARIABLES = new Set([
  "business_timezone",
  "calendar_system",
  "calendar_provider",
  "routing_policy",
  "required_booking_fields",
  "notification_channels",
]);

type AfterHoursBehavior = "answer" | "take_message" | "closed";
type WeekDayKey = "monday" | "tuesday" | "wednesday" | "thursday" | "friday" | "saturday" | "sunday";
type DayHours = { enabled: boolean; start: string; end: string };
type WeeklyHours = Record<WeekDayKey, DayHours>;

const OPERATING_WEEK_DAYS: Array<{ key: WeekDayKey; label: string }> = [
  { key: "monday", label: "Monday" },
  { key: "tuesday", label: "Tuesday" },
  { key: "wednesday", label: "Wednesday" },
  { key: "thursday", label: "Thursday" },
  { key: "friday", label: "Friday" },
  { key: "saturday", label: "Saturday" },
  { key: "sunday", label: "Sunday" },
];

const DEFAULT_BOOKING_FIELDS = ["caller_name", "caller_phone", "service_type", "appointment_at"];
const DEFAULT_BOOKING_NOTIFICATIONS = ["dashboard", "email"];
const CORE_BOOKING_FIELDS = new Set(DEFAULT_BOOKING_FIELDS);
const BOOKING_FIELD_CATALOG = [
  "caller_email", "reason_for_visit", "date_of_birth", "patient_date_of_birth", "preferred_staff",
  "location", "party_size", "seating_preference", "dietary_accessibility_notes", "pet_name",
  "pet_species", "pet_breed", "pet_age", "property_reference", "buyer_or_renter", "financing_status",
  "move_timeline", "service_address", "issue_description", "urgency_level", "vehicle_make_model",
  "vehicle_year", "vehicle_registration", "student_name", "subject", "grade_level", "learning_goal",
  "order_number", "account_number", "company_name", "company_size", "budget_range", "notes",
] as const;

const templates: Template[] = [
  {
    id: "appointment",
    name: "Appointment Booker",
    category: "Appointments",
    group: "Appointments",
    description: "Checks availability, collects caller details, and confirms bookings.",
    icon: CalendarCheck,
    greeting: "Open naturally in the caller's language. Introduce yourself as {{agent_name}} once, sound like a warm receptionist, and ask one simple question about what the caller wants to book.",
    prompt: `You are a professional appointment booking assistant.

GOALS
• Understand what the caller wants to book.
• Collect their name, preferred date and time, contact details, and relevant notes.
• Check availability before offering or confirming a time.
• Read the final details back and ask for confirmation.

CONVERSATION STYLE
• Be warm, concise, and professional.
• Ask one question at a time.
• Never invent availability or confirm a booking before the calendar tool succeeds.
• Offer a human transfer when the request is outside your scope.`,
    bookingEnabled: true,
    source: "custom", defaultLanguage: "en", supportedLanguages: ["en", "fr", "ar"],
    escalationPhrases: ["speak to a person", "talk to a human", "human agent"],
    variables: [],
    defaultBookingDurationMinutes: 60,
    bookingRequiredFields: DEFAULT_BOOKING_FIELDS,
    bookingNotificationChannels: DEFAULT_BOOKING_NOTIFICATIONS,
  },
  {
    id: "support",
    name: "Customer Support",
    category: "Support",
    group: "Support",
    description: "Answers common questions, troubleshoots issues, and escalates safely.",
    icon: Headphones,
    greeting: "Open naturally in the caller's language. Introduce yourself as {{agent_name}} once, sound calm and helpful, and ask one simple question about what the caller needs help with.",
    prompt: `You are a calm customer support voice agent.

GOALS
• Identify the caller's issue and confirm your understanding.
• Use approved knowledge to provide accurate, concise guidance.
• Summarize completed steps before ending the call.

RULES
• Ask one question at a time.
• Never guess when information is missing.
• Escalate urgent, sensitive, or unresolved requests to a human.`,
    bookingEnabled: false,
    source: "custom", defaultLanguage: "en", supportedLanguages: ["en", "fr", "ar"],
    escalationPhrases: ["speak to a person", "talk to a human", "human agent"],
    variables: [],
    defaultBookingDurationMinutes: 60,
    bookingRequiredFields: [],
    bookingNotificationChannels: ["dashboard"],
  },
  {
    id: "qualifier",
    name: "Lead Qualifier",
    category: "Sales",
    group: "Sales",
    description: "Qualifies enquiries and routes promising opportunities to the right team.",
    icon: UsersRound,
    greeting: "Open naturally in the caller's language. Introduce yourself as {{agent_name}} once, sound curious and helpful, and ask one simple question about what the caller is looking for.",
    prompt: `You are a helpful lead qualification agent.

GOALS
• Learn the caller's need, location, timeframe, and budget range.
• Ask only the qualification questions relevant to their request.
• Summarize the opportunity and route it to the appropriate team.

RULES
• Be conversational rather than interrogative.
• Do not make pricing or delivery promises.
• Ask permission before collecting contact details.`,
    bookingEnabled: true,
    source: "custom", defaultLanguage: "en", supportedLanguages: ["en", "fr", "ar"],
    escalationPhrases: ["speak to a person", "talk to a human", "human agent"],
    variables: [],
    defaultBookingDurationMinutes: 60,
    bookingRequiredFields: DEFAULT_BOOKING_FIELDS,
    bookingNotificationChannels: DEFAULT_BOOKING_NOTIFICATIONS,
  },
  {
    id: "callback",
    name: "Support Callback",
    category: "Support",
    group: "Support",
    description: "Captures the issue and schedules a callback with the correct specialist.",
    icon: PhoneCall,
    greeting: "Open naturally in the caller's language. Introduce yourself as {{agent_name}} once, sound organized and helpful, and ask one simple question about who or what the caller needs.",
    prompt: `You arrange customer support callbacks.

Collect the caller's name, phone number, issue summary, urgency, and preferred callback time. Confirm every detail before creating the callback. For emergencies or high-risk requests, transfer to a human immediately.`,
    bookingEnabled: true,
    source: "custom", defaultLanguage: "en", supportedLanguages: ["en", "fr", "ar"],
    escalationPhrases: ["speak to a person", "talk to a human", "human agent"],
    variables: [],
    defaultBookingDurationMinutes: 60,
    bookingRequiredFields: DEFAULT_BOOKING_FIELDS,
    bookingNotificationChannels: DEFAULT_BOOKING_NOTIFICATIONS,
  },
];

const iconMap: Record<string, LucideIcon> = {
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

function mapStoredTemplate(template: StoredAgentTemplate): Template {
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

const studioSections = [
  { id: "main", label: "Main settings", description: "Name, purpose, voice & more", icon: Settings2 },
  { id: "behavior", label: "Prompt", description: "Instructions and conversation rules", icon: MessageSquareText },
  { id: "speech", label: "Speech & transcription", description: "Voice and recognition", icon: Languages },
  { id: "calls", label: "Call behaviour", description: "Silence, voicemail & DTMF", icon: PhoneCall },
  { id: "routing", label: "Routing", description: "Transfers and operating hours", icon: Route },
  { id: "integrations", label: "Integrations", description: "Connected business tools", icon: Plug },
  { id: "knowledge", label: "Knowledge", description: "Documents and approved answers", icon: BookOpen },
  { id: "postcall", label: "Post-call", description: "Outcomes and extraction", icon: Check },
] as const;

export function AgentCreator({
  agentId,
  openPersonalisation = false,
  openPhonePicker = false,
}: {
  agentId?: string;
  openPersonalisation?: boolean;
  openPhonePicker?: boolean;
} = {}) {
  const router = useRouter();
  const { session } = useAuth();
  const editing = Boolean(agentId);
  const [stage, setStage] = useState<"templates" | "studio">(editing ? "studio" : "templates");
  const [brief, setBrief] = useState("");
  const [availableTemplates, setAvailableTemplates] = useState<Template[]>(templates);
  const [category, setCategory] = useState("All");
  const [selectedTemplate, setSelectedTemplate] = useState<Template>(templates[0]);
  const [activeSection, setActiveSection] = useState<(typeof studioSections)[number]["id"]>("main");
  const [channelFocusRequest, setChannelFocusRequest] = useState(0);
  const [name, setName] = useState("Amina");
  const [description, setDescription] = useState(templates[0].description);
  const [language, setLanguage] = useState(templates[0].defaultLanguage);
  const [supportedLanguages, setSupportedLanguages] = useState(templates[0].supportedLanguages);
  const [voice, setVoice] = useState("");
  const [timezone, setTimezone] = useState("Africa/Nairobi");
  const [greeting, setGreeting] = useState(templates[0].greeting);
  const [prompt, setPrompt] = useState(templates[0].prompt);
  const [bookingEnabled, setBookingEnabled] = useState(true);
  const [defaultBookingDurationMinutes, setDefaultBookingDurationMinutes] = useState(60);
  const [workspaceBookingDurationMinutes, setWorkspaceBookingDurationMinutes] = useState(60);
  const [maxDuration, setMaxDuration] = useState("5");
  const [saveTranscript, setSaveTranscript] = useState(true);
  const [recordCalls, setRecordCalls] = useState(false);
  const [transferEnabled, setTransferEnabled] = useState(false);
  const [transferNumber, setTransferNumber] = useState("");
  const [escalationPhrases, setEscalationPhrases] = useState(templates[0].escalationPhrases.join(", "));
  const [operatingHours, setOperatingHours] = useState("always");
  const [afterHoursBehavior, setAfterHoursBehavior] = useState<AfterHoursBehavior>("answer");
  const [afterHoursMessage, setAfterHoursMessage] = useState("");
  const [knowledgeBase, setKnowledgeBase] = useState("");
  const [llmTier, setLlmTier] = useState<"standard" | "advanced">("standard");
  const [bargeInSensitivity, setBargeInSensitivity] = useState(0.7);
  const [bargeInGraceMs, setBargeInGraceMs] = useState(300);
  const [endCallOnSilenceSeconds, setEndCallOnSilenceSeconds] = useState(60);
  const [reminderAfterSilenceSeconds, setReminderAfterSilenceSeconds] = useState(30);
  const [maxReminders, setMaxReminders] = useState(1);
  const [detectVoicemail, setDetectVoicemail] = useState(true);
  const [handleCallScreening, setHandleCallScreening] = useState(true);
  const [dtmfEnabled, setDtmfEnabled] = useState(false);
  const [dtmfTerminationKey, setDtmfTerminationKey] = useState<"#" | "*">("#");
  const [dtmfInputTimeoutSeconds, setDtmfInputTimeoutSeconds] = useState(5);
  const [dtmfMaxDigits, setDtmfMaxDigits] = useState(8);
  const [dtmfDigitMappings, setDtmfDigitMappings] = useState<Record<string, string>>({});
  const [webVoiceEnabled, setWebVoiceEnabled] = useState(false);
  const [webVoiceAllowedOrigins, setWebVoiceAllowedOrigins] = useState("");
  const [webVoiceRequireConsent, setWebVoiceRequireConsent] = useState(true);
  const [whatsappEnabled, setWhatsappEnabled] = useState(false);
  const [whatsappPhoneNumberId, setWhatsappPhoneNumberId] = useState("");
  const [sttEndpointingMs, setSttEndpointingMs] = useState(300);
  const [sttVocabularyDomain, setSttVocabularyDomain] = useState("general");
  const [sttBoostedKeywords, setSttBoostedKeywords] = useState("");
  const [safetyGuardrails, setSafetyGuardrails] = useState<string[]>([]);
  const [postCallExtractionFields, setPostCallExtractionFields] = useState(["summary", "successful", "sentiment", "intent"]);
  const [bookingRequiredFields, setBookingRequiredFields] = useState<string[]>(DEFAULT_BOOKING_FIELDS);
  const [bookingNotificationChannels, setBookingNotificationChannels] = useState<string[]>(DEFAULT_BOOKING_NOTIFICATIONS);
  const [bookingNotificationRecipient, setBookingNotificationRecipient] = useState("");
  const [variableValues, setVariableValues] = useState<Record<string, string>>({});
  const [phoneCountries, setPhoneCountries] = useState<CountryCallingCode[]>([]);
  const [customVariables, setCustomVariables] = useState<AgentVariableDefinition[]>([]);
  const [existingVariableKeys, setExistingVariableKeys] = useState<string[]>([]);
  const [showPersonalisation, setShowPersonalisation] = useState(openPersonalisation);
  const [loadingAgent, setLoadingAgent] = useState(editing);
  const [loadFailure, setLoadFailure] = useState<{ status?: number; message: string } | null>(null);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [saved, setSaved] = useState(false);
  // The live test panel: open by default only when there is room for it beside the form.
  const [testPanelOpen, setTestPanelOpen] = useState(false);
  useEffect(() => { setTestPanelOpen(window.innerWidth >= 1560); }, []);
  const [loadedAgent, setLoadedAgent] = useState<Agent | null>(null);
  const [readiness, setReadiness] = useState<AgentReadiness | null>(null);
  const [showDelete, setShowDelete] = useState(false);
  const [showNumberPicker, setShowNumberPicker] = useState(openPhonePicker);
  const [deleteError, setDeleteError] = useState("");
  const [generating, setGenerating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const studioFormRef = useRef<HTMLFormElement>(null);
  const openPhoneAfterSaveRef = useRef(false);

  useEffect(() => {
    listCountryCallingCodes().then(setPhoneCountries).catch(() => setPhoneCountries([]));
  }, []);

  useEffect(() => {
    if (editing) return;
    Promise.all([loadWorkspaceProfile(), loadWorkspaceCallDefaults()]).then(([profile, defaults]) => {
      setTimezone(profile.timezone);
      setDefaultBookingDurationMinutes(profile.defaultBookingDurationMinutes);
      setWorkspaceBookingDurationMinutes(profile.defaultBookingDurationMinutes);
      setSaveTranscript(defaults.saveTranscript);
      setRecordCalls(defaults.recordCalls);
      setBargeInSensitivity(defaults.bargeInSensitivity);
    }).catch(() => {
      // Keep safe local defaults when workspace preferences are unavailable.
    });
  }, [editing]);

  useEffect(() => {
    listAgentTemplates()
      .then((storedTemplates) => {
        if (storedTemplates.length) {
          setAvailableTemplates(storedTemplates.map(mapStoredTemplate));
        }
      })
      .catch(() => {
        // Keep the bundled preview templates when the backend is unavailable.
      });
  }, []);

  useEffect(() => {
    if (!agentId) return;
    setLoadingAgent(true);
    setLoadFailure(null);
    Promise.all([getAgent(agentId), listAgentVariables(agentId), getAgentReadiness(agentId)])
      .then(([agent, variables, agentReadiness]) => {
        const definitions = variables.map((variable) => ({
          key: variable.key,
          label: variable.label,
          description: variable.description ?? "",
          required: variable.required,
        }));
        setSelectedTemplate({
          id: `saved-${agent.id}`,
          name: "Saved agent",
          category: "Custom",
          group: "Custom",
          description: agent.description,
          icon: Bot,
          greeting: agent.greetingMessage,
          prompt: agent.systemPrompt,
          bookingEnabled: agent.bookingEnabled,
          source: "custom",
          defaultLanguage: agent.defaultLanguage,
          supportedLanguages: agent.supportedLanguages,
          escalationPhrases: agent.escalationPhrases,
          variables: [],
          defaultBookingDurationMinutes: agent.defaultBookingDurationMinutes,
          bookingRequiredFields: agent.bookingRequiredFields,
          bookingNotificationChannels: agent.bookingNotificationChannels,
        });
        setLoadedAgent(agent);
        setReadiness(agentReadiness);
        setName(agent.name);
        setDescription(agent.description);
        setLanguage(agent.defaultLanguage);
        setSupportedLanguages(agent.supportedLanguages);
        setVoice(agent.ttsVoiceId ?? "");
        setTimezone(agent.timezone);
        setGreeting(agent.greetingMessage);
        setPrompt(agent.systemPrompt);
        setBookingEnabled(agent.bookingEnabled);
        setDefaultBookingDurationMinutes(agent.defaultBookingDurationMinutes);
        setMaxDuration(String(Math.max(1, Math.round(agent.maxCallDurationSeconds / 60))));
        setSaveTranscript(agent.saveTranscript);
        setRecordCalls(agent.recordCalls);
        setTransferEnabled(Boolean(agent.humanTransferNumber));
        setTransferNumber(agent.humanTransferNumber ?? "");
        setEscalationPhrases(agent.escalationPhrases.join(", "));
        setOperatingHours(agent.operatingHours ?? "always");
        setAfterHoursBehavior(agent.afterHoursBehavior ?? "answer");
        setAfterHoursMessage(agent.afterHoursMessage ?? "");
        setKnowledgeBase(agent.knowledgeBase ?? "");
        setLlmTier(agent.llmTier);
        setBargeInSensitivity(agent.bargeInSensitivity);
        setBargeInGraceMs(agent.bargeInGraceMs);
        setEndCallOnSilenceSeconds(agent.endCallOnSilenceSeconds);
        setReminderAfterSilenceSeconds(agent.reminderAfterSilenceSeconds);
        setMaxReminders(agent.maxReminders);
        setDetectVoicemail(agent.detectVoicemail);
        setHandleCallScreening(agent.handleCallScreening);
        setDtmfEnabled(agent.dtmfEnabled);
        setDtmfTerminationKey(agent.dtmfTerminationKey);
        setDtmfInputTimeoutSeconds(agent.dtmfInputTimeoutSeconds);
        setDtmfMaxDigits(agent.dtmfMaxDigits);
        setDtmfDigitMappings(agent.dtmfDigitMappings);
        setWebVoiceEnabled(agent.webVoiceEnabled);
        setWebVoiceAllowedOrigins(agent.webVoiceAllowedOrigins.join("\n"));
        setWebVoiceRequireConsent(agent.webVoiceRequireConsent);
        setWhatsappEnabled(agent.whatsappEnabled);
        setWhatsappPhoneNumberId(agent.whatsappPhoneNumberId ?? "");
        setSttEndpointingMs(agent.sttEndpointingMs);
        setSttVocabularyDomain(agent.sttVocabularyDomain ?? "general");
        setSttBoostedKeywords(agent.sttBoostedKeywords ?? "");
        setSafetyGuardrails(agent.safetyGuardrails);
        setPostCallExtractionFields(agent.postCallExtractionFields);
        setBookingRequiredFields(agent.bookingRequiredFields);
        setBookingNotificationChannels(agent.bookingNotificationChannels);
        setBookingNotificationRecipient(agent.bookingNotificationRecipient ?? "");
        setCustomVariables(definitions);
        setExistingVariableKeys(definitions.map((variable) => variable.key));
        setVariableValues({
          ...Object.fromEntries(variables.map((variable) => [variable.key, variable.value])),
          ...(agent.calendarProvider ? { calendar_provider: agent.calendarProvider } : {}),
          ...(agent.routingPolicy ? { routing_policy: agent.routingPolicy } : {}),
        });
        setStage("studio");
      })
      .catch((caught) => setLoadFailure({
        status: caught instanceof ApiError ? caught.status : undefined,
        message: caught instanceof Error ? caught.message : "We couldn’t load this agent. Please try again.",
      }))
      .finally(() => setLoadingAgent(false));
  }, [agentId, loadAttempt]);

  const categories = ["All", "Appointments", "Support", "Sales"];

  const visibleTemplates = useMemo(
    () => category === "All"
      ? availableTemplates
      : availableTemplates.filter((template) => template.group === category),
    [availableTemplates, category],
  );
  const agentVariables = useMemo(
    () => [...selectedTemplate.variables, ...customVariables],
    [selectedTemplate.variables, customVariables],
  );
  const countryName = COUNTRIES.find((country) => country.code === session?.tenant?.countryCode)?.name
    ?? "your country";

  function selectTemplate(template: Template) {
    setSelectedTemplate(template);
    setDescription(template.description);
    setGreeting(template.greeting);
    setPrompt(template.prompt);
    setBookingEnabled(template.bookingEnabled);
    setLanguage(template.defaultLanguage);
    setSupportedLanguages(Array.from(new Set([template.defaultLanguage, ...template.supportedLanguages])));
    setEscalationPhrases(template.escalationPhrases.join(", "));
    setDefaultBookingDurationMinutes(workspaceBookingDurationMinutes);
    setBookingRequiredFields(template.bookingRequiredFields);
    setBookingNotificationChannels(template.bookingNotificationChannels);
    setBookingNotificationRecipient("");
    setCustomVariables([]);
    setVariableValues(Object.fromEntries(template.variables.map((variable) => [variable.key, ""])));
    setStage("studio");
    setShowPersonalisation(template.variables.length > 0 || template.bookingEnabled);
    setActiveSection("main");
  }

  async function generateFromBrief() {
    if (!brief.trim()) return;
    setGenerating(true);
    setError("");
    try {
      const generated = await generateAgentFromBrief(brief.trim());
      const custom: Template = {
        id: "generated",
        name: generated.name,
        category: "Custom",
        group: "Custom",
        description: generated.description,
        icon: WandSparkles,
        greeting: generated.greetingMessage,
        prompt: generated.systemPrompt,
        bookingEnabled: generated.bookingEnabled,
        source: "custom",
        defaultLanguage: generated.defaultLanguage,
        supportedLanguages: generated.supportedLanguages,
        escalationPhrases: generated.escalationPhrases,
        variables: generated.variables ?? [],
        defaultBookingDurationMinutes: generated.defaultBookingDurationMinutes,
        bookingRequiredFields: generated.bookingEnabled ? DEFAULT_BOOKING_FIELDS : [],
        bookingNotificationChannels: generated.bookingEnabled ? DEFAULT_BOOKING_NOTIFICATIONS : ["dashboard"],
      };
      setName(generated.name);
      selectTemplate(custom);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to generate an agent setup.");
    } finally {
      setGenerating(false);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (!session) {
        router.replace("/agents");
        return;
      }
      const draft = {
        name,
        description,
        greetingMessage: greeting,
        systemPrompt: prompt,
        defaultLanguage: language,
        supportedLanguages: Array.from(new Set([language, ...supportedLanguages])),
        ttsVoiceId: voice,
        humanTransferNumber: transferEnabled && transferNumber.trim() ? transferNumber.trim() : null,
        escalationPhrases: escalationPhrases.split(",").map((phrase) => phrase.trim()).filter(Boolean),
        bookingEnabled,
        defaultBookingDurationMinutes,
        timezone,
        knowledgeBase,
        operatingHours,
        afterHoursBehavior,
        afterHoursMessage: afterHoursMessage.trim() || null,
        maxCallDurationSeconds: Math.max(60, Number(maxDuration || 5) * 60),
        saveTranscript,
        recordCalls,
        llmTier,
        bargeInSensitivity,
        bargeInGraceMs,
        endCallOnSilenceSeconds,
        reminderAfterSilenceSeconds,
        maxReminders,
        detectVoicemail,
        handleCallScreening,
        dtmfEnabled,
        dtmfTerminationKey,
        dtmfInputTimeoutSeconds,
        dtmfMaxDigits,
        dtmfDigitMappings,
        webVoiceEnabled,
        webVoiceAllowedOrigins: webVoiceAllowedOrigins.split(/\r?\n|,/).map((origin) => origin.trim()).filter(Boolean),
        webVoiceRequireConsent,
        whatsappEnabled,
        whatsappPhoneNumberId: whatsappEnabled && whatsappPhoneNumberId.trim() ? whatsappPhoneNumberId.trim() : null,
        sttEndpointingMs,
        sttVocabularyDomain: sttVocabularyDomain === "general" ? null : sttVocabularyDomain,
        sttBoostedKeywords: sttBoostedKeywords.trim() || null,
        safetyGuardrails,
        postCallExtractionFields,
        bookingRequiredFields,
        bookingNotificationChannels,
        bookingNotificationRecipient: bookingNotificationRecipient.trim() || null,
      };
      if (agentId) {
        setLoadedAgent(await updateAgent(agentId, draft));
        const persistedValues = Object.fromEntries(
          Object.entries(variableValues).filter(([key]) => existingVariableKeys.includes(key)),
        );
        if (Object.keys(persistedValues).length) await updateAgentVariables(agentId, persistedValues);
        for (const variable of customVariables.filter((item) => !existingVariableKeys.includes(item.key))) {
          await createAgentVariable(agentId, {
            key: variable.key,
            label: variable.label,
            description: variable.description,
            value: variableValues[variable.key] ?? "",
            required: variable.required,
          });
        }
        setExistingVariableKeys(customVariables.map((variable) => variable.key));
        setReadiness(await getAgentReadiness(agentId));
        markSaved();
        setBusy(false);
        return;
      }
      let created;
      if (selectedTemplate.source === "stored") {
        created = await createAgentFromTemplate(selectedTemplate.id, {
          name,
          timezone,
          humanTransferNumber: draft.humanTransferNumber,
          defaultBookingDurationMinutes,
        });
        await updateAgent(created.id, draft);
        if (selectedTemplate.variables.length) {
          await updateAgentVariables(
            created.id,
            Object.fromEntries(selectedTemplate.variables.map((variable) => [variable.key, variableValues[variable.key] ?? ""])),
          );
        }
      } else {
        created = await createAgent(draft);
      }
      for (const variable of customVariables) {
        await createAgentVariable(created.id, {
          key: variable.key,
          label: variable.label,
          description: variable.description,
          value: variableValues[variable.key] ?? "",
          required: variable.required,
        });
      }
      router.replace(`/agents/${created.id}${openPhoneAfterSaveRef.current ? "?panel=phone" : ""}`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to create the agent.");
      setBusy(false);
    }
  }

  async function continueSetup() {
    if (!agentId || !readiness) return;
    if (readiness.nextStep === "complete_business_details") {
      setShowPersonalisation(true);
      return;
    }
    if (readiness.nextStep === "connect_calendar") {
      setActiveSection("integrations");
      return;
    }
    if (readiness.nextStep === "enable_channel") {
      setActiveSection("main");
      setChannelFocusRequest((current) => current + 1);
      return;
    }
    setBusy(true);
    setError("");
    try {
    if (readiness.nextStep === "assign_phone_number") {
        setShowNumberPicker(true);
      } else if (readiness.nextStep === "activate_agent") {
        setLoadedAgent(await activateAgent(agentId));
      }
      setReadiness(await getAgentReadiness(agentId));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to continue setup.");
    } finally {
      setBusy(false);
    }
  }

  async function removeSavedAgent() {
    if (!agentId) return;
    setBusy(true);
    setDeleteError("");
    try {
      await deleteAgent(agentId);
      router.replace("/agents");
    } catch (caught) {
      setDeleteError(caught instanceof Error ? caught.message : "Unable to delete this agent.");
    } finally {
      setBusy(false);
    }
  }

  const requiredDetailCount = agentVariables.filter(
    (variable) => variable.required && !variableValues[variable.key]?.trim(),
  ).length;
  const needsBusinessDetails = requiredDetailCount > 0
    || readiness?.nextStep === "complete_business_details";
  const setupActionLabel = readiness?.nextStep === "complete_business_details" ? "Complete business details"
    : readiness?.nextStep === "connect_calendar" ? "Connect calendar"
      : readiness?.nextStep === "assign_phone_number" ? "Assign number"
        : readiness?.nextStep === "enable_channel" ? "Enable a channel"
        : readiness?.nextStep === "activate_agent" ? "Activate agent"
          : readiness?.active ? "Active" : "";
  const setupMilestones = [
    Boolean(name.trim() && description.trim()),
    Boolean(voice),
    !needsBusinessDetails,
    !bookingEnabled || Boolean(readiness?.calendarConfigured),
    Boolean(readiness?.channelConfigured),
    Boolean(readiness?.active),
  ];
  const completedMilestones = setupMilestones.filter(Boolean).length;
  const setupProgress = Math.round((completedMilestones / setupMilestones.length) * 100);
  const completedStudioSections = new Set<(typeof studioSections)[number]["id"]>();
  if (name.trim() && description.trim()) completedStudioSections.add("main");
  if (prompt.trim()) completedStudioSections.add("behavior");
  if (voice) completedStudioSections.add("speech");
  if (maxDuration) completedStudioSections.add("calls");
  if (timezone) completedStudioSections.add("routing");
  if (!bookingEnabled || readiness?.calendarConfigured) completedStudioSections.add("integrations");
  if (knowledgeBase.trim()) completedStudioSections.add("knowledge");
  if (postCallExtractionFields.length) completedStudioSections.add("postcall");

  /*
   * Change tracking: a snapshot of everything Save sends. Compared with the
   * snapshot taken after loading or saving, so switches, sliders, pickers and
   * text fields all count as changes, not just native form input.
   */
  const snapshot = JSON.stringify([
    name, description, greeting, prompt, language, supportedLanguages, voice, transferEnabled, transferNumber,
    escalationPhrases, bookingEnabled, defaultBookingDurationMinutes, timezone, knowledgeBase, operatingHours,
    afterHoursBehavior, afterHoursMessage, maxDuration, saveTranscript, recordCalls, llmTier, bargeInSensitivity,
    bargeInGraceMs, endCallOnSilenceSeconds, reminderAfterSilenceSeconds, maxReminders, detectVoicemail,
    handleCallScreening, dtmfEnabled, dtmfTerminationKey, dtmfInputTimeoutSeconds, dtmfMaxDigits, dtmfDigitMappings,
    webVoiceEnabled, webVoiceAllowedOrigins, webVoiceRequireConsent, whatsappEnabled, whatsappPhoneNumberId,
    sttEndpointingMs, sttVocabularyDomain, sttBoostedKeywords, safetyGuardrails, postCallExtractionFields,
    bookingRequiredFields, bookingNotificationChannels, bookingNotificationRecipient, variableValues,
  ]);
  const snapshotRef = useRef(snapshot);
  snapshotRef.current = snapshot;
  const [savedSnapshot, setSavedSnapshot] = useState<string | null>(null);
  useEffect(() => {
    if (!loadingAgent && savedSnapshot === null && stage === "studio") setSavedSnapshot(snapshot);
  }, [loadingAgent, savedSnapshot, snapshot, stage]);
  const dirty = savedSnapshot !== null && snapshot !== savedSnapshot;
  function markSaved() {
    setSaved(true);
    setSavedSnapshot(snapshotRef.current);
  }

  return (
    <>
      {loadingAgent ? (
        <div className="agent-editor-loading"><LoaderCircle className="spin" /> Loading agent configuration...</div>
      ) : loadFailure ? (
        <LoadFailure
          status={loadFailure.status}
          message={loadFailure.message}
          onRetry={() => setLoadAttempt((attempt) => attempt + 1)}
          notFoundTitle="This agent doesn’t exist"
          notFoundDescription="It may have been deleted, or the link belongs to another workspace."
          backHref="/agents"
          backLabel="Back to agents"
        />
      ) : stage === "templates" ? (
        <TemplateSelection
          brief={brief}
          category={category}
          categories={categories}
          templates={visibleTemplates}
          onBriefChange={setBrief}
          onCategoryChange={setCategory}
          onGenerate={generateFromBrief}
          generating={generating}
          error={error}
          onSelect={selectTemplate}
          onBack={() => router.push("/agents")}
        />
      ) : (
        <form className="agent-studio" ref={studioFormRef} onChange={() => {
          if (saved) setSaved(false);
        }} onSubmit={submit}>
          <header className="agent-studio-header">
            <button type="button" onClick={() => editing ? router.push("/agents") : setStage("templates")}>
              <ArrowLeft size={17} /> {editing ? "Agents" : "Templates"}
            </button>
            <div className="agent-studio-title">
              <span className="agent-list-avatar"><Bot size={20} /></span>
              <div><small>{editing ? "Agent configuration" : "Draft agent"}</small><h1>{name || "Unnamed agent"}</h1></div>
            </div>
            <div className="studio-kit ml-auto flex min-w-0 items-center gap-2 font-sans">
              <div className="mr-1 hidden items-center gap-2 whitespace-nowrap text-[0.8rem] text-fg-muted lg:flex" aria-live="polite">
                {editing ? (
                  <span className="inline-flex items-center gap-1.5 font-medium text-fg-soft">
                    <span aria-hidden="true" className={cn("size-2 rounded-full", loadedAgent?.active ? "bg-success shadow-[0_0_0_3px_color-mix(in_oklab,var(--color-success)_20%,transparent)]" : "bg-fg-muted")} />
                    {loadedAgent?.active ? "Live" : "Draft"}
                  </span>
                ) : null}
                {editing ? <span aria-hidden="true" className="text-line-strong">·</span> : null}
                {busy ? (
                  <span className="inline-flex items-center gap-1.5"><LoaderCircle className="spin size-3.5" /> Saving…</span>
                ) : editing && !dirty ? (
                  <span className="inline-flex items-center gap-1.5"><Check className="size-3.5 text-success" /> All changes saved</span>
                ) : editing ? (
                  <span className="inline-flex items-center gap-1.5 text-fg-soft"><span aria-hidden="true" className="size-1.5 rounded-full bg-warning" /> Unsaved changes</span>
                ) : (
                  <span>Not saved yet</span>
                )}
              </div>
              {(needsBusinessDetails || (editing && readiness && readiness.nextStep !== "ready")) && (
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={busy}
                  className={cn(needsBusinessDetails && "border-warning/40 bg-warning/[0.08] hover:bg-warning/[0.12]")}
                  onClick={() => needsBusinessDetails ? setShowPersonalisation(true) : void continueSetup()}
                >
                  {needsBusinessDetails ? <CircleAlert className="text-warning" /> : <Settings2 />}
                  <span className="max-[1279px]:hidden">{requiredDetailCount > 0 ? `${requiredDetailCount} details required` : setupActionLabel}</span>
                </Button>
              )}
              <Button
                variant={testPanelOpen ? "soft" : "secondary"}
                size="sm"
                aria-pressed={testPanelOpen}
                onClick={() => setTestPanelOpen((open) => !open)}
              >
                {testPanelOpen ? <PanelRightClose /> : <Play />}
                {testPanelOpen ? "Hide test" : "Test"}
              </Button>
              {editing && (
                <Tooltip content="Delete agent" side="bottom">
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label="Delete agent"
                    className="text-fg-muted hover:bg-danger/[0.08] hover:text-danger"
                    onClick={() => {
                      setDeleteError("");
                      setShowDelete(true);
                    }}
                  >
                    <Trash2 />
                  </Button>
                </Tooltip>
              )}
              <span aria-hidden="true" className="mx-0.5 h-5 w-px bg-line" />
              <Button
                type="submit"
                size="sm"
                variant={editing && !dirty && !busy ? "secondary" : "primary"}
                disabled={busy || (editing && !dirty)}
                className={cn("min-w-[7.5rem]", editing && !dirty && !busy && "disabled:opacity-100 disabled:text-fg-muted disabled:shadow-none")}
              >
                {busy ? <LoaderCircle className="spin" /> : editing && !dirty ? <Check /> : null}
                {busy ? "Saving…" : editing ? dirty ? "Save changes" : "Saved" : "Save draft"}
              </Button>
            </div>
          </header>

          <div className={`agent-studio-layout ${testPanelOpen ? "test-open" : "test-closed"}`}>
            <aside className="agent-studio-nav">
              <span>Agent setup</span>
              <div className="studio-setup-progress" aria-label={`Agent is ${setupProgress}% ready to go live`}>
                <div><strong>Launch readiness</strong><span>{setupProgress}%</span></div>
                <i aria-hidden="true"><span style={{ width: `${setupProgress}%` }} /></i>
                <small>{setupProgress === 100 ? "Ready to activate" : "Complete the remaining setup requirements"}</small>
              </div>
              <div className="studio-nav-sections" aria-label="Agent setup sections">
                {studioSections.map(({ id, label, description: sectionDescription, icon: Icon }) => (
                  <button className={activeSection === id ? "active" : ""} type="button" key={id} onClick={() => setActiveSection(id)}>
                    <Icon size={18} />
                    <span><strong>{label}</strong><small>{sectionDescription}</small></span>
                    {completedStudioSections.has(id) && <Check className="studio-nav-check" size={14} aria-label="Configured" />}
                  </button>
                ))}
              </div>
              <div className="studio-template-note">
                <Sparkles size={17} />
                <div><small>{editing ? "Business details" : "Template"}</small><strong>{editing ? `${agentVariables.length} prompt variables` : selectedTemplate.name}</strong></div>
                <button className="console-secondary-button" type="button" onClick={() => editing ? setShowPersonalisation(true) : agentVariables.length ? setShowPersonalisation(true) : setStage("templates")}>
                  {editing || agentVariables.length ? "Personalise" : "Change"}
                </button>
              </div>
            </aside>

            <section className="agent-studio-form">
              {activeSection === "main" && (
                <MainSettings
                  name={name}
                  description={description}
                  language={language}
                  supportedLanguages={supportedLanguages}
                  voice={voice}
                  timezone={timezone}
                  maxDuration={maxDuration}
                  greeting={greeting}
                  bookingEnabled={bookingEnabled}
                  defaultBookingDurationMinutes={defaultBookingDurationMinutes}
                  bookingRequiredFields={bookingRequiredFields}
                  bookingFieldSuggestions={selectedTemplate.bookingRequiredFields}
                  bookingNotificationChannels={bookingNotificationChannels}
                  bookingNotificationRecipient={bookingNotificationRecipient}
                  saveTranscript={saveTranscript}
                  recordCalls={recordCalls}
                  webVoiceEnabled={webVoiceEnabled}
                  webVoiceAllowedOrigins={webVoiceAllowedOrigins}
                  webVoiceRequireConsent={webVoiceRequireConsent}
                  webVoicePublicId={loadedAgent?.webVoicePublicId}
                  whatsappEnabled={whatsappEnabled}
                  whatsappPhoneNumberId={whatsappPhoneNumberId}
                  phoneNumber={loadedAgent?.twilioPhoneNumber}
                  phoneNumberProvider={loadedAgent?.phoneNumberProvider}
                  phoneNumberStatus={loadedAgent?.phoneNumberStatus}
                  channelFocusRequest={channelFocusRequest}
                  onAssignPhone={() => {
                    if (agentId) {
                      setShowNumberPicker(true);
                      return;
                    }
                    openPhoneAfterSaveRef.current = true;
                    studioFormRef.current?.requestSubmit();
                  }}
                  onRefreshPhone={agentId && loadedAgent?.phoneNumberOrderId ? async () => {
                    setBusy(true);
                    setError("");
                    try {
                      const refreshed = await refreshAgentPhoneNumber(agentId);
                      setLoadedAgent(refreshed);
                      setReadiness(await getAgentReadiness(agentId));
                    } catch (caught) {
                      setError(caught instanceof Error ? caught.message : "Unable to refresh number status.");
                    } finally {
                      setBusy(false);
                    }
                  } : undefined}
                  onName={setName}
                  onDescription={setDescription}
                  onLanguage={(value) => {
                    setLanguage(value);
                    setSupportedLanguages((current) => Array.from(new Set([value, ...current])));
                  }}
                  onSupportedLanguages={setSupportedLanguages}
                  onVoice={setVoice}
                  onTimezone={setTimezone}
                  onDuration={setMaxDuration}
                  onGreeting={setGreeting}
                  onBooking={setBookingEnabled}
                  onDefaultBookingDurationMinutes={setDefaultBookingDurationMinutes}
                  onBookingRequiredFields={setBookingRequiredFields}
                  onBookingNotificationChannels={setBookingNotificationChannels}
                  onBookingNotificationRecipient={setBookingNotificationRecipient}
                  onSaveTranscript={setSaveTranscript}
                  onRecordCalls={setRecordCalls}
                  onWebVoiceEnabled={setWebVoiceEnabled}
                  onWebVoiceAllowedOrigins={setWebVoiceAllowedOrigins}
                  onWebVoiceRequireConsent={setWebVoiceRequireConsent}
                  onWhatsappEnabled={setWhatsappEnabled}
                  onWhatsappPhoneNumberId={setWhatsappPhoneNumberId}
                />
              )}
              {activeSection === "behavior" && (
                <BehaviorSettings
                  prompt={prompt}
                  onPrompt={setPrompt}
                  template={selectedTemplate.name}
                  variables={agentVariables}
                  values={{ ...variableValues, agent_name: name, timezone }}
                />
              )}
              {activeSection === "speech" && (
                <SpeechSettings
                  voice={voice}
                  language={language}
                  supportedLanguages={supportedLanguages}
                  bargeInSensitivity={bargeInSensitivity}
                  bargeInGraceMs={bargeInGraceMs}
                  sttEndpointingMs={sttEndpointingMs}
                  vocabularyDomain={sttVocabularyDomain}
                  boostedKeywords={sttBoostedKeywords}
                  onVoice={setVoice}
                  onBargeInSensitivity={setBargeInSensitivity}
                  onBargeInGraceMs={setBargeInGraceMs}
                  onSttEndpointingMs={setSttEndpointingMs}
                  onVocabularyDomain={setSttVocabularyDomain}
                  onBoostedKeywords={setSttBoostedKeywords}
                />
              )}
              {activeSection === "calls" && (
                <CallBehaviorSettings
                  maxDuration={maxDuration}
                  endCallOnSilenceSeconds={endCallOnSilenceSeconds}
                  reminderAfterSilenceSeconds={reminderAfterSilenceSeconds}
                  maxReminders={maxReminders}
                  detectVoicemail={detectVoicemail}
                  handleCallScreening={handleCallScreening}
                  dtmfEnabled={dtmfEnabled}
                  dtmfTerminationKey={dtmfTerminationKey}
                  dtmfInputTimeoutSeconds={dtmfInputTimeoutSeconds}
                  dtmfMaxDigits={dtmfMaxDigits}
                  dtmfDigitMappings={dtmfDigitMappings}
                  safetyGuardrails={safetyGuardrails}
                  onMaxDuration={setMaxDuration}
                  onEndCallOnSilenceSeconds={setEndCallOnSilenceSeconds}
                  onReminderAfterSilenceSeconds={setReminderAfterSilenceSeconds}
                  onMaxReminders={setMaxReminders}
                  onDetectVoicemail={setDetectVoicemail}
                  onHandleCallScreening={setHandleCallScreening}
                  onDtmfEnabled={setDtmfEnabled}
                  onDtmfTerminationKey={setDtmfTerminationKey}
                  onDtmfInputTimeoutSeconds={setDtmfInputTimeoutSeconds}
                  onDtmfMaxDigits={setDtmfMaxDigits}
                  onDtmfDigitMappings={setDtmfDigitMappings}
                  onSafetyGuardrails={setSafetyGuardrails}
                />
              )}
              {activeSection === "routing" && (
                <RoutingSettings
                  transferEnabled={transferEnabled}
                  transferNumber={transferNumber}
                  escalationPhrases={escalationPhrases}
                  operatingHours={operatingHours}
                  timezone={timezone}
                  afterHoursBehavior={afterHoursBehavior}
                  afterHoursMessage={afterHoursMessage}
                  onTransferEnabled={setTransferEnabled}
                  onTransferNumber={setTransferNumber}
                  onEscalationPhrases={setEscalationPhrases}
                  onOperatingHours={setOperatingHours}
                  onAfterHoursBehavior={setAfterHoursBehavior}
                  onAfterHoursMessage={setAfterHoursMessage}
                />
              )}
              {activeSection === "integrations" && (
                <IntegrationsSettings agentId={agentId} bookingEnabled={bookingEnabled} />
              )}
              {activeSection === "knowledge" && <KnowledgeSettings agentId={agentId} knowledgeBase={knowledgeBase} onKnowledgeBase={setKnowledgeBase} />}
              {activeSection === "postcall" && <PostCallSettings fields={postCallExtractionFields} onFields={setPostCallExtractionFields} />}
              {error && <div className="form-alert error">{error}</div>}
            </section>

            <TestCallPanel
              onClose={() => setTestPanelOpen(false)}
              agentId={agentId}
              agentName={name || "your agent"}
              voiceId={voice}
              defaultLanguage={language}
              supportedLanguages={supportedLanguages}
            />
          </div>
          {showPersonalisation && (
            <PersonalisationDrawer
              agentSaved={Boolean(agentId)}
              countryName={countryName}
              defaultPhoneRegion={session?.tenant?.countryCode || ""}
              phoneCountries={phoneCountries}
              timezone={timezone}
              bookingEnabled={bookingEnabled}
              defaultBookingDurationMinutes={defaultBookingDurationMinutes}
              values={variableValues}
              variables={agentVariables}
              onAdd={addCustomVariable}
              onChange={(key, value) => {
                setSaved(false);
                setVariableValues((current) => ({ ...current, [key]: value }));
              }}
              onClose={() => setShowPersonalisation(false)}
              onTimezone={(value) => {
                setSaved(false);
                setTimezone(value);
              }}
              onDefaultBookingDurationMinutes={(value) => {
                setSaved(false);
                setDefaultBookingDurationMinutes(value);
              }}
              onSave={savePersonalisation}
            />
          )}
          {showDelete && loadedAgent && (
            <DeleteAgentDialog
              agent={loadedAgent}
              busy={busy}
              error={deleteError}
              onCancel={() => !busy && setShowDelete(false)}
              onConfirm={() => void removeSavedAgent()}
            />
          )}
          {showNumberPicker && agentId && (
            <PhoneNumberPicker
              agentId={agentId}
              defaultCountryCode={session?.tenant?.countryCode ?? "US"}
              existingNumber={loadedAgent?.twilioPhoneNumber}
              existingProvider={loadedAgent?.phoneNumberProvider}
              onClose={() => setShowNumberPicker(false)}
              onAssigned={(agent) => {
                setLoadedAgent(agent);
                setShowNumberPicker(false);
                markSaved();
                void getAgentReadiness(agent.id).then(setReadiness);
              }}
            />
          )}
        </form>
      )}
    </>
  );

  function addCustomVariable(variable: CreateAgentVariable) {
    if (agentVariables.some((existing) => existing.key === variable.key)) {
      throw new Error(`Variable {{${variable.key}}} already exists.`);
    }
    setCustomVariables((current) => [...current, {
      key: variable.key,
      label: variable.label,
      description: variable.description,
      required: variable.required,
    }]);
    setVariableValues((current) => ({ ...current, [variable.key]: variable.value }));
    setError("");
  }

  async function savePersonalisation() {
    if (!agentId) {
      setShowPersonalisation(false);
      return;
    }
    setBusy(true);
    setError("");
    try {
      const persistedValues = Object.fromEntries(
        Object.entries(variableValues).filter(([key]) => existingVariableKeys.includes(key)),
      );
      if (Object.keys(persistedValues).length) {
        await updateAgentVariables(agentId, persistedValues);
      }
      const newVariables = customVariables.filter((variable) => !existingVariableKeys.includes(variable.key));
      for (const variable of newVariables) {
        await createAgentVariable(agentId, {
          key: variable.key,
          label: variable.label,
          description: variable.description,
          value: variableValues[variable.key] ?? "",
          required: variable.required,
        });
      }
      setExistingVariableKeys((current) => Array.from(new Set([
        ...current,
        ...newVariables.map((variable) => variable.key),
      ])));
      if (loadedAgent && loadedAgent.timezone !== timezone) {
        setLoadedAgent(await updateAgentTimezone(agentId, timezone));
      }
      setReadiness(await getAgentReadiness(agentId));
      markSaved();
      setShowPersonalisation(false);
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : "Unable to save business details.";
      setError(message);
      throw new Error(message);
    } finally {
      setBusy(false);
    }
  }
}

function CountryFlag({ code }: { code: string }) {
  return (
    <span
      aria-hidden="true"
      className={`fi fi-${code.toLowerCase()} block h-[14px] w-[19px] shrink-0 overflow-hidden rounded-[3px] bg-cover bg-center shadow-[0_0_0_1px_rgb(0_0_0/0.12)]`}
    />
  );
}

const COUNTRY_OPTIONS = COUNTRIES.map((country) => ({
  value: country.code,
  label: country.name,
  keywords: [country.code],
  leading: <CountryFlag code={country.code} />,
}));

function PhoneNumberPicker({
  agentId,
  defaultCountryCode,
  existingNumber,
  existingProvider,
  onClose,
  onAssigned,
}: {
  agentId: string;
  defaultCountryCode: string;
  existingNumber?: string | null;
  existingProvider?: string | null;
  onClose: () => void;
  onAssigned: (agent: Agent) => void;
}) {
  const [countryCode, setCountryCode] = useState(defaultCountryCode);
  const [numbers, setNumbers] = useState<AvailablePhoneNumber[]>([]);
  const [selectedNumber, setSelectedNumber] = useState("");
  const [loading, setLoading] = useState(false);
  const [ordering, setOrdering] = useState(false);
  const [error, setError] = useState("");
  const [providerChargesConfirmed, setProviderChargesConfirmed] = useState(false);
  const [warningDismissed, setWarningDismissed] = useState(false);
  const selectedCountry = COUNTRIES.find((country) => country.code === countryCode);
  const selected = numbers.find((number) => number.phoneNumber === selectedNumber);

  async function searchNumbers() {
    setLoading(true);
    setError("");
    setSelectedNumber("");
    setProviderChargesConfirmed(false);
    try {
      const availableNumbers = await listAvailableAgentNumbers(agentId, countryCode, 12);
      setNumbers(availableNumbers);
      setSelectedNumber(availableNumbers[0]?.phoneNumber ?? "");
    } catch (caught) {
      setNumbers([]);
      setError(caught instanceof Error ? caught.message : "Unable to load available numbers.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void searchNumbers();
    setWarningDismissed(false);
    // Search whenever the user changes market. The function only depends on these values.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentId, countryCode]);

  async function assignNumber(phoneNumber?: string) {
    setOrdering(true);
    setError("");
    try {
      onAssigned(await provisionAgentNumber(
        agentId,
        phoneNumber ?? "",
        Boolean(existingNumber),
        providerChargesConfirmed,
      ));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to assign this number.");
      setOrdering(false);
    }
  }

  return (
    <DialogPrimitive.Root open onOpenChange={(open) => { if (!open && !ordering) onClose(); }}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-[150] bg-scrim data-[state=open]:animate-[fade-in_180ms_ease-out]" />
        <DialogPrimitive.Content
          aria-describedby={undefined}
          onInteractOutside={(event) => { if (ordering) event.preventDefault(); }}
          className="fixed left-1/2 top-1/2 z-[151] flex max-h-[min(90dvh,760px)] w-[min(94vw,680px)] -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded-[var(--radius-card)] border border-line-strong bg-popover font-sans text-fg shadow-[var(--shadow-pop)] outline-none data-[state=open]:animate-[dialog-in_220ms_cubic-bezier(0.16,1,0.3,1)] max-sm:inset-x-0 max-sm:bottom-0 max-sm:top-auto max-sm:max-h-[92dvh] max-sm:w-full max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-b-none"
        >
          <header className="flex items-start justify-between gap-4 px-6 pb-4 pt-5">
            <div className="min-w-0">
              <DialogPrimitive.Title className="m-0 text-[1.05rem] font-semibold tracking-[-0.01em] text-ink">
                {existingNumber ? "Change phone number" : "Choose a phone number"}
              </DialogPrimitive.Title>
              <p className="m-0 mt-1 text-[0.85rem] leading-relaxed text-fg-soft">Pick the country your customers call from. Availability and paperwork vary by country.</p>
            </div>
            <DialogPrimitive.Close asChild>
              <Button variant="ghost" size="icon-xs" aria-label="Close" disabled={ordering} className="-mr-1.5"><X /></Button>
            </DialogPrimitive.Close>
          </header>

          <div className="flex items-end gap-2 px-6 pb-4">
            <div className="grid min-w-0 flex-1 gap-1.5 sm:max-w-[18rem]">
              <span className="text-[0.8rem] font-medium text-ink">Country</span>
              <Select
                size="md"
                className="h-10 text-[0.88rem]"
                ariaLabel="Country"
                searchable
                searchPlaceholder="Search countries"
                value={countryCode}
                onValueChange={setCountryCode}
                options={COUNTRY_OPTIONS}
                disabled={ordering}
              />
            </div>
            <Button variant="secondary" size="sm" className="h-10" disabled={loading || ordering} onClick={() => void searchNumbers()}>
              <RefreshCw className={loading ? "spin" : undefined} /> Refresh
            </Button>
          </div>

          {existingNumber && !warningDismissed ? (
            <div className="px-6 pb-4">
              <Note
                tone="warning"
                icon={<CircleAlert />}
                title={`This replaces ${existingNumber}`}
                action={<Button variant="ghost" size="icon-xs" aria-label="Dismiss" onClick={() => setWarningDismissed(true)}><X /></Button>}
              >
                Your current {readableProvider(existingProvider)} number stays in that provider account and may keep billing until you release it there.
              </Note>
            </div>
          ) : null}

          <div className="flex items-center justify-between gap-3 border-y border-line bg-tint/[0.02] px-6 py-2 text-[0.75rem] text-fg-muted">
            <span className="font-medium text-fg-soft">Available in {selectedCountry?.name ?? countryCode}</span>
            <span className="tabular-nums">{loading ? "Checking…" : `${numbers.length} ${numbers.length === 1 ? "number" : "numbers"}`}</span>
          </div>

          <div className="min-h-[200px] flex-1 overflow-y-auto px-3 py-2">
            {loading ? (
              <div className="grid gap-2 p-3">{[0, 1, 2, 3].map((item) => <div key={item} className="h-14 animate-pulse rounded-lg bg-tint/[0.05]" />)}</div>
            ) : numbers.length ? (
              <div role="radiogroup" aria-label="Available numbers" className="grid gap-0.5">
                {numbers.map((number, index) => {
                  const checked = selectedNumber === number.phoneNumber;
                  const place = [number.locality, number.region].filter(Boolean).join(", ");
                  return (
                    <div
                      key={number.phoneNumber}
                      role="radio"
                      aria-checked={checked}
                      tabIndex={0}
                      onClick={() => { setSelectedNumber(number.phoneNumber); setProviderChargesConfirmed(false); }}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          setSelectedNumber(number.phoneNumber);
                          setProviderChargesConfirmed(false);
                        }
                      }}
                      className={cn(
                        "group grid cursor-pointer grid-cols-[18px_minmax(0,1fr)_auto] items-center gap-3 rounded-lg border px-3 py-2.5 outline-none transition-colors focus-visible:ring-2 focus-visible:ring-signal/40",
                        checked ? "border-signal/35 bg-signal/[0.07]" : "border-transparent hover:bg-tint/[0.04]",
                      )}
                    >
                      <RadioDot checked={checked} />
                      <span className="min-w-0">
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="font-mono text-[0.92rem] font-medium tabular-nums text-ink">{number.phoneNumber}</span>
                          {index === 0 ? <span className="rounded-[4px] bg-signal/10 px-1.5 py-[3px] text-[0.68rem] font-medium leading-none text-signal">Recommended</span> : null}
                        </span>
                        <span className="mt-0.5 block truncate text-[0.76rem] text-fg-muted">
                          {[place || selectedCountry?.name, readableNumberType(number.type)].filter(Boolean).join(" · ")}
                        </span>
                      </span>
                      <span className="text-right">
                        <span className="block text-[0.88rem] font-medium tabular-nums text-ink">{formatPhoneNumberCost(number.monthlyCost, number.currency)}</span>
                        <span className="block text-[0.72rem] text-fg-muted">per month</span>
                      </span>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="grid place-items-center gap-1.5 px-6 py-12 text-center">
                <span className="mb-1 grid size-10 place-items-center rounded-full bg-tint/[0.06] text-fg-muted"><Phone className="size-[18px]" /></span>
                <p className="m-0 text-[0.9rem] font-medium text-ink">No numbers to choose from in {selectedCountry?.name ?? countryCode}</p>
                <p className="m-0 max-w-sm text-[0.82rem] leading-relaxed text-fg-soft">{error || "Try another country, or contact support and we’ll source one for you."}</p>
              </div>
            )}
          </div>

          {error && numbers.length > 0 ? <div className="px-6 pt-3"><Alert>{error}</Alert></div> : null}

          {selected ? (
            <div className="px-6 pt-4">
              <label className="flex cursor-pointer items-start gap-3 rounded-[var(--radius-control)] border border-line p-3.5 transition-colors hover:bg-tint/[0.02]">
                <Checkbox
                  checked={providerChargesConfirmed}
                  disabled={ordering}
                  onCheckedChange={(next) => setProviderChargesConfirmed(next === true)}
                  className="mt-px"
                />
                <span className="min-w-0">
                  <span className="block text-[0.86rem] font-medium text-ink">
                    I agree to pay {formatPhoneNumberCost(selected.monthlyCost, selected.currency)} a month for {selected.phoneNumber}
                  </span>
                  <span className="mt-0.5 block text-[0.78rem] leading-relaxed text-fg-muted">
                    {Number(selected.upfrontCost) > 0 ? `Plus a one-off ${formatPhoneNumberCost(selected.upfrontCost, selected.currency)} setup charge. ` : ""}Calls, texts, taxes and carrier fees are billed separately. Numbers that need identity documents stay pending until approved.
                  </span>
                </span>
              </label>
            </div>
          ) : null}

          <footer className="mt-4 flex items-center justify-between gap-3 border-t border-line px-6 py-3.5 max-sm:flex-col max-sm:items-stretch">
            <p className="m-0 flex items-center gap-1.5 text-[0.76rem] text-fg-muted">
              <ShieldCheck className="size-3.5 shrink-0" /> Nothing is charged until you assign a number.
            </p>
            <div className="flex gap-2 max-sm:justify-end">
              <DialogPrimitive.Close asChild>
                <Button variant="secondary" size="sm" disabled={ordering}>Cancel</Button>
              </DialogPrimitive.Close>
              <Button size="sm" disabled={!selectedNumber || !providerChargesConfirmed || ordering} onClick={() => void assignNumber(selectedNumber)}>
                {ordering ? <LoaderCircle className="spin" /> : null}
                {ordering ? "Assigning…" : "Assign number"}
              </Button>
            </div>
          </footer>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

function readableNumberType(value: string) {
  if (!value) return "";
  return value.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function readableProvider(value?: string | null) {
  if (!value || value === "legacy") return "previous provider";
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function readablePhoneStatus(value?: string | null) {
  if (!value || value === "legacy" || value === "active" || value === "success") return "Connected";
  if (value === "pending") return "Pending activation";
  if (value === "failed" || value === "failure") return "Activation failed";
  return readableNumberType(value);
}

function formatPhoneNumberCost(value: string, currency: string) {
  const amount = Number(value);
  if (!value || Number.isNaN(amount)) return "Price on order";
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: currency || "USD",
      minimumFractionDigits: amount % 1 === 0 ? 0 : 2,
    }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency || "USD"}`;
  }
}

const BRIEF_EXAMPLES = [
  "Answer calls for our dental clinic, book check-ups and send urgent cases to the front desk.",
  "Take bookings for a hair salon, quote prices from our menu and confirm by SMS.",
  "Handle after-hours calls for a property agency and take detailed messages.",
];

const GENERATION_STEPS = ["Reading your description", "Writing the prompt", "Choosing tools and safety rules", "Setting languages and details"];

function TemplateSelection({
  brief,
  category,
  categories,
  templates: visibleTemplates,
  onBriefChange,
  onCategoryChange,
  onGenerate,
  generating,
  error,
  onSelect,
  onBack,
}: {
  brief: string;
  category: string;
  categories: string[];
  templates: Template[];
  onBriefChange: (value: string) => void;
  onCategoryChange: (value: string) => void;
  onGenerate: () => void;
  generating: boolean;
  error: string;
  onSelect: (template: Template) => void;
  onBack: () => void;
}) {
  const [step, setStep] = useState(0);
  useEffect(() => {
    if (!generating) { setStep(0); return; }
    const timer = window.setInterval(() => setStep((current) => Math.min(current + 1, GENERATION_STEPS.length - 1)), 1800);
    return () => window.clearInterval(timer);
  }, [generating]);

  const blank: Template = {
    ...templates[0],
    id: "blank",
    name: "Blank agent",
    category: "Custom",
    group: "Custom",
    description: "Start without predefined behavior.",
    greeting: "Open naturally in the caller's language. Introduce yourself as {{agent_name}} once, and ask one simple question about what the caller needs.",
    prompt: "",
    bookingEnabled: false,
    source: "custom",
    defaultLanguage: "en",
    supportedLanguages: ["en"],
    escalationPhrases: ["speak to a person", "talk to a human", "human agent"],
  };

  return (
    <section className="studio-kit mx-auto grid w-full max-w-[64rem] grid-cols-[minmax(0,1fr)] gap-10 px-1 pb-16 pt-2 font-sans text-fg">
      <header className="grid grid-cols-[minmax(0,1fr)] gap-4">
        <Button variant="ghost" size="xs" className="-ml-3 w-fit" onClick={onBack}><ArrowLeft /> Agents</Button>
        <div>
          <p className="m-0 text-[0.78rem] font-medium text-fg-muted">New agent</p>
          <h1 className="m-0 mt-1 text-[1.6rem] font-semibold leading-tight tracking-[-0.02em] text-ink">How should your agent help callers?</h1>
          <p className="m-0 mt-2 max-w-2xl text-[0.92rem] leading-relaxed text-fg-soft">
            Describe the job in your own words, or start from a template. You can change everything afterwards.
          </p>
        </div>
      </header>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-3">
        <div
          className={cn(
            "rounded-[var(--radius-card)] border bg-surface shadow-[var(--shadow-card)] transition-[border-color,box-shadow] focus-within:border-signal/50 focus-within:ring-4 focus-within:ring-signal/10",
            generating ? "border-signal/40" : "border-line-strong",
          )}
        >
          <label htmlFor="agent-brief" className="sr-only">Describe what the agent should do</label>
          <textarea
            id="agent-brief"
            rows={4}
            maxLength={1000}
            disabled={generating}
            value={brief}
            onChange={(event) => onBriefChange(event.target.value)}
            onKeyDown={(event) => {
              if ((event.metaKey || event.ctrlKey) && event.key === "Enter" && brief.trim() && !generating) onGenerate();
            }}
            placeholder="For example: answer calls for our clinic, book appointments, and transfer urgent requests to the front desk."
            className="block min-h-28 w-full resize-none border-0 bg-transparent px-5 pt-4 font-sans text-[0.98rem] leading-relaxed text-fg outline-none placeholder:text-fg-muted disabled:opacity-60"
          />
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-3">
            <span className="text-[0.76rem] tabular-nums text-fg-muted">
              {brief.length} / 1000 <span className="max-sm:hidden">· Ctrl + Enter to generate</span>
            </span>
            <Button size="sm" disabled={!brief.trim() || generating} onClick={onGenerate}>
              {generating ? <LoaderCircle className="spin" /> : <Sparkles />}
              {generating ? "Setting up…" : "Set up my agent"}
            </Button>
          </div>
        </div>

        {!brief.trim() && !generating ? (
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[0.76rem] text-fg-muted">Try:</span>
            {BRIEF_EXAMPLES.map((example) => (
              <button
                key={example}
                type="button"
                onClick={() => onBriefChange(example)}
                className="max-w-full cursor-pointer truncate rounded-full border border-line bg-transparent px-3 py-1.5 text-left font-sans text-[0.78rem] text-fg-soft transition-colors hover:border-line-strong hover:text-ink"
              >
                {example}
              </button>
            ))}
          </div>
        ) : null}

        {generating ? (
          <div role="status" className="rounded-[var(--radius-card)] border border-line bg-tint/[0.025] p-4">
            <div className="h-1 overflow-hidden rounded-full bg-tint/[0.08]">
              <div className="h-full rounded-full bg-signal transition-[width] duration-700" style={{ width: `${((step + 1) / GENERATION_STEPS.length) * 100}%` }} />
            </div>
            <ol className="m-0 mt-3 grid list-none gap-1.5 p-0 sm:grid-cols-4">
              {GENERATION_STEPS.map((label, index) => (
                <li key={label} className={cn("flex items-center gap-2 text-[0.8rem]", index <= step ? "text-ink" : "text-fg-muted")}>
                  {index < step ? <Check className="size-3.5 text-signal" /> : index === step ? <LoaderCircle className="spin size-3.5 text-signal" /> : <span className="size-3.5 rounded-full border border-line-strong" />}
                  {label}
                </li>
              ))}
            </ol>
          </div>
        ) : null}

        {error ? <Alert>{error}</Alert> : null}
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-5">
        <div className="flex flex-wrap items-end justify-between gap-4 border-t border-line pt-8">
          <div>
            <h2 className="m-0 text-[1.05rem] font-semibold tracking-[-0.01em] text-ink">Or start from a template</h2>
            <p className="m-0 mt-1 text-[0.84rem] text-fg-muted">Ready-made prompts and tools for common call jobs.</p>
          </div>
          <Tabs value={category} onValueChange={onCategoryChange}>
            <TabsList aria-label="Template category">
              {categories.map((item) => <TabsTrigger key={item} value={item} disabled={generating}>{item}</TabsTrigger>)}
            </TabsList>
          </Tabs>
        </div>

        <div className="grid grid-cols-[minmax(0,1fr)] gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {visibleTemplates.map((template) => (
            <TemplateCard key={template.id} template={template} disabled={generating} onSelect={() => onSelect(template)} />
          ))}
          <TemplateCard template={blank} disabled={generating} blank onSelect={() => onSelect(blank)} />
        </div>
      </div>
    </section>
  );
}

function TemplateCard({ template, disabled, blank = false, onSelect }: { template: Template; disabled: boolean; blank?: boolean; onSelect: () => void }) {
  const Icon = blank ? Plus : template.icon;
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onSelect}
      className={cn(
        "group flex min-h-[11.5rem] cursor-pointer flex-col rounded-[var(--radius-card)] border p-5 text-left font-sans transition-[border-color,background-color,box-shadow,transform] duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal disabled:cursor-not-allowed disabled:opacity-50",
        blank
          ? "border-dashed border-line-strong bg-transparent hover:border-fg-muted/50 hover:bg-tint/[0.02]"
          : "border-line bg-surface shadow-[var(--shadow-card)] hover:-translate-y-px hover:border-line-strong hover:shadow-[var(--shadow-pop)]",
      )}
    >
      <span className="flex items-start justify-between gap-3">
        <span className={cn("grid size-9 place-items-center rounded-[var(--radius-control)] [&_svg]:size-[18px]", blank ? "bg-tint/[0.05] text-fg-soft" : "bg-signal/10 text-signal")}>
          <Icon />
        </span>
        <span className="text-[0.72rem] font-medium text-fg-muted">{blank ? "Custom" : template.group}</span>
      </span>
      <span className="mt-4 block text-[0.95rem] font-semibold tracking-[-0.01em] text-ink">{template.name}</span>
      <span className="mt-1 line-clamp-3 block text-[0.82rem] leading-relaxed text-fg-soft">
        {blank ? "Write the prompt and choose every setting yourself." : template.description}
      </span>
      <span className="mt-auto flex items-center gap-1.5 pt-4 text-[0.8rem] font-medium text-fg-muted transition-colors group-hover:text-ink">
        {blank ? "Start blank" : "Use template"}
        <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" />
      </span>
    </button>
  );
}

function languageName(code: string) {
  return LANGUAGE_OPTIONS.find(([value]) => value === code)?.[1] ?? code.toUpperCase();
}

function MainSettings(props: {
  name: string; description: string; language: string; supportedLanguages: string[]; voice: string; timezone: string; maxDuration: string; greeting: string;
  bookingEnabled: boolean; defaultBookingDurationMinutes: number; bookingRequiredFields: string[]; bookingFieldSuggestions: string[]; bookingNotificationChannels: string[];
  bookingNotificationRecipient: string; saveTranscript: boolean; recordCalls: boolean;
  webVoiceEnabled: boolean; webVoiceAllowedOrigins: string; webVoiceRequireConsent: boolean;
  webVoicePublicId?: string; whatsappEnabled: boolean; whatsappPhoneNumberId: string; phoneNumber?: string | null;
  phoneNumberProvider?: string | null; phoneNumberStatus?: string | null;
  channelFocusRequest: number;
  onAssignPhone?: () => void;
  onRefreshPhone?: () => void;
  onName: (value: string) => void; onDescription: (value: string) => void; onLanguage: (value: string) => void;
  onSupportedLanguages: (value: string[]) => void; onVoice: (value: string) => void;
  onTimezone: (value: string) => void; onDuration: (value: string) => void; onGreeting: (value: string) => void;
  onBooking: (value: boolean) => void; onDefaultBookingDurationMinutes: (value: number) => void; onBookingRequiredFields: (value: string[]) => void;
  onBookingNotificationChannels: (value: string[]) => void; onBookingNotificationRecipient: (value: string) => void;
  onSaveTranscript: (value: boolean) => void; onRecordCalls: (value: boolean) => void;
  onWebVoiceEnabled: (value: boolean) => void; onWebVoiceAllowedOrigins: (value: string) => void;
  onWebVoiceRequireConsent: (value: boolean) => void;
  onWhatsappEnabled: (value: boolean) => void; onWhatsappPhoneNumberId: (value: string) => void;
}) {
  const channelSettingsRef = useRef<HTMLElement>(null);
  const [highlightChannels, setHighlightChannels] = useState(false);
  const [widgetColor, setWidgetColor] = useState("#31d9c9");
  const [widgetPosition, setWidgetPosition] = useState<"right" | "left">("right");
  const [widgetLanguage, setWidgetLanguage] = useState(props.language);
  const [widgetLabel, setWidgetLabel] = useState("Talk to us");
  const [languageCodeToAdd, setLanguageCodeToAdd] = useState("");
  useEffect(() => {
    if (!props.supportedLanguages.includes(widgetLanguage)) setWidgetLanguage(props.language);
  }, [props.language, props.supportedLanguages, widgetLanguage]);
  useEffect(() => {
    if (!props.channelFocusRequest) return;
    setHighlightChannels(true);
    window.requestAnimationFrame(() => {
      channelSettingsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    const timeout = window.setTimeout(() => setHighlightChannels(false), 1800);
    return () => window.clearTimeout(timeout);
  }, [props.channelFocusRequest]);
  function focusChannels() {
    setHighlightChannels(true);
    channelSettingsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    window.setTimeout(() => setHighlightChannels(false), 1800);
  }
  const normalizedPhoneStatus = props.phoneNumberStatus?.toLowerCase();
  const phoneConfigured = Boolean(props.phoneNumber)
    && (!normalizedPhoneStatus || normalizedPhoneStatus === "active"
      || normalizedPhoneStatus === "success" || normalizedPhoneStatus === "legacy");
  const channelConfigured = phoneConfigured || props.webVoiceEnabled
    || (props.whatsappEnabled && Boolean(props.whatsappPhoneNumberId.trim()));
  const widgetEmbedCode = props.webVoicePublicId
    ? `<script src="https://YOUR_SAUTI_DOMAIN/sauti-widget.js" data-base-url="https://YOUR_SAUTI_DOMAIN" data-agent="${props.webVoicePublicId}" data-lang="${widgetLanguage}" data-color="${widgetColor}" data-position="${widgetPosition}" data-label="${widgetLabel.replaceAll("\"", "&quot;")}" defer></script>`
    : "";
  const customLanguages = props.supportedLanguages.filter((code) => !LANGUAGE_OPTIONS.some(([known]) => known === code));
  const languageCodeValid = /^[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*$/.test(languageCodeToAdd.trim());

  function toggleLanguage(code: string) {
    if (code === props.language) return;
    props.onSupportedLanguages(props.supportedLanguages.includes(code)
      ? props.supportedLanguages.filter((item) => item !== code)
      : [...props.supportedLanguages, code]);
  }

  function addLanguage() {
    if (!languageCodeValid) return;
    props.onSupportedLanguages(Array.from(new Set([...props.supportedLanguages, languageCodeToAdd.trim()])));
    setLanguageCodeToAdd("");
  }

  return (
    <StudioSection eyebrow="Main settings" title="Identity and call setup" description="Configure the essentials callers experience on every conversation.">
      {!channelConfigured && (
        <div className="pb-6">
          <Note
            tone="warning"
            icon={<RadioTower />}
            title="No customer channel yet"
            action={<Button variant="secondary" size="xs" onClick={focusChannels}><Plus /> Add channel</Button>}
          >
            Turn on Web Voice or WhatsApp, or choose a phone number, so customers can reach this agent.
          </Note>
        </div>
      )}

      <StudioGroup title="Agent profile" description="How this agent is identified inside your workspace.">
        <FieldGrid>
          <Field label="Agent name" htmlFor="agent-name">
            <StudioInput id="agent-name" value={props.name} onChange={(event) => props.onName(event.target.value)} placeholder="e.g. Front desk" />
          </Field>
          <Field label="Short description" htmlFor="agent-description">
            <StudioInput id="agent-description" maxLength={500} value={props.description} onChange={(event) => props.onDescription(event.target.value)} placeholder="What this agent handles" />
          </Field>
        </FieldGrid>
      </StudioGroup>

      <StudioGroup title="Call defaults" description="The language and local time the agent uses on every conversation.">
        <FieldGrid>
          <Field label="Primary language" hint="Callers are greeted in this language.">
            <Select
              size="md"
              className={studioSelectClass}
              ariaLabel="Primary language"
              icon={<Languages />}
              value={props.language}
              onValueChange={props.onLanguage}
              options={Array.from(new Set([...LANGUAGE_OPTIONS.map(([value]) => value), ...props.supportedLanguages]))
                .map((value) => ({ value, label: languageName(value) }))}
            />
          </Field>
          <Field label="Timezone" hint="Used for opening hours and bookings.">
            <Select
              size="md"
              className={studioSelectClass}
              ariaLabel="Agent timezone"
              icon={<Clock3 />}
              searchable
              searchPlaceholder="Find a city or timezone"
              value={props.timezone}
              onValueChange={props.onTimezone}
              options={TIMEZONE_OPTIONS}
            />
          </Field>
        </FieldGrid>
      </StudioGroup>

      <StudioGroup
        id="agent-channel-settings"
        groupRef={channelSettingsRef}
        highlight={highlightChannels}
        title="Live channels"
        description="Customers can reach this agent through any combination of these."
      >
        <RowList>
          <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-3 pb-3.5">
            <div className="min-w-0">
              <span className="flex flex-wrap items-center gap-2 text-[0.88rem] font-medium text-ink">
                Phone calls
                {props.phoneNumber ? (
                  <Badge tone={phoneConfigured ? "success" : "warning"} dot>{readablePhoneStatus(props.phoneNumberStatus)}</Badge>
                ) : null}
              </span>
              <span className="mt-0.5 block text-[0.78rem] leading-relaxed text-fg-muted">
                {props.phoneNumber
                  ? `${props.phoneNumber} · ${readableProvider(props.phoneNumberProvider)}`
                  : "Pick a local business number. You confirm the price before anything is bought."}
              </span>
            </div>
            <div className="flex shrink-0 gap-2">
              {props.phoneNumber && props.phoneNumberStatus !== "active" && props.onRefreshPhone ? (
                <Button variant="ghost" size="xs" onClick={props.onRefreshPhone}><RefreshCw /> Refresh</Button>
              ) : null}
              <Button variant="secondary" size="xs" disabled={!props.onAssignPhone} onClick={props.onAssignPhone}>
                <Phone /> {props.phoneNumber ? "Change number" : props.onAssignPhone ? "Choose number" : "Save agent first"}
              </Button>
            </div>
          </div>

          <SwitchRow
            title="Web Voice"
            description="Visitors talk to this agent from your website, without a phone call."
            checked={props.webVoiceEnabled}
            onChange={props.onWebVoiceEnabled}
          >
            <RowList>
              <SwitchRow
                title="Ask for microphone consent"
                description="Visitors accept microphone use before the conversation starts."
                checked={props.webVoiceRequireConsent}
                onChange={props.onWebVoiceRequireConsent}
              />
            </RowList>
            <Field label="Allowed websites" htmlFor="web-voice-origins" hint="One address per line. Leave empty while you test the hosted page.">
              <Textarea
                id="web-voice-origins"
                rows={3}
                className="min-h-0 font-mono text-[0.82rem]"
                value={props.webVoiceAllowedOrigins}
                onChange={(event) => props.onWebVoiceAllowedOrigins(event.target.value)}
                placeholder={"https://example.com\nhttps://booking.example.com"}
              />
            </Field>
            {props.webVoicePublicId ? (
              <>
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-[var(--radius-control)] border border-line bg-surface px-3.5 py-2.5">
                  <div className="min-w-0">
                    <p className="m-0 text-[0.74rem] text-fg-muted">Hosted call page</p>
                    <code className="block truncate font-mono text-[0.82rem] text-ink">/call/{props.webVoicePublicId}</code>
                  </div>
                  <Button asChild variant="secondary" size="xs">
                    <Link href={`/call/${props.webVoicePublicId}`} target="_blank">Open page <ExternalLink /></Link>
                  </Button>
                </div>
                <FieldGrid>
                  <Field label="Button label" htmlFor="widget-label">
                    <StudioInput id="widget-label" maxLength={40} value={widgetLabel} onChange={(event) => setWidgetLabel(event.target.value)} />
                  </Field>
                  <Field label="Widget language">
                    <Select size="md" className={studioSelectClass} ariaLabel="Widget language" value={widgetLanguage} onValueChange={setWidgetLanguage}
                      options={props.supportedLanguages.map((code) => ({ value: code, label: languageName(code) }))} />
                  </Field>
                  <Field label="Accent colour" htmlFor="widget-color">
                    <div className="flex h-10 items-center gap-2.5 rounded-[var(--radius-control)] border border-line-strong bg-tint/[0.03] px-2">
                      <input
                        id="widget-color"
                        type="color"
                        value={widgetColor}
                        onChange={(event) => setWidgetColor(event.target.value)}
                        className="size-7 cursor-pointer rounded-[5px] border-0 bg-transparent p-0"
                      />
                      <code className="font-mono text-[0.82rem] uppercase text-fg-soft">{widgetColor}</code>
                    </div>
                  </Field>
                  <Field label="Position">
                    <Select size="md" className={studioSelectClass} ariaLabel="Widget position" value={widgetPosition} onValueChange={(value) => setWidgetPosition(value as "right" | "left")}
                      options={[{ value: "right", label: "Bottom right" }, { value: "left", label: "Bottom left" }]} />
                  </Field>
                </FieldGrid>
                <Field
                  label="Embed code"
                  hint="Paste this just before the closing </body> tag on your site."
                  badge={(
                    <Button variant="ghost" size="xs" className="-my-1 h-7" onClick={() => void navigator.clipboard.writeText(widgetEmbedCode).then(() => toast.success("Embed code copied"))}>
                      <Copy /> Copy
                    </Button>
                  )}
                >
                  <pre className="m-0 max-h-32 overflow-auto whitespace-pre-wrap break-all rounded-[var(--radius-control)] border border-line bg-tint/[0.04] p-3 font-mono text-[0.74rem] leading-relaxed text-fg-soft">{widgetEmbedCode}</pre>
                </Field>
              </>
            ) : (
              <p className="m-0 text-[0.78rem] text-fg-muted">Save the agent to get its hosted page and embed code.</p>
            )}
          </SwitchRow>

          <SwitchRow
            title="WhatsApp"
            description="Reply to customer WhatsApp messages. Text works today; voice notes are coming next."
            checked={props.whatsappEnabled}
            onChange={props.onWhatsappEnabled}
          >
            <Field label="Meta phone number ID" htmlFor="whatsapp-number-id" hint="The ID shown in WhatsApp Manager, not the phone number itself.">
              <StudioInput
                id="whatsapp-number-id"
                inputMode="numeric"
                className="font-mono"
                value={props.whatsappPhoneNumberId}
                onChange={(event) => props.onWhatsappPhoneNumberId(event.target.value.replace(/\D/g, ""))}
                placeholder="123456789012345"
              />
            </Field>
            <p className="m-0 text-[0.78rem] leading-relaxed text-fg-muted">
              Set the Meta webhook to <code className="rounded-[4px] bg-tint/[0.06] px-1 py-0.5 font-mono text-[0.76rem] text-fg-soft">https://YOUR_BACKEND/webhooks/whatsapp</code>.
            </p>
          </SwitchRow>
        </RowList>
      </StudioGroup>

      <StudioGroup
        title="Caller languages"
        description="The agent understands and replies in these languages. The primary language is always on."
        action={<Meta>{props.supportedLanguages.length} on</Meta>}
      >
        <ChipGroup label="Supported languages">
          {LANGUAGE_OPTIONS.map(([code, label]) => (
            <Chip
              key={code}
              selected={props.supportedLanguages.includes(code)}
              locked={code === props.language}
              onClick={() => toggleLanguage(code)}
              trailing={code === props.language ? <span className="text-[0.72rem] font-normal text-fg-muted">Primary</span> : undefined}
            >
              {label}
            </Chip>
          ))}
          {customLanguages.map((code) => (
            <Chip
              key={code}
              selected
              locked={code === props.language}
              onClick={() => toggleLanguage(code)}
              trailing={code !== props.language ? <X className="text-fg-muted" aria-label={`Remove ${code}`} /> : undefined}
            >
              {languageName(code)}
            </Chip>
          ))}
        </ChipGroup>
        <Field label="Add another language" htmlFor="language-code" hint="Use a language code, for example nl for Dutch or fr-CA for Canadian French.">
          <div className="flex gap-2">
            <StudioInput
              id="language-code"
              className="font-mono"
              value={languageCodeToAdd}
              onChange={(event) => setLanguageCodeToAdd(event.target.value)}
              onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); addLanguage(); } }}
              placeholder="nl"
            />
            <Button variant="secondary" size="sm" className="h-10 shrink-0" disabled={!languageCodeValid} onClick={addLanguage}><Plus /> Add</Button>
          </div>
        </Field>
      </StudioGroup>

      <StudioGroup title="Bookings" description="Let the agent check your calendar and book appointments during the call.">
        <RowList>
          <SwitchRow
            title="Appointment booking"
            description="Check availability and create bookings while the caller is on the line."
            checked={props.bookingEnabled}
            onChange={props.onBooking}
          >
            <BookingDurationSetting value={props.defaultBookingDurationMinutes} onChange={props.onDefaultBookingDurationMinutes} />
            <BookingFieldPicker fields={props.bookingRequiredFields} onChange={props.onBookingRequiredFields} suggestions={props.bookingFieldSuggestions} />
            <Field label="Notify the business owner" hint="Where new bookings are announced.">
              <ChipGroup label="Booking notifications">
                {[{ value: "dashboard", label: "Dashboard" }, { value: "email", label: "Email" }].map((channel) => {
                  const selected = props.bookingNotificationChannels.includes(channel.value);
                  return (
                    <Chip
                      key={channel.value}
                      selected={selected}
                      onClick={() => props.onBookingNotificationChannels(selected
                        ? props.bookingNotificationChannels.filter((item) => item !== channel.value)
                        : [...props.bookingNotificationChannels, channel.value])}
                    >
                      {channel.label}
                    </Chip>
                  );
                })}
              </ChipGroup>
            </Field>
            {props.bookingNotificationChannels.includes("email") && (
              <Field label="Notification email" htmlFor="booking-email" badge={<Meta>Optional</Meta>} hint="Leave blank to email the workspace owner.">
                <StudioInput
                  id="booking-email"
                  inputMode="email"
                  type="email"
                  value={props.bookingNotificationRecipient}
                  onChange={(event) => props.onBookingNotificationRecipient(event.target.value)}
                  placeholder="owner@business.com"
                />
              </Field>
            )}
          </SwitchRow>
        </RowList>
      </StudioGroup>

      <StudioGroup title="Privacy and call records" description="What Sauti keeps after each call.">
        <RowList>
          <SwitchRow title="Save transcripts" description="Keep a written record of each call for review and analytics." checked={props.saveTranscript} onChange={props.onSaveTranscript} />
          <SwitchRow
            title="Record phone calls"
            description="Keep audio of both sides. Make sure your greeting gives any consent notice your callers’ location requires."
            checked={props.recordCalls}
            onChange={props.onRecordCalls}
          />
        </RowList>
      </StudioGroup>

      <StudioGroup title="Greeting" description="The first thing callers hear when the agent answers." action={<Meta>{props.greeting.length} characters</Meta>}>
        <Textarea
          aria-label="Greeting"
          rows={3}
          className="min-h-0 text-[0.9rem]"
          value={props.greeting}
          onChange={(event) => props.onGreeting(event.target.value)}
          placeholder="Hello, you’ve reached {{business_name}}. How can I help?"
        />
      </StudioGroup>
    </StudioSection>
  );
}


function BookingDurationSetting({ value, onChange }: { value: number; onChange: (value: number) => void }) {
  return (
    <Field label="Standard appointment length" hint="Used to find free slots, unless the caller asks for a different length.">
      <Select
        size="md"
        className={cn(studioSelectClass, "sm:max-w-[calc(50%-0.5rem)]")}
        ariaLabel="Standard booking duration"
        icon={<Clock3 />}
        value={String(value)}
        onValueChange={(next) => onChange(Number(next))}
        options={bookingDurationOptions(value).map((minutes) => ({ value: String(minutes), label: bookingDurationLabel(minutes) }))}
      />
    </Field>
  );
}

function BookingFieldPicker({
  fields,
  suggestions,
  onChange,
}: {
  fields: string[];
  suggestions: string[];
  onChange: (value: string[]) => void;
}) {
  const [customField, setCustomField] = useState("");
  const [showAll, setShowAll] = useState(false);
  const normalizedFields = Array.from(new Set([...DEFAULT_BOOKING_FIELDS, ...fields]));
  const suggestedFields = Array.from(new Set(suggestions)).filter((field) => !CORE_BOOKING_FIELDS.has(field));
  const moreFields = Array.from(new Set([...BOOKING_FIELD_CATALOG, ...fields]))
    .filter((field) => !CORE_BOOKING_FIELDS.has(field) && !suggestedFields.includes(field));
  const atLimit = normalizedFields.length >= 25;
  const normalizedCustom = normalizeBookingField(customField);

  function toggleField(field: string) {
    if (CORE_BOOKING_FIELDS.has(field)) return;
    onChange(normalizedFields.includes(field)
      ? normalizedFields.filter((item) => item !== field)
      : [...normalizedFields, field]);
  }

  function addCustomField() {
    if (!normalizedCustom) return;
    if (!normalizedFields.includes(normalizedCustom)) onChange([...normalizedFields, normalizedCustom]);
    setCustomField("");
  }

  const chip = (field: string) => {
    const selected = normalizedFields.includes(field);
    return (
      <Chip key={field} selected={selected} disabled={atLimit && !selected} onClick={() => toggleField(field)}>
        {bookingFieldLabel(field)}
      </Chip>
    );
  };

  return (
    <div className="grid gap-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="m-0 text-[0.84rem] font-medium text-ink">Details to collect</p>
          <p className="m-0 mt-0.5 text-[0.76rem] leading-relaxed text-fg-muted">The agent asks for every selected detail before it books.</p>
        </div>
        <Meta>{normalizedFields.length} of 25</Meta>
      </div>
      <div className="grid gap-2">
        <p className="m-0 text-[0.74rem] font-medium text-fg-muted">Always collected</p>
        <ChipGroup label="Always collected">
          {DEFAULT_BOOKING_FIELDS.map((field) => <Chip key={field} selected locked>{bookingFieldLabel(field)}</Chip>)}
        </ChipGroup>
      </div>
      {suggestedFields.length > 0 && (
        <div className="grid gap-2">
          <p className="m-0 text-[0.74rem] font-medium text-fg-muted">Suggested for your business</p>
          <ChipGroup label="Suggested details">{suggestedFields.map(chip)}</ChipGroup>
        </div>
      )}
      {moreFields.length > 0 && (
        <div className="grid gap-2">
          {showAll ? <ChipGroup label="More details">{moreFields.map(chip)}</ChipGroup> : null}
          <Button variant="ghost" size="xs" className="-ml-3 w-fit" onClick={() => setShowAll((current) => !current)}>
            <ChevronDown className={cn("transition-transform", showAll && "rotate-180")} />
            {showAll ? "Show fewer details" : `Show ${moreFields.length} more details`}
          </Button>
        </div>
      )}
      <Field
        label="Add your own"
        htmlFor="custom-booking-field"
        hint={normalizedCustom ? <>Saved as <code className="font-mono text-fg-soft">{normalizedCustom}</code></> : "For example, insurance member number."}
      >
        <div className="flex gap-2">
          <StudioInput
            id="custom-booking-field"
            value={customField}
            onChange={(event) => setCustomField(event.target.value)}
            onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); addCustomField(); } }}
            placeholder="Insurance member number"
          />
          <Button variant="secondary" size="sm" className="h-10 shrink-0" disabled={atLimit || !normalizedCustom} onClick={addCustomField}><Plus /> Add</Button>
        </div>
      </Field>
    </div>
  );
}

function normalizeBookingField(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^[^a-z]+|_+$/g, "").slice(0, 64);
}

function bookingFieldLabel(field: string) {
  const aliases: Record<string, string> = {
    caller_name: "Customer name",
    caller_phone: "Phone number",
    caller_email: "Email address",
    service_type: "Service",
    appointment_at: "Date and time",
    patient_date_of_birth: "Patient date of birth",
  };
  return aliases[field] ?? field.split("_").map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(" ");
}

function BehaviorSettings({
  prompt,
  onPrompt,
  template,
  variables,
  values,
}: {
  prompt: string;
  onPrompt: (value: string) => void;
  template: string;
  variables: AgentVariableDefinition[];
  values: Record<string, string>;
}) {
  const [view, setView] = useState<"edit" | "preview">(variables.length > 0 ? "preview" : "edit");
  const showPreview = view === "preview" && variables.length > 0;
  return (
    <StudioSection eyebrow="Prompt" title="Agent prompt" description="Define what the agent should accomplish and the boundaries it must follow.">
      <StudioGroup
        title="Instructions"
        description={<>Started from <span className="font-medium text-fg-soft">{template}</span>. Use <code className="font-mono text-fg-soft">{"{{variable}}"}</code> to insert business details.</>}
        action={variables.length > 0 ? (
          <Tabs value={view} onValueChange={(next) => setView(next as "edit" | "preview")}>
            <TabsList aria-label="Prompt view">
              <TabsTrigger value="edit"><PencilLine /> Edit</TabsTrigger>
              <TabsTrigger value="preview"><Eye /> Preview</TabsTrigger>
            </TabsList>
          </Tabs>
        ) : undefined}
      >
        {showPreview
          ? <PromptPreview prompt={prompt} values={values} />
          : (
            <Textarea
              aria-label="Agent prompt"
              spellCheck
              className="min-h-[min(64vh,600px)] resize-y font-mono text-[0.84rem] leading-6"
              value={prompt}
              onChange={(event) => onPrompt(event.target.value)}
            />
          )}
        <div className="-mt-2 flex justify-between gap-4 text-[0.74rem] text-fg-muted">
          <span>{showPreview ? "Highlighted text comes from your business details." : "Drag the corner to resize."}</span>
          <span className="tabular-nums">{prompt.length.toLocaleString()} characters</span>
        </div>
      </StudioGroup>
      <div className="border-t border-line pt-6">
        <Note tone="signal" icon={<ShieldCheck />} title="Keep instructions testable">
          Spell out the goal, the details to collect, when to use each tool, when to hand over to a person, and the tone to use.
        </Note>
      </div>
    </StudioSection>
  );
}

function PromptPreview({ prompt, values }: { prompt: string; values: Record<string, string> }) {
  const segments = prompt.split(/(\{\{[a-zA-Z0-9_]+}})/g);
  return (
    <pre className="m-0 min-h-[min(64vh,600px)] overflow-auto whitespace-pre-wrap rounded-[var(--radius-control)] border border-line bg-tint/[0.03] p-3.5 font-mono text-[0.84rem] leading-6 text-fg">
      {segments.map((segment, index) => {
        const match = segment.match(/^\{\{([a-zA-Z0-9_]+)}}$/);
        if (!match) return <span key={`${index}-${segment.slice(0, 8)}`}>{segment}</span>;
        const value = values[match[1]]?.trim();
        return value
          ? <mark key={`${index}-${match[1]}`} className="rounded-[3px] bg-signal/15 px-0.5 text-ink">{value}</mark>
          : <em key={`${index}-${match[1]}`} className="rounded-[3px] bg-warning/15 px-0.5 not-italic text-warning">{match[1]} not provided</em>;
      })}
    </pre>
  );
}

function SpeechSettings(props: {
  voice: string; language: string; supportedLanguages: string[]; bargeInSensitivity: number; bargeInGraceMs: number; sttEndpointingMs: number;
  vocabularyDomain: string; boostedKeywords: string;
  onVoice: (value: string) => void; onBargeInSensitivity: (value: number) => void;
  onBargeInGraceMs: (value: number) => void; onSttEndpointingMs: (value: number) => void;
  onVocabularyDomain: (value: string) => void; onBoostedKeywords: (value: string) => void;
}) {
  return (
    <StudioSection eyebrow="Speech & transcription" title="Voice and turn-taking" description="Tune how the agent sounds, listens, and decides that a caller has finished speaking.">
      <StudioGroup title="Voice" description="What callers hear. Listen to each voice before choosing.">
        <VoicePicker value={props.voice} primaryLanguage={props.language} supportedLanguages={props.supportedLanguages} onChange={props.onVoice} />
      </StudioGroup>
      <StudioGroup title="Turn-taking" description="How quickly the agent replies, and how easily callers can interrupt it.">
        <SliderField
          label="Interruption sensitivity"
          description="How readily the agent stops talking when the caller starts speaking."
          value={props.bargeInSensitivity}
          min={0}
          max={1}
          step={0.05}
          format={(value) => `${Math.round(value * 100)}%`}
          lowLabel="Finishes its sentence"
          highLabel="Stops straight away"
          onChange={props.onBargeInSensitivity}
        />
        <SliderField
          label="Interruption grace period"
          description="How long the caller must speak before it counts as an interruption."
          value={props.bargeInGraceMs}
          min={0}
          max={1500}
          step={50}
          format={(value) => `${value} ms`}
          lowLabel="Any sound"
          highLabel="A full phrase"
          onChange={props.onBargeInGraceMs}
        />
        <SliderField
          label="Reply delay"
          description="How long the caller must be silent before the agent answers."
          value={props.sttEndpointingMs}
          min={100}
          max={1500}
          step={50}
          format={(value) => `${value} ms`}
          lowLabel="Quick replies"
          highLabel="Waits for long pauses"
          onChange={props.onSttEndpointingMs}
        />
      </StudioGroup>
      <StudioGroup title="Transcription" description="Help speech recognition with your field and the names callers say.">
        <FieldGrid>
          <Field label="Vocabulary">
            <Select size="md" className={studioSelectClass} ariaLabel="Vocabulary specialization" value={props.vocabularyDomain || "general"} onValueChange={props.onVocabularyDomain}
              options={[{ value: "general", label: "General" }, { value: "medical", label: "Medical" }]} />
          </Field>
          <Field label="Words to listen for" htmlFor="boosted-keyterms" hint="Separate with commas: names, places, products.">
            <StudioInput id="boosted-keyterms" value={props.boostedKeywords} onChange={(event) => props.onBoostedKeywords(event.target.value)} placeholder="Kibera, Thiong'o, Invisalign" />
          </Field>
        </FieldGrid>
      </StudioGroup>
    </StudioSection>
  );
}

function formatSeconds(total: number) {
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  if (!minutes) return `${seconds} s`;
  return seconds ? `${minutes} min ${seconds} s` : `${minutes} min`;
}

function CallBehaviorSettings(props: {
  maxDuration: string; endCallOnSilenceSeconds: number; reminderAfterSilenceSeconds: number; maxReminders: number;
  detectVoicemail: boolean; handleCallScreening: boolean; dtmfEnabled: boolean; safetyGuardrails: string[];
  dtmfTerminationKey: "#" | "*"; dtmfInputTimeoutSeconds: number; dtmfMaxDigits: number;
  dtmfDigitMappings: Record<string, string>;
  onMaxDuration: (value: string) => void; onEndCallOnSilenceSeconds: (value: number) => void;
  onReminderAfterSilenceSeconds: (value: number) => void; onMaxReminders: (value: number) => void;
  onDetectVoicemail: (value: boolean) => void; onHandleCallScreening: (value: boolean) => void;
  onDtmfEnabled: (value: boolean) => void; onSafetyGuardrails: (value: string[]) => void;
  onDtmfTerminationKey: (value: "#" | "*") => void;
  onDtmfInputTimeoutSeconds: (value: number) => void; onDtmfMaxDigits: (value: number) => void;
  onDtmfDigitMappings: (value: Record<string, string>) => void;
}) {
  const guardrails = [["medical diagnosis", "Medical diagnosis"], ["legal advice", "Legal advice"], ["financial advice", "Financial advice"]];
  const mappings = Object.entries(props.dtmfDigitMappings);

  function updateMapping(index: number, digits: string, meaning: string) {
    const next = mappings.map(([currentDigits, currentMeaning], currentIndex) => (
      currentIndex === index ? [digits.replace(/[^0-9*#]/g, "").slice(0, props.dtmfMaxDigits), meaning] : [currentDigits, currentMeaning]
    ));
    props.onDtmfDigitMappings(Object.fromEntries(next.filter(([key]) => key)));
  }

  function addMapping() {
    const available = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]
      .find((digit) => !Object.prototype.hasOwnProperty.call(props.dtmfDigitMappings, digit));
    if (available) props.onDtmfDigitMappings({ ...props.dtmfDigitMappings, [available]: "" });
  }

  function removeMapping(digits: string) {
    props.onDtmfDigitMappings(Object.fromEntries(mappings.filter(([key]) => key !== digits)));
  }

  return (
    <StudioSection eyebrow="Call behaviour" title="Silence, screening, and safety" description="Control how the agent handles unattended calls, long pauses, and sensitive topics.">
      <StudioGroup title="Call handling" description="How the agent deals with voicemail, call screeners and keypad presses.">
        <RowList>
          <SwitchRow title="Voicemail detection" description="Hang up when the call reaches a voicemail greeting." checked={props.detectVoicemail} onChange={props.onDetectVoicemail} />
          <SwitchRow title="Call-screen handling" description="Answer screening prompts such as “Who is calling?”" checked={props.handleCallScreening} onChange={props.onHandleCallScreening} />
          <SwitchRow title="Keypad input" description="Understand numbers callers type on their keypad." checked={props.dtmfEnabled} onChange={props.onDtmfEnabled}>
            <FieldGrid columns={3}>
              <Field label="Finish key" hint="Sends the digits straight away.">
                <Select size="md" className={studioSelectClass} ariaLabel="DTMF completion key" value={props.dtmfTerminationKey} onValueChange={(value) => props.onDtmfTerminationKey(value as "#" | "*")}
                  options={[{ value: "#", label: "# Hash" }, { value: "*", label: "* Star" }]} />
              </Field>
              <Field label="Wait for input" hint="Sends after the caller stops typing.">
                <NumberField ariaLabel="Input timeout in seconds" value={props.dtmfInputTimeoutSeconds} min={1} max={30} unit="sec" onChange={props.onDtmfInputTimeoutSeconds} />
              </Field>
              <Field label="Maximum digits" hint="For codes and reference numbers.">
                <NumberField ariaLabel="Maximum digits" value={props.dtmfMaxDigits} min={1} max={32} onChange={props.onDtmfMaxDigits} />
              </Field>
            </FieldGrid>
            <div className="grid gap-3">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="m-0 text-[0.84rem] font-medium text-ink">Keypad menu</p>
                  <p className="m-0 mt-0.5 text-[0.76rem] leading-relaxed text-fg-muted">Tell the agent what each key means, for example “1 = confirm the appointment”.</p>
                </div>
                <Button variant="secondary" size="xs" className="shrink-0" onClick={addMapping} disabled={mappings.length >= 10}><Plus /> Add key</Button>
              </div>
              {mappings.length === 0 ? (
                <p className="m-0 rounded-[var(--radius-control)] border border-dashed border-line-strong px-4 py-5 text-center text-[0.8rem] text-fg-muted">
                  No keys mapped yet. Anything the caller types is still passed to the agent.
                </p>
              ) : (
                <div className="grid gap-2">
                  <div className="grid grid-cols-[5.5rem_minmax(0,1fr)_2rem] gap-2 text-[0.74rem] font-medium text-fg-muted">
                    <span>Keys</span><span>What it means</span><span />
                  </div>
                  {mappings.map(([digits, meaning], index) => (
                    <div className="grid grid-cols-[5.5rem_minmax(0,1fr)_2rem] items-center gap-2" key={`${index}-${digits}`}>
                      <StudioInput aria-label="Keys" inputMode="numeric" className="text-center font-mono" maxLength={props.dtmfMaxDigits} value={digits} onChange={(event) => updateMapping(index, event.target.value, meaning)} placeholder="1" />
                      <StudioInput aria-label="What it means" value={meaning} onChange={(event) => updateMapping(index, digits, event.target.value)} placeholder="Confirm the appointment" />
                      <Button variant="ghost" size="icon-xs" aria-label={`Remove key ${digits}`} className="hover:bg-danger/[0.08] hover:text-danger" onClick={() => removeMapping(digits)}><Trash2 /></Button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </SwitchRow>
        </RowList>
      </StudioGroup>

      <StudioGroup title="Duration and silence" description="Limits that stop calls running on.">
        <SliderField
          label="Maximum call length"
          description="The agent politely wraps up and ends the call at this point."
          value={Number(props.maxDuration)}
          min={1}
          max={60}
          step={1}
          format={(value) => `${value} min`}
          marks={[{ value: 1, label: "1 min" }, { value: 15, label: "15" }, { value: 30, label: "30" }, { value: 45, label: "45" }, { value: 60, label: "60 min" }]}
          onChange={(value) => props.onMaxDuration(String(value))}
        />
        <SliderField
          label="End call after silence"
          description="Hang up when nobody has spoken for this long."
          value={props.endCallOnSilenceSeconds}
          min={60}
          max={900}
          step={30}
          format={formatSeconds}
          marks={[{ value: 60, label: "1 min" }, { value: 300, label: "5" }, { value: 600, label: "10" }, { value: 900, label: "15 min" }]}
          onChange={props.onEndCallOnSilenceSeconds}
        />
        <FieldGrid>
          <Field label="Check in after" hint="The agent asks if the caller is still there.">
            <NumberField ariaLabel="Reminder after silence in seconds" value={props.reminderAfterSilenceSeconds} min={30} max={600} step={5} unit="sec" onChange={props.onReminderAfterSilenceSeconds} />
          </Field>
          <Field label="Check-ins before hanging up" hint="Set 0 to end without checking in.">
            <NumberField ariaLabel="Maximum reminders" value={props.maxReminders} min={0} max={10} onChange={props.onMaxReminders} />
          </Field>
        </FieldGrid>
      </StudioGroup>

      <StudioGroup
        title="Safety guardrails"
        description="Topics the agent politely declines. These rules are added after your prompt."
        action={<Meta>{props.safetyGuardrails.length} on</Meta>}
      >
        <ChipGroup label="Safety guardrails">
          {guardrails.map(([value, label]) => {
            const selected = props.safetyGuardrails.includes(value);
            return (
              <Chip key={value} selected={selected} onClick={() => props.onSafetyGuardrails(selected ? props.safetyGuardrails.filter((item) => item !== value) : [...props.safetyGuardrails, value])}>
                {label}
              </Chip>
            );
          })}
        </ChipGroup>
      </StudioGroup>
    </StudioSection>
  );
}

const POST_CALL_FIELDS: Array<[string, string, string]> = [
  ["summary", "Call summary", "A short paragraph describing what happened on the call."],
  ["successful", "Call outcome", "Whether the caller got what they needed."],
  ["sentiment", "Caller sentiment", "Positive, neutral or negative, from the caller’s words and tone."],
  ["intent", "Reason for calling", "The main thing the caller wanted."],
];

function PostCallSettings({ fields, onFields }: { fields: string[]; onFields: (fields: string[]) => void }) {
  const extraFields = fields.filter((field) => !POST_CALL_FIELDS.some(([value]) => value === field));
  return (
    <StudioSection eyebrow="Post-call" title="Structured call analysis" description="Choose the fields generated after the call for reporting and downstream workflows.">
      <StudioGroup
        title="Fields to extract"
        description="Filled in from the transcript after every call, then shown on the call and sent to connected tools."
        action={<Meta>{POST_CALL_FIELDS.filter(([value]) => fields.includes(value)).length} of {POST_CALL_FIELDS.length} on</Meta>}
      >
        <RowList>
          {POST_CALL_FIELDS.map(([value, title, description]) => (
            <SwitchRow
              key={value}
              title={title}
              description={description}
              checked={fields.includes(value)}
              onChange={(enabled) => onFields(enabled ? [...fields, value] : fields.filter((item) => item !== value))}
            />
          ))}
        </RowList>
        {extraFields.length > 0 && (
          <Field label="Other fields" hint="Added for this agent elsewhere. Click one to stop extracting it.">
            <ChipGroup label="Other extracted fields">
              {extraFields.map((field) => (
                <Chip key={field} selected onClick={() => onFields(fields.filter((item) => item !== field))} trailing={<X className="text-fg-muted" />}>
                  {bookingFieldLabel(field)}
                </Chip>
              ))}
            </ChipGroup>
          </Field>
        )}
      </StudioGroup>
      <div className="border-t border-line pt-6">
        <Note icon={<ShieldCheck />} title="Analysis follows your transcript setting">
          These fields are only generated when a transcript is saved. Turn on transcripts in Main settings to use them.
        </Note>
      </div>
    </StudioSection>
  );
}

function RoutingSettings(props: {
  transferEnabled: boolean;
  transferNumber: string;
  escalationPhrases: string;
  operatingHours: string;
  timezone: string;
  afterHoursBehavior: AfterHoursBehavior;
  afterHoursMessage: string;
  onTransferEnabled: (value: boolean) => void;
  onTransferNumber: (value: string) => void;
  onEscalationPhrases: (value: string) => void;
  onOperatingHours: (value: string) => void;
  onAfterHoursBehavior: (value: AfterHoursBehavior) => void;
  onAfterHoursMessage: (value: string) => void;
}) {
  const alwaysOpen = props.operatingHours === "always" || props.operatingHours === "workspace";
  return (
    <StudioSection eyebrow="Routing" title="Escalation and availability" description="Control when calls stay with the agent and when they move to your team.">
      <StudioGroup title="Human transfer" description="Hand the call to your team when the caller asks for a person, or the agent can’t safely continue.">
        <RowList>
          <SwitchRow title="Allow live transfer" description="The agent tells the caller it’s connecting them, then transfers the call." checked={props.transferEnabled} onChange={props.onTransferEnabled}>
            <Field label="Transfer to" htmlFor="transfer-number" hint="Include the country code.">
              <StudioInput id="transfer-number" inputMode="tel" className="sm:max-w-[calc(50%-0.5rem)]" value={props.transferNumber} onChange={(event) => props.onTransferNumber(event.target.value)} placeholder="+254 7XX XXX XXX" />
            </Field>
            <Field label="Phrases that trigger a transfer" htmlFor="escalation-phrases" hint="Separate phrases with commas.">
              <Textarea id="escalation-phrases" rows={3} className="min-h-0 text-[0.88rem]" value={props.escalationPhrases} onChange={(event) => props.onEscalationPhrases(event.target.value)} placeholder="speak to a person, manager, complaint" />
            </Field>
          </SwitchRow>
        </RowList>
      </StudioGroup>

      <StudioGroup
        title="Operating hours"
        description={<>Checked against <span className="font-medium text-fg-soft">{props.timezone}</span>. Overnight ranges such as 20:00–02:00 work.</>}
      >
        <WeeklyHoursEditor value={props.operatingHours} onChange={props.onOperatingHours} />
      </StudioGroup>

      <StudioGroup
        title="Outside operating hours"
        description={alwaysOpen ? "Only used once you set a weekly schedule above." : "What callers experience when your team is closed."}
      >
        <RadioGroup
          aria-label="After-hours behaviour"
          value={props.afterHoursBehavior}
          onValueChange={(value) => props.onAfterHoursBehavior(value as AfterHoursBehavior)}
          className="sm:grid-cols-3"
        >
          {([
            ["answer", "Answer normally", "The agent and its tools work as usual."],
            ["take_message", "Take a message", "Collect the caller’s name, number and reason for calling."],
            ["closed", "Say you’re closed", "Play a short message, thank the caller and end the call."],
          ] as Array<[AfterHoursBehavior, string, string]>).map(([value, title, detail]) => (
            <RadioCard key={value} value={value} title={title} description={detail} />
          ))}
        </RadioGroup>
        {props.afterHoursBehavior !== "answer" && (
          <Field
            label="What callers hear"
            htmlFor="after-hours-message"
            badge={<Meta>{props.afterHoursMessage.length} / 1000</Meta>}
            hint="Leave empty to use Sauti’s default message in the caller’s language."
          >
            <Textarea
              id="after-hours-message"
              rows={3}
              maxLength={1000}
              className="min-h-0 text-[0.88rem]"
              value={props.afterHoursMessage}
              onChange={(event) => props.onAfterHoursMessage(event.target.value)}
              placeholder={props.afterHoursBehavior === "closed"
                ? "Thank you for calling. We’re closed right now. Please call again during business hours."
                : "We’re closed right now, but I can take your details and ask the team to call you back."}
            />
          </Field>
        )}
      </StudioGroup>
    </StudioSection>
  );
}

function WeeklyHoursEditor({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const custom = value !== "always" && value !== "workspace";
  const schedule = parseWeeklyHours(value);

  function setMode(mode: string) {
    onChange(mode === "always" ? "always" : JSON.stringify(schedule));
  }

  function updateDay(day: WeekDayKey, update: Partial<DayHours>) {
    onChange(JSON.stringify({ ...schedule, [day]: { ...schedule[day], ...update } }));
  }

  return (
    <div className="grid gap-4">
      <Tabs value={custom ? "custom" : "always"} onValueChange={setMode}>
        <TabsList aria-label="Operating-hours mode">
          <TabsTrigger value="always">Always open</TabsTrigger>
          <TabsTrigger value="custom">Weekly schedule</TabsTrigger>
        </TabsList>
      </Tabs>
      {custom ? (
        <div className="divide-y divide-line overflow-hidden rounded-[var(--radius-card)] border border-line">
          {OPERATING_WEEK_DAYS.map(({ key, label }) => {
            const hours = schedule[key];
            return (
              <div className="grid min-h-[3.25rem] grid-cols-[9rem_minmax(0,1fr)] items-center gap-4 px-4 py-2 max-sm:grid-cols-1 max-sm:gap-2" key={key}>
                <label className="flex cursor-pointer items-center gap-2.5 text-[0.86rem] font-medium text-ink">
                  <Checkbox checked={hours.enabled} onCheckedChange={(checked) => updateDay(key, { enabled: checked === true })} aria-label={`Open on ${label}`} />
                  {label}
                </label>
                {hours.enabled ? (
                  <div className="flex items-center gap-2">
                    <StudioInput aria-label={`${label} opening time`} type="time" className="h-9 w-[9rem] tabular-nums" value={hours.start} onChange={(event) => updateDay(key, { start: event.target.value })} />
                    <span className="text-[0.8rem] text-fg-muted">to</span>
                    <StudioInput aria-label={`${label} closing time`} type="time" className="h-9 w-[9rem] tabular-nums" value={hours.end} onChange={(event) => updateDay(key, { end: event.target.value })} />
                  </div>
                ) : <span className="text-[0.84rem] text-fg-muted">Closed</span>}
              </div>
            );
          })}
        </div>
      ) : (
        <p className="m-0 text-[0.82rem] text-fg-muted">The agent answers at any hour, every day. Choose a weekly schedule to set opening times.</p>
      )}
    </div>
  );
}

function parseWeeklyHours(value: string): WeeklyHours {
  const defaults = Object.fromEntries(OPERATING_WEEK_DAYS.map(({ key }) => [
    key,
    { enabled: !["saturday", "sunday"].includes(key), start: "09:00", end: "17:00" },
  ])) as WeeklyHours;
  if (!value || value === "always" || value === "workspace" || value === "weekdays") return defaults;
  try {
    const parsed = JSON.parse(value) as Partial<WeeklyHours>;
    return Object.fromEntries(OPERATING_WEEK_DAYS.map(({ key }) => [
      key,
      { ...defaults[key], ...(parsed[key] ?? {}) },
    ])) as WeeklyHours;
  } catch {
    return defaults;
  }
}

function KnowledgeSettings({ agentId, knowledgeBase, onKnowledgeBase }: { agentId?: string; knowledgeBase: string; onKnowledgeBase: (value: string) => void }) {
  const chunks = knowledgeChunks(knowledgeBase);
  const totalChunkCharacters = chunks.reduce((total, chunk) => total + chunk.length, 0);
  const promptCharacters = Math.min(3000, totalChunkCharacters);
  const omitted = totalChunkCharacters > 3000;
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [documentBusy, setDocumentBusy] = useState(false);
  const [documentError, setDocumentError] = useState("");
  const [openChunk, setOpenChunk] = useState<number | null>(0);

  useEffect(() => {
    if (!agentId) return;
    listKnowledgeDocuments(agentId)
      .then(setDocuments)
      .catch((caught) => setDocumentError(caught instanceof Error ? caught.message : "Unable to load documents."));
  }, [agentId]);

  async function addDocument(file?: File) {
    if (!agentId || !file) return;
    setDocumentBusy(true);
    setDocumentError("");
    try {
      const document = await uploadKnowledgeDocument(agentId, file);
      setDocuments((current) => [document, ...current]);
      toast.success(`${file.name} added`, { description: "The agent can use it on the next call." });
    } catch (caught) {
      setDocumentError(caught instanceof Error ? caught.message : "Unable to index this document.");
    } finally {
      setDocumentBusy(false);
    }
  }

  async function removeDocument(documentId: string) {
    if (!agentId) return;
    setDocumentBusy(true);
    setDocumentError("");
    try {
      await deleteKnowledgeDocument(agentId, documentId);
      setDocuments((current) => current.filter((document) => document.id !== documentId));
    } catch (caught) {
      setDocumentError(caught instanceof Error ? caught.message : "Unable to delete this document.");
    } finally {
      setDocumentBusy(false);
    }
  }

  const usage = Math.min(100, (promptCharacters / 3000) * 100);

  return (
    <StudioSection eyebrow="Knowledge" title="Ground the agent in approved information" description="Add notes directly or index business documents for semantic retrieval.">
      <StudioGroup
        title="Approved knowledge"
        description="Facts the agent may quote: services, prices, policies. Separate topics with a blank line."
        action={<Meta>{knowledgeBase.length.toLocaleString()} / 10,000</Meta>}
      >
        <Textarea
          aria-label="Approved knowledge"
          maxLength={10000}
          className="min-h-[340px] text-[0.88rem] leading-relaxed"
          value={knowledgeBase}
          onChange={(event) => onKnowledgeBase(event.target.value)}
          placeholder={"Services\nConsultations are available Monday to Friday.\n\nCancellation policy\nPlease give at least 24 hours’ notice."}
        />
        <div className="grid gap-2">
          <div className="flex flex-wrap justify-between gap-2 text-[0.76rem] text-fg-muted">
            <span><span className="font-medium tabular-nums text-fg-soft">{promptCharacters.toLocaleString()}</span> of 3,000 characters added to each call</span>
            <span className="tabular-nums">{chunks.length} {chunks.length === 1 ? "section" : "sections"}</span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-tint/[0.08]">
            <div className={cn("h-full rounded-full transition-[width] duration-300", omitted ? "bg-warning" : "bg-signal")} style={{ width: `${usage}%` }} />
          </div>
        </div>
        {omitted && (
          <Note tone="warning" icon={<CircleAlert />} title="Some of this won’t fit in a call">
            Only the first 3,000 characters go into each call. Move longer material into a document below; documents are searched and only the relevant parts are used.
          </Note>
        )}
      </StudioGroup>

      <StudioGroup title="How it’s split" description="What you write above is divided into sections like these." action={<Meta>{chunks.length} {chunks.length === 1 ? "section" : "sections"}</Meta>}>
        {chunks.length === 0 ? (
          <EmptyBox icon={<BookOpen />} title="Nothing to preview yet" text="Add knowledge above to see how it’s divided." />
        ) : (
          <div className="divide-y divide-line overflow-hidden rounded-[var(--radius-card)] border border-line">
            {chunks.map((chunk, index) => {
              const offset = chunks.slice(0, index).reduce((total, item) => total + item.length, 0);
              const status = offset >= 3000 ? "Left out" : offset + chunk.length > 3000 ? "Partly included" : "Included";
              const open = openChunk === index;
              return (
                <div key={`${index}-${chunk.slice(0, 20)}`}>
                  <button
                    type="button"
                    aria-expanded={open}
                    onClick={() => setOpenChunk(open ? null : index)}
                    className="flex w-full cursor-pointer items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-tint/[0.03]"
                  >
                    <span className="text-[0.86rem] font-medium text-ink">Section {index + 1}</span>
                    <span className="text-[0.76rem] tabular-nums text-fg-muted">{chunk.length} characters</span>
                    <Badge tone={status === "Included" ? "success" : status === "Left out" ? "warning" : "neutral"} className="ml-auto">{status}</Badge>
                    <ChevronDown className={cn("size-4 text-fg-muted transition-transform", open && "rotate-180")} />
                  </button>
                  {open ? <p className="m-0 whitespace-pre-wrap px-4 pb-4 text-[0.82rem] leading-relaxed text-fg-soft">{chunk}</p> : null}
                </div>
              );
            })}
          </div>
        )}
      </StudioGroup>

      <StudioGroup
        title="Documents"
        description="Upload policies, menus or FAQs. The agent searches them on every call and uses only the parts that are relevant."
        action={agentId ? (
          <label className={cn(buttonVariants({ variant: "secondary", size: "xs" }), documentBusy && "pointer-events-none opacity-45")}>
            {documentBusy ? <LoaderCircle className="spin" /> : <FileUp />}
            {documentBusy ? "Adding…" : "Upload"}
            <input
              className="sr-only"
              accept=".pdf,.doc,.docx,.txt,.md,.csv,.html,.htm"
              disabled={documentBusy}
              type="file"
              onChange={(event) => {
                void addDocument(event.target.files?.[0]);
                event.currentTarget.value = "";
              }}
            />
          </label>
        ) : (
          <Button variant="secondary" size="xs" disabled><FileUp /> Save agent first</Button>
        )}
      >
        {documentError ? <Alert>{documentError}</Alert> : null}
        {documents.length === 0 ? (
          <EmptyBox icon={<FileText />} title="No documents yet" text="PDF, Word, text, Markdown, CSV or HTML. Up to 10 MB each, 20 per agent." />
        ) : (
          <div className="divide-y divide-line overflow-hidden rounded-[var(--radius-card)] border border-line">
            {documents.map((document) => (
              <div key={document.id} className="flex items-center gap-3 px-4 py-3">
                <span className="grid size-9 shrink-0 place-items-center rounded-[var(--radius-control)] bg-tint/[0.05] text-fg-soft"><FileText className="size-4" /></span>
                <div className="min-w-0 flex-1">
                  <p className="m-0 truncate text-[0.86rem] font-medium text-ink">{document.fileName}</p>
                  <p className="m-0 mt-0.5 text-[0.76rem] text-fg-muted">
                    {document.chunkCount} sections · {document.characterCount.toLocaleString()} characters
                    {document.originalStored && document.originalSizeBytes ? ` · ${formatFileSize(document.originalSizeBytes)}` : ""}
                  </p>
                </div>
                <Badge tone={document.status === "ready" ? "success" : "neutral"} className="capitalize">{document.status}</Badge>
                <Button variant="ghost" size="icon-xs" aria-label={`Delete ${document.fileName}`} disabled={documentBusy} className="hover:bg-danger/[0.08] hover:text-danger" onClick={() => void removeDocument(document.id)}>
                  <Trash2 />
                </Button>
              </div>
            ))}
          </div>
        )}
        {documents.length > 0 ? <p className="m-0 text-[0.76rem] text-fg-muted">PDF, Word, text, Markdown, CSV or HTML · up to 10 MB each · {documents.length} of 20 used</p> : null}
      </StudioGroup>
    </StudioSection>
  );
}

function EmptyBox({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return (
    <div className="grid place-items-center gap-1.5 rounded-[var(--radius-card)] border border-dashed border-line-strong px-6 py-8 text-center">
      <span className="mb-1 grid size-9 place-items-center rounded-full bg-tint/[0.05] text-fg-muted [&_svg]:size-4">{icon}</span>
      <p className="m-0 text-[0.86rem] font-medium text-ink">{title}</p>
      <p className="m-0 max-w-sm text-[0.78rem] leading-relaxed text-fg-muted">{text}</p>
    </div>
  );
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function knowledgeChunks(value: string) {
  const normalized = value.replace(/\r\n?/g, "\n").split("\n").map((line) => line.trim()).join("\n").replace(/\n{3,}/g, "\n\n").trim();
  if (!normalized) return [];
  const chunks: string[] = [];
  let current = "";
  for (const paragraph of normalized.split(/\n\s*\n/)) {
    for (const word of paragraph.trim().split(/\s+/)) {
      if (current && current.length + word.length + 1 > 700) {
        chunks.push(current.trim());
        current = "";
      }
      current += `${current && !/\s$/.test(current) ? " " : ""}${word}`;
    }
    if (current.length >= 490) {
      chunks.push(current.trim());
      current = "";
    } else if (current) {
      current += "\n\n";
    }
  }
  if (current.trim()) chunks.push(current.trim());
  return chunks;
}

const FALLBACK_AGENT_INTEGRATIONS: IntegrationCatalogEntry[] = [
  { provider: "google_calendar", name: "Google Calendar", category: "Calendar", description: "Check free slots during the call and create the event when the caller books.", duringCall: true, postCall: false, requiresConnection: true, configurationFields: [], credentialFields: [], authorizationConfigured: true },
  { provider: "custom_webhook", name: "Webhook", category: "Developer", description: "Send a signed JSON event to your own endpoint after each call.", duringCall: false, postCall: true, requiresConnection: true, configurationFields: [], credentialFields: [], authorizationConfigured: true },
];

const CALENDAR_PROVIDERS = new Set(["google_calendar", "calendly"]);

function IntegrationsSettings({ agentId, bookingEnabled }: { agentId?: string; bookingEnabled: boolean }) {
  const [catalog, setCatalog] = useState<IntegrationCatalogEntry[]>([]);
  const [bindings, setBindings] = useState<AgentIntegration[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    Promise.allSettled([getIntegrationCatalog(), agentId ? getAgentIntegrations(agentId) : Promise.resolve([] as AgentIntegration[])])
      .then(([catalogResult, bindingResult]) => {
        if (!active) return;
        setCatalog(catalogResult.status === "fulfilled" && catalogResult.value.length ? catalogResult.value : FALLBACK_AGENT_INTEGRATIONS);
        setBindings(bindingResult.status === "fulfilled" ? bindingResult.value : []);
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [agentId]);

  const stateOf = (provider: string) => {
    const binding = bindings.find((item) => item.provider === provider);
    const connected = Boolean(binding && ["connected", "built_in", "active"].includes(binding.connectionStatus));
    return { binding, connected, active: connected && binding?.enabled === true };
  };
  const rows = catalog.map((entry) => ({ entry, ...stateOf(entry.provider) }));
  const activeRows = rows.filter((row) => row.active);
  const otherRows = rows.filter((row) => !row.active)
    .sort((left, right) => Number(right.connected) - Number(left.connected) || left.entry.name.localeCompare(right.entry.name));
  const calendarReady = rows.some((row) => CALENDAR_PROVIDERS.has(row.entry.provider) && row.active);
  const manageHref = (provider: string) => `/dashboard/integrations?provider=${provider}${agentId ? `&agentId=${agentId}` : ""}`;

  const renderRow = ({ entry, binding, connected, active }: (typeof rows)[number]) => (
    <div key={entry.provider} className="flex items-start gap-3.5 px-4 py-3.5 max-sm:flex-wrap">
      <span className="grid size-10 shrink-0 place-items-center rounded-[var(--radius-control)] border border-line bg-surface">
        <IntegrationLogo provider={entry.provider} fallback={<Plug className="size-[18px] text-fg-muted" />} />
      </span>
      <div className="min-w-0 flex-1">
        <p className="m-0 flex flex-wrap items-center gap-2 text-[0.88rem] font-medium text-ink">
          {entry.name}
          {active ? <Badge tone="success" dot>On for this agent</Badge> : connected ? <Badge tone="neutral">Connected · off</Badge> : null}
        </p>
        <p className="m-0 mt-0.5 text-[0.78rem] leading-relaxed text-fg-muted">{entry.description}</p>
        <p className="m-0 mt-1.5 flex flex-wrap gap-x-3 gap-y-1 text-[0.72rem] text-fg-muted">
          <span>{entry.category}</span>
          {entry.duringCall ? <span>· Used during calls</span> : null}
          {entry.postCall ? <span>· Runs after calls</span> : null}
          {binding?.lastDelivery?.deliveredAt ? <span>· Last used {new Date(binding.lastDelivery.deliveredAt).toLocaleDateString(undefined, { day: "numeric", month: "short" })}</span> : null}
        </p>
      </div>
      <div className="shrink-0 self-center max-sm:ml-[3.375rem]">
        {agentId ? (
          <Button asChild variant={active || connected ? "ghost" : "secondary"} size="xs">
            <Link href={manageHref(entry.provider)}>{active || connected ? "Manage" : "Connect"} <ArrowUpRight /></Link>
          </Button>
        ) : (
          <Button variant="secondary" size="xs" disabled>Save agent first</Button>
        )}
      </div>
    </div>
  );

  return (
    <StudioSection
      eyebrow="Tools & integrations"
      title="Connect actions this agent can perform"
      description="Integrations give the agent live information and let it take actions during and after calls."
      actions={(
        <Button asChild variant="ghost" size="xs">
          <Link href="/dashboard/integrations">All integrations <ArrowUpRight /></Link>
        </Button>
      )}
    >
      {!agentId ? (
        <div className="pb-6">
          <Note tone="warning" icon={<CircleAlert />} title="Save the agent first">Integrations are linked to a saved agent so credentials stay secure.</Note>
        </div>
      ) : null}
      {bookingEnabled && !calendarReady && !loading ? (
        <div className="pb-6">
          <Note
            tone="warning"
            icon={<CalendarCheck />}
            title="Bookings need a calendar"
            action={agentId ? (
              <Button asChild variant="secondary" size="xs"><Link href={manageHref("google_calendar")}>Connect calendar</Link></Button>
            ) : undefined}
          >
            Appointment booking is on, but no calendar is connected for this agent yet.
          </Note>
        </div>
      ) : null}

      <StudioGroup title="On for this agent" description="Tools the agent uses right now." action={<Meta>{loading ? "…" : activeRows.length}</Meta>}>
        {loading ? (
          <div className="grid gap-2">{[0, 1].map((item) => <div key={item} className="h-[72px] animate-pulse rounded-[var(--radius-card)] bg-tint/[0.05]" />)}</div>
        ) : activeRows.length ? (
          <div className="divide-y divide-line overflow-hidden rounded-[var(--radius-card)] border border-line">{activeRows.map(renderRow)}</div>
        ) : (
          <EmptyBox icon={<Plug />} title="No tools on yet" text="Connect a tool below and the agent can use it on its next call." />
        )}
      </StudioGroup>

      {!loading && otherRows.length ? (
        <StudioGroup title="Available" description="Connect once for the workspace, then choose which agents use it." action={<Meta>{otherRows.length}</Meta>}>
          <div className="divide-y divide-line overflow-hidden rounded-[var(--radius-card)] border border-line">{otherRows.map(renderRow)}</div>
        </StudioGroup>
      ) : null}
    </StudioSection>
  );
}

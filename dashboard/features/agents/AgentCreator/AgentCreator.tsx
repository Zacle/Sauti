"use client";

import "./AgentCreator.css";
import Link from "next/link";
import * as Slider from "@radix-ui/react-slider";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import type { LucideIcon } from "lucide-react";
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Bot,
  CalendarCheck,
  Check,
  ChevronDown,
  ChevronRight,
  CircleAlert,
  ExternalLink,
  FileText,
  FileUp,
  Clock3,
  Headphones,
  Languages,
  LoaderCircle,
  MessageSquareText,
  Mic2,
  Phone,
  PhoneCall,
  Play,
  Plug,
  Plus,
  RefreshCw,
  Route,
  Search,
  Settings2,
  ShieldCheck,
  Sparkles,
  UsersRound,
  WandSparkles,
  Webhook,
  Trash2,
  X,
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
  updateAgentVariables,
  uploadKnowledgeDocument,
} from "@/lib/api/agents";
import type { Agent, AgentReadiness, AgentTemplate as StoredAgentTemplate, AgentVariableDefinition, AvailablePhoneNumber, CreateAgentVariable, KnowledgeDocument } from "@/types/api";
import { useAuth } from "@/hooks/useAuth";
import { TIMEZONE_GROUPS } from "@/lib/timezones";
import { COUNTRIES } from "@/lib/countries";
import { VoicePicker } from "./VoicePicker";
import { TestCallPanel } from "./TestCallPanel";
import { AddVariableForm } from "../AgentVariables/AddVariableForm";
import { getAgentIntegrations, type AgentIntegration } from "@/lib/api/integrations";
import { DeleteAgentDialog } from "@/features/agents/DeleteAgentDialog/DeleteAgentDialog";
import { DarkSelect } from "@/components/DarkSelect/DarkSelect";
import { structuredAgentSetting, type StructuredAgentSetting } from "@/features/agents/domain/structured-agent-settings";

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
};

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
  bot: Bot,
};

function mapStoredTemplate(template: StoredAgentTemplate): Template {
  let bookingEnabled = false;
  let group: Template["group"] = "Appointments";
  let icon: LucideIcon = Bot;
  let escalationPhrases = ["speak to a person", "talk to a human", "human agent"];
  let variables: AgentVariableDefinition[] = [];
  try {
    const configuration = JSON.parse(template.configurationJson) as {
      bookingEnabled?: boolean;
      group?: Template["group"];
      icon?: string;
      escalationPhrases?: string[];
      variables?: AgentVariableDefinition[];
    };
    bookingEnabled = Boolean(configuration.bookingEnabled);
    group = configuration.group ?? group;
    icon = iconMap[configuration.icon ?? "bot"] ?? Bot;
    escalationPhrases = configuration.escalationPhrases ?? escalationPhrases;
    variables = Array.isArray(configuration.variables) ? configuration.variables : [];
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
  };
}

const studioSections = [
  { id: "main", label: "Main settings", icon: Settings2 },
  { id: "behavior", label: "Behavior & prompt", icon: MessageSquareText },
  { id: "speech", label: "Speech & transcription", icon: Languages },
  { id: "calls", label: "Call behaviour", icon: PhoneCall },
  { id: "routing", label: "Routing", icon: Route },
  { id: "integrations", label: "Integrations", icon: Plug },
  { id: "knowledge", label: "Knowledge", icon: BookOpen },
  { id: "postcall", label: "Post-call", icon: Check },
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
  const [variableValues, setVariableValues] = useState<Record<string, string>>({});
  const [customVariables, setCustomVariables] = useState<AgentVariableDefinition[]>([]);
  const [existingVariableKeys, setExistingVariableKeys] = useState<string[]>([]);
  const [showPersonalisation, setShowPersonalisation] = useState(openPersonalisation);
  const [loadingAgent, setLoadingAgent] = useState(editing);
  const [saved, setSaved] = useState(false);
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
        setCustomVariables(definitions);
        setExistingVariableKeys(definitions.map((variable) => variable.key));
        setVariableValues({
          ...Object.fromEntries(variables.map((variable) => [variable.key, variable.value])),
          ...(agent.calendarProvider ? { calendar_provider: agent.calendarProvider } : {}),
          ...(agent.routingPolicy ? { routing_policy: agent.routingPolicy } : {}),
        });
        setStage("studio");
      })
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load this agent."))
      .finally(() => setLoadingAgent(false));
  }, [agentId]);

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
  const countryName = COUNTRIES.find((country) => country.code === session?.tenant.countryCode)?.name
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
    setCustomVariables([]);
    setVariableValues(Object.fromEntries(template.variables.map((variable) => [variable.key, ""])));
    setStage("studio");
    setShowPersonalisation(template.variables.length > 0);
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
        setSaved(true);
        setBusy(false);
        return;
      }
      let created;
      if (selectedTemplate.source === "stored") {
        created = await createAgentFromTemplate(selectedTemplate.id, {
          name,
          timezone,
          humanTransferNumber: draft.humanTransferNumber,
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

  return (
    <>
      {loadingAgent ? (
        <div className="agent-editor-loading"><LoaderCircle className="spin" /> Loading agent configuration...</div>
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
            <div className="agent-studio-actions">
              {(needsBusinessDetails || (editing && readiness && readiness.nextStep !== "ready")) && (
                <button
                  className={`agent-setup-action ${needsBusinessDetails ? "attention" : ""}`}
                  disabled={busy}
                  onClick={() => needsBusinessDetails ? setShowPersonalisation(true) : void continueSetup()}
                  type="button"
                >
                  <span className="agent-setup-action-icon">
                    {needsBusinessDetails ? <CircleAlert size={16} /> : <Settings2 size={16} />}
                  </span>
                  <span className="agent-setup-action-copy">
                    <small>{needsBusinessDetails ? "Setup incomplete" : "Next step"}</small>
                    <span>{requiredDetailCount > 0 ? `${requiredDetailCount} details required` : setupActionLabel}</span>
                  </span>
                  <ArrowRight size={15} />
                </button>
              )}
              {editing && loadedAgent?.active && <span className="agent-live-badge"><Check size={13} /> Live</span>}
              <button className="console-secondary-button" type="button"><Play size={15} /> Test</button>
              {editing && (
                <button aria-label="Delete agent" className="studio-delete-button" onClick={() => {
                  setDeleteError("");
                  setShowDelete(true);
                }} type="button"><Trash2 size={16} /></button>
              )}
              <button className="console-primary-button" disabled={busy} type="submit">
                {busy && <LoaderCircle className="spin" size={16} />}
                {saved && !busy ? <Check size={15} /> : null}
                {editing ? saved ? "Saved" : "Save changes" : "Save draft"} <ArrowRight size={15} />
              </button>
            </div>
          </header>

          <div className="agent-studio-layout">
            <aside className="agent-studio-nav">
              <span>Configure</span>
              {studioSections.map(({ id, label, icon: Icon }) => (
                <button className={activeSection === id ? "active" : ""} type="button" key={id} onClick={() => setActiveSection(id)}>
                  <Icon size={18} /> {label}
                </button>
              ))}
              <div className="studio-template-note">
                <Sparkles size={17} />
                <div><small>{editing ? "Business details" : "Template"}</small><strong>{editing ? `${agentVariables.length} prompt variables` : selectedTemplate.name}</strong></div>
                <button type="button" onClick={() => editing ? setShowPersonalisation(true) : agentVariables.length ? setShowPersonalisation(true) : setStage("templates")}>
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
                  llmTier={llmTier}
                  onLlmTier={setLlmTier}
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

            <TestCallPanel agentId={agentId} agentName={name || "your agent"} voiceId={voice} />
          </div>
          {showPersonalisation && (
            <PersonalisationDrawer
              agentSaved={Boolean(agentId)}
              countryName={countryName}
              values={variableValues}
              variables={agentVariables}
              onAdd={addCustomVariable}
              onChange={(key, value) => {
                setSaved(false);
                setVariableValues((current) => ({ ...current, [key]: value }));
              }}
              onClose={() => setShowPersonalisation(false)}
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
              defaultCountryCode={session?.tenant.countryCode ?? "US"}
              existingNumber={loadedAgent?.twilioPhoneNumber}
              existingProvider={loadedAgent?.phoneNumberProvider}
              onClose={() => setShowNumberPicker(false)}
              onAssigned={(agent) => {
                setLoadedAgent(agent);
                setShowNumberPicker(false);
                setSaved(true);
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
      setReadiness(await getAgentReadiness(agentId));
      setSaved(true);
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
  const [warningDismissed, setWarningDismissed] = useState(false);
  const [countryMenuOpen, setCountryMenuOpen] = useState(false);
  const [countrySearch, setCountrySearch] = useState("");
  const selectedCountry = COUNTRIES.find((country) => country.code === countryCode);
  const matchingCountries = COUNTRIES.filter((country) => {
    const query = countrySearch.trim().toLowerCase();
    return !query
      || country.name.toLowerCase().includes(query)
      || country.code.toLowerCase().includes(query);
  });

  async function searchNumbers() {
    setLoading(true);
    setError("");
    setSelectedNumber("");
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
      onAssigned(await provisionAgentNumber(agentId, phoneNumber, Boolean(existingNumber)));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to assign this number.");
      setOrdering(false);
    }
  }

  return (
    <div className="number-picker-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !ordering) onClose();
    }}>
      <section aria-labelledby="number-picker-title" aria-modal="true" className="number-picker-modal" role="dialog">
        <header>
          <span className="number-picker-icon"><Phone size={26} /></span>
          <div>
            <h2 id="number-picker-title">Choose a phone number</h2>
            <p>Select the market your customers call from. Availability and regulatory requirements vary by country.</p>
          </div>
          <button aria-label="Close" className="number-picker-close" disabled={ordering} onClick={onClose} type="button"><X size={20} /></button>
        </header>

        <div className="number-picker-toolbar">
          <div className="number-picker-country-field">
            <span>Country or market</span>
            <div className="number-picker-country">
              <button
                aria-expanded={countryMenuOpen}
                className="number-picker-country-trigger"
                onClick={() => {
                  setCountryMenuOpen((open) => !open);
                  setCountrySearch("");
                }}
                type="button"
              >
                <span
                  className={`fi fi-${countryCode.toLowerCase()} number-picker-flag`}
                  aria-hidden="true"
                />
                <span>{selectedCountry?.name ?? countryCode}</span>
                <ChevronDown className={countryMenuOpen ? "open" : ""} size={16} />
              </button>
              {countryMenuOpen && (
                <div className="number-picker-country-menu">
                  <label>
                    <Search size={15} />
                    <input
                      autoFocus
                      onChange={(event) => setCountrySearch(event.target.value)}
                      placeholder="Search countries"
                      value={countrySearch}
                    />
                  </label>
                  <div role="listbox" aria-label="Countries and markets">
                    {matchingCountries.map((country) => (
                      <button
                        aria-selected={country.code === countryCode}
                        className={country.code === countryCode ? "selected" : ""}
                        key={country.code}
                        onClick={() => {
                          setCountryCode(country.code);
                          setCountryMenuOpen(false);
                          setCountrySearch("");
                        }}
                        role="option"
                        type="button"
                      >
                        <span
                          className={`fi fi-${country.code.toLowerCase()} number-picker-flag`}
                          aria-hidden="true"
                        />
                        <span>{country.name}</span>
                        {country.code === countryCode && <Check size={14} />}
                      </button>
                    ))}
                    {!matchingCountries.length && (
                      <span className="number-picker-country-empty">No countries match your search.</span>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
          <button className="number-picker-refresh" disabled={loading || ordering} onClick={() => void searchNumbers()} type="button">
            {loading ? <LoaderCircle className="spin" size={18} /> : <RefreshCw size={18} />} Refresh
          </button>
        </div>

        {existingNumber && !warningDismissed && (
          <div className="number-replacement-warning">
            <CircleAlert size={18} />
            <div>
              <strong>You are replacing {existingNumber}</strong>
              <span>
                The existing {readableProvider(existingProvider)} number remains in that provider account and may continue billing until you release it there.
              </span>
            </div>
            <button
              aria-label="Dismiss warning"
              className="number-warning-dismiss"
              onClick={() => setWarningDismissed(true)}
              type="button"
            >
              <X size={15} />
            </button>
          </div>
        )}

        <div className="number-picker-results">
          {loading ? (
            <div className="number-picker-state"><LoaderCircle className="spin" size={24} /><span>Checking availability...</span></div>
          ) : numbers.length ? (
            numbers.map((number, index) => {
              const selected = selectedNumber === number.phoneNumber;
              const isRecommended = index === 0;
              return (
                <button
                  aria-pressed={selected}
                  className={selected ? "selected" : ""}
                  key={number.phoneNumber}
                  onClick={() => setSelectedNumber(number.phoneNumber)}
                  type="button"
                >
                  <span className="number-picker-radio">{selected && <span className="number-picker-radio-dot" />}</span>
                  <span className="number-picker-number">
                    <strong>{number.phoneNumber}</strong>
                    <small>
                      {[number.locality || countryCode, number.region, readableNumberType(number.type)].filter(Boolean).join(" · ")}
                      {isRecommended && (
                        <span className="number-recommended-badge">
                          <Sparkles size={11} /> Recommended
                        </span>
                      )}
                    </small>
                  </span>
                  <span className="number-picker-price">
                    <strong>{formatPhoneNumberCost(number.monthlyCost, number.currency)}</strong>
                    <small>per month</small>
                  </span>
                  <ChevronRight className="number-row-chevron" size={17} />
                </button>
              );
            })
          ) : (
            <div className="number-picker-state">
              <span className="number-picker-state-icon"><Phone size={22} /></span>
              <strong>No browsable numbers found</strong>
              <span>{error || "This provider or market does not currently expose selectable numbers."}</span>
            </div>
          )}
        </div>

        {error && numbers.length > 0 && <div className="number-picker-error"><CircleAlert size={15} /> {error}</div>}
        <footer>
          <div className="number-picker-footer-notice">
            <ShieldCheck className="number-picker-shield" size={36} />
            <p>Purchasing a number may create a recurring provider charge. Numbers requiring identity documents can remain pending until approved.</p>
          </div>
          <div className="number-picker-footer-actions">
            <button className="number-picker-cancel" disabled={ordering} onClick={onClose} type="button">Cancel</button>
            {numbers.length > 0 && (
              <button className="number-picker-select-rec" disabled={ordering} onClick={() => setSelectedNumber(numbers[0].phoneNumber)} type="button">
                <Sparkles size={14} /> Select recommended
              </button>
            )}
            <button
              className="number-picker-assign"
              disabled={!selectedNumber || ordering}
              onClick={() => void assignNumber(selectedNumber)}
              type="button"
            >
              {ordering && <LoaderCircle className="spin" size={16} />}
              Assign number
            </button>
          </div>
        </footer>
      </section>
    </div>
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
  return (
    <section className="agent-template-page">
      <button className="page-back-button" type="button" onClick={onBack}><ArrowLeft size={16} /> Agents</button>
      <div className="template-page-heading">
        <span className="page-eyebrow">Create an agent</span>
        <h1>How should your agent help callers?</h1>
        <p>Describe the job in your own words or start from a proven template. Everything remains editable.</p>
      </div>

      <div className="agent-brief-composer">
        <div className="agent-brief-body">
          <span><WandSparkles size={21} /></span>
          <textarea maxLength={1000} value={brief} onChange={(event) => onBriefChange(event.target.value)} placeholder="Example: Answer calls for our clinic, book appointments, and transfer urgent requests to the front desk." />
        </div>
        <div className="agent-brief-footer">
          <span>{brief.length} / 1000</span>
          <button disabled={!brief.trim() || generating} type="button" onClick={onGenerate}>
            {generating ? <><LoaderCircle className="spin" size={15} /> Generating</> : <>Generate setup <Sparkles size={15} /></>}
          </button>
        </div>
      </div>
      {generating && (
        <div className="agent-generation-state" role="status">
          <span><LoaderCircle className="spin" size={21} /></span>
          <div>
            <strong>Generating your agent</strong>
            <p>Analyzing the role, conversation flow, tools, safety rules, languages, and business details.</p>
          </div>
          <i><span /></i>
        </div>
      )}
      {error && <div className="form-alert error template-generation-error">{error}</div>}

      <div className="template-section-head">
        <div><h2>Start with a template</h2><p>Preconfigured prompts and tools for common call workflows.</p></div>
        <div className="template-filters">
          {categories.map((item) => <button className={category === item ? "active" : ""} disabled={generating} type="button" onClick={() => onCategoryChange(item)} key={item}>{item}</button>)}
        </div>
      </div>

      <div className="agent-template-grid">
        {visibleTemplates.map((template) => {
          const Icon = template.icon;
          return (
            <button disabled={generating} type="button" key={template.id} onClick={() => onSelect(template)}>
              <span className="template-card-top">
                <span className="template-card-icon"><Icon size={21} /></span>
                <h3>{template.name}</h3>
                <small>{template.group}</small>
              </span>
              <p>{template.description}</p>
              <i>Use template <ArrowRight size={15} /></i>
            </button>
          );
        })}
        <button className="blank-template-card" disabled={generating} type="button" onClick={() => onSelect({
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
        })}>
          <span className="template-card-top"><span className="template-card-icon"><Bot size={21} /></span><h3>Blank agent</h3><small>Custom</small></span><p>Configure every behavior and tool yourself.</p><i>Start blank <ArrowRight size={15} /></i>
        </button>
      </div>
    </section>
  );
}

function languageName(code: string) {
  return ({ en: "English", fr: "French", ar: "Arabic" } as Record<string, string>)[code] ?? code.toUpperCase();
}

function MainSettings(props: {
  name: string; description: string; language: string; supportedLanguages: string[]; voice: string; timezone: string; maxDuration: string; greeting: string;
  bookingEnabled: boolean; saveTranscript: boolean; recordCalls: boolean;
  webVoiceEnabled: boolean; webVoiceAllowedOrigins: string; webVoiceRequireConsent: boolean;
  webVoicePublicId?: string; whatsappEnabled: boolean; whatsappPhoneNumberId: string; phoneNumber?: string | null;
  phoneNumberProvider?: string | null; phoneNumberStatus?: string | null;
  channelFocusRequest: number;
  onAssignPhone?: () => void;
  onRefreshPhone?: () => void;
  onName: (value: string) => void; onDescription: (value: string) => void; onLanguage: (value: string) => void;
  onSupportedLanguages: (value: string[]) => void; onVoice: (value: string) => void;
  onTimezone: (value: string) => void; onDuration: (value: string) => void; onGreeting: (value: string) => void;
  onBooking: (value: boolean) => void; onSaveTranscript: (value: boolean) => void; onRecordCalls: (value: boolean) => void;
  onWebVoiceEnabled: (value: boolean) => void; onWebVoiceAllowedOrigins: (value: string) => void;
  onWebVoiceRequireConsent: (value: boolean) => void;
  onWhatsappEnabled: (value: boolean) => void; onWhatsappPhoneNumberId: (value: string) => void;
}) {
  const channelSettingsRef = useRef<HTMLDivElement>(null);
  const [highlightChannels, setHighlightChannels] = useState(false);
  const [widgetColor, setWidgetColor] = useState("#31d9c9");
  const [widgetPosition, setWidgetPosition] = useState<"right" | "left">("right");
  const [widgetLanguage, setWidgetLanguage] = useState(props.language);
  const [widgetLabel, setWidgetLabel] = useState("Talk to us");
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
  return (
    <>
      <StudioHeading eyebrow="Main settings" title="Identity and call setup" description="Configure the essentials callers experience on every conversation." />
      {!channelConfigured && (
        <div className="studio-warning">
          <CircleAlert size={18} />
          <div><strong>Enable a customer channel</strong><span>Use Web Voice, connect WhatsApp, or assign a phone number.</span></div>
          <button type="button" onClick={focusChannels}>Choose channel</button>
        </div>
      )}
      <div className="studio-form-grid">
        <label>Agent name<input value={props.name} onChange={(event) => props.onName(event.target.value)} /></label>
        <label>Card description<input maxLength={500} value={props.description} onChange={(event) => props.onDescription(event.target.value)} /></label>
        <label>
          Phone number
          <button
            className="studio-disabled-field studio-phone-selector"
            disabled={!props.onAssignPhone}
            onClick={props.onAssignPhone}
            type="button"
          >
            <Phone size={16} />
            {props.phoneNumber ?? (props.onAssignPhone ? "Choose an available number" : "Save the agent first")}
            <ChevronDown size={15} />
          </button>
        </label>
        <label>Primary language<DarkSelect ariaLabel="Primary language" icon={<Languages size={16} />} value={props.language} onValueChange={props.onLanguage}
          options={[{ value: "en", label: "English" }, { value: "fr", label: "French" }, { value: "ar", label: "Arabic" }]} /></label>
        <label>
          Timezone
          <DarkSelect ariaLabel="Agent timezone" icon={<Clock3 size={16} />} value={props.timezone} onValueChange={props.onTimezone}
            options={TIMEZONE_GROUPS.flatMap((group) => group.zones.map((zone) => ({ value: zone.value, label: zone.label })))} />
        </label>
      </div>
      <div
        className={`studio-setting-group web-voice-settings${highlightChannels ? " channel-focus" : ""}`}
        id="agent-channel-settings"
        ref={channelSettingsRef}
        tabIndex={-1}
      >
        <h3>Live channels</h3>
        <p>Web Voice and phone calls can operate together. Enabling one does not disable the other.</p>
        <ToggleRow icon={Mic2} title="Web Voice" detail="Let visitors speak with this agent from a browser without calling a phone number." value={props.webVoiceEnabled} onChange={props.onWebVoiceEnabled} />
        {props.webVoiceEnabled && (
          <div className="web-voice-config">
            <ToggleRow icon={ShieldCheck} title="Require microphone consent" detail="Visitors must explicitly accept microphone use before a session starts." value={props.webVoiceRequireConsent} onChange={props.onWebVoiceRequireConsent} />
            <label>
              Allowed website origins
              <textarea value={props.webVoiceAllowedOrigins} onChange={(event) => props.onWebVoiceAllowedOrigins(event.target.value)} placeholder={"https://example.com\nhttps://booking.example.com"} />
              <small>One origin per line. Leave empty while testing the hosted call page.</small>
            </label>
            {props.webVoicePublicId ? (
              <div className="web-voice-share">
                <div><span>Hosted call page</span><code>/call/{props.webVoicePublicId}</code></div>
                <Link href={`/call/${props.webVoicePublicId}`} target="_blank">Open page <ExternalLink size={14} /></Link>
                <div className="web-voice-widget-options">
                  <label>
                    Button label
                    <input maxLength={40} value={widgetLabel} onChange={(event) => setWidgetLabel(event.target.value)} />
                  </label>
                  <label>
                    Language
                    <DarkSelect ariaLabel="Widget language" value={widgetLanguage} onValueChange={setWidgetLanguage}
                      options={props.supportedLanguages.map((code) => ({ value: code, label: languageName(code) }))} />
                  </label>
                  <label>
                    Accent color
                    <span className="web-voice-color"><input type="color" value={widgetColor} onChange={(event) => setWidgetColor(event.target.value)} /><code>{widgetColor}</code></span>
                  </label>
                  <label>
                    Position
                    <DarkSelect ariaLabel="Widget position" value={widgetPosition} onValueChange={(value) => setWidgetPosition(value as "right" | "left")}
                      options={[{ value: "right", label: "Bottom right" }, { value: "left", label: "Bottom left" }]} />
                  </label>
                </div>
                <div className="web-voice-embed">
                  <span>Embed code</span>
                  <code>{widgetEmbedCode}</code>
                  <button type="button" onClick={() => void navigator.clipboard.writeText(widgetEmbedCode)}>Copy embed code</button>
                </div>
              </div>
            ) : <small className="field-help">Save the agent to generate its hosted page and embed code.</small>}
          </div>
        )}
        <ToggleRow
          icon={MessageSquareText}
          title="WhatsApp"
          detail="Reply to customer WhatsApp messages with this agent. Text is available now; voice notes are the next channel step."
          value={props.whatsappEnabled}
          onChange={props.onWhatsappEnabled}
        />
        {props.whatsappEnabled && (
          <div className="whatsapp-channel-config">
            <label>
              Meta phone number ID
              <input
                inputMode="numeric"
                value={props.whatsappPhoneNumberId}
                onChange={(event) => props.onWhatsappPhoneNumberId(event.target.value.replace(/\D/g, ""))}
                placeholder="123456789012345"
              />
              <small>Use the phone number ID shown in WhatsApp Manager, not the visible phone number.</small>
            </label>
            <p>Configure the Meta webhook as <code>https://YOUR_BACKEND/webhooks/whatsapp</code>.</p>
          </div>
        )}
        <div className="channel-status">
          <Phone size={18} />
          <div>
            <strong>Phone calls</strong>
            <small>
              {phoneConfigured
                ? `${props.phoneNumber} · ${readableProvider(props.phoneNumberProvider)}`
                : props.phoneNumber
                  ? `${props.phoneNumber} · ${readablePhoneStatus(props.phoneNumberStatus)}`
                  : "Browse available Telnyx numbers and confirm before purchasing."}
            </small>
          </div>
          <div className="channel-status-actions">
            {props.phoneNumber && <span className={`phone-status ${props.phoneNumberStatus ?? "active"}`}>{readablePhoneStatus(props.phoneNumberStatus)}</span>}
            {props.phoneNumber && props.phoneNumberStatus !== "active" && props.onRefreshPhone && (
              <button onClick={props.onRefreshPhone} type="button">Refresh status</button>
            )}
            <button disabled={!props.onAssignPhone} onClick={props.onAssignPhone} type="button">
              {props.phoneNumber ? "Change number" : "Choose number"}
            </button>
          </div>
        </div>
      </div>
      <div className="studio-setting-group">
        <h3>Supported caller languages</h3>
        <p>The agent detects these languages during a call. The primary language cannot be removed.</p>
        <div className="studio-language-options">
          {[["en", "English"], ["fr", "French"], ["ar", "Arabic"]].map(([code, label]) => {
            const selected = props.supportedLanguages.includes(code);
            return (
              <button
                className={selected ? "selected" : ""}
                disabled={code === props.language}
                key={code}
                type="button"
                onClick={() => props.onSupportedLanguages(selected
                  ? props.supportedLanguages.filter((item) => item !== code)
                  : [...props.supportedLanguages, code])}
              >
                {selected && <Check size={15} />} {label}
              </button>
            );
          })}
        </div>
      </div>
      <div className="studio-setting-group">
        <h3>Capabilities</h3>
        <ToggleRow icon={CalendarCheck} title="Appointment booking" detail="Allow the agent to check availability and create bookings." value={props.bookingEnabled} onChange={props.onBooking} />
      </div>
      <div className="studio-setting-group">
        <h3>Privacy and call records</h3>
        <ToggleRow icon={MessageSquareText} title="Save transcript" detail="Store the transcript for call review and analytics." value={props.saveTranscript} onChange={props.onSaveTranscript} />
        <ToggleRow
          icon={Mic2}
          title="Record phone calls"
          detail="Capture both caller and agent audio. Make sure your greeting provides any consent notice required in your callers' locations."
          value={props.recordCalls}
          onChange={props.onRecordCalls}
        />
      </div>
      <div className="studio-setting-group">
        <h3>Inbound greeting</h3>
        <p>The first message callers hear when the agent answers.</p>
        <textarea className="studio-greeting" value={props.greeting} onChange={(event) => props.onGreeting(event.target.value)} />
        <small className="field-help">Use clear, natural language. Variables can be added later.</small>
      </div>
    </>
  );
}

function BehaviorSettings({
  prompt,
  onPrompt,
  template,
  llmTier,
  onLlmTier,
  variables,
  values,
}: {
  prompt: string;
  onPrompt: (value: string) => void;
  template: string;
  llmTier: "standard" | "advanced";
  onLlmTier: (value: "standard" | "advanced") => void;
  variables: AgentVariableDefinition[];
  values: Record<string, string>;
}) {
  const [preview, setPreview] = useState(variables.length > 0);
  return (
    <>
      <StudioHeading eyebrow="Behavior" title="Prompt and conversation rules" description="Define what the agent should accomplish and the boundaries it must follow." />
      <div className="llm-tier-section">
        <div><h3>Conversation intelligence</h3><p>Choose based on call complexity. Sauti manages the underlying model.</p></div>
        <div className="llm-tier-options">
          <button className={llmTier === "standard" ? "selected" : ""} type="button" onClick={() => onLlmTier("standard")}>
            <span>{llmTier === "standard" && <Check size={14} />}</span><div><strong>Standard</strong><small>Bookings, FAQs, and simple routing</small></div><i>Faster · Lower cost</i>
          </button>
          <button className={llmTier === "advanced" ? "selected" : ""} type="button" onClick={() => onLlmTier("advanced")}>
            <span>{llmTier === "advanced" && <Check size={14} />}</span><div><strong>Advanced</strong><small>Complex intake and sensitive conversations</small></div><i>Higher reasoning quality</i>
          </button>
        </div>
      </div>
      <div className="prompt-toolbar">
        <button type="button"><Sparkles size={15} /> {template}</button>
        {variables.length > 0 && (
          <div className="prompt-view-toggle">
            <button className={!preview ? "active" : ""} type="button" onClick={() => setPreview(false)}>Raw prompt</button>
            <button className={preview ? "active" : ""} type="button" onClick={() => setPreview(true)}>Preview details</button>
          </div>
        )}
      </div>
      {preview && variables.length > 0
        ? <PromptPreview prompt={prompt} values={values} />
        : <textarea className="studio-prompt-editor" value={prompt} onChange={(event) => onPrompt(event.target.value)} spellCheck />}
      <div className="prompt-guidance">
        <ShieldCheck size={19} />
        <div><strong>Keep instructions testable</strong><p>Specify goals, required details, tool rules, escalation conditions, and the desired conversation style.</p></div>
      </div>
    </>
  );
}

function PromptPreview({ prompt, values }: { prompt: string; values: Record<string, string> }) {
  const segments = prompt.split(/(\{\{[a-zA-Z0-9_]+}})/g);
  return (
    <pre className="studio-prompt-preview">
      {segments.map((segment, index) => {
        const match = segment.match(/^\{\{([a-zA-Z0-9_]+)}}$/);
        if (!match) return <span key={`${index}-${segment.slice(0, 8)}`}>{segment}</span>;
        const value = values[match[1]]?.trim();
        return value
          ? <mark key={`${index}-${match[1]}`}>{value}</mark>
          : <em key={`${index}-${match[1]}`}>Not provided</em>;
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
    <>
      <StudioHeading eyebrow="Speech & transcription" title="Voice and turn-taking" description="Tune how the agent sounds, listens, and decides that a caller has finished speaking." />
      <div className="studio-setting-group first">
        <h3>Agent voice</h3>
        <p>Select and preview a voice from the enabled TTS provider.</p>
        <VoicePicker value={props.voice} primaryLanguage={props.language} supportedLanguages={props.supportedLanguages} onChange={props.onVoice} />
      </div>
      <RangeSetting label="Interruption sensitivity" detail="Higher values make the agent stop speaking more quickly when the caller talks." value={props.bargeInSensitivity} min={0} max={1} step={0.05} suffix={props.bargeInSensitivity.toFixed(2)} onChange={props.onBargeInSensitivity} />
      <RangeSetting label="Interruption grace period" detail="Minimum caller speech before an interruption is accepted." value={props.bargeInGraceMs} min={0} max={1500} step={50} suffix={`${props.bargeInGraceMs} ms`} onChange={props.onBargeInGraceMs} />
      <RangeSetting label="Response latency" detail="Silence Deepgram waits before finalizing the caller's turn." value={props.sttEndpointingMs} min={100} max={1500} step={50} suffix={`${props.sttEndpointingMs} ms`} onChange={props.onSttEndpointingMs} />
      <div className="studio-form-grid advanced-grid">
        <label>Vocabulary specialization<DarkSelect ariaLabel="Vocabulary specialization" value={props.vocabularyDomain} onValueChange={props.onVocabularyDomain}
          options={[{ value: "general", label: "General" }, { value: "medical", label: "Medical" }]} /></label>
        <label>Boosted keyterms<input value={props.boostedKeywords} onChange={(event) => props.onBoostedKeywords(event.target.value)} placeholder="Kibera, Ouagadougou, Thiong'o" /></label>
      </div>
    </>
  );
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
    <>
      <StudioHeading eyebrow="Call behaviour" title="Silence, screening, and safety" description="Control how the agent handles unattended calls, long pauses, and sensitive topics." />
      <div className="studio-setting-group first">
        <ToggleRow icon={PhoneCall} title="Voicemail detection" detail="End the call when the first transcript matches a voicemail greeting." value={props.detectVoicemail} onChange={props.onDetectVoicemail} />
        <ToggleRow icon={ShieldCheck} title="Call-screen handling" detail="Recognize screening prompts such as “Who is calling?”" value={props.handleCallScreening} onChange={props.onHandleCallScreening} />
        <ToggleRow icon={Settings2} title="DTMF keypad input" detail="Capture keypad sequences when Twilio media-event support is enabled." value={props.dtmfEnabled} onChange={props.onDtmfEnabled} />
      </div>
      {props.dtmfEnabled && (
        <div className="dtmf-configuration">
          <div className="dtmf-heading">
            <div><h3>Keypad menu</h3><p>Translate caller key presses into explicit intents the agent can follow.</p></div>
            <button onClick={addMapping} type="button"><Plus size={15} /> Add option</button>
          </div>
          <div className="studio-form-grid advanced-grid">
            <label>
              Completion key
              <DarkSelect ariaLabel="DTMF completion key" value={props.dtmfTerminationKey} onValueChange={(value) => props.onDtmfTerminationKey(value as "#" | "*")}
                options={[{ value: "#", label: "# Hash" }, { value: "*", label: "* Star" }]} />
              <small>Submits the digits immediately and is not included in the value.</small>
            </label>
            <label>
              Input timeout
              <div className="input-with-suffix"><input type="number" min="1" max="30" value={props.dtmfInputTimeoutSeconds} onChange={(event) => props.onDtmfInputTimeoutSeconds(Number(event.target.value))} /><span>seconds</span></div>
              <small>Submits automatically after the caller stops entering digits.</small>
            </label>
            <label>
              Maximum digits
              <input type="number" min="1" max="32" value={props.dtmfMaxDigits} onChange={(event) => props.onDtmfMaxDigits(Number(event.target.value))} />
              <small>Useful for confirmation codes and reference numbers.</small>
            </label>
          </div>
          <div className="dtmf-options">
            {mappings.length === 0 ? (
              <div className="dtmf-empty">No menu options yet. Unmapped digit sequences are still passed to the agent.</div>
            ) : mappings.map(([digits, meaning], index) => (
              <div className="dtmf-option" key={`${digits}-${index}`}>
                <label><span>Digits</span><input inputMode="numeric" maxLength={props.dtmfMaxDigits} value={digits} onChange={(event) => updateMapping(index, event.target.value, meaning)} placeholder="1" /></label>
                <label><span>Caller intent</span><input value={meaning} onChange={(event) => updateMapping(index, digits, event.target.value)} placeholder="Confirm the appointment" /></label>
                <button aria-label={`Remove keypad option ${digits}`} onClick={() => removeMapping(digits)} type="button"><Trash2 size={16} /></button>
              </div>
            ))}
          </div>
        </div>
      )}
      <RangeSetting label="Maximum call duration" detail="End calls that exceed this duration." value={Number(props.maxDuration)} min={1} max={60} step={1} suffix={`${props.maxDuration} min`} onChange={(value) => props.onMaxDuration(String(value))} />
      <RangeSetting label="End call on silence" detail="End an unattended call after sustained silence." value={props.endCallOnSilenceSeconds} min={60} max={900} step={30} suffix={`${Math.round(props.endCallOnSilenceSeconds / 60)} min`} onChange={props.onEndCallOnSilenceSeconds} />
      <div className="studio-form-grid advanced-grid">
        <label>Reminder after silence<div className="input-with-suffix"><input type="number" min="30" max="600" value={props.reminderAfterSilenceSeconds} onChange={(event) => props.onReminderAfterSilenceSeconds(Number(event.target.value))} /><span>seconds</span></div></label>
        <label>Maximum reminders<input type="number" min="0" max="10" value={props.maxReminders} onChange={(event) => props.onMaxReminders(Number(event.target.value))} /></label>
      </div>
      <div className="studio-setting-group">
        <h3>Safety guardrails</h3><p>These constraints are appended after the editable business prompt.</p>
        <div className="studio-language-options">{guardrails.map(([value, label]) => <button className={props.safetyGuardrails.includes(value) ? "selected" : ""} type="button" key={value} onClick={() => props.onSafetyGuardrails(props.safetyGuardrails.includes(value) ? props.safetyGuardrails.filter((item) => item !== value) : [...props.safetyGuardrails, value])}>{props.safetyGuardrails.includes(value) && <Check size={15} />}{label}</button>)}</div>
      </div>
    </>
  );
}

function PostCallSettings({ fields, onFields }: { fields: string[]; onFields: (fields: string[]) => void }) {
  const options = [["summary", "Call summary"], ["successful", "Call successful"], ["sentiment", "User sentiment"], ["intent", "Caller intent"]];
  return (
    <>
      <StudioHeading eyebrow="Post-call" title="Structured call analysis" description="Choose the fields generated after the call for reporting and downstream workflows." />
      <div className="studio-setting-group first">
        {options.map(([value, title]) => <ToggleRow icon={Check} title={title} detail={`Extract ${title.toLowerCase()} after the call ends.`} value={fields.includes(value)} onChange={(enabled) => onFields(enabled ? [...fields, value] : fields.filter((item) => item !== value))} key={value} />)}
      </div>
      <div className="prompt-guidance"><ShieldCheck size={19} /><div><strong>Analysis respects transcript retention</strong><p>Post-call fields are only generated when a transcript is available.</p></div></div>
    </>
  );
}

function RangeSetting({ label, detail, value, min, max, step, suffix, onChange }: { label: string; detail: string; value: number; min: number; max: number; step: number; suffix: string; onChange: (value: number) => void }) {
  return <div className="range-setting"><div><strong>{label}</strong><small>{detail}</small></div><span>{suffix}</span>
    <Slider.Root className="studio-slider" value={[value]} min={min} max={max} step={step} onValueChange={([next]) => onChange(next)} aria-label={label}>
      <Slider.Track className="studio-slider-track"><Slider.Range className="studio-slider-range" /></Slider.Track>
      <Slider.Thumb className="studio-slider-thumb" />
    </Slider.Root>
  </div>;
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
  return (
    <>
      <StudioHeading eyebrow="Routing" title="Escalation and availability" description="Control when calls stay with the agent and when they move to your team." />
      <div className="studio-setting-group"><h3>Human transfer</h3><ToggleRow icon={UsersRound} title="Allow live transfer" detail="Transfer callers when they request a person or the agent cannot safely continue." value={props.transferEnabled} onChange={props.onTransferEnabled} /></div>
      <div className="studio-form-grid"><label>Transfer number<input disabled={!props.transferEnabled} inputMode="tel" value={props.transferNumber} onChange={(event) => props.onTransferNumber(event.target.value)} placeholder="+254..." /></label></div>
      <div className="studio-setting-group">
        <h3>Escalation phrases</h3>
        <p>Comma-separated caller phrases that should trigger a transfer.</p>
        <textarea className="studio-greeting" value={props.escalationPhrases} onChange={(event) => props.onEscalationPhrases(event.target.value)} />
      </div>
      <div className="studio-setting-group">
        <h3>Operating hours</h3>
        <p>Calls are evaluated in {props.timezone}. Overnight ranges such as 20:00–02:00 are supported.</p>
        <WeeklyHoursEditor value={props.operatingHours} onChange={props.onOperatingHours} />
      </div>
      <div className="studio-setting-group">
        <h3>Outside operating hours</h3>
        <p>Choose what callers experience when your team is closed.</p>
        <div className="after-hours-options">
          {([
            ["answer", "Answer normally", "Keep the full agent and its tools available."],
            ["take_message", "Collect a message", "Capture the caller’s name, number, and reason for calling."],
            ["closed", "Announce closure", "Play a short message, thank the caller, and end the call."],
          ] as Array<[AfterHoursBehavior, string, string]>).map(([value, title, detail]) => (
            <button className={props.afterHoursBehavior === value ? "selected" : ""} key={value} onClick={() => props.onAfterHoursBehavior(value)} type="button">
              <span className="selection-dot">{props.afterHoursBehavior === value && <Check size={12} />}</span>
              <span><strong>{title}</strong><small>{detail}</small></span>
            </button>
          ))}
        </div>
        {props.afterHoursBehavior !== "answer" && (
          <label className="after-hours-message">
            Message callers hear
            <textarea
              maxLength={1000}
              value={props.afterHoursMessage}
              onChange={(event) => props.onAfterHoursMessage(event.target.value)}
              placeholder={props.afterHoursBehavior === "closed"
                ? "Thank you for calling. We are currently closed. Please call again during business hours."
                : "We are currently closed, but I can take your details and ask the team to call you back."}
            />
            <small>Leave empty to use Sauti’s localized default message.</small>
          </label>
        )}
      </div>
    </>
  );
}

function WeeklyHoursEditor({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const custom = value !== "always" && value !== "workspace";
  const schedule = parseWeeklyHours(value);

  function setMode(mode: "always" | "custom") {
    onChange(mode === "always" ? "always" : JSON.stringify(schedule));
  }

  function updateDay(day: WeekDayKey, update: Partial<DayHours>) {
    onChange(JSON.stringify({ ...schedule, [day]: { ...schedule[day], ...update } }));
  }

  return (
    <div className="weekly-hours">
      <div className="weekly-hours-mode" role="group" aria-label="Operating-hours mode">
        <button className={!custom ? "selected" : ""} onClick={() => setMode("always")} type="button">Always available</button>
        <button className={custom ? "selected" : ""} onClick={() => setMode("custom")} type="button">Weekly schedule</button>
      </div>
      {custom && (
        <div className="weekly-hours-days">
          {OPERATING_WEEK_DAYS.map(({ key, label }) => {
            const hours = schedule[key];
            return (
              <div className={`weekly-hours-row ${hours.enabled ? "" : "disabled"}`} key={key}>
                <label>
                  <input checked={hours.enabled} onChange={(event) => updateDay(key, { enabled: event.target.checked })} type="checkbox" />
                  <span>{label}</span>
                </label>
                {hours.enabled ? (
                  <div>
                    <input aria-label={`${label} opening time`} type="time" value={hours.start} onChange={(event) => updateDay(key, { start: event.target.value })} />
                    <span>to</span>
                    <input aria-label={`${label} closing time`} type="time" value={hours.end} onChange={(event) => updateDay(key, { end: event.target.value })} />
                  </div>
                ) : <small>Closed</small>}
              </div>
            );
          })}
        </div>
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

  return (
    <>
      <StudioHeading eyebrow="Knowledge" title="Ground the agent in approved information" description="Add notes directly or index business documents for semantic retrieval." />
      <div className="knowledge-overview">
        <div><strong>{knowledgeBase.length.toLocaleString()}</strong><small>of 10,000 manual characters</small></div>
        <div><strong>{chunks.length}</strong><small>manual knowledge chunks</small></div>
        <div><strong>{promptCharacters.toLocaleString()}</strong><small>manual characters per call</small></div>
      </div>
      <div className="knowledge-budget"><span style={{ width: `${Math.min(100, (knowledgeBase.length / 10000) * 100)}%` }} /></div>
      <label className="knowledge-text">
        Approved knowledge
        <textarea maxLength={10000} value={knowledgeBase} onChange={(event) => onKnowledgeBase(event.target.value)} placeholder={"Add short, factual sections separated by blank lines.\n\nServices\nConsultations are available Monday to Friday.\n\nCancellation policy\nPlease give at least 24 hours’ notice."} />
        <small>{knowledgeBase.length.toLocaleString()} / 10,000</small>
      </label>
      <div className={`knowledge-budget-note ${omitted ? "warning" : ""}`}>
        <ShieldCheck size={18} />
        <div>
          <strong>{omitted ? "Some manual knowledge will be held outside the live prompt" : "Within the manual prompt budget"}</strong>
          <p>Sauti injects at most 3,000 manual characters per call. Uploaded documents are searched separately and only relevant excerpts are added.</p>
        </div>
      </div>
      <div className="knowledge-chunks">
        <header><div><strong>Manual chunk preview</strong><p>This is how directly entered knowledge is divided.</p></div><span>{chunks.length} {chunks.length === 1 ? "chunk" : "chunks"}</span></header>
        {chunks.length === 0 ? (
          <div className="knowledge-empty"><BookOpen size={20} /><span>Add knowledge above to preview its chunks.</span></div>
        ) : chunks.map((chunk, index) => {
          const offset = chunks.slice(0, index).reduce((total, item) => total + item.length, 0);
          const status = offset >= 3000 ? "Held back" : offset + chunk.length > 3000 ? "Partially included" : "Included";
          return (
            <details key={`${index}-${chunk.slice(0, 20)}`} open={index === 0}>
              <summary><span>Chunk {index + 1}</span><small>{chunk.length} characters · {status}</small><ChevronDown size={15} /></summary>
              <p>{chunk}</p>
            </details>
          );
        })}
      </div>
      <div className="document-knowledge">
        <header>
          <div><strong>Document knowledge</strong><p>Relevant excerpts are retrieved with pgvector for every caller turn.</p></div>
          {agentId ? (
            <label className={documentBusy ? "disabled" : ""}>
              {documentBusy ? <LoaderCircle className="spin" size={16} /> : <FileUp size={16} />}
              {documentBusy ? "Indexing…" : "Upload document"}
              <input
                accept=".pdf,.doc,.docx,.txt,.md,.csv,.html,.htm"
                disabled={documentBusy}
                onChange={(event) => {
                  void addDocument(event.target.files?.[0]);
                  event.currentTarget.value = "";
                }}
                type="file"
              />
            </label>
          ) : <button disabled type="button"><FileUp size={16} /> Save agent before uploading</button>}
        </header>
        <p className="document-formats">PDF, Word, text, Markdown, CSV, and HTML · 10 MB maximum · 20 documents per agent</p>
        {documentError && <div className="document-error">{documentError}</div>}
        {documents.length === 0 ? (
          <div className="document-empty"><FileText size={22} /><strong>No indexed documents</strong><span>Upload policies, service catalogues, FAQs, or operating procedures.</span></div>
        ) : (
          <div className="document-list">
            {documents.map((document) => (
              <article key={document.id}>
                <span><FileText size={18} /></span>
                <div>
                  <strong>{document.fileName}</strong>
                  <small>
                    {document.chunkCount} chunks · {document.characterCount.toLocaleString()} characters
                    {document.originalStored && document.originalSizeBytes ? ` · Original secured (${formatFileSize(document.originalSizeBytes)})` : ""}
                  </small>
                </div>
                <i>{document.status}</i>
                <button aria-label={`Delete ${document.fileName}`} disabled={documentBusy} onClick={() => void removeDocument(document.id)} type="button"><Trash2 size={15} /></button>
              </article>
            ))}
          </div>
        )}
      </div>
    </>
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

function PersonalisationDrawer({
  agentSaved,
  variables,
  values,
  countryName,
  onChange,
  onAdd,
  onClose,
  onSave,
}: {
  agentSaved: boolean;
  variables: AgentVariableDefinition[];
  values: Record<string, string>;
  countryName: string;
  onChange: (key: string, value: string) => void;
  onAdd: (variable: CreateAgentVariable) => void;
  onClose: () => void;
  onSave: () => Promise<void>;
}) {
  const completed = variables.filter((variable) => values[variable.key]?.trim()).length;
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  async function finish() {
    setSaving(true);
    setSaveError("");
    try {
      await onSave();
    } catch (caught) {
      setSaveError(caught instanceof Error ? caught.message : "Unable to save business details.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="personalisation-drawer-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <aside aria-label="Agent personalisation" className="personalisation-drawer">
        <header>
          <div className="personalisation-heading"><span><Sparkles size={13} /> Business details</span><h2>Personalise this agent</h2><p>Give this agent the facts it should use during every conversation. Values are inserted wherever matching <code>{"{{variables}}"}</code> appear.</p></div>
          <button aria-label="Close personalisation" onClick={onClose} type="button"><X size={19} /></button>
        </header>
        <div className="personalisation-body">
          <div className="personalisation-progress">
            <div><strong>{completed} of {variables.length}</strong><span>details completed</span></div>
            <span>{variables.length ? Math.round(completed / variables.length * 100) : 0}%</span>
            <i><span style={{ width: `${variables.length ? completed / variables.length * 100 : 0}%` }} /></i>
          </div>
          <AddVariableForm onAdd={onAdd} />
          {variables.length ? (
            <div className="personalisation-drawer-fields">
              {variables.map((variable) => (
                <VariableValueField
                  countryName={countryName}
                  key={variable.key}
                  variable={variable}
                  value={values[variable.key] ?? ""}
                  onChange={(value) => {
                    onChange(variable.key, value);
                    if (variable.key === "calendar_provider") {
                      onChange("routing_policy", value === "Set up later" ? "Set up later" : "Fixed calendar");
                    }
                  }}
                />
              ))}
            </div>
          ) : <div className="personalisation-empty">No business details yet. Add a variable to reference it from the prompt.</div>}
          {saveError && <div className="personalisation-save-error"><CircleAlert size={16} /> {saveError}</div>}
        </div>
        <footer>
          <span>{agentSaved ? "Save these details before starting a new test call." : "These details will be saved when you create the agent."}</span>
          <button className="console-primary-button" disabled={saving} onClick={() => void finish()} type="button">{saving ? <><LoaderCircle className="spin" size={16} /> Saving...</> : agentSaved ? "Save details" : "Done"}</button>
        </footer>
      </aside>
    </div>
  );
}

function IntegrationsSettings({ agentId, bookingEnabled }: { agentId?: string; bookingEnabled: boolean }) {
  const [calendarIntegration, setCalendarIntegration] = useState<AgentIntegration | null>(null);

  useEffect(() => {
    if (!agentId) return;
    getAgentIntegrations(agentId)
      .then((integrations) => setCalendarIntegration(
        integrations.find((integration) => integration.provider === "google_calendar") ?? null,
      ))
      .catch(() => setCalendarIntegration(null));
  }, [agentId]);

  const calendarConnected = calendarIntegration?.connectionStatus === "connected";
  const calendarEnabled = calendarConnected && calendarIntegration?.enabled === true;
  const calendarLabel = calendarEnabled
    ? "Connected"
    : calendarConnected ? "Connected · disabled" : "Not connected";

  return (
    <>
      <StudioHeading eyebrow="Tools & integrations" title="Connect actions this agent can perform" description="Integrations provide live data and controlled actions during a call." />
      {!agentId && (
        <div className="integration-save-first"><CircleAlert size={18} /><div><strong>Save the agent first</strong><p>A saved agent ID is required before credentials can be connected securely.</p></div></div>
      )}
      <div className="studio-integration-list">
        <article>
          <span className="integration-card-icon"><CalendarCheck size={21} /></span>
          <div><h3>Google Calendar</h3><p>Check availability and create confirmed booking events during calls.</p><small>{bookingEnabled ? "Used by appointment-booking tools" : "Enable appointment booking to use this tool"}</small></div>
          <i className={calendarEnabled ? "connected" : ""}>{calendarLabel}</i>
          {agentId
            ? <Link href={`/dashboard/integrations?provider=google_calendar&agentId=${agentId}`}>{calendarConnected ? "Manage" : "Connect"} <ExternalLink size={14} /></Link>
            : <button disabled type="button">Connect after saving</button>}
        </article>
        <article>
          <span className="integration-card-icon"><Webhook size={21} /></span>
          <div><h3>Custom webhook</h3><p>Call your own API for availability, CRM actions, or business workflows.</p><small>Developer tool</small></div>
          <i>Not configured</i>
          {agentId
            ? <Link href={`/dashboard/integrations?provider=webhook&agentId=${agentId}`}>Configure <ExternalLink size={14} /></Link>
            : <button disabled type="button">Configure after saving</button>}
        </article>
      </div>
    </>
  );
}

const SERVICE_VARIABLE_KEYS = new Set(["services", "services_and_prices", "treatments", "bookable_services"]);
const WEEK_DAYS = [
  ["Mon", "Monday"],
  ["Tue", "Tuesday"],
  ["Wed", "Wednesday"],
  ["Thu", "Thursday"],
  ["Fri", "Friday"],
  ["Sat", "Saturday"],
  ["Sun", "Sunday"],
] as const;

type ScheduleDay = { enabled: boolean; open: string; close: string };
type WeeklySchedule = Record<(typeof WEEK_DAYS)[number][0], ScheduleDay>;

function VariableValueField({
  variable,
  value,
  countryName,
  onChange,
}: {
  variable: AgentVariableDefinition;
  value: string;
  countryName: string;
  onChange: (value: string) => void;
}) {
  const kind = variableKind(variable.key);
  const structured = structuredAgentSetting(variable.key);
  const filled = Boolean(value.trim());
  const error = variableValueError(variable, value);
  return (
    <div className={`personalise-field ${error ? "invalid" : ""}`}>
      <span className="personalise-field-label">
        {variable.label}
        <i className={filled && !error ? "filled" : ""}>{error ? "Check value" : filled ? "Complete" : variable.required ? "Required" : "Optional"}</i>
      </span>
      {structured ? (
        <StructuredVariableInput config={structured} value={value} onChange={onChange} />
      ) : kind === "services" ? (
        <ServiceChipInput value={value} onChange={onChange} />
      ) : kind === "hours" ? (
        <WeeklyHoursInput value={value} onChange={onChange} />
      ) : (
        <input
          inputMode={kind === "phone" ? "tel" : undefined}
          type={kind === "phone" ? "tel" : "text"}
          value={value}
          placeholder={variablePlaceholder(variable, kind, countryName)}
          onChange={(event) => onChange(kind === "phone" ? sanitizePhone(event.target.value) : event.target.value)}
        />
      )}
      <small className={error ? "field-error" : ""}>{error || variable.description}</small>
    </div>
  );
}

function StructuredVariableInput({
  config,
  value,
  onChange,
}: {
  config: StructuredAgentSetting;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="structured-variable-options" role="radiogroup" aria-label={config.title}>
      {config.options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            aria-checked={selected}
            className={selected ? "selected" : ""}
            key={option.value}
            onClick={() => onChange(option.value)}
            role="radio"
            type="button"
          >
            <span>{selected && <Check size={13} />}</span>
            <span><strong>{option.label}</strong><small>{option.description}</small></span>
          </button>
        );
      })}
    </div>
  );
}

function ServiceChipInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const [draft, setDraft] = useState("");
  const services = value.split(",").map((service) => service.trim()).filter(Boolean);

  function addService() {
    const service = draft.trim().replace(/,+$/g, "");
    if (!service || services.some((current) => current.toLowerCase() === service.toLowerCase())) return;
    onChange([...services, service].join(", "));
    setDraft("");
  }

  return (
    <div className="service-chip-editor">
      {services.length > 0 && (
        <div className="service-chip-list">
          {services.map((service) => (
            <span key={service}>{service}<button aria-label={`Remove ${service}`} onClick={() => onChange(services.filter((item) => item !== service).join(", "))} type="button"><X size={13} /></button></span>
          ))}
        </div>
      )}
      <div className="service-chip-entry">
        <input
          value={draft}
          placeholder="Enter a service"
          onChange={(event) => setDraft(event.target.value.replace(/,/g, ""))}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              addService();
            }
          }}
        />
        <button disabled={!draft.trim()} onClick={addService} type="button"><Plus size={15} /> Add</button>
      </div>
    </div>
  );
}

function WeeklyHoursInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const schedule = parseSchedule(value);

  function update(day: keyof WeeklySchedule, patch: Partial<ScheduleDay>) {
    onChange(serializeSchedule({ ...schedule, [day]: { ...schedule[day], ...patch } }));
  }

  function setWeekdays() {
    const next = emptySchedule();
    for (const [day] of WEEK_DAYS.slice(0, 5)) next[day] = { enabled: true, open: "09:00", close: "17:00" };
    onChange(serializeSchedule(next));
  }

  return (
    <div className="weekly-hours-editor">
      <div className="weekly-hours-head"><span>Weekly schedule</span><button onClick={setWeekdays} type="button">Set weekday hours</button></div>
      {WEEK_DAYS.map(([day, label]) => {
        const current = schedule[day];
        return (
          <div className="weekly-hours-row" key={day}>
            <button
              aria-pressed={current.enabled}
              className={current.enabled ? "enabled" : ""}
              onClick={() => update(day, { enabled: !current.enabled })}
              type="button"
            >
              {current.enabled && <Check size={12} />}
            </button>
            <strong>{label}</strong>
            {current.enabled ? (
              <div><input aria-label={`${label} opening time`} type="time" value={current.open} onChange={(event) => update(day, { open: event.target.value })} /><span>to</span><input aria-label={`${label} closing time`} type="time" value={current.close} onChange={(event) => update(day, { close: event.target.value })} /></div>
            ) : <small>Closed</small>}
          </div>
        );
      })}
    </div>
  );
}

function variableKind(key: string) {
  if (SERVICE_VARIABLE_KEYS.has(key)) return "services";
  if (key.includes("hours")) return "hours";
  if (key.includes("phone") || key.endsWith("_number")) return "phone";
  if (key.includes("address") || key.includes("location")) return "address";
  return "text";
}

function variablePlaceholder(
  variable: AgentVariableDefinition,
  kind: ReturnType<typeof variableKind>,
  countryName: string,
) {
  if (kind === "phone") return "+[country code] [phone number]";
  if (kind === "address") return `Enter an address in ${countryName}`;
  return `Enter ${variable.label.toLowerCase()}`;
}

function variableValueError(variable: AgentVariableDefinition, value: string) {
  if (!value.trim()) return "";
  const kind = variableKind(variable.key);
  if (kind === "phone") {
    const digits = value.replace(/\D/g, "");
    if (digits.length < 7 || digits.length > 15) return "Enter a valid phone number with 7 to 15 digits.";
  }
  if (kind === "hours") {
    const invalidDay = Object.values(parseSchedule(value)).find(
      (day) => day.enabled && (!day.open || !day.close || day.close <= day.open),
    );
    if (invalidDay) return "Closing time must be later than opening time.";
  }
  return "";
}

function sanitizePhone(value: string) {
  const startsWithPlus = value.trimStart().startsWith("+");
  const digitsAndSpaces = value.replace(/[^\d ]/g, "").replace(/\s+/g, " ").trimStart();
  return `${startsWithPlus ? "+" : ""}${digitsAndSpaces}`;
}

function emptySchedule(): WeeklySchedule {
  return Object.fromEntries(WEEK_DAYS.map(([day]) => [day, { enabled: false, open: "09:00", close: "17:00" }])) as WeeklySchedule;
}

function parseSchedule(value: string): WeeklySchedule {
  const schedule = emptySchedule();
  for (const segment of value.split(";").map((item) => item.trim()).filter(Boolean)) {
    const match = segment.match(/^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+(\d{2}:\d{2})-(\d{2}:\d{2})$/);
    if (match) schedule[match[1] as keyof WeeklySchedule] = { enabled: true, open: match[2], close: match[3] };
  }
  return schedule;
}

function serializeSchedule(schedule: WeeklySchedule) {
  return WEEK_DAYS
    .filter(([day]) => schedule[day].enabled)
    .map(([day]) => `${day} ${schedule[day].open}-${schedule[day].close}`)
    .join("; ");
}

function StudioHeading({ eyebrow, title, description }: { eyebrow: string; title: string; description: string }) {
  return <div className="studio-heading"><span>{eyebrow}</span><h2>{title}</h2><p>{description}</p></div>;
}

function ToggleRow({ icon: Icon, title, detail, value, onChange, disabled = false }: { icon: LucideIcon; title: string; detail: string; value: boolean; onChange: (value: boolean) => void; disabled?: boolean }) {
  return <div className="studio-toggle-row"><span><Icon size={18} /></span><div><strong>{title}</strong><small>{detail}</small></div><button className={value ? "on" : ""} disabled={disabled} type="button" role="switch" aria-checked={value} onClick={() => onChange(!value)}><i /></button></div>;
}
